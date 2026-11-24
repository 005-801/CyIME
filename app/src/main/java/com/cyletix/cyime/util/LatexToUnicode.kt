package com.kingzcheung.xime.handwriting

/**
 * 手写公式 LaTeX -> Unicode 上下标纯文本。
 * 能映射成 Unicode 的上下标直接转，映射不了的（字母、希腊字母、嵌套）退回最精简 LaTeX。
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
    private val RE_ESCAPED = Regex("""\\([{}^_%${'$'}&#])""")
    private val RE_CMD = Regex("""\\([A-Za-z]+)""")
    private val RE_PURE_CMD = Regex("""\\[A-Za-z]+""")
    private val RE_WS_CMD = Regex("""\\[,;:!>]""")
    private val RE_EMPTY_BRACES = Regex("""\{\s*}""")
    private val RE_DOUBLE_SLASH = Regex("""\\\\""")
    private val RE_CMD_AT = Regex("""^\\[A-Za-z]+|^\\[^A-Za-z]""")

    private fun zipMap(keys: String, values: String): Map<Char, String> {
        val m = HashMap<Char, String>(keys.length)
        for (i in keys.indices) m[keys[i]] = values[i].toString()
        return m
    }

    private val SUP: Map<Char, String> = buildMap {
        putAll(zipMap("0123456789", "⁰¹²³⁴⁵⁶⁷⁸⁹"))
        putAll(zipMap("+-=()", "⁺⁻⁼⁽⁾"))
        if (ALLOW_SUP_LETTERS) {
            putAll(zipMap("abcdefghijklmnoprstuvwxyz", "ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻ"))
            putAll(zipMap("ABDEGHIJKLMNOPRTUVW", "ᴬᴮᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿᵀᵁⱽᵂ"))
        }
    }

    private val SUB: Map<Char, String> = buildMap {
        putAll(zipMap("0123456789", "₀₁₂₃₄₅₆₇₈₉"))
        putAll(zipMap("+-=()", "₊₋₌₍₎"))
        if (ALLOW_SUB_LETTERS) {
            putAll(zipMap("aehijklmnoprstuvx", "ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓ"))
        }
    }

    private val GREEK_SUP = mapOf(
        "beta" to "ᵝ", "gamma" to "ᵞ", "delta" to "ᵟ", "phi" to "ᵠ", "chi" to "ᵡ",
    )

    private val GREEK = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "omicron" to "ο",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ",
        "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
        "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ",
        "Omega" to "Ω",
    )

    private val SYMBOL = mapOf(
        "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠",
        "approx" to "≈", "equiv" to "≡", "propto" to "∝", "infty" to "∞", "partial" to "∂",
        "nabla" to "∇", "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "in" to "∈", "notin" to "∉",
        "subset" to "⊂", "subseteq" to "⊆", "cup" to "∪", "cap" to "∩",
        "forall" to "∀", "exists" to "∃", "sum" to "∑", "prod" to "∏",
        "int" to "∫", "oint" to "∮", "angle" to "∠", "circ" to "∘",
        "ldots" to "…", "cdots" to "⋯",
    )

    fun convert(text: String): String {
        var s = RE_DOUBLE_SLASH.replace(text, "\u0000")
        s = RE_LAYOUT.replace(s, "")
        s = RE_ESCAPED.replace(s) { it.groupValues[1] }
        s = s.replace("\\qquad", "  ").replace("\\quad", "  ")
        s = RE_WS_CMD.replace(s, "")
        s = s.replace("\\ ", " ")
        s = convertScripts(s, 0)
        s = RE_CMD.replace(s) { m -> cmdRepl(m.groupValues[1]) ?: m.value }
        s = RE_EMPTY_BRACES.replace(s, "")
        return s.replace("\u0000", "\\\\")
    }

    private data class Group(val text: String, val next: Int, val closed: Boolean)

    private fun matching(s: String, start: Int): Int {
        var depth = 0
        for (k in start until s.length) {
            when (s[k]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return k
                }
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
            return if (kind == '^') GREEK_SUP[content.substring(1)] else null
        }
        val table = if (kind == '^') SUP else SUB
        val sb = StringBuilder(content.length)
        for (ch in content) {
            val mapped = table[ch] ?: return null
            sb.append(mapped)
        }
        return sb.toString()
    }

    private fun cmdRepl(name: String): String? {
        GREEK[name]?.let { return it }
        if (ENABLE_SYMBOL_MAP) SYMBOL[name]?.let { return it }
        return null
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
                out.append(c).append('{').append(norm)
                if (g.closed) out.append('}')
            }
            i = g.next
        }
        return out.toString()
    }
}
