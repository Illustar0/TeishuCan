package com.illustar0.teishucan.ui.component.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 让一组元素跟着布局目标过渡。目标一变，每个元素就从上一帧实际画出的样子出发，按同一个进度插值到新目标。
 *
 * 同一帧里所有元素读的是同一个 [progress]，彼此总是吻合。若各自独立做动画，元素之间可能差一帧，
 * 高度加起来就会来回跳。过渡中途目标又变时，新的过渡从当前画面接着走。
 */
@Stable
internal class FrameTransition<K, F>(
    private val from: Map<K, F>,
    private val interpolate: (start: F, target: F, progress: Float) -> F,
    private val onDrawn: (Map<K, F>) -> Unit,
) {
    private val position = Animatable(if (from.isEmpty()) 1f else 0f)
    private val time = Animatable(if (from.isEmpty()) 1f else 0f)

    /** 位移进度，按 [CalmMotion.spring] 从 0 走到 1。 */
    val progress: Float get() = position.value

    /** 时间进度，在 [CalmMotion.DurationMillis] 内从 0 匀速走到 1，供按时间安排的淡入淡出使用。 */
    val elapsed: Float get() = time.value

    suspend fun run() {
        coroutineScope {
            launch { position.animateTo(1f, CalmMotion.spring()) }
            time.animateTo(1f, tween(durationMillis = CalmMotion.DurationMillis, easing = LinearEasing))
        }
    }

    /** [key] 在这次过渡中的起点；新出现的元素没有起点，返回 null。 */
    fun startOf(key: K): F? = from[key]

    /**
     * 按 [targets] 算出各元素这一帧的样子：新出现的元素直接画在目标处，其余的按进度插值。
     * 算出的这一帧会记下来，作为下一次过渡的起点。
     */
    fun nextFrames(targets: Map<K, F>): Map<K, F> {
        val fraction = position.value
        val frames = targets.mapValues { (key, target) ->
            val start = from[key]
            if (start == null) target else interpolate(start, target, fraction)
        }
        onDrawn(frames)
        return frames
    }
}

/** [trigger] 每变一次就开始一次新的 [FrameTransition]；首次组合时直接画在目标处，不做过渡。 */
@Composable
internal fun <K, F> rememberFrameTransition(
    trigger: Any,
    interpolate: (start: F, target: F, progress: Float) -> F,
): FrameTransition<K, F> {
    val drawn = remember { DrawnFrames<K, F>() }
    val transition = remember(trigger) {
        FrameTransition(from = drawn.frames, interpolate = interpolate, onDrawn = { drawn.frames = it })
    }
    LaunchedEffect(transition) { transition.run() }
    return transition
}

/** 上一帧实际画出的样子。新的过渡开始时仍然保留，作为它的起点。 */
private class DrawnFrames<K, F> {
    var frames: Map<K, F> = emptyMap()
}
