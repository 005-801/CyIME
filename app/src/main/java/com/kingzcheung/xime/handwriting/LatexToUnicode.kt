package com.kingzcheung.xime.handwriting

/**
 * 手写公式 LaTeX 转 Unicode 纯文本。
 *
 * 全文不使用泛型尖括号：映射表用「键串 + 值串」或两个等长 list 下标对齐，
 * 避免从聊天/网页复制时尖括号被当成 HTML 标签吃掉，导致编译失败。
 *
 * Android 的 ICU 正则比 JVM 严格：正则里 { } 单独出现必须转义成 \{ \}。
 */
object LatexToUnicode {

    /** 字母也转上下标：x^{n} -> xⁿ、x_{i} -> xᵢ */
    private const val ALLOW_SUP_LETTERS = true
    private const val ALLOW_SUB_LETTERS = true
    private const val ENABLE_SYMBOL_MAP = true
    private const val GREEDY_DIGITS = true

    /** 分式线性化：\frac{y}{x} -> y/x。关掉则原样保留 LaTeX。 */
    private const val LINEARIZE_FRAC = true

    private val RE_LAYOUT = Regex(
        """\\(?:left|right|big|Big|bigg|Bigg|bigl|bigr|Bigl|Bigr""" +
            """|displaystyle|textstyle|limits|nolimits)(?![A-Za-z])"""
    )
    private val RE_ESCAPED = Regex("\\\\([{}^_%\$&#])")
    private val RE_CMD = Regex("""\\([A-Za-z]+)""")
    private val RE_PURE_CMD = Regex("""\$$A-Za-z]+""")
    private val RE_WS_CMD = Regex("""\\[,;:!>]""")
    private val RE_EMPTY_BRACES = Regex("""\{\s*\}""")
    private val RE_DOUBLE_SLASH = Regex("""\\\\""")
    private val RE_CMD_AT = Regex("""^\\[A-Za-z]+|^\\[^A-Za-z]""")

    private val RE_NROOT = Regex("""\\sqrt\s*\[([^$$]*)\]\s*\{([^{}]*)\}""")
    private val RE_SQRT = Regex("""\\sqrt\s*\{([^{}]*)\}""")
    private val RE_ACCENT = Regex("""\\(vec|hat|bar|tilde|dot|ddot)\s*\{([^{}]*)\}""")
    private val RE_TEXTCMD = Regex("""\\(?:text|mathrm|mathbf|mathit|operatorname)\s*\{([^{}]*)\}""")
    private val RE_FRAC = Regex("""\\frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}""")

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

    /** 函数名：去掉反斜杠当正体文本，\sin x -> sin x */
    private val FUNC_NAMES = listOf(
        "sin", "cos", "tan", "cot", "sec", "csc",
        "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
        "log", "ln", "lg", "exp", "lim", "max", "min",
        "sup", "inf", "det", "dim", "ker", "gcd", "mod", "bmod",
    )

    private val SYMBOL_NAMES = listOf(
        "times", "cdot", "div", "pm", "mp",
        "leq", "le", "geq", "ge", "neq", "ne",
        "approx", "equiv", "sim", "cong", "propto",
        "infty", "partial", "nabla", "degree", "prime",
        "to", "rightarrow", "leftarrow", "Rightarrow", "Leftarrow",
        "in", "notin", "subset", "subseteq", "supset", "cup", "cap",
        "forall", "exists", "emptyset", "because", "therefore",
        "sum", "prod", "int", "oint", "iint",
        "angle", "circ", "perp", "parallel",
        "mid", "vert", "Vert",
        "ldots", "cdots", "dots",
        "triangle", "square", "oplus", "otimes", "odot", "bigcup", "bigcap",
    )
    private val SYMBOL_CHARS = listOf(
        "×", "·", "÷", "±", "∓",
        "≤", "≤", "≥", "≥", "≠", "≠",
        "≈", "≡", "∼", "≅", "∝",
        "∞", "∂", "∇", "°", "′",
        "→", "→", "←", "⇒", "⇐",
        "∈", "∉", "⊂", "⊆", "⊃", "∪", "∩",
        "∀", "∃", "∅", "∵", "∴",
        "∑", "∏", "∫", "∮", "∬",
        "∠", "∘", "⊥", "∥",
        "|", "|", "‖",
        "…", "⋯", "…",
        "△", "□", "⊕", "⊗", "⊙", "⋃", "⋂",
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
        if (FUNC_NAMES.indexOf(name) >= 0) return name
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
        // 角度：90^\circ -> 90°
        s = s.replace("^{\\circ}", "°").replace("^\\circ", "°")
        // \text{...} \mathrm{...} 只留内容
        s = RE_TEXTCMD.replace(s) { it.groupValues[1] }
        s = convertScripts(s, 0)
        // 重音：\vec{v} -> v⃗，\hat{x} -> x̂
        s = RE_ACCENT.replace(s) { m ->
            val mark = when (m.groupValues[1]) {
                "vec" -> "\u20D7"
                "hat" -> "\u0302"
                "bar" -> "\u0304"
                "tilde" -> "\u0303"
                "dot" -> "\u0307"
                else -> "\u0308"
            }
            m.groupValues[2] + mark
        }
        // n 次根：\sqrt[3]{x} -> ³√(x)
        s = RE_NROOT.replace(s) { m ->
            val n = m.groupValues[1]
            val inner = m.groupValues[2]
            val head = if (n.length == 1) (supMap(n[0]) ?: n) else n
            head + "√(" + inner + ")"
        }
        // 平方根：\sqrt{x} -> √x，多字符加括号
        s = RE_SQRT.replace(s) { m ->
            val inner = m.groupValues[1]
            if (inner.length <= 1) "√" + inner else "√(" + inner + ")"
        }
        s = RE_CMD.replace(s) { m -> cmdRepl(m.groupValues[1]) ?: m.value }
        // 分式线性化：\frac{y}{x} -> y/x，多字符自动加括号
        if (LINEARIZE_FRAC) {
            s = RE_FRAC.replace(s) { m ->
                val a = m.groupValues[1]
                val b = m.groupValues[2]
                val na = if (a.length <= 1) a else "(" + a + ")"
                val nb = if (b.length <= 1) b else "(" + b + ")"
                na + "/" + nb
            }
        }
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
