package com.kingzcheung.xime.ui.render

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.CandidateBar
import com.kingzcheung.xime.ui.keyboard.CandidateBarCallbacks
import com.kingzcheung.xime.ui.keyboard.CandidateBarState
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 开了「候选滑到头回弹展开」，「到头」是一屏（11-22 他：「一个屏幕就是一页」；群里：「最低 20 个用回弹展开每次想展开还得滑好一会」）：
 * 候选栏只排一开始露出来的候选和栏尾露半截的那一个，露全它再往左拉就展开，不用滑完引擎一页 20 个。
 * 开关关着时照旧整页都能滑（1.15.101）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w400dp-h800dp-xhdpi")
// 「一屏」按字宽算：默认的假排版里字宽不真实，量出来的一屏和实际排出来的对不上（11-22 电脑上全量：排到了第 19 个）。
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateFirstScreenTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val visuals = CandidateBarVisuals(Color.White, Color.Black, Color.Gray)
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val words = listOf("你好", "你", "拟好", "泥", "呢", "尼", "妮", "倪", "逆", "腻", "匿", "溺", "拟", "昵", "霓",
        "你们好", "你号", "尼豪", "泥浩", "你好啊")
    private val typing = CandidateBarState.ChineseCandidates(words, words.map { "ni hao" }, inputText = "nihao", hasMore = true)

    @After fun reset() {
        SettingsPreferences.getPrefsPublic(context).edit().remove(SettingsPreferences.KEY_CANDIDATE_SWIPE_EXPAND).commit()
    }

    private fun pullLeft() {
        rule.onNodeWithTag("candidate-pull-expand", useUnmergedTree = true).performTouchInput {
            swipe(Offset(width * 0.95f, centerY), Offset(width * 0.05f, centerY), durationMillis = 400)
        }
        rule.waitForIdle()
    }

    /** 整页 20 个候选：手指往左一拉——露全栏尾那个、接着拉过门槛——松手就展开。旧代码这一下只是在滑候选。 */
    @Test fun onePullExpandsAFullPage() {
        SettingsPreferences.setCandidateSwipeExpand(context, true)
        var opens = 0
        rule.setContent {
            CandidateBar(state = typing, visuals = visuals,
                callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {}, onShowMoreCandidates = { opens++ }),
                showPreeditPreview = false, modifier = Modifier.width(400.dp))
        }
        pullLeft()
        rule.runOnIdle { assertEquals("一屏到头，拉一下就展开", 1, opens) }
    }

    /** 开关开着：怎么往左滑都只到一屏的尾巴，后面的候选不排在栏里（展开页里看）。 */
    @Test fun rowStopsAfterTheFirstScreen() {
        SettingsPreferences.setCandidateSwipeExpand(context, true)
        rule.setContent {
            CandidateBar(state = typing, visuals = visuals,
                callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {}),
                showPreeditPreview = false, modifier = Modifier.width(400.dp))
        }
        val seen = HashSet<Int>()
        repeat(8) {
            words.indices.filterTo(seen) { index ->
                runCatching { rule.onNodeWithTag("bar-candidate:$index", useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            pullLeft()
        }
        assertTrue("栏里排到第 ${seen.maxOrNull()} 个，应只有一屏", seen.size >= 2 && seen.max() < 12)
    }

    /** 开关关着（默认）：和 1.15.101 一样，整页 20 个都排在栏里，能滑到第 20 个。 */
    @Test fun switchOffKeepsTheWholePage() {
        rule.setContent {
            CandidateBar(state = typing, visuals = visuals,
                callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {}, onShowMoreCandidates = {}),
                showPreeditPreview = false, modifier = Modifier.width(400.dp))
        }
        rule.onNodeWithTag("candidate-scroll-row", useUnmergedTree = true).performScrollToIndex(19)
        rule.onNodeWithTag("bar-candidate:19", useUnmergedTree = true).fetchSemanticsNode()
    }

    /** 上屏后的联想也一样：一屏到头，拉一下就展开。 */
    @Test fun associationsAlsoStopAfterOneScreen() {
        SettingsPreferences.setCandidateSwipeExpand(context, true)
        var opens = 0
        rule.setContent {
            CandidateBar(state = CandidateBarState.AssociationOnly(words, hasMore = true), visuals = visuals,
                callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {}, onAssociationSelect = {},
                    onShowMoreCandidates = { opens++ }),
                showPreeditPreview = false, modifier = Modifier.width(400.dp))
        }
        pullLeft()
        rule.runOnIdle { assertEquals("联想一屏到头，拉一下就展开", 1, opens) }
    }
}

/** 平板宽屏：一页的候选一屏还排不满时接上后面的把这一屏填满（不会因为「每页候选词数」少而空着半条）。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1000dp-h700dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateFirstScreenWideTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @After fun reset() {
        SettingsPreferences.getPrefsPublic(context).edit().remove(SettingsPreferences.KEY_CANDIDATE_SWIPE_EXPAND).commit()
    }

    @Test fun shortPageAsksForMore() {
        SettingsPreferences.setCandidateSwipeExpand(context, true)
        var extends = 0
        rule.setContent {
            CandidateBar(state = CandidateBarState.ChineseCandidates(listOf("你", "拟", "泥", "呢"), inputText = "ni", hasMore = true),
                visuals = CandidateBarVisuals(Color.White, Color.Black, Color.Gray),
                callbacks = CandidateBarCallbacks(onCandidateSelect = {}, onCancelInput = {}, onShowMoreCandidates = {},
                    onCandidateStripNearEnd = { extends++ }),
                showPreeditPreview = false, modifier = Modifier.width(1000.dp))
        }
        rule.runOnIdle { assertTrue("一屏没排满要接上后面的：$extends", extends >= 1) }
    }
}
