package com.kingzcheung.xime.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeSystemStatusTest {
    private val pkg = "com.cyletix.cyime"

    @Test fun anyEvidenceCountsAsEnabled() {
        // #508：红米 Turbo5 / Android 17 启用列表里没有，但设置里写着、或已是当前输入法、或服务已开。
        assertTrue(imeEnabledFrom(pkg, false, "com.other/.Ime:$pkg/com.kingzcheung.xime.service.XimeInputMethodService;123", false, false))
        assertTrue(imeEnabledFrom(pkg, false, null, selected = true, serviceAlive = false))
        assertTrue(imeEnabledFrom(pkg, false, "", selected = false, serviceAlive = true))
        assertTrue(imeEnabledFrom(pkg, true, null, false, false))
    }

    @Test fun anotherPackageIsNotCyime() {
        assertFalse(imeEnabledFrom(pkg, false, "com.cyletix.cyime.debug/.Ime:com.other/.Ime", false, false))
        assertFalse(imeEnabledFrom(pkg, false, null, false, false))
    }

    @Test fun theDefaultSettingWinsWhenReadable() {
        assertTrue(imeSelectedFrom(pkg, "$pkg/com.kingzcheung.xime.service.XimeInputMethodService", null))
        assertFalse("读得到别的输入法就不看当前输入法", imeSelectedFrom(pkg, "com.other/.Ime", pkg))
        assertTrue("读不到时看当前输入法", imeSelectedFrom(pkg, "", pkg))
        assertFalse(imeSelectedFrom(pkg, "", null))
    }
}
