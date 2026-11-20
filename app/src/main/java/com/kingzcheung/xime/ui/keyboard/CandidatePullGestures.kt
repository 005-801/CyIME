package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/*
 * 候选栏一边只有一个按钮（用户 11-20）：左边「<」取消，右边「⌄」收起键盘，展开候选不占按钮。
 * 候选栏这一行不滚动，放得下几个显示几个（用户 11-20：「显示多少就多少长度」）。
 * 展开：在候选栏上左右划，整行跟手但越拉越紧，拉过门槛松手就展开，不够就弹回（像安卓下拉通知栏）。
 * 收回：在展开页的候选上左右划，同样拉过门槛松手收回。返回键、选词照旧也收回。
 * 两个方向都算，不用记往哪边划。
 *
 * 不用系统的边缘拉伸回弹：11-05 用户录屏，有的手机回弹收不完，列表把每次按下都当成接着滑，点候选选不上。
 * 这里的回弹是自己画的位移，松手后 [RELEASE_MS] 内一定回到 0；回弹途中按下去就停住，点到的就是看到的那个候选，抬手再接着弹回。
 * 点候选、长按候选（删词、整句选段）不受影响：手指横着走过触摸阈值才算拉，之前照旧是点和长按。
 */

/** 松手后拉出的部分弹回原位的时长。 */
internal const val RELEASE_MS = 150

/** 手指走的距离只有这么多会变成位移（越拉越紧）。 */
internal const val PULL_RESISTANCE = 0.5f

/** 被拉出这么多（dp）松手就展开 / 收回。 */
internal const val PULL_THRESHOLD_DP = 48f

/** 最多被拉出门槛的这么多倍。 */
private const val PULL_MAX_FACTOR = 1.5f

/** 手指横着走了 [raw] 像素（带方向），整行被拉出多少像素（同方向）。 */
internal fun pulledDistance(raw: Float, threshold: Float): Float =
    sign(raw) * (abs(raw) * PULL_RESISTANCE).coerceAtMost(threshold * PULL_MAX_FACTOR)

/** 松手时拉出 [shown] 像素（哪个方向都算），要不要展开 / 收回。 */
internal fun pullReleases(shown: Float, threshold: Float): Boolean = threshold > 0f && abs(shown) >= threshold

/** 一次横拉：手指走的距离、拉出的位移、是否已过门槛、正在弹回的动画。 */
private class RubberBand(private val scope: CoroutineScope, private val haptic: HapticFeedback, val threshold: Float) {
    var shown by mutableFloatStateOf(0f)
    private var raw = 0f
    private var armed = false
    private var job: Job? = null

    /** 新的一下按下去：没收完的回弹停在原地（接着拉就从这里拉），点到的就是看到的那个候选。 */
    fun hold() {
        job?.cancel()
        raw = shown / PULL_RESISTANCE
        armed = pullReleases(shown, threshold)
    }

