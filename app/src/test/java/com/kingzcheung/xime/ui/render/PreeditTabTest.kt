package com.kingzcheung.xime.ui.render

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import com.kingzcheung.xime.ui.keyboard.KeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.LocalPreeditLineCorrections
import com.kingzcheung.xime.ui.keyboard.PreeditPopupBoundsKey
import androidx.compose.ui.unit.toRect
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.LocalKeyboardPalette
import com.kingzcheung.xime.ui.theme.resolveKeyboardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 「紧贴候选上方」（11-20 他定）：拼音是贴在键盘边框正上方的小签。候选栏 44dp 和候选位置一点不动；
 * 小签在栏外、底边贴着栏顶、高 26dp 能点；首选是纠错来的时显示纠正读音；联想态没有小签。
 * 设了 CYIME_RENDER_OUT 时出图 preedit-tab*.png（Robolectric 只画主窗口，小签是单独的弹窗，图里看栏不变）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreeditTabTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val typing = CandidateBarState.ChineseCandidates(listOf("我们是好朋友", "我们", "我"),
        inputText = "womenshihaopengyou", preeditText = "wo men shi hao peng you", hasMore = true)

    @Test fun tabSitsOnTopOfTheBarAndTheBarStaysPut() {
        KeyboardThemes.initFromConfig(context)
        val state = mutableStateOf<CandidateBarState>(typing)
        val corrections = mutableStateOf(emptyMap<String, String>())
        var edits = 0
        rule.setContent {
            val palette = resolveKeyboardPalette("soft_blue", false)
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(200.dp))
                Box(Modifier.width(400.dp).height(44.dp).background(Color.White).testTag("bar")) {
                    CompositionLocalProvider(LocalKeyboardPalette provides palette,
                        LocalKeyboardInputPreferences provides KeyboardInputPreferences(preeditStyle = SettingsPreferences.PREEDIT_STYLE_LINE),
                        LocalPreeditLineCorrections provides corrections.value) {
                        CandidateBar(state = state.value,
                            visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText, Color.Gray, palette.accent, palette.selectedText),
                            callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {}, onShowMoreCandidates = {}),
                            onEditPreedit = { edits++ }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        val bar = rule.onNodeWithTag("bar").fetchSemanticsNode().boundsInWindow
        // 小签是单独的弹窗：它自己的 boundsInWindow 是弹窗里的坐标，用它在键盘窗口里的位置比。
        val tab = rule.onNodeWithTag("preedit-tab", true).fetchSemanticsNode().config[PreeditPopupBoundsKey].toRect()
        val word = rule.onNodeWithText("我们是好朋友", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        assertEquals("小签底边贴着栏顶", bar.top, tab.bottom, 2f)
        assertTrue("小签高 26dp，点得到", tab.height >= with(rule.density) { 26.dp.toPx() } - 1f)
        assertEquals("默认靠左、贴死键盘左边缘（11-21）", bar.left, tab.left, 1f)
        assertEquals("候选还在 44dp 栏的正中，没被挤", bar.center.y, word.center.y, with(rule.density) { 2.dp.toPx() })
        rule.onNodeWithTag("preedit-tab-text", true).assertExists()
        rule.onNodeWithTag("candidate-preedit", true).assertDoesNotExist()
        rule.onNodeWithTag("inline-preedit", true).assertDoesNotExist()
        rule.onNodeWithTag("preedit-tab", true).performClick()
        rule.runOnIdle { assertEquals("点小签打开拼音编辑", 1, edits) }

        rule.runOnIdle { corrections.value = mapOf("我们是好朋友" to "wo men shi hao pemg=peng you") }
        rule.onNodeWithTag("preedit-tab-correction", true).assertExists()
        render("preedit-tab-typing.png")

        rule.runOnIdle { state.value = CandidateBarState.AssociationOnly(listOf("的", "了", "吗"), hasMore = true) }
        rule.waitForIdle()
        rule.onNodeWithTag("preedit-tab-text", true).assertDoesNotExist()
        val association = rule.onNodeWithText("的", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        assertEquals("联想态候选也在栏正中", bar.center.y, association.center.y, with(rule.density) { 2.dp.toPx() })
        render("preedit-tab-association.png")
    }

    /** 四种样式都能选左中右（11-21）：小签、方块靠边贴死卡片边缘，顶上一行和卡片一样宽；底边都贴着栏顶。 */
    @Test fun stylesAndAlignments() {
        KeyboardThemes.initFromConfig(context)
        val prefs = mutableStateOf(KeyboardInputPreferences(preeditStyle = SettingsPreferences.PREEDIT_STYLE_LINE))
        rule.setContent {
            val palette = resolveKeyboardPalette("soft_blue", false)
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(200.dp))
                Box(Modifier.width(400.dp).height(44.dp).background(Color.White).testTag("bar")) {
                    CompositionLocalProvider(LocalKeyboardPalette provides palette,
                        LocalKeyboardInputPreferences provides prefs.value) {
                        CandidateBar(state = typing,
                            visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText, Color.Gray, palette.accent, palette.selectedText),
                            callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {}, onShowMoreCandidates = {}),
                            onEditPreedit = {}, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        val bar = rule.onNodeWithTag("bar").fetchSemanticsNode().boundsInWindow
        fun tab() = rule.onNodeWithTag("preedit-tab", true).fetchSemanticsNode().config[PreeditPopupBoundsKey].toRect()
        fun set(style: String, align: String) {
            rule.runOnIdle { prefs.value = KeyboardInputPreferences(preeditStyle = style, preeditAlign = align) }
            rule.waitForIdle()
        }
        val px = with(rule.density) { 2.dp.toPx() }
        set(SettingsPreferences.PREEDIT_STYLE_LINE, SettingsPreferences.PREEDIT_ALIGN_RIGHT)
        assertEquals("小签靠右贴死右边缘", bar.right, tab().right, 1f)
        assertEquals("底边贴着栏顶", bar.top, tab().bottom, 2f)
        rule.onNodeWithTag("preedit-tab-corner-fill", true).assertExists()
        set(SettingsPreferences.PREEDIT_STYLE_LINE, SettingsPreferences.PREEDIT_ALIGN_CENTER)
        assertEquals("小签居中", bar.center.x, tab().center.x, px)
        rule.onNodeWithTag("preedit-tab-corner-fill", true).assertDoesNotExist()
        set(SettingsPreferences.PREEDIT_STYLE_BLOCK, SettingsPreferences.PREEDIT_ALIGN_LEFT)
        assertEquals("方块靠左贴死左边缘", bar.left, tab().left, 1f)
        assertTrue("方块比小签高（36dp）", tab().height >= with(rule.density) { 36.dp.toPx() } - 1f)
        assertEquals("底边贴着栏顶", bar.top, tab().bottom, 2f)
        set(SettingsPreferences.PREEDIT_STYLE_STRIP, SettingsPreferences.PREEDIT_ALIGN_CENTER)
        assertEquals("顶上一行和键盘一样宽", bar.width, tab().width, 1f)
        assertEquals("顶上一行从键盘左边缘起", bar.left, tab().left, 1f)
        assertEquals("底边贴着栏顶", bar.top, tab().bottom, 2f)
        val text = rule.onNodeWithTag("preedit-tab-text", true).fetchSemanticsNode().boundsInWindow
        // 拼音在弹窗里：自己的 boundsInWindow 是弹窗里的坐标，和弹窗宽比。
        assertEquals("顶上一行里拼音居中", tab().width / 2f, text.center.x, px)
        render("preedit-strip-center.png")
    }

    private fun render(name: String) {
        val out = System.getenv("CYIME_RENDER_OUT") ?: return
        File(out).mkdirs()
        rule.runOnIdle { }
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(android.graphics.Canvas(bitmap)) }
        File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
