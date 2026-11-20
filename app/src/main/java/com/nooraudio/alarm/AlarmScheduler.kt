package com.nooraudio.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {
    const val ACTION_FIRE = "com.nooraudio.alarm.FIRE"
    const val EXTRA_ID = "alarm_id"

    fun hasExactAccess(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    fun nextTime(alarm: AudioAlarm): Long? = try {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val date = if (alarm.daily) LocalDate.now(zone) else LocalDate.parse(alarm.date)
        var day = date
        var whenMs = day.atTime(LocalTime.of(alarm.hour, alarm.minute))
            .atZone(zone).toInstant().toEpochMilli()
        if (alarm.daily && whenMs <= now) {
            day = day.plusDays(1)
            whenMs = day.atTime(LocalTime.of(alarm.hour, alarm.minute))
                .atZone(zone).toInstant().toEpochMilli()
        }
        if (whenMs <= now) null else whenMs
    } catch (_: Exception) { null }

    fun schedule(context: Context, alarm: AudioAlarm) {
        cancel(context, alarm.id)
        if (!alarm.enabled) return
        val time = nextTime(alarm) ?: return
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pending(context, alarm.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (hasExactAccess(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
            } else {
                // Best-effort backup only; Android can delay this inexact alarm.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
        }
    }

    fun cancel(context: Context, id: Int) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pending(context, id, PendingIntent.FLAG_NO_CREATE) ?: return
        manager.cancel(pending)
        pending.cancel()
    }

    fun restoreAll(context: Context) {
        AlarmStore.all(context).forEach { schedule(context, it) }
    }

    private fun pending(context: Context, id: Int, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id, intent, flag or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val alarm = AlarmStore.find(context, id) ?: return
        if (!alarm.enabled) return

        // Schedule the *next* daily alarm before media starts, so failures of media
        // decoding cannot break the repeating schedule.
        if (alarm.daily) {
            AlarmScheduler.schedule(context, alarm)
        } else {
            AlarmStore.upsert(context, alarm.copy(enabled = false))
        }
        try {
            AudioAlarmService.play(context, id, scheduled = true)
        } catch (_: Exception) {
            // Some OEM power-management policies may block background service starts.
        }
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                AlarmScheduler.restoreAll(context)
        }
    }
}
