package com.acc.spendsentry.data

import android.content.Context
import android.content.SharedPreferences
import com.acc.spendsentry.model.TransactionDraft
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TransactionRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("spend_sentry_store", Context.MODE_PRIVATE)
    private val parser = NotificationTransactionParser()
    private val _drafts = MutableStateFlow(loadDrafts())

    val drafts: StateFlow<List<TransactionDraft>> = _drafts

    fun ingestNotification(sourcePackage: String, title: String?, text: String?, postedAtMillis: Long): TransactionDraft? {
        val candidate = parser.parse(title, text, sourcePackage) ?: return null
        val existing = _drafts.value.firstOrNull {
            !it.acknowledged &&
                it.postedAtMillis == postedAtMillis &&
                it.sourcePackage == sourcePackage &&
                it.rawTitle == title.orEmpty() &&
                it.rawText == text.orEmpty()
        }

        val draft = existing ?: TransactionDraft(
            id = UUID.randomUUID().toString(),
            sourcePackage = sourcePackage,
            postedAtMillis = postedAtMillis,
            merchant = candidate.merchant,
            amount = candidate.amount,
            currency = candidate.currency,
            rawTitle = title.orEmpty(),
            rawText = text.orEmpty(),
        )

        saveDraft(draft)
        return draft
    }

    fun updateDraft(updated: TransactionDraft) {
        saveDraft(updated)
    }

    fun acknowledgeDraft(id: String) {
        val now = System.currentTimeMillis()
        val updated = _drafts.value.map {
            if (it.id == id) it.copy(acknowledged = true, acknowledgedAtMillis = now) else it
        }
        persist(updated)
    }

    fun deleteDraft(id: String) {
        persist(_drafts.value.filterNot { it.id == id })
    }

    fun pendingDrafts(): List<TransactionDraft> = _drafts.value.filterNot { it.acknowledged }

    private fun saveDraft(draft: TransactionDraft) {
        val withoutDraft = _drafts.value.filterNot { it.id == draft.id }
        persist(listOf(draft) + withoutDraft)
    }

    private fun persist(drafts: List<TransactionDraft>) {
        _drafts.value = drafts.sortedByDescending { it.postedAtMillis }
        prefs.edit().putString(KEY_DRAFTS, serialize(_drafts.value)).apply()
    }

    private fun loadDrafts(): List<TransactionDraft> {
        val raw = prefs.getString(KEY_DRAFTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(item.toDraft())
            }
        }
    }

    private fun serialize(drafts: List<TransactionDraft>): String {
        val array = JSONArray()
        drafts.forEach { draft ->
            array.put(
                JSONObject().apply {
                    put("id", draft.id)
                    put("sourcePackage", draft.sourcePackage)
                    put("postedAtMillis", draft.postedAtMillis)
                    put("merchant", draft.merchant)
                    put("amount", draft.amount)
                    put("currency", draft.currency)
                    put("rawTitle", draft.rawTitle)
                    put("rawText", draft.rawText)
                    put("acknowledged", draft.acknowledged)
                    put("acknowledgedAtMillis", draft.acknowledgedAtMillis)
                },
            )
        }
        return array.toString()
    }

    private fun JSONObject.toDraft(): TransactionDraft {
        return TransactionDraft(
            id = optString("id"),
            sourcePackage = optString("sourcePackage"),
            postedAtMillis = optLong("postedAtMillis"),
            merchant = optString("merchant"),
            amount = optString("amount"),
            currency = optString("currency"),
            rawTitle = optString("rawTitle"),
            rawText = optString("rawText"),
            acknowledged = optBoolean("acknowledged"),
            acknowledgedAtMillis = if (has("acknowledgedAtMillis") && !isNull("acknowledgedAtMillis")) optLong("acknowledgedAtMillis") else null,
        )
    }

    companion object {
        private const val KEY_DRAFTS = "drafts"
    }
}
