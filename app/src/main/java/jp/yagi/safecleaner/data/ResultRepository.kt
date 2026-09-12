package jp.yagi.safecleaner.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import jp.yagi.safecleaner.model.AppOperationResult
import jp.yagi.safecleaner.model.CleanerMode
import jp.yagi.safecleaner.model.RunSummary
import jp.yagi.safecleaner.model.StepStatus
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.resultDataStore by preferencesDataStore("last_result")

class ResultRepository(private val context: Context) {
    private val summaryKey = stringPreferencesKey("summary_json")

    suspend fun save(summary: RunSummary) {
        context.resultDataStore.edit { it[summaryKey] = summary.toJson().toString() }
    }

    suspend fun load(): RunSummary? = runCatching {
        context.resultDataStore.data.first()[summaryKey]?.let(::decode)
    }.getOrNull()

    private fun RunSummary.toJson() = JSONObject().apply {
        put("id", id)
        put("mode", mode.name)
        put("started", startedAtMillis)
        put("finished", finishedAtMillis)
        put("targets", targetCount)
        put("automatic", automaticExcludedCount)
        put("user", userExcludedCount)
        put("ramBefore", availableRamBefore)
        put("ramAfter", availableRamAfter)
        put("results", JSONArray().apply {
            results.forEach { result -> put(JSONObject().apply {
                put("package", result.packageName)
                put("label", result.label)
                put("cache", result.cacheStatus.name)
                put("force", result.forceStopStatus.name)
                put("reason", result.failureReason ?: JSONObject.NULL)
            }) }
        })
    }

    private fun decode(raw: String): RunSummary {
        val json = JSONObject(raw)
        val array = json.getJSONArray("results")
        val results = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(AppOperationResult(
                    packageName = item.getString("package"),
                    label = item.getString("label"),
                    cacheStatus = StepStatus.valueOf(item.getString("cache")),
                    forceStopStatus = StepStatus.valueOf(item.getString("force")),
                    failureReason = item.optString("reason").takeIf { it.isNotBlank() && it != "null" },
                ))
            }
        }
        return RunSummary(
            id = json.getString("id"), mode = CleanerMode.valueOf(json.getString("mode")),
            startedAtMillis = json.getLong("started"), finishedAtMillis = json.getLong("finished"),
            targetCount = json.getInt("targets"), automaticExcludedCount = json.getInt("automatic"),
            userExcludedCount = json.getInt("user"), results = results,
            availableRamBefore = json.getLong("ramBefore"), availableRamAfter = json.getLong("ramAfter"),
        )
    }
}
