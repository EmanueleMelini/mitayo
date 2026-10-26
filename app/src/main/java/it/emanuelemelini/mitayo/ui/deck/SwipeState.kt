package it.emanuelemelini.mitayo.ui.deck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign

enum class SwipeDirection { LEFT, RIGHT, UP }

/**
 * Position of the top card of a deck. Released past the threshold (or flung) it leaves in
 * that direction, otherwise it springs back. [width] and [height]: the deck area, in px.
 */
@Stable
class SwipeState(private val width: Float, private val height: Float, initialOffset: Offset = Offset.Zero) {

    val offset = Animatable(initialOffset, Offset.VectorConverter)

    /** Leaving: ignores further drags and buttons until the deck moves on. */
    var isLeaving by mutableStateOf(false)
        private set

    /** Where the card leans; up wins only when it's dragged more up than sideways. */
    val direction: SwipeDirection?
        get() {
            val x = offset.value.x
            val y = offset.value.y
            return when {
                y < 0f && -y > abs(x) -> SwipeDirection.UP
                x > 0f -> SwipeDirection.RIGHT
                x < 0f -> SwipeDirection.LEFT
                else -> null
            }
        }

    /** 0 to 1: 1 means "released now, it goes". */
    fun progress(direction: SwipeDirection): Float {
        if (this.direction != direction) return 0f
        val value = when (direction) {
            SwipeDirection.RIGHT -> offset.value.x / (width * HORIZONTAL_THRESHOLD)
            SwipeDirection.LEFT -> -offset.value.x / (width * HORIZONTAL_THRESHOLD)
            SwipeDirection.UP -> -offset.value.y / (height * VERTICAL_THRESHOLD)
        }
        return value.coerceIn(0f, 1f)
    }

    /** The largest progress in any direction: how much the next card comes forward. */
    val maxProgress: Float get() = direction?.let { progress(it) } ?: 0f

    /** The direction the card would go if released now. */
    val armedDirection: SwipeDirection? get() = direction?.takeIf { progress(it) >= 1f }

    /** Card rotation in degrees, leaning with the horizontal drag. */
    val rotation: Float get() = (offset.value.x / width * MAX_ROTATION).coerceIn(-MAX_ROTATION, MAX_ROTATION)

    suspend fun dragBy(delta: Offset) {
        if (!isLeaving) offset.snapTo(offset.value + delta)
    }

    /** At the end of a drag: where it goes, or null to spring back. */
    fun releaseDirection(velocity: Velocity): SwipeDirection? {
        armedDirection?.let { return it }
        val x = offset.value.x
        val y = offset.value.y
        return when {
            velocity.y < -FLING_VELOCITY && abs(velocity.y) > abs(velocity.x) && y < 0f -> SwipeDirection.UP
            abs(velocity.x) > FLING_VELOCITY && abs(velocity.x) > abs(velocity.y) && sign(velocity.x) == sign(x) ->
                if (x > 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
            else -> null
        }
    }

    /** Sends the card off screen. false when it was already leaving (double tap on a button). */
    suspend fun fling(direction: SwipeDirection): Boolean {
        if (isLeaving) return false
        isLeaving = true
        offset.animateTo(offscreen(direction, width, height, from = offset.value), tween(FLING_MS, easing = FastOutLinearInEasing))
        return true
    }

    suspend fun settle() {
        offset.animateTo(Offset.Zero, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow))
    }

    companion object {
        private const val HORIZONTAL_THRESHOLD = 0.3f
        private const val VERTICAL_THRESHOLD = 0.2f
        private const val MAX_ROTATION = 14f
        /** px/s: a quick flick is enough even before the threshold. */
        private const val FLING_VELOCITY = 1800f
        private const val FLING_MS = 240

        /** Off-screen position in [direction]; also where an undone card comes back from. */
        fun offscreen(direction: SwipeDirection, width: Float, height: Float, from: Offset = Offset.Zero): Offset =
            when (direction) {
                SwipeDirection.RIGHT -> Offset(width * 1.5f, from.y + height * 0.08f)
                SwipeDirection.LEFT -> Offset(-width * 1.5f, from.y + height * 0.08f)
                SwipeDirection.UP -> Offset(from.x, -height * 1.3f)
            }
    }
}
