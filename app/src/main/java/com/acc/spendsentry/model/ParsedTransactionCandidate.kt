package com.acc.spendsentry.model

data class ParsedTransactionCandidate(
    val merchant: String,
    val amount: String,
    val currency: String,
    val confidence: Int,
    val reason: String,
)
