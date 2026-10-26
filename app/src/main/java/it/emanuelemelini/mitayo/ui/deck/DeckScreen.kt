package it.emanuelemelini.mitayo.ui.deck

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.imageLoader
import coil3.request.ImageRequest
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.mal.MalListStatus
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.categories.title
import it.emanuelemelini.mitayo.ui.components.countUp
import it.emanuelemelini.mitayo.ui.components.listStatusIcon
import it.emanuelemelini.mitayo.ui.components.listStatusLabel
import it.emanuelemelini.mitayo.ui.components.malEntryLine
import it.emanuelemelini.mitayo.ui.components.staggeredEntrance
import it.emanuelemelini.mitayo.ui.theme.MitayoColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private val ActionBarHeight = 96.dp
private val DeckPadding = 16.dp

/** Cards stacked under the top one. */
private const val VISIBLE_CARDS = 3

/** Widest card, as a fraction of its height. */
private const val MAX_CARD_ASPECT = 0.72f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(categoryKey: String, onBack: () -> Unit) {
    val category = remember(categoryKey) { Category.fromKey(categoryKey) ?: Category.Popular }
    val container = appContainer()
    val viewModel: DeckViewModel = viewModel {
        DeckViewModel(
            category,
            container.deckRepository,
            container.listRepository,
            container.catalogRepository,
            container.settingsRepository,
            container.syncManager,
            container.telemetry,
            container.statusEditor,
            container.otherLists,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Your own list: left doesn't skip, it only moves on
    val leftPasses = category is Category.OfStatus
    val details by viewModel.details.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) } }

    // Few cards left: more of the catalog, before the deck runs dry
    LaunchedEffect(state.loaded, state.cards.size, state.catalogSyncing) {
        if (state.loaded && state.cards.size < DeckViewModel.LOW_WATER) viewModel.topUp()
    }

    // The next posters are ready before they come to the top
    LaunchedEffect(state.cards.take(VISIBLE_CARDS + 3).map { it.anime.id }) {
        state.cards.take(VISIBLE_CARDS + 3).forEach { card ->
            card.anime.pictureUrl?.let { context.imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(category.title(state.genreName), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.loaded) {
                            Text(
                                text = countUp(state.remaining, label = "remaining").let { remaining ->
                                    pluralStringResource(R.plurals.deck_remaining, remaining, remaining)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.loadingMore) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val density = LocalDensity.current
            val areaHeight = maxHeight - ActionBarHeight - DeckPadding / 2
            // Poster-shaped on tablets and in landscape, full width on phones
            val cardWidth = minOf(maxWidth - DeckPadding * 2, areaHeight * MAX_CARD_ASPECT)
            val deckWidth = with(density) { cardWidth.toPx() }
            val deckHeight = with(density) { areaHeight.toPx() }
            val scope = rememberCoroutineScope()
            val top = state.cards.firstOrNull()
            val topState = remember(top?.anime?.id, deckWidth, deckHeight) {
                val returning = state.returning?.takeIf { it.animeId == top?.anime?.id }
                SwipeState(
                    width = deckWidth,
                    height = deckHeight,
                    initialOffset = returning?.let { SwipeState.offscreen(it.direction, deckWidth, deckHeight) } ?: Offset.Zero,
                )
            }
            ArmedHaptics(topState)
            LaunchedEffect(topState) {
                // An undone card flies back in
                if (topState.offset.value != Offset.Zero) {
                    viewModel.onReturnShown()
                    topState.settle()
                }
            }

            fun swipeTop(direction: SwipeDirection, score: Int? = null) {
                val card = top ?: return
                scope.launch { if (topState.fling(direction)) viewModel.swipe(card, direction, score) }
            }
            var scoringId by rememberSaveable { mutableStateOf<Int?>(null) }

            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = DeckPadding, end = DeckPadding, top = DeckPadding / 2)
                ) {
                    val body = when {
                        !state.loaded -> DeckBody.LOADING
                        top == null && (state.loadingMore || state.catalogSyncing) -> DeckBody.LOADING_MORE
                        top == null && state.error != null -> DeckBody.ERROR
                        top == null -> DeckBody.DONE
                        else -> DeckBody.CARDS
                    }
                    // Loading, cards and "finished" fade into each other instead of switching at once
                    AnimatedContent(
                        targetState = body,
                        transitionSpec = { fadeIn(tween(BODY_FADE_MS)) togetherWith fadeOut(tween(BODY_FADE_MS)) },
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                        label = "deck_body",
                    ) { shown ->
                        when (shown) {
                            DeckBody.LOADING -> Loading(null)
                            DeckBody.LOADING_MORE -> Loading(stringResource(R.string.deck_loading))
                            DeckBody.ERROR -> Message(
                                icon = null,
                                title = state.error?.asString().orEmpty(),
                                body = null,
                                action = stringResource(R.string.action_retry),
                                onAction = viewModel::retry,
                            )
                            // A deck by status passed through: the passed cards can come back
                            DeckBody.DONE -> if (state.passed > 0) {
                                Message(
                                    icon = Icons.Rounded.Replay,
                                    title = stringResource(R.string.deck_passed_title),
                                    body = stringResource(R.string.deck_passed_body),
                                    action = stringResource(R.string.deck_start_over),
                                    onAction = viewModel::startOver,
                                )
                            } else {
                                Message(
                                    icon = Icons.Rounded.Celebration,
                                    title = stringResource(R.string.deck_done_title),
                                    body = stringResource(R.string.deck_done_body),
                                    action = stringResource(R.string.deck_done_back),
                                    onAction = onBack,
                                )
                            }
                            DeckBody.CARDS -> CardStack(
                                modifier = Modifier.fillMaxHeight().width(cardWidth),
                                cards = state.cards.take(VISIBLE_CARDS),
                                topState = topState,
                                titles = state.titles,
                                otherUser = (category as? Category.OfUser)?.userName,
                                others = state.others,
                                leftPasses = leftPasses,
                                scope = scope,
                                onSwiped = { card, direction -> viewModel.swipe(card, direction) },
                                onTap = viewModel::openDetails,
                            )
                        }
                    }
                }
                ActionBar(
                    topState = topState.takeIf { top != null },
                    hasCard = top != null,
                    leftPasses = leftPasses,
                    canUndo = state.canUndo,
                    // An anime still airing can't be completed, with or without a score
                    canScore = top != null && !top.anime.isAiring,
                    onSwipe = { swipeTop(it) },
                    onUndo = viewModel::undo,
                    onScore = { scoringId = top?.anime?.id },
                    otherStatuses = OTHER_STATUSES.filter { it != top?.listStatus },
                    onOtherStatus = { status ->
                        val card = top
                        if (card != null) scope.launch { if (topState.fling(SwipeDirection.UP)) viewModel.setStatus(card, status) }
                    },
                    onInfo = { top?.let(viewModel::openDetails) },
                )
                top?.takeIf { it.anime.id == scoringId }?.let { card ->
                    ScoreDialog(
                        card = card,
                        titles = state.titles,
                        onConfirm = { score ->
                            scoringId = null
                            swipeTop(SwipeDirection.RIGHT, score)
                        },
                        onDismiss = { scoringId = null },
                    )
                }
            }
        }
    }

    details?.let {
        AnimeDetailsSheet(
            state = it,
            titles = state.titles,
            onDismiss = viewModel::closeDetails,
            onSetRelatedStatus = viewModel::setRelatedStatus,
        )
    }
    val relatedPrompt by viewModel.relatedPrompt.collectAsStateWithLifecycle()
    relatedPrompt?.let { card ->
        EpisodesDialog(
            prompt = EpisodesPrompt(card, SwipeDirection.UP),
            titles = state.titles,
            onConfirm = viewModel::confirmRelatedEpisodes,
            onDismiss = viewModel::cancelRelatedEpisodes,
        )
    }
    state.episodesPrompt?.let { prompt ->
        EpisodesDialog(
            prompt = prompt,
            titles = state.titles,
            onConfirm = viewModel::confirmEpisodes,
            onDismiss = viewModel::cancelEpisodes,
        )
    }
}

