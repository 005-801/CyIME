package com.kingzcheung.xime.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 拼音位置（11-19）：没设过的用候选上方一行；老开关「拼音放在键盘内」亲手设过的照旧；纠错读音默认不在候选旁。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PreeditStyleTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = SettingsPreferences.getPrefsPublic(app)

    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun unsetUsesTheTopLine() {
        assertEquals(SettingsPreferences.PREEDIT_STYLE_LINE, SettingsPreferences.preeditStyle(app))
        assertTrue("拼音编辑栏本身默认照旧打开", SettingsPreferences.showPreeditPreview(app))
    }

    @Test fun oldInsideKeyboardSwitchIsKept() {
        prefs.edit().putBoolean(SettingsPreferences.KEY_INLINE_PREEDIT_PREVIEW, true).commit()
        assertEquals(SettingsPreferences.PREEDIT_STYLE_LEFT, SettingsPreferences.preeditStyle(app))
        prefs.edit().putBoolean(SettingsPreferences.KEY_INLINE_PREEDIT_PREVIEW, false).commit()
        assertEquals("亲手关过的是气泡", SettingsPreferences.PREEDIT_STYLE_BUBBLE, SettingsPreferences.preeditStyle(app))
    }

    @Test fun chosenStyleWinsOverTheOldSwitch() {
        prefs.edit().putBoolean(SettingsPreferences.KEY_INLINE_PREEDIT_PREVIEW, true).commit()
        SettingsPreferences.setPreeditStyle(app, SettingsPreferences.PREEDIT_STYLE_LINE)
        assertEquals(SettingsPreferences.PREEDIT_STYLE_LINE, SettingsPreferences.preeditStyle(app))
        SettingsPreferences.setPreeditStyle(app, "nonsense")
        assertEquals(SettingsPreferences.PREEDIT_STYLE_LINE, SettingsPreferences.preeditStyle(app))
    }

    @Test fun resolveTable() {
        assertEquals(SettingsPreferences.PREEDIT_STYLE_LINE, SettingsPreferences.resolvePreeditStyle(null, null))
        assertEquals(SettingsPreferences.PREEDIT_STYLE_LEFT, SettingsPreferences.resolvePreeditStyle(null, true))
        assertEquals(SettingsPreferences.PREEDIT_STYLE_BUBBLE, SettingsPreferences.resolvePreeditStyle(null, false))
        assertEquals(SettingsPreferences.PREEDIT_STYLE_BUBBLE, SettingsPreferences.resolvePreeditStyle("bubble", true))
        assertEquals(SettingsPreferences.PREEDIT_STYLE_LINE, SettingsPreferences.resolvePreeditStyle("x", null))
    }

    @Test fun correctionMarksBesideCandidatesDefaultOffSavedKept() {
        assertFalse(SettingsPreferences.showCorrectionMarks(app))
        SettingsPreferences.setShowCorrectionMarks(app, true)
        assertTrue(SettingsPreferences.showCorrectionMarks(app))
    }
}
