package io.github.hypernotifyfix.domain

/** Lossless parser for an existing OEM list. It never guesses when delimiters are mixed/unknown. */
object XiaomiWhitelist {
    data class Parsed(val raw: String, val delimiter: String, val tokens: List<String>)
    fun parse(raw: String): Parsed? {
        if (raw.isEmpty()) return Parsed(raw, ",", emptyList())
        val present = listOf(",", ";", ":", "|").filter { raw.contains(it) }
        if (present.size > 1 || raw.any { it == '\n' || it == '\r' || it == '\u0000' }) return null
        val delimiter = present.singleOrNull() ?: ","
        val tokens = if (present.isEmpty()) listOf(raw.trim()) else raw.split(delimiter).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.any { it.isEmpty() || !it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) }) return null
        return Parsed(raw, delimiter, tokens)
    }
    fun merge(raw: String, packageName: String): String? {
        if (!packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) return null
        val parsed = parse(raw) ?: return null
        if (parsed.tokens.any { it == packageName }) return raw
        if (raw.isEmpty()) return packageName
        val trailingWhitespace = raw.takeLastWhile(Char::isWhitespace)
        val withoutWhitespace = raw.dropLast(trailingWhitespace.length)
        val hasTrailingDelimiter = withoutWhitespace.endsWith(parsed.delimiter)
        val core = if (hasTrailingDelimiter) withoutWhitespace.dropLast(parsed.delimiter.length) else withoutWhitespace
        return core + parsed.delimiter + packageName + (if (hasTrailingDelimiter) parsed.delimiter else "") + trailingWhitespace
    }
}
