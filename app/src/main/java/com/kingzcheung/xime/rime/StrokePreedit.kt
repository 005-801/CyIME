package com.kingzcheung.xime.rime

/** 横竖撇点折：和笔画方案补丁的 xlit/hspnz/⼀⼁⼃⼂乛/、lua 里的 STROKE_GLYPHS 一样（康熙部首，折用键面上的乛，#412）。 */
private val STROKE_GLYPHS = mapOf('h' to '⼀', 's' to '⼁', 'p' to '⼃', 'n' to '⼂', 'z' to '乛')

/**
 * 「笔画编码显示成 一丨丿丶乛」打开时显示的编码：h s p n z 换成笔画，分词号、通配符 * 和已经选出的汉字原样留着。
 * 对上候选的那段方案补丁已经换好，这里补上 Rime 照原样给的那段字母（11-18：输入串不能一半字母一半笔画）。
 */
internal fun strokeGlyphs(preedit: String): String =
    buildString(preedit.length) { preedit.forEach { append(STROKE_GLYPHS[it] ?: it) } }

private val STROKE_LETTERS = STROKE_GLYPHS.entries.associate { (letter, glyph) -> glyph to letter }

/** [strokeGlyphs] 反过来：笔画换回 h s p n z，别的原样。一个对一个，位置不变。 */
internal fun strokeLetters(text: String): String =
    buildString(text.length) { text.forEach { append(STROKE_LETTERS[it] ?: it) } }

/**
 * 编码编辑条里显示的笔画编码：组字区现在显示的是笔画（「笔画编码显示成 一丨丿丶乛」开着）时，编辑条也换成笔画，
 * 和组字区一样（11-22 他转来的笔画用户改动；折用键面上的乛，#412）；关着时照旧字母。* 和分词号原样。
 * 一个字母换一个笔画，光标位置、点哪改哪都不受影响。
 */
internal fun strokeEditorText(code: String, shownPreedit: String): String =
    if (shownPreedit.any { it in STROKE_LETTERS }) strokeGlyphs(code) else code
