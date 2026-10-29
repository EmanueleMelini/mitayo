package it.emanuelemelini.mitayo.ui.anilist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.anilist.SyncNotice
import it.emanuelemelini.mitayo.data.anilist.syncNotices
import it.emanuelemelini.mitayo.ui.aniListErrorText
import it.emanuelemelini.mitayo.ui.appContainer
import kotlinx.coroutines.delay

/**
 * The notices of the AniList syncs, one at a time over any screen of the app: tapping one opens
 * the AniList page, or the session in the log when it happened while the app was away.
 */
@Composable
fun SyncNoticeHost(onOpenAniList: () -> Unit, onOpenSession: (Long) -> Unit, modifier: Modifier = Modifier) {
    val aniListSync = appContainer().aniListSync
    if (!aniListSync.enabled) return
    val queue = remember { mutableStateListOf<SyncNotice>() }
    val lifecycleOwner = LocalLifecycleOwner.current
    // What ended before the app came to the foreground ended while it was away
    var foregroundSince by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) foregroundSince = System.currentTimeMillis()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // A dialog or a sheet takes the focus (the new terms, the changelog, the details): the notices wait
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(lifecycleOwner, focused) {
        if (!focused) return@LaunchedEffect
        // A dialog shown as the app opens takes the focus a moment later
        delay(FOCUS_SETTLE_MS)
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            aniListSync.unnoticedSessions.collect { sessions ->
                for (summary in sessions) {
                    val (notices, notice) = syncNotices(summary, foregroundSince)
                    queue += notices
                    if (notice > summary.session.notice) aniListSync.markNoticed(summary.session.id, notice)
                }
            }
        }
    }
    // Behind a dialog a notice neither shows nor runs out
    val current = queue.firstOrNull()?.takeIf { focused }
    LaunchedEffect(current) {
        if (current == null) return@LaunchedEffect
        delay(NOTICE_MS)
        queue.remove(current)
    }
    // The last one stays on screen while it slides away
    var shown by remember { mutableStateOf<SyncNotice?>(null) }
    if (current != null) shown = current
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val notice = shown ?: return@AnimatedVisibility
        NoticeCard(
            notice = notice,
            onClick = {
                queue.remove(notice)
                if (notice.away) onOpenSession(notice.sessionId) else onOpenAniList()
            },
            onClose = { queue.remove(notice) },
        )
    }
}

@Composable
private fun NoticeCard(notice: SyncNotice, onClick: () -> Unit, onClose: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
            Icon(noticeIcon(notice), contentDescription = null, tint = MaterialTheme.colorScheme.inversePrimary)
            Spacer(Modifier.width(12.dp))
            Text(
                text = noticeText(notice),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
            }
        }
    }
}

@Composable
private fun noticeText(notice: SyncNotice): String = when (notice) {
    is SyncNotice.Running -> pluralStringResource(R.plurals.anilist_notice_running, notice.remaining, notice.remaining)
    is SyncNotice.Finished -> {
        val done = listOfNotNull(
            notice.toAniList.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_summary_to_anilist, it, it) },
            notice.toMal.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_summary_to_mal, it, it) },
        ).joinToString(", ")
        when {
            done.isEmpty() -> stringResource(if (notice.away) R.string.anilist_notice_done_away else R.string.anilist_notice_done)
            notice.away -> stringResource(R.string.anilist_notice_done_away_summary, done)
            else -> stringResource(R.string.anilist_notice_done_summary, done)
        }
    }
    is SyncNotice.Failed -> stringResource(R.string.anilist_notice_failed, aniListErrorText(notice.errorCode).asString())
    is SyncNotice.NotFound -> pluralStringResource(R.plurals.anilist_notice_not_found, notice.count, notice.count)
    is SyncNotice.Found -> if (notice.count == 1 && notice.title != null) {
        stringResource(R.string.anilist_event_found, notice.title)
    } else {
        pluralStringResource(R.plurals.anilist_notice_found, notice.count, notice.count)
    }
}

private fun noticeIcon(notice: SyncNotice): ImageVector = when (notice) {
    is SyncNotice.Running -> Icons.Rounded.CloudSync
    is SyncNotice.Finished -> Icons.Rounded.CloudDone
    is SyncNotice.Failed -> Icons.Rounded.SyncProblem
    is SyncNotice.NotFound -> Icons.Rounded.LinkOff
    is SyncNotice.Found -> Icons.Rounded.Link
}

/** How long a notice stays. */
private const val NOTICE_MS = 5_000L

/** How long the window keeps the focus before the notices count it as free of dialogs. */
private const val FOCUS_SETTLE_MS = 600L
