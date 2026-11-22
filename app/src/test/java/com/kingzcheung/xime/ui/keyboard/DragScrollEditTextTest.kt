package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 翻译栏一行输入框：横着拖是滚字（不拖光标），滚动范围不超出这一行字。 */
class DragScrollEditTextTest {
    @Test fun horizontalDragPastSlopStartsScrolling() {
        assertTrue(isHorizontalDrag(dx = 30f, dy = 4f, touchSlop = 16))
        assertTrue(isHorizontalDrag(dx = -30f, dy = 10f, touchSlop = 16))
    }

    @Test fun tapsJitterAndVerticalMovesStayWithTheEditText() {
        assertFalse(isHorizontalDrag(dx = 10f, dy = 0f, touchSlop = 16))
        assertFalse(isHorizontalDrag(dx = 20f, dy = 40f, touchSlop = 16))
    }

    @Test fun shortTextDoesNotScroll() {
        // 字 200 宽，框 600 宽
        assertEquals(0, clampTextScroll(150, lineLeft = 0, lineRight = 200, visibleWidth = 600))
        assertEquals(0, clampTextScroll(-80, lineLeft = 0, lineRight = 200, visibleWidth = 600))
    }

    @Test fun longTextScrollsUntilTheLastCharacterReachesTheRightEdge() {
        assertEquals(300, clampTextScroll(300, lineLeft = 0, lineRight = 1000, visibleWidth = 600))
        assertEquals(400, clampTextScroll(900, lineLeft = 0, lineRight = 1000, visibleWidth = 600))
        assertEquals(0, clampTextScroll(-50, lineLeft = 0, lineRight = 1000, visibleWidth = 600))
    }
}
