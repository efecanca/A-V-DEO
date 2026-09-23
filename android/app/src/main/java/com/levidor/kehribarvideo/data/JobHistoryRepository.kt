package com.levidor.kehribarvideo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.historyDataStore by preferencesDataStore(name = "kehribar_job_history")

/**
 * "Projeler / Sonuçlar" ekranı için üretilen videoların KALICI geçmişini
 * cihazda saklar. Backend'in kendi iş listesi (/jobs) yalnızca bellek içi ve
 * geçicidir (Colab oturumu kapanınca sıfırlanır); asıl kalıcı kayıt burada,
 * istemci tarafında tutulur - böylece geçmiş, backend yeniden başlasa bile kaybolmaz.
 */
class JobHistoryRepository(private val context: Context) {

    companion object {
        private val HISTORY_KEY = stringPreferencesKey("job_history_json")
        private const val MAX_ENTRIES = 100
    }

    val historyFlow: Flow<List<JobRecord>> = context.historyDataStore.data.map { prefs ->
        parseHistory(prefs[HISTORY_KEY] ?: "[]")
    }

    suspend fun addOrUpdate(record: JobRecord) {
        context.historyDataStore.edit { prefs ->
            val current = parseHistory(prefs[HISTORY_KEY] ?: "[]").toMutableList()
            val idx = current.indexOfFirst { it.jobId == record.jobId }
            if (idx >= 0) current[idx] = record else current.add(0, record)
            val trimmed = current.sortedByDescending { it.createdAtMillis }.take(MAX_ENTRIES)
            prefs[HISTORY_KEY] = serializeHistory(trimmed)
        }
    }

    private fun parseHistory(json: String): List<JobRecord> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                JobRecord(
                    jobId = o.getString("jobId"),
                    createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                    productLabel = o.optString("productLabel", "Ürün"),
                    mode = o.optString("mode", "image_to_video"),
                    durationSeconds = o.optInt("durationSeconds", 5),
                    aspectRatio = o.optString("aspectRatio", "16:9"),
                    status = o.optString("status", "processing"),
                    progress = o.optInt("progress", 0),
                    localVideoPath = o.optString("localVideoPath", "").ifBlank { null },
                    errorMessage = o.optString("errorMessage", "").ifBlank { null }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeHistory(records: List<JobRecord>): String {
        val arr = JSONArray()
        records.forEach { r ->
            val o = JSONObject()
            o.put("jobId", r.jobId)
            o.put("createdAtMillis", r.createdAtMillis)
            o.put("productLabel", r.productLabel)
            o.put("mode", r.mode)
            o.put("durationSeconds", r.durationSeconds)
            o.put("aspectRatio", r.aspectRatio)
            o.put("status", r.status)
            o.put("progress", r.progress)
            o.put("localVideoPath", r.localVideoPath ?: "")
            o.put("errorMessage", r.errorMessage ?: "")
            arr.put(o)
        }
        return arr.toString()
    }
}

data class JobRecord(
    val jobId: String,
    val createdAtMillis: Long,
    val productLabel: String,
    val mode: String,
    val durationSeconds: Int,
    val aspectRatio: String,
    val status: String,
    val progress: Int,
    val localVideoPath: String? = null,
    val errorMessage: String? = null
)
