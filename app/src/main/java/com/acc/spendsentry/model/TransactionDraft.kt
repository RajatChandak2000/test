package com.acc.spendsentry.model

data class TransactionDraft(
    val id: String,
    val sourcePackage: String,
    val postedAtMillis: Long,
    val merchant: String,
    val amount: String,
    val currency: String,
    val rawTitle: String,
    val rawText: String,
    val acknowledged: Boolean = false,
    val acknowledgedAtMillis: Long? = null,
)
