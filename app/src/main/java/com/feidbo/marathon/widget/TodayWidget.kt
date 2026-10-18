package com.feidbo.marathon.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.feidbo.marathon.MainActivity
import com.feidbo.marathon.R
import com.feidbo.marathon.core.DayStatus
import com.feidbo.marathon.core.Plan
import com.feidbo.marathon.core.Schedule
import com.feidbo.marathon.core.computeProgress
import com.feidbo.marathon.core.formatMiles
import com.feidbo.marathon.core.primaryTarget
import com.feidbo.marathon.core.strengthSession
import com.feidbo.marathon.core.workoutTitle
import com.feidbo.marathon.data.Store
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Home-screen widget: today's workout, what's still to do, streak and level. Works fully offline. */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = render(context)
        ids.forEach { manager.updateAppWidget(it, views) }
        scheduleMidnightRefresh(context)
    }

    override fun onEnabled(context: Context) = scheduleMidnightRefresh(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(refreshIntent(context))
    }

    companion object {
        /** Redraws every placed widget; called after each change in the app. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val views = render(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun render(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.w_root, open)

            val data = try { Store(context).load() } catch (_: Exception) { null }
            val today = LocalDate.now()
            if (data == null) return views
            val schedule = Schedule(data.logs, data.settings, today)
            val progress = computeProgress(schedule, data.logs, data.stretchDays, today)
            val toRace = ChronoUnit.DAYS.between(today, Plan.raceDay)
            views.setTextViewText(R.id.w_streak, "🔥 ${progress.streak} · Lv ${progress.level.level}")

            val sd = schedule.on(today)
            if (sd == null) {
                views.setTextViewText(R.id.w_header, "🦆 Road to Eugene")
                views.setTextViewText(R.id.w_title, if (today < Plan.start) "Plan starts Oct 16" else "Marathoner! 🏅")
                views.setTextViewText(R.id.w_detail, if (today < Plan.start) "Get ready to run." else "You finished Eugene.")
                views.setViewVisibility(R.id.w_strength, View.GONE)
                views.setTextViewText(R.id.w_todo, "${progress.xp} XP earned")
                return views
            }

            val day = sd.day
            views.setTextViewText(
                R.id.w_header,
                "🦆 Day ${day.index + 1} · " + if (toRace > 0) "$toRace days to Eugene" else "RACE DAY",
            )
            views.setTextViewText(R.id.w_title, if (day.run.isRun) "${workoutTitle(day)} · ${formatMiles(day.miles)} mi" else workoutTitle(day))
            val target = primaryTarget(day, data.settings.marathonPaceSec)
            views.setTextViewText(
                R.id.w_detail,
                when {
                    target != null -> "${target.rangeText} · effort ${target.effort}"
                    day.strength.isPlanned -> "No run today — just the machines"
                    else -> "Recover. 3-minute quad stretch."
                },
            )
            val session = strengthSession(day.strength)
            if (session != null) {
                views.setViewVisibility(R.id.w_strength, View.VISIBLE)
                views.setTextViewText(R.id.w_strength, "🏋️ Strength · ${session.minutes}")
            } else {
                views.setViewVisibility(R.id.w_strength, View.GONE)
            }

            val log = data.logs[day.index]
            val stretched = day.index in data.stretchDays
            val todo = buildList {
                if (day.run.isRun && (log == null || log.run == null) && log?.status != DayStatus.MISSED) add("🏃 Run")
                if (day.strength.isPlanned && log?.strength.isNullOrEmpty() && log?.status != DayStatus.MISSED) add("🏋️ Lift")
                if (!stretched) add("🧘 Stretch")
            }
            views.setTextViewText(
                R.id.w_todo,
                when {
                    log?.status == DayStatus.MISSED && todo.isEmpty() -> "💚 Rest taken. Back at it soon."
                    todo.isEmpty() -> "✅ All done today! Total ${progress.xp} XP"
                    else -> "To do: " + todo.joinToString(" · ")
                },
            )
            return views
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, TodayWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TodayWidget::class.java)),
                )
            return PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /** Refreshes just after midnight so the widget shows the new day's workout (inexact; no special permission). */
        private fun scheduleMidnightRefresh(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val next = LocalDate.now().plusDays(1).atTime(0, 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarms.set(AlarmManager.RTC, next, refreshIntent(context))
        }
    }
}
