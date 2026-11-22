package com.kingzcheung.xime.ui.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.EditText
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 一行的面板输入框（翻译栏、剪贴板搜索）：左右拖动时整行字跟着滚，光标不动，和设置页的搜索框一样。
 *
 * 安卓 11 起 EditText 横着拖会拖光标（系统的「从任意处拖动光标」），翻译栏里字一长，想往回看前面的字只能把光标拖过去（用户 11-22）。
 * 现在横着拖过触摸阈值后，这一下手势由这里接管：先给 EditText 发一个取消（光标、长按菜单都不动），之后只改横向滚动位置。
 * 点一下、长按照旧交给 EditText：点哪光标去哪，长按出粘贴菜单；拖光标下面的小把手也照旧能挪光标。
 */
internal class DragScrollEditText(context: Context) : EditText(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false

    @SuppressLint("ClickableViewAccessibility") // 点击照旧交给 EditText（它在抬手时 performClick）
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && isHorizontalDrag(event.x - downX, event.y - downY, touchSlop)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                }
                if (dragging) {
                    scrollTextBy(lastX - event.x)
                    lastX = event.x
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scrollTextBy(dx: Float) {
        val layout = layout ?: return
        if (layout.lineCount == 0) return
        val visible = width - totalPaddingLeft - totalPaddingRight
        val target = clampTextScroll(scrollX + dx.roundToInt(),
            floor(layout.getLineLeft(0)).toInt(), ceil(layout.getLineRight(0)).toInt(), visible)
        if (target != scrollX) scrollTo(target, scrollY)
    }
}

/** 横着拖：横向超过触摸阈值，且不比竖向少。 */
internal fun isHorizontalDrag(dx: Float, dy: Float, touchSlop: Int): Boolean =
    abs(dx) > touchSlop && abs(dx) >= abs(dy)

/** 横向滚动位置夹在这一行字的范围里：字没超出框就不滚，超出了最多滚到最后一个字贴着右边。 */
internal fun clampTextScroll(target: Int, lineLeft: Int, lineRight: Int, visibleWidth: Int): Int {
    val max = maxOf(lineLeft, lineRight - visibleWidth)
    return target.coerceIn(minOf(lineLeft, max), max)
}
