package com.nooraudio.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** All music paths are Android SAF content: URIs, not fragile filesystem paths. */
data class AudioAlarm(
    val id: Int,
    val name: String,
    val hour: Int,
    val minute: Int,
    val daily: Boolean,
    val date: String,            // yyyy-MM-dd, used only when daily = false
    val enabled: Boolean,
    val sourceType: String,      // "files" or "folder"
    val files: List<String> = emptyList(),
    val folder: String = "",
    val sourceLabel: String = "",
    val shuffle: Boolean = false
)

object AlarmStore {
    private const val PREFS = "noor_audio_alarms_v1"
    private const val KEY = "alarms"

    fun all(context: Context): List<AudioAlarm> = try {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            try {
                val obj = array.getJSONObject(index)
                val fileArray = obj.optJSONArray("files") ?: JSONArray()
                AudioAlarm(
                    id = obj.getInt("id"),
                    name = obj.optString("name", "My recitation"),
                    hour = obj.optInt("hour", 6).coerceIn(0, 23),
                    minute = obj.optInt("minute", 0).coerceIn(0, 59),
                    daily = obj.optBoolean("daily", true),
                    date = obj.optString("date", LocalDate.now().toString()),
                    enabled = obj.optBoolean("enabled", true),
                    sourceType = obj.optString("sourceType", "files"),
                    files = (0 until fileArray.length()).map { fileArray.getString(it) },
                    folder = obj.optString("folder", ""),
                    sourceLabel = obj.optString("sourceLabel", ""),
                    shuffle = obj.optBoolean("shuffle", false)
                )
            } catch (_: Exception) { null }
        }
    } catch (_: Exception) { emptyList() }

    @Synchronized
    fun upsert(context: Context, alarm: AudioAlarm) {
        val list = all(context).toMutableList()
        val index = list.indexOfFirst { it.id == alarm.id }
        if (index >= 0) list[index] = alarm else list.add(alarm)
        write(context, list)
    }

    @Synchronized
    fun delete(context: Context, id: Int) = write(context, all(context).filterNot { it.id == id })

    fun find(context: Context, id: Int): AudioAlarm? = all(context).firstOrNull { it.id == id }

    @Synchronized
    fun nextId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getInt("nextId", 1000)
        prefs.edit().putInt("nextId", next + 1).apply()
        return next
    }

    private fun write(context: Context, alarms: List<AudioAlarm>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(JSONObject().apply {
                put("id", alarm.id)
                put("name", alarm.name)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("daily", alarm.daily)
                put("date", alarm.date)
                put("enabled", alarm.enabled)
                put("sourceType", alarm.sourceType)
                put("files", JSONArray(alarm.files))
                put("folder", alarm.folder)
                put("sourceLabel", alarm.sourceLabel)
                put("shuffle", alarm.shuffle)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).commit()
    }
}
