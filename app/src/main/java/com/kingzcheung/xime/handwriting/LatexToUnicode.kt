package com.kingzcheung.xime.handwriting

/**
 * 手写公式 LaTeX 转 Unicode 上下标纯文本。
 *
 * 全文不使用泛型尖括号：映射表用「键串 + 值串」下标对齐，
 * 避免从聊天/网页复制时尖括号被当成 HTML 标签吃掉，导致编译失败。
 */
object LatexToUnicode {

    private const val ALLOW_SUP_LETTERS = false
    private const val ALLOW_SUB_LETTERS = false
    private const val ENABLE_SYMBOL_MAP = true
    private const val GREEDY_DIGITS = true

    private val RE_LAYOUT = Regex(
        """\\(?:left|right|big|Big|bigg|Bigg|bigl|bigr|Bigl|Bigr""" +
            """|displaystyle|textstyle|limits|nolimits)(?![A-Za-z])"""
    )
    private val RE_ESCAPED = Regex("\\\\([{}^_%\$&#])")
    private val RE_CMD = Regex("""\\([A-Za-z]+)""")
    private val RE_PURE_CMD = Regex("""\\[A-Za-z]+""")
    private val RE_WS_CMD = Regex("""\\[,;:!>]""")
    private val RE_EMPTY_BRACES = Regex("""\{\s*\}""")
    private val RE_DOUBLE_SLASH = Regex("""\\\\""")
    private val RE_CMD_AT = Regex("""^\\[A-Za-z]+|^\\[^A-Za-z]""")

    private const val SUP_KEYS = "0123456789+-=()"
    private const val SUP_VALS = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾"
    private const val SUP_LET_KEYS = "abcdefghijklmnoprstuvwxyz"
    private const val SUP_LET_VALS = "ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻ"
    private const val SUP_CAP_KEYS = "ABDEGHIJKLMNOPRTUVW"
    private const val SUP_CAP_VALS = "ᴬᴮᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿᵀᵁⱽᵂ"

    private const val SUB_KEYS = "0123456789+-=()"
    private const val SUB_VALS = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎"
    private const val SUB_LET_KEYS = "aehijklmnoprstuvx"
    private const val SUB_LET_VALS = "ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓ"

    private val GREEK_NAMES = listOf(
        "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta",
        "theta", "vartheta", "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron",
        "pi", "rho", "sigma", "tau", "upsilon", "phi", "varphi", "chi", "psi", "omega",
        "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Upsilon",
        "Phi", "Psi", "Omega",
    )
    private val GREEK_CHARS = listOf(
        "α", "β", "γ", "δ", "ε", "ε", "ζ", "η",
        "θ", "ϑ", "ι", "κ", "λ", "μ", "ν", "ξ", "ο",
        "π", "ρ", "σ", "τ", "υ", "φ", "φ", "χ", "ψ", "ω",
        "Γ", "Δ", "Θ", "Λ", "Ξ", "Π", "Σ", "Υ",
        "Φ", "Ψ", "Ω",
    )

    private val GREEK_SUP_NAMES = listOf("beta", "gamma", "delta", "phi", "chi")
    private val GREEK_SUP_CHARS = listOf("ᵝ", "ᵞ", "ᵟ", "ᵠ", "ᵡ")

    private val SYMBOL_NAMES = listOf(
        "times", "cdot", "div", "pm", "mp", "leq", "le", "geq", "ge", "neq", "ne",
        "approx", "equiv", "propto", "infty", "partial", "nabla", "to", "rightarrow",
        "leftarrow", "Rightarrow", "Leftarrow", "in", "notin", "subset", "subseteq",
        "cup", "cap", "forall", "exists", "sum", "prod", "int", "oint", "angle",
        "circ", "ldots", "cdots",
    )
    private val SYMBOL_CHARS = listOf(
        "×", "·", "÷", "±", "∓", "≤", "≤", "≥", "≥", "≠", "≠",
        "≈", "≡", "∝", "∞", "∂", "∇", "→", "→",
        "←", "⇒", "⇐", "∈", "∉", "⊂", "⊆",
        "∪", "∩", "∀", "∃", "∑", "∏", "∫", "∮", "∠",
        "∘", "…", "⋯",
    )

    private fun at(keys: String, vals: String, ch: Char): String? {
        val i = keys.indexOf(ch)
        if (i < 0) return null
        return vals.substring(i, i + 1)
    }

