package com.kingzcheung.xime.handwriting

/**
 * 手写公式 LaTeX 转 Unicode 纯文本。
 *
 * 全文不使用泛型尖括号，也不使用方括号转义这类正则字面量：
 * 前者会被当成 HTML 标签吃掉，后者会被 Markdown 当成公式定界符替换，
 * 两者都会让复制后的代码编译不过。这里一律用下标表、手写循环、字符串替换绕开。
 */
object LatexToUnicode {

    /** 字母也转上下标：x^{n} -> xⁿ、x_{i} -> xᵢ */
    private const val ALLOW_SUP_LETTERS = true
    private const val ALLOW_SUB_LETTERS = true
    private const val ENABLE_SYMBOL_MAP = true
    private const val GREEDY_DIGITS = true

    /** 分式线性化：\frac{y}{x} -> y/x。关掉则原样保留 LaTeX。 */
    private const val LINEARIZE_FRAC = true

    /** 下标没有对应 Unicode 字符时，去掉 _{} 直接连写：R_{f} -> Rf */
    private const val BARE_SUB_FALLBACK = true

    private val RE_LAYOUT = Regex(
        """\\(?:left|right|big|Big|bigg|Bigg|bigl|bigr|Bigl|Bigr""" +
            """|displaystyle|textstyle|limits|nolimits)(?![A-Za-z])"""
    )
    private val RE_ESCAPED = Regex("\\\\([{}^_%\$&#])")
    private val RE_CMD = Regex("""\\([A-Za-z]+)""")
    /** 命令 + 可选的尾随空格：符号要吞掉空格（A\cup B -> A∪B），函数名保留（\sin x -> sin x） */
    private val RE_CMD_SP = Regex("""\\([A-Za-z]+) ?""")
    private val RE_EMPTY_BRACES = Regex("""\{\s*\}""")
    private val RE_DOUBLE_SLASH = Regex("""\\\\""")
    private val RE_SQRT = Regex("""\\sqrt\s*\{([^{}]*)\}""")
    private val RE_ACCENT = Regex("""\\(vec|hat|bar|tilde|dot|ddot)\s*\{([^{}]*)\}""")
    private val RE_TEXTCMD = Regex("""\\(?:text|mathrm|mathbf|mathit|operatorname)\s*\{([^{}]*)\}""")

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

    /** 空白命令，直接删掉 */
    private val SPACE_CMDS = listOf("\\,", "\\;", "\\:", "\\!", "\\>")

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

    /** 整串就是一个命令名：反斜杠开头，后面全是字母 */
    private fun isPureCmd(s: String): Boolean {
        if (s.length < 2 || s[0] != '\\') return false
        for (k in 1 until s.length) {
            if (!s[k].isLetter()) return false
        }
        return true
    }

    fun convert(text: String): String {
        var s = RE_DOUBLE_SLASH.replace(text, "\u0000")
        s = RE_LAYOUT.replace(s, "")
        s = RE_ESCAPED.replace(s) { it.groupValues[1] }
        s = s.replace("\\qquad", " ").replace("\\quad", " ")
        for (cmd in SPACE_CMDS) s = s.replace(cmd, "")
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
        // 平方根放到分式之后，才能处理 \sqrt{\frac{a}{b}}
        s = RE_CMD_SP.replace(s) { m ->
            val name = m.groupValues[1]
            val r = cmdRepl(name)
            if (r == null) {
                m.value
            } else if (FUNC_NAMES.indexOf(name) >= 0) {
                if (m.value.length > name.length + 1) r + " " else r
            } else {
                r
            }
        }
        if (LINEARIZE_FRAC) s = linearizeFrac(s)
        s = RE_SQRT.replace(s) { m ->
            val inner = m.groupValues[1]
            if (inner.length <= 1) "√" + inner else "√(" + inner + ")"
        }
        s = RE_EMPTY_BRACES.replace(s, "")
        return s.replace("\u0000", "\\\\")
    }

    /**
     * 把 \frac{a}{b} 线性化成 a/b，多字符自动加括号。
     * 用配对花括号解析而不是正则，所以 \frac{R_{f}}{R_{1}} 这种内层带花括号的也能处理。
     */
    private fun linearizeFrac(s: String): String {
        var out = s
        var guard = 0
        var from = 0
        while (guard < 8) {
            val idx = out.indexOf("\\frac", from)
            if (idx < 0) break
            var p = idx + 5
            while (p < out.length && out[p] == ' ') p++
            if (p >= out.length || out[p] != '{') {
                from = idx + 5
                continue
            }
            val e1 = matching(out, p)
            if (e1 < 0) {
                from = idx + 5
                continue
            }
            var q = e1 + 1
            while (q < out.length && out[q] == ' ') q++
            if (q >= out.length || out[q] != '{') {
                from = idx + 5
                continue
            }
            val e2 = matching(out, q)
            if (e2 < 0) {
                from = idx + 5
                continue
            }
            val a = out.substring(p + 1, e1)
            val b = out.substring(q + 1, e2)
            val na = if (needsParen(a)) "(" + a + ")" else a
            val nb = if (needsParen(b)) "(" + b + ")" else b
            out = out.substring(0, idx) + na + "/" + nb + out.substring(e2 + 1)
            from = idx + 1
            guard++
        }
        return out
    }

    /** 分子分母含运算符或空格时才加括号，否则连写更清爽 */
    private fun needsParen(x: String): Boolean {
        for (ch in x) {
            if (ch == '+' || ch == '-' || ch == '=' || ch == ' ' || ch == ',') {
                return true
            }
        }
        return false
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
            var j = i + 1
            while (j < s.length && s[j].isLetter()) j++
            if (j == i + 1 && j < s.length) j++
            return Group(s.substring(i, j), j, true)
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
        if (isPureCmd(content)) {
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
            } else if (c == '_' && BARE_SUB_FALLBACK && norm.length == 1) {
                out.append('_')
                out.append(norm)
            } else if (c == '^' && norm.length == 1) {
                out.append('^')
                out.append(norm)
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
