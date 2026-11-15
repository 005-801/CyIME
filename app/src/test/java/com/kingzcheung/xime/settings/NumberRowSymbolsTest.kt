package com.kingzcheung.xime.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 用户 11-15：数字行的键也要能在「按键符号」里直接改，开数字行的三套各存各的，中文每格可设跟随全角半角。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NumberRowSymbolsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = SettingsPreferences.getPrefsPublic(app)

    @Before fun clean() { prefs.edit().clear().commit(); KeysConfigHelper.loadConfig(app) }
    @After fun restore() {
        KeysConfigHelper.cornerVariant = CornerVariant.BOTH
        KeysConfigHelper.cornerFullWidth = true
    }

    @Test fun `presets are the old shift symbols on both sides`() {
        KeysConfigHelper.cornerVariant = CornerVariant.NR_BOTH
        assertEquals("!@#$%^&*()", "1234567890".map { KeysConfigHelper.numberRowCorners(it.toString(), false).first }.joinToString(""))
        assertEquals("!@#$%^&*()", "1234567890".map { KeysConfigHelper.numberRowCorners(it.toString(), true).second }.joinToString(""))
        // 自己加到数字行的符号键原来没有角标，现在也没有。
        assertEquals("" to "", KeysConfigHelper.numberRowCorners("+", false))
    }

    @Test fun `each number row layout and language keeps its own edits`() {
        NumberRowSymbols.save(app, false, CornerVariant.NR_UP, mapOf("up.1" to "“", "down.1" to "!"))
        NumberRowSymbols.save(app, true, CornerVariant.NR_BOTH, mapOf("down.2" to "€"))
        KeysConfigHelper.cornerVariant = CornerVariant.NR_UP
        assertEquals("“" to "!", KeysConfigHelper.numberRowCorners("1", false))
        assertEquals("!" to "!", KeysConfigHelper.numberRowCorners("1", true))
        KeysConfigHelper.cornerVariant = CornerVariant.NR_BOTH
        assertEquals("!" to "!", KeysConfigHelper.numberRowCorners("1", false))
        assertEquals("@" to "€", KeysConfigHelper.numberRowCorners("2", true))
        // 和预设一样的格子不存，只存改了的那一格。
        assertEquals(mapOf("up.1" to "“"), NumberRowSymbols.load(app, false, CornerVariant.NR_UP))
        // 改回预设就删掉记录。
        NumberRowSymbols.save(app, false, CornerVariant.NR_UP, mapOf("up.1" to "!"))
        assertEquals(emptyMap<String, String>(), NumberRowSymbols.load(app, false, CornerVariant.NR_UP))
    }

    @Test fun `follow full and half width per cell in chinese only`() {
        NumberRowSymbols.save(app, false, CornerVariant.NR_BOTH, emptyMap(), mapOf("up.1" to true, "down.9" to true))
        NumberRowSymbols.save(app, true, CornerVariant.NR_BOTH, emptyMap(), mapOf("up.1" to true))
        KeysConfigHelper.cornerVariant = CornerVariant.NR_BOTH
        KeysConfigHelper.cornerFullWidth = true
        assertEquals("！" to "!", KeysConfigHelper.numberRowCorners("1", false))
        assertEquals("(" to "（", KeysConfigHelper.numberRowCorners("9", false))
        KeysConfigHelper.cornerFullWidth = false
        assertEquals("!" to "!", KeysConfigHelper.numberRowCorners("1", false))
        // 英文不分全角半角。
        KeysConfigHelper.cornerFullWidth = true
        assertEquals("!" to "!", KeysConfigHelper.numberRowCorners("1", true))
        assertEquals(setOf("up.1", "down.9"), NumberRowSymbols.follows(false, NumberRowSymbols.loadFollows(app, false, CornerVariant.NR_BOTH)))
    }

    @Test fun `a key named equals sign keeps its follow setting`() {
        NumberRowSymbols.save(app, false, CornerVariant.NR_BOTH, mapOf("up.=" to "+"), mapOf("up.=" to true))
        assertEquals(mapOf("up.=" to true), NumberRowSymbols.loadFollows(app, false, CornerVariant.NR_BOTH))
        assertEquals("+" to "", NumberRowSymbols.cells("=", NumberRowSymbols.load(app, false, CornerVariant.NR_BOTH)))
    }

    @Test fun `reset clears only that layout`() {
        NumberRowSymbols.save(app, false, CornerVariant.NR_BOTH, mapOf("up.1" to "a"), mapOf("up.1" to true))
        NumberRowSymbols.save(app, false, CornerVariant.NR_DOWN, mapOf("down.1" to "b"))
        NumberRowSymbols.reset(app, false, CornerVariant.NR_BOTH)
        assertEquals(emptyMap<String, String>(), NumberRowSymbols.load(app, false, CornerVariant.NR_BOTH))
        assertEquals(emptyMap<String, Boolean>(), NumberRowSymbols.loadFollows(app, false, CornerVariant.NR_BOTH))
        assertEquals(mapOf("down.1" to "b"), NumberRowSymbols.load(app, false, CornerVariant.NR_DOWN))
    }
}
