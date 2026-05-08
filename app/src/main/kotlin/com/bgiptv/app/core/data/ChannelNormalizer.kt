package com.bgiptv.app.core.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Normalise les noms de chaînes pour la déduplication.
 * Ex: "FR| TF1 HD", "[FR] TF1 4K", "TF1 FHD" → "tf1"
 */
@Singleton
class ChannelNormalizer @Inject constructor() {

    private val prefixPatterns = listOf(
        Regex("""^[A-Z]{2,3}[\s|:\-]+"""),           // "FR| ", "FR: ", "FR - "
        Regex("""^\[[A-Z]{2,3}\]\s*"""),              // "[FR] "
        Regex("""^[🇦-🇿]{2}\s*"""), // drapeaux emoji
    )

    private val qualitySuffixes = listOf(
        Regex("""\s*(4K|8K|UHD|FHD|HD\+|HD|SD|HQ|LQ)\s*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*(H\.?265|H\.?264|HEVC|AVC|x265|x264)\s*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*(IPTV|FR|ENG|VF|VOST|VO)\s*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*[\|\-\:\.]+\s*$"""),             // trailing separators
    )

    private val inlineQuality = listOf(
        Regex("""\s+4K\b""", RegexOption.IGNORE_CASE),
        Regex("""\s+8K\b""", RegexOption.IGNORE_CASE),
        Regex("""\s+UHD\b""", RegexOption.IGNORE_CASE),
        Regex("""\s+FHD\b""", RegexOption.IGNORE_CASE),
        Regex("""\s+HD\b""", RegexOption.IGNORE_CASE),
        Regex("""\s+SD\b""", RegexOption.IGNORE_CASE),
    )

    private val decorativeChars = Regex("""[▸▶►◄★☆✦•·⚡🔴🟢🔵⭐]+""")
    private val multipleSpaces = Regex("""\s+""")

    fun canonicalName(rawName: String): String {
        var name = rawName.trim()

        // Strip country prefixes
        for (pattern in prefixPatterns) {
            name = name.replace(pattern, "")
        }

        // Strip quality inline markers
        for (pattern in inlineQuality) {
            name = name.replace(pattern, "")
        }

        // Strip quality suffixes (iterative until stable)
        var prev = ""
        while (prev != name) {
            prev = name
            for (pattern in qualitySuffixes) {
                name = name.replace(pattern, "")
            }
        }

        // Strip decorative chars
        name = name.replace(decorativeChars, "")

        // Normalize spaces
        name = name.replace(multipleSpaces, " ").trim()

        return name.lowercase()
    }

    fun extractQuality(rawName: String): String {
        val upper = rawName.uppercase()
        return when {
            upper.contains("8K") -> "8K"
            upper.contains("4K") || upper.contains("UHD") -> "4K"
            upper.contains("FHD") || upper.contains("1080") -> "FHD"
            upper.contains("HD") -> "HD"
            upper.contains("SD") || upper.contains("LQ") -> "SD"
            else -> "HD" // default assumption
        }
    }

    fun extractCountryCode(rawName: String): String? {
        // Match "FR|", "[FR]", "FR - ", "FR: " at the start
        val prefixMatch = Regex("""^([A-Z]{2,3})[\s|:\-\[\]]+""").find(rawName.trim())
        val code = prefixMatch?.groupValues?.get(1)

        // Filter known non-country codes
        val nonCountryCodes = setOf("HD", "SD", "UHD", "FHD", "HQ", "LQ", "VF", "VO")
        return if (code != null && code !in nonCountryCodes) code.take(2) else null
    }

    fun groupTag(rawGroupName: String, rawChannelName: String): String {
        val upper = (rawGroupName + " " + rawChannelName).uppercase()
        return when {
            upper.containsAny("FOOT", "SOCCER", "LIGUE 1", "LIGUE1", "CHAMPIONS", "PREMIER LEAGUE",
                "LA LIGA", "SERIE A", "BEIN", "CANAL+FOOT", "LIGUE1+", "DAZN") -> "FOOT"
            upper.containsAny("F1", "FORMULE 1", "FORMULA 1", "MOTO GP", "MOTOGP") -> "F1"
            upper.containsAny("NBA", "BASKET", "BASKETBALL") -> "NBA"
            upper.containsAny("TENNIS", "ATP", "WTA", "ROLAND GARROS", "WIMBLEDON") -> "TENNIS"
            upper.containsAny("SPORT") -> "SPORT"
            upper.containsAny("FILM", "CINEMA", "MOVIE", "CINE") -> "CINEMA"
            upper.containsAny("INFO", "NEWS", "BFM", "CNEWS", "LCI", "FRANCE INFO") -> "NEWS"
            upper.containsAny("JEUNESSE", "KIDS", "ENFANT", "CARTOON", "DISNEY", "GULLI") -> "JEUNESSE"
            upper.containsAny("DOCU", "DOCUMENTAIRE", "DISCOVERY", "NATIONAL GEO") -> "DOCS"
            upper.containsAny("MUSIQUE", "MUSIC", "CLIPS", "MTV") -> "MUSIQUE"
            else -> "GENERAL"
        }
    }

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { this.contains(it) }
}
