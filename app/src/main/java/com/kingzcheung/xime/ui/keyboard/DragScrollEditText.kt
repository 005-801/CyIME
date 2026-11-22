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
 *
 * 光标滚出框外时不画：安卓会把框外的光标贴着框边画（Editor 的 clampHorizontalPosition），
 * 拖完后光标看着停在框边的两个字中间，打字却加在末尾（平板 11-22 验收「保护|人」）。
 * 光标回到框里（打字、点一下、拖回来）就照常画。
 */
internal class DragScrollEditText(context: Context) : EditText(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    /** 外面要不要光标（翻译栏：打字去了应用输入框时不要）；真画不画还要看光标在不在框里。 */
    private var cursorWanted = true

    /** 代替 [setCursorVisible]：光标在框外时先不画，回到框里再画。 */
    fun setCursorWanted(visible: Boolean) {
        if (cursorWanted == visible) return
        cursorWanted = visible
        applyCursor()
    }

    private fun applyCursor() {
        val show = cursorWanted && !cursorOutside()
        if (isCursorVisible != show) isCursorVisible = show
    }

    private fun cursorOutside(): Boolean {
        val layout = layout ?: return false
        val start = selectionStart
        val end = selectionEnd
        if (start < 0 || start != end || end > layout.text.length) return false
        return cursorOutsideView(layout.getPrimaryHorizontal(end), scrollX, width - totalPaddingLeft - totalPaddingRight)
    }

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        applyCursor()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // 选区变了以后 TextView 会在下一帧前把光标滚进框里，那时 onScrollChanged 再算；不用滚时这里算
        post { applyCursor() }
    }

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

/** 光标的横坐标（这一行字里的位置）在不在框里看得到的范围外；留 2 像素，贴着框边的不算。 */
internal fun cursorOutsideView(x: Float, scrollX: Int, visibleWidth: Int): Boolean =
    x < scrollX - 2 || x > scrollX + visibleWidth + 2

/** 横着拖：横向超过触摸阈值，且不比竖向少。 */
internal fun isHorizontalDrag(dx: Float, dy: Float, touchSlop: Int): Boolean =
    abs(dx) > touchSlop && abs(dx) >= abs(dy)

/** 横向滚动位置夹在这一行字的范围里：字没超出框就不滚，超出了最多滚到最后一个字贴着右边。 */
internal fun clampTextScroll(target: Int, lineLeft: Int, lineRight: Int, visibleWidth: Int): Int {
    val max = maxOf(lineLeft, lineRight - visibleWidth)
    return target.coerceIn(minOf(lineLeft, max), max)
}