    private fun supMap(ch: Char): String? {
        at(SUP_KEYS, SUP_VALS, ch)?.let { return it }
        if (ALLOW_SUP_LETTERS) {
            at(SUP_LET_KEYS, SUP_LET_VALS, ch)?.let { return it }
            at(SUP_CAP_KEYS, SUP_CAP_VALS, ch)?.let { return it }
        }
        return null
    }

    private fun subMap(ch: Char): String? {
        at(SUB_KEYS, SUB_VALS, ch)?.let { return it }
        if (ALLOW_SUB_LETTERS) at(SUB_LET_KEYS, SUB_LET_VALS, ch)?.let { return it }
        return null
    }

    private fun cmdRepl(name: String): String? {
        val g = GREEK_NAMES.indexOf(name)
        if (g >= 0) return GREEK_CHARS[g]
        if (!ENABLE_SYMBOL_MAP) return null
        val s = SYMBOL_NAMES.indexOf(name)
        if (s >= 0) return SYMBOL_CHARS[s]
        return null
    }

    fun convert(text: String): String {
        var s = RE_DOUBLE_SLASH.replace(text, "\u0000")
        s = RE_LAYOUT.replace(s, "")
        s = RE_ESCAPED.replace(s) { it.groupValues[1] }
        s = s.replace("\\qquad", " ").replace("\\quad", " ")
        s = RE_WS_CMD.replace(s, "")
        s = s.replace("\\ ", " ")
        s = convertScripts(s, 0)
        s = RE_CMD.replace(s) { m -> cmdRepl(m.groupValues[1]) ?: m.value }
        s = RE_EMPTY_BRACES.replace(s, "")
        return s.replace("\u0000", "\\\\")
    }

    private class Group(val text: String, val next: Int, val closed: Boolean)

    private fun matching(s: String, start: Int): Int {
        var depth = 0
        for (k in start until s.length) {
            if (s[k] == '{') {
                depth++
            } else if (s[k] == '}') {
                depth--
                if (depth == 0) return k
            }
        }
        return -1
    }

    private fun unwrap(s: String): String {
        var t = s
        while (t.length >= 2 && t[0] == '{' && matching(t, 0) == t.length - 1) {
            t = t.substring(1, t.length - 1)
        }
        return t
    }

    private fun readGroup(s: String, start: Int): Group {
        var i = start
        while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
        if (i >= s.length) return Group("", s.length, true)
        if (s[i] == '{') {
            val end = matching(s, i)
            if (end < 0) return Group(s.substring(i + 1), s.length, false)
            return Group(s.substring(i + 1, end), end + 1, true)
        }
        if (s[i] == '\\') {
            val m = RE_CMD_AT.find(s.substring(i))
            if (m != null) return Group(m.value, i + m.value.length, true)
        }
        if (GREEDY_DIGITS && s[i].isDigit()) {
            var j = i
            while (j < s.length && s[j].isDigit()) j++
            return Group(s.substring(i, j), j, true)
        }
        return Group(s.substring(i, i + 1), i + 1, true)
    }

    private fun toUnicode(content: String, kind: Char): String? {
        if (content.isEmpty()) return null
        if (RE_PURE_CMD.matches(content)) {
            if (kind != '^') return null
            val i = GREEK_SUP_NAMES.indexOf(content.substring(1))
            return if (i < 0) null else GREEK_SUP_CHARS[i]
        }
        val sb = StringBuilder(content.length)
        for (ch in content) {
            val mapped = if (kind == '^') supMap(ch) else subMap(ch)
            if (mapped == null) return null
            sb.append(mapped)
        }
        return sb.toString()
    }

    private fun convertScripts(s: String, depth: Int): String {
        if (depth > 8) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '^' && c != '_') {
                out.append(c)
                i++
                continue
            }
            val g = readGroup(s, i + 1)
            val content = convertScripts(unwrap(g.text), depth + 1)
            val direct = toUnicode(content, c)
            val norm = if (direct == null) {
                RE_CMD.replace(content) { m -> cmdRepl(m.groupValues[1]) ?: m.value }
            } else {
                content
            }
            val mapped = direct ?: if (norm != content) toUnicode(norm, c) else null
            if (mapped != null) {
                out.append(mapped)
            } else {
                out.append(c)
                out.append('{')
                out.append(norm)
                if (g.closed) out.append('}')
            }
            i = g.next
        }
        return out.toString()
    }
}