    fun move(dx: Float) {
        raw += dx
        shown = pulledDistance(raw, threshold)
        val now = pullReleases(shown, threshold)
        if (now && !armed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        armed = now
    }

    /** 松手（[lifted] 为 false 是手势被取消）：弹回原位，返回要不要展开 / 收回。 */
    fun release(lifted: Boolean): Boolean {
        val trigger = lifted && pullReleases(shown, threshold)
        raw = 0f
        armed = false
        job?.cancel()
        val from = shown
        if (from != 0f) job = scope.launch {
            animate(from, 0f, animationSpec = tween(RELEASE_MS)) { value, _ -> shown = value }
            shown = 0f
        }
        return trigger
    }
}

@Composable
private fun rememberRubberBand(): RubberBand {
    val threshold = with(LocalDensity.current) { PULL_THRESHOLD_DP.dp.toPx() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    return remember(threshold, scope, haptic) { RubberBand(scope, haptic, threshold) }
}

/** 横着拉过门槛松手调 [onTrigger]；[enabled] 为 false 时手指照常交给里面的候选。 */
private fun Modifier.rubberBandDrag(band: RubberBand, enabled: () -> Boolean, onTrigger: () -> Unit): Modifier =
    pointerInput(band) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            band.hold()
            if (enabled()) {
                // 里面的候选先看到这一下（长按整句选段会接走），横着走过触摸阈值、没被接走才算拉。
                val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    change.consume()
                    band.move(over)
                }
                if (start != null) {
                    // 拉起来以后这一下归拉动：在 Initial 阶段先于里面的候选接走移动和抬起。候选用的 tolerantClick 不看移动
                    // 有没有被接走，只认没被接走的抬起；不接走抬起，拉一小段（没过门槛）松手时会把手指下的候选当成点了
                    // （11-20 电脑上 AssociationHideButtonTest：英文补全展开页短划一下，选上了一个补全）。
                    var lifted = false
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == start.id } ?: break
                        if (!change.pressed) {
                            lifted = true
                            change.consume()
                            break
                        }
                        band.move(change.positionChange().x)
                        change.consume()
                    }
                    if (band.release(lifted)) onTrigger()
                    return@awaitEachGesture
                }
            }
            // 不是拉（点候选、长按）：手指抬起来以后再接着弹回，按着时不动，点到的就是看到的那个。
            while (currentEvent.changes.any { it.pressed }) awaitPointerEvent(PointerEventPass.Final)
            band.release(false)
        }
    }

/**
 * 包住候选栏里的候选行：左右拉过门槛松手调 [onExpand]。
 * [enabled] 为 false（没有更多候选、已经展开、滑行预览）时不拉，行为和以前一样。
 */
@Composable
internal fun PullToExpandCandidates(
    enabled: Boolean,
    onExpand: () -> Unit,
    hintColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val band = rememberRubberBand()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentExpand by rememberUpdatedState(onExpand)
    Box(
        modifier
            .clipToBounds()
            .testTag("candidate-pull-expand")
            .semantics {
                if (enabled) customActions = listOf(CustomAccessibilityAction("展开候选") { currentExpand(); true })
            }
            .rubberBandDrag(band, { currentEnabled }) { currentExpand() },
    ) {
        // 位移只在画的时候读，拉动时候选行不重组。
        Box(Modifier.graphicsLayer { translationX = band.shown }) { content() }
        PullHint(band, hintColor)
    }
}

/** 整行往哪边走，空出来的那一边写「展开」，过了门槛写「松手展开」。 */
@Composable
private fun BoxScope.PullHint(band: RubberBand, hintColor: Color) {
    val shown = band.shown
    if (shown == 0f) return
    val widthDp = with(LocalDensity.current) { abs(shown).toDp() }
    Box(Modifier.align(if (shown < 0f) Alignment.CenterEnd else Alignment.CenterStart).width(widthDp).fillMaxHeight(),
        contentAlignment = Alignment.Center) {
        Text(if (pullReleases(shown, band.threshold)) "松手展开" else "展开", color = hintColor.copy(alpha = 0.75f),
            fontSize = 11.sp, lineHeight = 12.sp, textAlign = TextAlign.Center, maxLines = 2,
            modifier = Modifier.graphicsLayer { alpha = (abs(band.shown) / band.threshold).coerceIn(0f, 1f) })
    }
}

/**
 * 展开页的候选区：左右划过门槛松手调 [onCollapse]，页面跟手挪，不够就弹回。
 * 上下划照旧滚列表；只认横着划（先过横向触摸阈值才算），不影响点候选、长按。
 */
@Composable
internal fun Modifier.swipeToCollapse(onCollapse: (() -> Unit)?): Modifier {
    if (onCollapse == null) return this
    val band = rememberRubberBand()
    val currentCollapse by rememberUpdatedState(onCollapse)
    return this
        .semantics { customActions = listOf(CustomAccessibilityAction("收回候选") { currentCollapse(); true }) }
        .rubberBandDrag(band, { true }) { currentCollapse() }
        .graphicsLayer {
            translationX = band.shown
            alpha = 1f - 0.3f * (abs(band.shown) / (band.threshold * PULL_MAX_FACTOR)).coerceIn(0f, 1f)
        }
}
