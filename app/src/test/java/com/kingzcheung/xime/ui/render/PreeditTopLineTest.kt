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
 * 拼音默认在键盘内候选上方一行小字（11-19，参照微信）：栏高不变、拼音行在栏顶、候选行在它下面不重叠、
 * 首选是纠错来的时这一行显示纠正读音；联想态这一行留空，候选不上下跳。设了 CYIME_RENDER_OUT 时出图 preedit-top-line.png。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreeditTopLineTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val typing = CandidateBarState.ChineseCandidates(listOf("我们是好朋友", "我们", "我"),
        inputText = "womenshihaopengyou", preeditText = "wo men shi hao peng you", hasMore = true)

    @Test fun lineSitsAboveTheCandidatesInsideTheBar() {
        KeyboardThemes.initFromConfig(context)
        val state = mutableStateOf<CandidateBarState>(typing)
        val corrections = mutableStateOf(emptyMap<String, String>())
        rule.setContent {
            val palette = resolveKeyboardPalette("soft_blue", false)
            Box(Modifier.width(400.dp).height(44.dp).background(Color.White).testTag("bar")) {
                CompositionLocalProvider(LocalKeyboardPalette provides palette,
                    LocalKeyboardInputPreferences provides KeyboardInputPreferences(preeditStyle = SettingsPreferences.PREEDIT_STYLE_LINE),
                    LocalPreeditLineCorrections provides corrections.value) {
                    CandidateBar(state = state.value,
                        visuals = CandidateBarVisuals(Color.Transparent, palette.candidateText, Color.Gray, palette.accent, palette.selectedText),
                        callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {}, onShowMoreCandidates = {}),
                        onEditPreedit = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val bar = rule.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        val line = rule.onNodeWithTag("preedit-line", true).fetchSemanticsNode().boundsInRoot
        val word = rule.onNodeWithText("我们是好朋友", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("栏顶就是拼音行", bar.top, line.top, 1f)
        assertTrue("拼音行在栏内", line.bottom <= bar.bottom)
        assertTrue("候选在拼音行下面，不重叠", word.top >= line.bottom - 1f)
        assertTrue("候选不出栏", word.bottom <= bar.bottom + 1f)
        rule.onNodeWithTag("preedit-line-text", true).assertExists()
        rule.onNodeWithTag("candidate-preedit", true).assertDoesNotExist()
        rule.onNodeWithTag("inline-preedit", true).assertDoesNotExist()

        rule.runOnIdle { corrections.value = mapOf("我们是好朋友" to "wo men shi hao pemg=peng you") }
        rule.onNodeWithTag("preedit-line-correction", true).assertExists()
        render("preedit-top-line-typing.png")

        rule.runOnIdle { state.value = CandidateBarState.AssociationOnly(listOf("的", "了", "吗"), hasMore = true) }
        rule.waitForIdle()
        val association = rule.onNodeWithText("的", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("联想态候选行和打字时同一高度", association.top >= line.bottom - 1f)
        rule.onNodeWithTag("preedit-line-text", true).assertDoesNotExist()
        render("preedit-top-line.png")
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
