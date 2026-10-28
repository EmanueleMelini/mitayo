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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.rounded.ShuffleOn
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.PlusOne
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
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
import it.emanuelemelini.mitayo.data.DeckFilters
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
            container.swipeHistory,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Your own list: left doesn't skip, it only moves on
    val leftPasses = category.isOwnList
    // The watching deck: ▶ adds the episode just seen
    val addsEpisodes = category == Category.OfStatus(ListStatus.WATCHING)
    // The deck to score: right and up ask for the score, nothing else
    val rates = category == Category.Unrated
    var showFilters by rememberSaveable { mutableStateOf(false) }
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
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                    }
                    IconButton(onClick = viewModel::toggleShuffle) {
                        Icon(
                            imageVector = if (state.shuffled) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle,
                            contentDescription = stringResource(if (state.shuffled) R.string.action_shuffle_off else R.string.action_shuffle_on),
                            tint = if (state.shuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = { if (state.filters.active) Badge() }) {
                            Icon(Icons.Rounded.FilterList, contentDescription = stringResource(R.string.action_filters))
                        }
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
                            // Nothing with these filters: they can go at once
                            DeckBody.DONE -> if (state.filters.active) {
                                Message(
                                    icon = Icons.Rounded.FilterListOff,
                                    title = stringResource(R.string.deck_filtered_title),
                                    body = stringResource(R.string.deck_filtered_body),
                                    action = stringResource(R.string.filters_reset),
                                    onAction = { viewModel.setFilters(DeckFilters()) },
                                )
                            // A deck by status passed through: the passed cards can come back
                            } else if (state.passed > 0) {
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
                                rates = rates,
                                scope = scope,
                                onSwiped = { card, direction -> viewModel.swipe(card, direction) },
                                onTap = viewModel::openDetails,
                                onOtherStatus = { card, status ->
                                    scope.launch { if (topState.fling(SwipeDirection.UP)) viewModel.setStatus(card, status) }
                                },
                            )
                        }
                    }
                }
                ActionBar(
                    topState = topState.takeIf { top != null },
                    hasCard = top != null,
                    leftPasses = leftPasses,
                    addsEpisodes = addsEpisodes,
                    rates = rates,
                    planToWatch = top?.anime?.notYetAired == true,
                    canUndo = state.canUndo,
                    // An anime still airing can't be completed, with or without a score
                    canScore = top != null && !top.anime.isAiring,
                    onSwipe = { swipeTop(it) },
                    onAddEpisode = { top?.let(viewModel::addEpisode) },
                    onUndo = viewModel::undo,
                    onScore = { scoringId = top?.anime?.id },
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
            state.finishPrompt?.let { card ->
                AlertDialog(
                    onDismissRequest = viewModel::closeFinishPrompt,
                    title = { Text(stringResource(R.string.finish_title)) },
                    text = { Text(stringResource(R.string.finish_text, card.anime.displayTitle(state.titles))) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.closeFinishPrompt()
                            swipeTop(SwipeDirection.RIGHT)
                        }) { Text(stringResource(R.string.finish_completed)) }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::keepWatching) { Text(stringResource(R.string.finish_keep_watching)) }
                    },
                )
            }
            if (state.showGuide && top != null) SwipeGuide(leftPasses, rates, onDismiss = viewModel::onGuideSeen)
        }
    }

    if (showFilters) {
        DeckFiltersSheet(
            filters = state.filters,
            onChange = viewModel::setFilters,
            onDismiss = {
                showFilters = false
                viewModel.onFiltersClosed()
            },
        )
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
    state.scorePrompt?.let { prompt ->
        ScoreDialog(
            card = prompt.card,
            titles = state.titles,
            onConfirm = viewModel::confirmScore,
            onDismiss = viewModel::cancelScore,
            onlyScore = true,
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
    rates: Boolean,
    scope: CoroutineScope,
    onSwiped: (DeckCard, SwipeDirection) -> Unit,
    onTap: (DeckCard) -> Unit,
    /** The ⋯ button on the top card. */
    onOtherStatus: (DeckCard, ListStatus) -> Unit,
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
                        overlay = {
                            if (isTop) {
                                SwipeStamps(topState, leftPasses, planToWatch = card.anime.notYetAired, rates = rates)
                                CardButtons(
                                    state = topState,
                                    otherStatuses = OTHER_STATUSES.filter { it != card.listStatus },
                                    onOtherStatus = { onOtherStatus(card, it) },
                                    onInfo = { onTap(card) },
                                )
                            }
                        },
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

/**
 * Small, big, medium, big, small around the centre: undo, left, up, right, score. ⋯ and ⓘ are on
 * the card ([CardButtons]).
 */
@Composable
private fun ActionBar(
    topState: SwipeState?,
    hasCard: Boolean,
    /** A deck by status: the left button moves on instead of skipping. */
    leftPasses: Boolean,
    /** The watching deck: ▶ is +1, without the card leaving. */
    addsEpisodes: Boolean,
    /** The deck to score: only "next" and a big ★, which swipes right. */
    rates: Boolean,
    /** The top card is only announced: right sets plan to watch, ▶ isn't possible. */
    planToWatch: Boolean,
    canUndo: Boolean,
    canScore: Boolean,
    onSwipe: (SwipeDirection) -> Unit,
    onAddEpisode: () -> Unit,
    onUndo: () -> Unit,
    onScore: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ActionBarHeight).navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideButton(
            icon = Icons.AutoMirrored.Rounded.Undo,
            description = stringResource(R.string.action_undo),
            enabled = canUndo,
            onClick = onUndo,
        )
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
        if (rates) {
            ActionButton(
                icon = Icons.Rounded.Star,
                color = MitayoColors.Kin,
                size = 64.dp,
                progress = topState?.let { maxOf(it.progress(SwipeDirection.RIGHT), it.progress(SwipeDirection.UP)) } ?: 0f,
                description = stringResource(R.string.action_score),
                onClick = { onSwipe(SwipeDirection.RIGHT) },
            )
            return@Row
        }
        if (addsEpisodes) {
            // The swipe up still asks the episodes: this button doesn't fill with it
            ActionButton(
                icon = Icons.Rounded.PlusOne,
                color = MitayoColors.Kin,
                size = 52.dp,
                progress = 0f,
                description = stringResource(R.string.action_add_episode),
                onClick = onAddEpisode,
            )
        } else {
            ActionButton(
                icon = Icons.Rounded.PlayArrow,
                color = MitayoColors.Kin,
                size = 52.dp,
                progress = if (planToWatch) 0f else topState?.progress(SwipeDirection.UP) ?: 0f,
                description = stringResource(R.string.action_watching),
                enabled = !planToWatch,
                onClick = { onSwipe(SwipeDirection.UP) },
            )
        }
        ActionButton(
            icon = if (planToWatch) Icons.Rounded.BookmarkAdd else Icons.Rounded.Check,
            color = if (planToWatch) MitayoColors.Sakura else MitayoColors.Matcha,
            size = 64.dp,
            progress = topState?.progress(SwipeDirection.RIGHT) ?: 0f,
            description = stringResource(if (planToWatch) R.string.action_plan_to_watch else R.string.action_completed),
            onClick = { onSwipe(SwipeDirection.RIGHT) },
        )
        SideButton(
            icon = Icons.Rounded.Star,
            description = stringResource(R.string.action_score),
            enabled = canScore,
            tint = MitayoColors.Kin,
            onClick = onScore,
        )
    }
}

/** The two small buttons at the ends of the bar, on a neutral background. */
@Composable
private fun SideButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = tint,
        ),
        modifier = Modifier.size(44.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

/** The statuses no swipe gives: on hold, dropped, plan to watch (without the card's own). */
private val OTHER_STATUSES = listOf(ListStatus.ON_HOLD, ListStatus.DROPPED, ListStatus.PLAN_TO_WATCH)

/** ⋯ and ⓘ in the corner of the top card: they fade while it's dragged, as the stamps come in. */
@Composable
private fun BoxScope.CardButtons(
    state: SwipeState,
    otherStatuses: List<ListStatus>,
    onOtherStatus: (ListStatus) -> Unit,
    onInfo: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp)
            .graphicsLayer { alpha = 1f - state.maxProgress.coerceIn(0f, 1f) },
    ) {
        OtherStatusesButton(otherStatuses, onSelect = onOtherStatus)
        GlassButton(Icons.Rounded.Info, stringResource(R.string.action_details), onClick = onInfo)
    }
}

@Composable
private fun OtherStatusesButton(statuses: List<ListStatus>, onSelect: (ListStatus) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassButton(Icons.Rounded.MoreHoriz, stringResource(R.string.action_other_statuses), onClick = { open = true })
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

/** Dark and see-through, readable on any poster: the card's own text is light on dark too. */
@Composable
private fun GlassButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MitayoColors.Sumi.copy(alpha = 0.55f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
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
    enabled: Boolean = true,
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
        enabled = enabled,
        shape = CircleShape,
        color = color.copy(alpha = 0.10f + 0.55f * progress),
        border = BorderStroke(2.dp, color),
        interactionSource = interactionSource,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                alpha = if (enabled) 1f else DISABLED_ALPHA
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

/** A round button that can't be used now, as Material dims them. */
private const val DISABLED_ALPHA = 0.38f
