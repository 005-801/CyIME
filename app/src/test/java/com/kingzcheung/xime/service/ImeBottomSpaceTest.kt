package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Test
import com.kingzcheung.xime.platform.DeviceVendor

class ImeBottomSpaceTest {
    @Test fun navigationAndTaskbarKeepTheirFullHeight() {
        for (height in listOf(16, 24, 48, 80, 128)) assertEquals(height, imeBottomSpaceDp(height * 3, 3f))
    }
    @Test fun fractionalPixelsRoundUpRatherThanLeaveAnUnpaintedStrip() {
        assertEquals(19, imeBottomSpaceDp(50, 2.75f))
    }
    @Test fun hiddenNavigationDoesNotInventBottomPadding() {
        assertEquals(0, imeBottomSpaceDp(0, 3f))
        assertEquals(0, imeBottomSpaceDp(-1, 3f))
        assertEquals(0, imeBottomSpaceDp(48, 0f))
    }

    // 密度 3：48dp = 144px，手势条 24dp = 72px。决策表见 classifyBottomNav（11-19 统一规则）。
    private fun reading(nav: Int = 144, tappable: Int = 0, mandatory: Int = 0, mode: Int? = null, vendor: Boolean? = null,
        verified: Boolean = false, large: Boolean = false, ime: Int = 0, hide: Boolean = false) =
        BottomNavReading(navPx = nav, tappablePx = tappable, mandatoryPx = mandatory, cutoutPx = 0, imeButtonsPx = ime,
            navigationMode = mode, vendorGesture = vendor, verifiedVendor = verified, largeScreen = large,
            samsungHideButton = hide, gestureBarPx = 72, density = 3f)
    private fun decide(r: BottomNavReading) = classifyBottomNav(r).let { it.kind to it.step }

    @Test fun decisionTableStepsInOrder() {
        // 1 大屏任务栏：平板 SM-X800 三键导航实测 nav = tappable = mandatory = 90px（10-31）。
        assertEquals(BottomNavKind.TASKBAR to 1, decide(reading(nav = 90, tappable = 90, mandatory = 90, mode = 0, large = true)))
        // 2 本窗口里系统画了按钮条，先于一切开关。
        assertEquals(BottomNavKind.IME_BUTTONS to 2, decide(reading(mode = 2, ime = 144)))
        // 3 三星三键（11-19 他的手机、平板实测 navigation_mode=0、navigation_bar_gesture_while_hidden=0），inset 什么都没报也是三键。
        assertEquals(BottomNavKind.BUTTONS to 3, decide(reading(mode = 0, vendor = false, verified = true)))
        assertEquals(BottomNavKind.BUTTONS to 3, decide(reading(mode = 0, verified = true)))
        assertEquals(BottomNavKind.BUTTONS to 3, decide(reading(mode = 0, vendor = false)))
        // 4 明确的手势：navigation_mode=2，或厂商开关开着（navigation_mode 还写 0 也算）。
        assertEquals(BottomNavKind.GESTURE to 4, decide(reading(mode = 2, tappable = 144)))
        assertEquals(BottomNavKind.GESTURE to 4, decide(reading(mode = 0, vendor = true, tappable = 144)))
        assertEquals(BottomNavKind.GESTURE to 4, decide(reading(mode = 0, vendor = true, verified = true)))
        // 开关矛盾（mode=2 但厂商开关关）、或没核对过的厂商只写 0：看 inset。5 可点区域占满 → 三键。
        assertEquals(BottomNavKind.BUTTONS to 5, decide(reading(mode = 2, vendor = false, tappable = 144)))
        assertEquals(BottomNavKind.BUTTONS to 5, decide(reading(mode = 0, tappable = 144, mandatory = 144)))
        // 6 可点区域没占满，且强制手势区比导航栏矮或整条只有手势条高 → 手势。
        assertEquals(BottomNavKind.GESTURE to 6, decide(reading(mode = 2, vendor = false)))
        assertEquals(BottomNavKind.GESTURE to 6, decide(reading(mode = 0, mandatory = 54)))
        assertEquals(BottomNavKind.GESTURE to 6, decide(reading(mode = 0, tappable = 60, mandatory = 54)))
        assertEquals(BottomNavKind.GESTURE to 6, decide(reading(nav = 48, mandatory = 48)))
        // 7 整条都是强制手势区、又没报可点区域：说不清，整条留。
        assertEquals(BottomNavKind.UNKNOWN to 7, decide(reading(mode = 0, mandatory = 144)))
    }

