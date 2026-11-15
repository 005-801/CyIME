package com.kingzcheung.xime.settings

import android.content.Context

/**
 * 数字行键的上下角标（用户 11-15：每个键都要能在「按键符号」里直接看到、直接改，数字行也算）。
 * 开数字行的三套（[CornerVariant.numberRow]）各存各的，中文、英文分开，和字母键的六套同一个分法。
 * 格子按键上写的字记（数字或自己加的符号），在「数字行」页改了顺序，角标跟着键走。
 * 预设和原来一样：数字键上下都是电脑键帽上 Shift 出的 ! @ # …，别的键没有角标；中文的格子可以单独打开「跟随全角半角」，
 * 预设都不跟随（原来数字行的角标不跟全角半角）。
 */
internal object NumberRowSymbols {
    /** [key] 这个数字行键的预设角标，上下一样；不是数字的键没有。 */
    fun preset(key: String): String = com.kingzcheung.xime.ui.keyboard.numberRowShiftSymbol(key).orEmpty()

    /** 格子名 `up.<键>` / `down.<键>` 里的键。 */
    fun keyOf(id: String): String = id.substringAfter('.')

    private fun prefix(english: Boolean, variant: CornerVariant) =
        "number_row_symbols_${if (english) "en" else "zh"}_${variant.id}:"
    private fun followKey(variant: CornerVariant) = "number_row_symbol_follow_zh_${variant.id}"

    /** 这一套里改过（和预设不同）的格子。 */
    fun load(context: Context, english: Boolean, variant: CornerVariant): Map<String, String> {
        val prefix = prefix(english, variant)
        return SettingsPreferences.getPrefsPublic(context).all.mapNotNull { (name, value) ->
            if (name.startsWith(prefix) && value is String) name.removePrefix(prefix) to value else null
        }.toMap()
    }

    /** [key] 在这一套里的上方、下方角标：改过的照改的，没改过的照预设。 */
    fun cells(key: String, overrides: Map<String, String>): Pair<String, String> =
        (overrides["up.$key"] ?: preset(key)) to (overrides["down.$key"] ?: preset(key))

    /** 改过的「跟随全角半角」：格子 → 开或关。英文不分全角半角。 */
    fun loadFollows(context: Context, english: Boolean, variant: CornerVariant): Map<String, Boolean> =
        if (english) emptyMap() else SettingsPreferences.getPrefsPublic(context).getStringSet(followKey(variant), null).orEmpty()
            .mapNotNull { entry ->
                // 键可能就是「=」，按最后一个 = 分。
                val at = entry.lastIndexOf('=')
                if (at <= 0) null else entry.substring(0, at) to (entry.substring(at + 1) == "1")
            }.toMap()

    fun follows(english: Boolean, overrides: Map<String, Boolean>): Set<String> =
        if (english) emptySet() else overrides.filterValues { it }.keys

    /**
     * 写入这一套里 [cells] 这些格子（格子 → 符号）：和预设一样的格子删掉记录，回到跟预设走；没列出的格子不动。
     * [follows] 不为 null 时整份换掉这一套的「跟随全角半角」。
     */
    fun save(context: Context, english: Boolean, variant: CornerVariant, cells: Map<String, String>,
        follows: Map<String, Boolean>? = null) {
        val edit = SettingsPreferences.getPrefsPublic(context).edit()
        val prefix = prefix(english, variant)
        for ((id, value) in cells) {
            if (value == preset(keyOf(id))) edit.remove(prefix + id) else edit.putString(prefix + id, value)
        }
        if (follows != null && !english) edit.putStringSet(followKey(variant),
            follows.filterValues { it }.map { (id, _) -> "$id=1" }.toSet())
        check(edit.commit()) { "无法保存数字行符号" }
        KeysConfigHelper.loadConfig(context)
    }

    /** 这一套恢复预设：清掉改过的格子和跟随全角半角。 */
    fun reset(context: Context, english: Boolean, variant: CornerVariant) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val prefix = prefix(english, variant)
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(edit::remove)
        if (!english) edit.remove(followKey(variant))
        check(edit.commit()) { "无法恢复数字行符号" }
        KeysConfigHelper.loadConfig(context)
    }
}