@Composable
private fun CardStack(
    modifier: Modifier,
    cards: List<DeckCard>,
    topState: SwipeState,
    titles: TitleChoice,
    /** Another user's deck: their name and their entries, for the note on the cards. */
    otherUser: String?,
    others: Map<Int, MalListStatus>,
    leftPasses: Boolean,
    scope: CoroutineScope,
    onSwiped: (DeckCard, SwipeDirection) -> Unit,
    onTap: (DeckCard) -> Unit,
) {
    val density = LocalDensity.current
    val stepPx = with(density) { 14.dp.toPx() }
    Box(modifier) {
        // Drawn from the back: the top card is the last one
        for (index in cards.indices.reversed()) {
            val card = cards[index]
            key(card.anime.id) {
                val isTop = index == 0
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (isTop) {
                                Modifier.swipeGestures(topState, scope, onSwiped = { onSwiped(card, it) }, onTap = { onTap(card) })
                            } else {
                                Modifier
                            }
                        )
                ) {
                    AnimeCard(
                        card = card,
                        titles = titles,
                        note = otherUser?.let { name -> others[card.anime.id]?.let { "$name: ${malEntryLine(it)}" } },
                        modifier = Modifier.graphicsLayer {
                            if (isTop) {
                                translationX = topState.offset.value.x
                                translationY = topState.offset.value.y
                                rotationZ = topState.rotation
                            } else {
                                // Comes forward as the top card leaves
                                val depth = (index - topState.maxProgress).coerceAtLeast(0f)
                                val scale = 1f - 0.05f * depth
                                scaleX = scale
                                scaleY = scale
                                translationY = depth * stepPx
                            }
                        },
                        overlay = { if (isTop) SwipeStamps(topState, leftPasses) },
                    )
                }
            }
        }
    }
}

/**
 * The gestures sit on the card's static frame, not on the moving card, so the positions the
 * velocity tracker sees don't move with it.
 */