    @Test fun suggestionShrinksToTheGestureBarOnlyOnVerifiedGesturePhones() {
        assertEquals(72, visibleBottomPx(reading(mode = 2, verified = true), 144))
        assertEquals(72, visibleBottomPx(reading(mode = 2, tappable = 144, verified = true), 144))
        assertEquals(40, visibleBottomPx(reading(nav = 40, mode = 2, verified = true), 40))
        // 三键、系统按钮条、三星隐藏键盘按钮、没核对过的厂商（vivo 手势时也画切换输入法按钮）：整条。
        assertEquals(144, visibleBottomPx(reading(mode = 0, verified = true), 144))
        assertEquals(144, visibleBottomPx(reading(mode = 2, verified = true, ime = 144), 144))
        assertEquals(144, visibleBottomPx(reading(mode = 2, verified = true, hide = true), 144))
        assertEquals(144, visibleBottomPx(reading(mode = 2), 144))
        assertEquals(0, visibleBottomPx(reading(nav = 0, mode = 2, verified = true), 0))
    }

    // vivo 手势导航时键盘下面有「切换输入法」按钮（10-31 用户反馈压住了）：建议值只有三星缩到手势条。
    @Test fun onlyVerifiedVendorsShrinkToTheGestureBar() {
        assertEquals(true, gestureStripVerifiedVendor(DeviceVendor.of("samsung")))
        assertEquals(true, gestureStripVerifiedVendor(DeviceVendor.of("Samsung")))
        for (vendor in listOf("vivo", "Xiaomi", "OPPO", "HONOR", "HUAWEI", "Google", "", null)) {
            assertEquals(vendor.toString(), false, gestureStripVerifiedVendor(DeviceVendor.of(vendor)))
        }
    }

    @Test fun vendorGestureKeysPerManufacturer() {
        val settings = mapOf(
            (true to "force_fsg_nav_bar") to 1,
            (false to "navigation_gesture_on") to 1,
            (false to "hide_navigationbar_enable") to 0,
            (false to "secure_gesture_navigation") to 1,
            (true to "navigation_bar_gesture_while_hidden") to 1,
        )
        val read: (Boolean, String) -> Int? = { global, key -> settings[global to key] }
        assertEquals(true, vendorGestureNavigationOn(DeviceVendor.of("Xiaomi"), read))
        assertEquals(true, vendorGestureNavigationOn(DeviceVendor.of("vivo"), read))
        assertEquals(false, vendorGestureNavigationOn(DeviceVendor.of("OPPO"), read))
        assertEquals(true, vendorGestureNavigationOn(DeviceVendor.of("HONOR"), read))
        assertEquals(true, vendorGestureNavigationOn(DeviceVendor.of("samsung"), read))
        // 平板实测：三键导航时三星这项为 0。
        assertEquals(false, vendorGestureNavigationOn(DeviceVendor.of("samsung")) { global, key ->
            if (global && key == "navigation_bar_gesture_while_hidden") 0 else null })
        assertEquals(null, vendorGestureNavigationOn(DeviceVendor.of("Google"), read))
        assertEquals(null, vendorGestureNavigationOn(DeviceVendor.of(null), read))
        assertEquals(null, vendorGestureNavigationOn(DeviceVendor.of("vivo")) { _, _ -> null })
    }
}
