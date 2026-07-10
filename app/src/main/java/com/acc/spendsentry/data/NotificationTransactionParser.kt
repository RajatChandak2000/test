package com.acc.spendsentry.data

import com.acc.spendsentry.model.ParsedTransactionCandidate
import java.util.Locale
import java.util.regex.Pattern

class NotificationTransactionParser {
    private val amountPatterns = listOf(
        Pattern.compile("""(?i)(?:₹|rs\.?|inr|usd|eur|gbp|\$)\s?([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""),
        Pattern.compile("""(?i)([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s?(?:spent|paid|charged|debited)"""),
    )

    private val currencyPatterns = mapOf(
        "₹" to "INR",
        "rs" to "INR",
        "inr" to "INR",
        "$" to "USD",
        "usd" to "USD",
        "eur" to "EUR",
        "gbp" to "GBP",
    )

    fun parse(title: String?, text: String?, sourcePackage: String): ParsedTransactionCandidate? {
        val haystack = listOfNotNull(title, text).joinToString(" ").trim()
        if (haystack.isBlank() || !looksLikeTransaction(haystack, sourcePackage)) return null

        val amount = extractAmount(haystack) ?: return null
        val merchant = extractMerchant(title, text, haystack)
        val currency = extractCurrency(haystack)
        val confidence = confidenceScore(haystack, merchant, amount)

        return ParsedTransactionCandidate(
            merchant = merchant,
            amount = amount,
            currency = currency,
            confidence = confidence,
            reason = "Matched transaction keywords and amount pattern",
        )
    }

    private fun looksLikeTransaction(haystack: String, sourcePackage: String): Boolean {
        val lower = haystack.lowercase(Locale.US)
        if (sourcePackage.contains("wallet", ignoreCase = true)) return true
        return listOf(
            "spent",
            "paid",
            "payment",
            "purchase",
            "charged",
            "debited",
            "transaction",
            "card ending",
            "google pay",
            "gpay",
            "tap to pay",
            "wallet",
            "apple pay",
            "venmo",
            "paypal",
        ).any { lower.contains(it) }
    }

    private fun extractAmount(haystack: String): String? {
        amountPatterns.forEach { pattern ->
            val matcher = pattern.matcher(haystack)
            if (matcher.find()) {
                return normalizeAmount(matcher.group(1))
            }
        }
        return null
    }

    private fun extractCurrency(haystack: String): String {
        val lower = haystack.lowercase(Locale.US)
        currencyPatterns.entries.forEach { (token, currency) ->
            if (lower.contains(token)) return currency
        }
        return ""
    }

    private fun extractMerchant(title: String?, text: String?, haystack: String): String {
        val titleValue = title?.trim().orEmpty()
        val textValue = text?.trim().orEmpty()

        extractAfterKeywords(textValue, listOf("at", "to", "for", "from"))
            ?.let { return tidyMerchant(it) }
        extractAfterKeywords(titleValue, listOf("at", "to", "for", "from"))
            ?.let { return tidyMerchant(it) }

        if (looksLikeMerchantTitle(titleValue)) {
            return tidyMerchant(titleValue)
        }

        val sentence = haystack.split("•", "|", "-", "—").firstOrNull().orEmpty()
        return tidyMerchant(sentence)
    }

    private fun looksLikeMerchantTitle(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        if (value.isBlank() || value.length > 40) return false
        if (listOf("payment", "approved", "spent", "paid", "charged", "debited", "card", "wallet", "transaction").any { lower.contains(it) }) {
            return false
        }
        return value.any { it.isLetterOrDigit() } && value.count { it.isLetterOrDigit() } >= 3
    }

    private fun extractAfterKeywords(value: String, keywords: List<String>): String? {
        val lower = value.lowercase(Locale.US)
        keywords.forEach { keyword ->
            val idx = lower.indexOf("$keyword ")
            if (idx >= 0) {
                val tail = value.substring(idx + keyword.length).trim()
                return trimMerchantTail(tail)
            }
        }
        return null
    }

    private fun trimMerchantTail(tail: String): String {
        val lower = tail.lowercase(Locale.US)
        val stopTokens = listOf(" using ", " via ", " with ", " card ", " transaction ", " txn ", " ref ", " ending ")
        val punctuationStops = listOf("•", "|", "—", "-", ";", ",")
        var endIndex = tail.length

        stopTokens.forEach { token ->
            val index = lower.indexOf(token)
            if (index >= 0 && index < endIndex) {
                endIndex = index
            }
        }
        punctuationStops.forEach { token ->
            val index = tail.indexOf(token)
            if (index >= 0 && index < endIndex) {
                endIndex = index
            }
        }

        return tail.substring(0, endIndex).trim()
    }

    private fun tidyMerchant(value: String): String {
        return value
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('.', ',', ':', ';')
            .take(60)
    }

    private fun normalizeAmount(raw: String): String {
        return raw.replace(",", "").trim()
    }

    private fun confidenceScore(haystack: String, merchant: String, amount: String): Int {
        var score = 30
        if (merchant.isNotBlank()) score += 25
        if (amount.isNotBlank()) score += 35
        if (haystack.contains("card", ignoreCase = true)) score += 5
        if (haystack.contains("spent", ignoreCase = true) || haystack.contains("paid", ignoreCase = true)) score += 5
        return score.coerceAtMost(100)
    }
}