private fun Modifier.swipeGestures(
    state: SwipeState,
    scope: CoroutineScope,
    onSwiped: (SwipeDirection) -> Unit,
    onTap: () -> Unit,
): Modifier = this
    .pointerInput(state) {
        val tracker = VelocityTracker()
        detectDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragEnd = {
                val direction = state.releaseDirection(tracker.calculateVelocity())
                scope.launch {
                    if (direction == null) {
                        state.settle()
                    } else if (state.fling(direction)) {
                        onSwiped(direction)
                    }
                }
            },
            onDragCancel = { scope.launch { state.settle() } },
        ) { change, dragAmount ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            scope.launch { state.dragBy(dragAmount) }
        }
    }
    .pointerInput(state) { detectTapGestures(onTap = { onTap() }) }

/** A tick when the card crosses the threshold: released now, it goes. */
@Composable
private fun ArmedHaptics(state: SwipeState) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.armedDirection }
            .distinctUntilChanged()
            .drop(1)
            .collect { if (it != null) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }
}

@Composable
private fun ActionBar(
    topState: SwipeState?,
    hasCard: Boolean,
    /** A deck by status: the left button moves on instead of skipping. */
    leftPasses: Boolean,
    canUndo: Boolean,
    canScore: Boolean,
    onSwipe: (SwipeDirection) -> Unit,
    onUndo: () -> Unit,
    onScore: () -> Unit,
    otherStatuses: List<ListStatus>,
    onOtherStatus: (ListStatus) -> Unit,
    onInfo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ActionBarHeight).navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.action_undo))
        }
        // Finished deck: only undo is left
        if (!hasCard) return@Row
        ActionButton(
            icon = if (leftPasses) Icons.Rounded.SkipNext else Icons.Rounded.Close,
            color = if (leftPasses) MitayoColors.Murasaki else MitayoColors.Aka,
            size = 64.dp,
            progress = topState?.progress(SwipeDirection.LEFT) ?: 0f,
            description = stringResource(if (leftPasses) R.string.action_next else R.string.action_skip),
            onClick = { onSwipe(SwipeDirection.LEFT) },
        )
        ActionButton(
            icon = Icons.Rounded.PlayArrow,
            color = MitayoColors.Kin,
            size = 52.dp,
            progress = topState?.progress(SwipeDirection.UP) ?: 0f,
            description = stringResource(R.string.action_watching),
            onClick = { onSwipe(SwipeDirection.UP) },
        )
        ActionButton(
            icon = Icons.Rounded.Check,
            color = MitayoColors.Matcha,
            size = 64.dp,
            progress = topState?.progress(SwipeDirection.RIGHT) ?: 0f,
            description = stringResource(R.string.action_completed),
            onClick = { onSwipe(SwipeDirection.RIGHT) },
        )
        FilledTonalIconButton(onClick = onScore, enabled = canScore) {
            Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.action_score))
        }
        OtherStatusesButton(otherStatuses, enabled = topState != null, onSelect = onOtherStatus)
        FilledTonalIconButton(onClick = onInfo, enabled = topState != null) {
            Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.action_details))
        }
    }
}

/** The statuses no swipe gives: on hold, dropped, plan to watch (without the card's own). */
private val OTHER_STATUSES = listOf(ListStatus.ON_HOLD, ListStatus.DROPPED, ListStatus.PLAN_TO_WATCH)

@Composable
private fun OtherStatusesButton(statuses: List<ListStatus>, enabled: Boolean, onSelect: (ListStatus) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { open = true }, enabled = enabled && statuses.isNotEmpty()) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.action_other_statuses))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            statuses.forEach { status ->
                DropdownMenuItem(
                    text = { Text(listStatusLabel(status)) },
                    leadingIcon = { Icon(listStatusIcon(status), contentDescription = null) },
                    onClick = {
                        open = false
                        onSelect(status)
                    },
                )
            }
        }
    }
}

/**
 * Round button in the action's color: it fills up and grows a little while the card is dragged
 * its way, and gives way with a bounce when pressed.
 */
@Composable
private fun ActionButton(
    icon: ImageVector,
    color: Color,
    size: Dp,
    progress: Float,
    description: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color.copy(alpha = 0.10f + 0.55f * progress),
        border = BorderStroke(2.dp, color),
        interactionSource = interactionSource,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val scale = pressScale * (1f + 0.12f * progress)
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(size * 0.48f))
        }
    }
}

@Composable
private fun Loading(text: String?) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        if (text != null) {
            Spacer(Modifier.height(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Its pieces enter one after the other. */
@Composable
private fun Message(icon: ImageVector?, title: String, body: String?, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.staggeredEntrance(0).size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.staggeredEntrance(1))
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.staggeredEntrance(2),
            )
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.staggeredEntrance(3)) {
            if (icon != null) {
                Button(onClick = onAction) { Text(action) }
            } else {
                OutlinedButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

private enum class DeckBody { LOADING, LOADING_MORE, ERROR, DONE, CARDS }

private const val BODY_FADE_MS = 220
