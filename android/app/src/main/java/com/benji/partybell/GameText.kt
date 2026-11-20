package com.benji.partybell

object GameText {
    private val roman = listOf("I", "II", "III", "IV", "V", "VI")
    private val symbols = mapOf(
        0xE04A to "○",
        0xE04B to "□",
        0xE04C to "×",
        0xE04D to "△",
        0xE06F to "→",
        0xE035 to "↓",
        0xE070 to "?",
        0xE0AF to "+",
        0xE0C0 to "★",
    )
    private val emoji = mapOf(
        0x2665 to "♥",
        0x2764 to "♥",
        0x1F499 to "♥",
        0x1F49A to "♥",
        0x1F49B to "♥",
        0x1F49C to "♥",
        0x1F5A4 to "♥",
        0x1F90E to "♥",
        0x1F9E1 to "♥",
        0x1FA75 to "♥",
        0x1FA76 to "♥",
        0x1FA77 to "♥",
        0x1F90D to "♡",
        0x2B50 to "★",
        0x1F31F to "★",
        0x1F3B5 to "♪",
        0x1F3B6 to "♫",
    )
    private val spaces = Regex(" {2,}")

    fun clean(text: String): String {
        if (text.none { it in ''..'' }) return text
        val out = StringBuilder()
        text.forEach { out.append(glyph(it.code) ?: it.toString()) }
        return out.toString().replace(spaces, " ").trim()
    }

    fun fromEmoji(text: String): String {
        val out = StringBuilder()
        var mapped = false
        for (code in text.codePoints()) {
            val symbol = emoji[code]
            if (symbol != null) out.append(symbol) else if (!(mapped && code == 0xFE0F)) out.appendCodePoint(code)
            mapped = symbol != null
        }
        return out.toString()
    }

    fun hasEmoji(text: String) = text.codePoints().anyMatch { it >= 0x1F000 || it == 0xFE0F || it == 0x200D || it == 0x20E3 }

    private fun glyph(code: Int): String? = when (code) {
        in 0xE060..0xE069 -> (code - 0xE060).toString()
        in 0xE0E0..0xE0E9 -> (code - 0xE0E0).toString()
        in 0xE08F..0xE0AE -> (code - 0xE08F).toString()
        in 0xE0B1..0xE0B9 -> (code - 0xE0B0).toString()
        in 0xE071..0xE08A -> ('A' + (code - 0xE071)).toString()
        in 0xE0C1..0xE0C6 -> roman[code - 0xE0C1]
        in symbols -> symbols.getValue(code)
        in 0xE000..0xF8FF -> ""
        else -> null
    }
}
