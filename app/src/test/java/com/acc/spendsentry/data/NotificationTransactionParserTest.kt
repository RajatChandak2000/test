package com.acc.spendsentry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTransactionParserTest {
    private val parser = NotificationTransactionParser()

    @Test
    fun parsesTransactionNotificationWithMerchantAndAmount() {
        val candidate = parser.parse(
            title = "Card payment approved",
            text = "Spent ₹249.50 at Cafe Co using card ending 1234",
            sourcePackage = "com.bank.app",
        )

        assertNotNull(candidate)
        assertEquals("Cafe Co", candidate!!.merchant)
        assertEquals("249.50", candidate.amount)
        assertEquals("INR", candidate.currency)
    }

    @Test
    fun parsesWalletNotificationBySourceAndPrefillsAmount() {
        val candidate = parser.parse(
            title = "Google Wallet",
            text = "Payment of $12.34 at Bookshop",
            sourcePackage = "com.google.android.apps.walletnfcrel",
        )

        assertNotNull(candidate)
        assertEquals("Bookshop", candidate!!.merchant)
        assertEquals("12.34", candidate.amount)
        assertEquals("USD", candidate.currency)
    }

    @Test
    fun ignoresNonTransactionNotifications() {
        val candidate = parser.parse(
            title = "Your statement is ready",
            text = "Your monthly statement is available to view in the app.",
            sourcePackage = "com.bank.app",
        )

        assertNull(candidate)
    }
}
