package com.feidbo.marathon.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.feidbo.marathon.MainActivity
import com.feidbo.marathon.R
import com.feidbo.marathon.core.DayStatus
import com.feidbo.marathon.core.Plan
import com.feidbo.marathon.core.Schedule
import com.feidbo.marathon.core.formatMiles
import com.feidbo.marathon.core.primaryTarget
import com.feidbo.marathon.core.strengthSession
import com.feidbo.marathon.core.workoutTitle
import com.feidbo.marathon.data.AppData
import com.feidbo.marathon.data.Store
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Home-screen widget: today's workout, what's still to do, streak and level. Works fully offline. */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        draw(context, manager, ids)
        scheduleMidnightRefresh(context)
    }

    /** Android 11 and older: the launcher reports a new size; pick the layout again. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        draw(context, manager, intArrayOf(id))
    }

    override fun onEnabled(context: Context) = scheduleMidnightRefresh(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(refreshIntent(context))
    }

    /** One layout per size range (docs/decisions.md §7). Every layout uses the same view ids. */
    internal enum class Size(val layout: Int, val minWidthDp: Float, val minHeightDp: Float) {
        COMPACT(R.layout.widget_today_compact, 110f, 100f),
        TALL(R.layout.widget_today, 110f, 180f),        // 2×2 in portrait, about 130 × 220 dp
        WIDE(R.layout.widget_today_wide, 200f, 100f),   // 2×2 in landscape, about 269 × 117 dp
        LARGE(R.layout.widget_today_large, 200f, 180f),
    }

    /** Everything a layout shows; worked out once and applied to each size. */
    internal class Content(
        val header: String,
        val streak: String,
        val title: String,
        val big: String,
        val detail: String,
        val strength: String?,
        val todoShort: String,
        val todoLong: String,
        val footer: String,
        val footerShort: String,
    )

    companion object {
        /** Redraws every placed widget; called after each change in the app. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            draw(context, manager, ids)
        }

        private fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val content = content(context)
            if (Build.VERSION.SDK_INT >= 31) {
                // The system picks the largest layout that fits the widget's current size.
                val views = RemoteViews(Size.entries.associate { SizeF(it.minWidthDp, it.minHeightDp) to views(context, it, content) })
                ids.forEach { manager.updateAppWidget(it, views) }
            } else {
                ids.forEach { id ->
                    val o = manager.getAppWidgetOptions(id)
                    // Portrait uses min width × max height; landscape max width × min height.
                    val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 130)
                    val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 220)
                    val size = when {
                        w >= 200 && h >= 180 -> Size.LARGE
                        w >= 200 -> Size.WIDE
                        h >= 180 -> Size.TALL
                        else -> Size.COMPACT
                    }
                    manager.updateAppWidget(id, views(context, size, content))
                }
            }
        }

        internal fun views(context: Context, size: Size, c: Content?): RemoteViews {
            val views = RemoteViews(context.packageName, size.layout)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.w_root, open)
            if (c == null) return views
            val roomy = size == Size.TALL || size == Size.LARGE
            views.setTextViewText(R.id.w_header, c.header)
            // Narrow sizes: just the streak, so the day number keeps its room.
            views.setTextViewText(R.id.w_streak, if (size == Size.TALL || size == Size.COMPACT) c.streak.substringBefore(" ·") else c.streak)
            views.setTextViewText(R.id.w_title, c.title)
            // "11.5 mi": the unit at half size so long-run distances fit a 2×2.
            views.setTextViewText(R.id.w_big, SpannableString(c.big).apply {
                val unit = c.big.indexOf(" mi")
                if (unit >= 0) setSpan(RelativeSizeSpan(0.5f), unit, c.big.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            })
            views.setTextViewText(R.id.w_detail, c.detail)
            // The smallest size keeps the to-do line instead of the strength line.
            views.setViewVisibility(R.id.w_strength, if (c.strength != null && size != Size.COMPACT) View.VISIBLE else View.GONE)
            c.strength?.let { views.setTextViewText(R.id.w_strength, it) }
            views.setTextViewText(R.id.w_todo, if (size == Size.LARGE) c.todoLong else c.todoShort)
            views.setTextViewText(R.id.w_footer, if (roomy) c.footer else c.footerShort)
            views.setViewVisibility(R.id.w_footer, if (size == Size.COMPACT) View.GONE else View.VISIBLE)
            return views
        }

        private fun content(context: Context): Content? {
            val data = try { Store(context).load() } catch (_: Exception) { null } ?: return null
            return content(data, LocalDate.now())
        }

        internal fun content(data: AppData, today: LocalDate): Content {
            val schedule = Schedule(data.logs, data.settings, today)
            val progress = com.feidbo.marathon.ui.AppViewModel.progressFor(data, schedule, today)
            val toRace = ChronoUnit.DAYS.between(today, Plan.raceDay)
            val streak = "🔥 ${progress.streak} · Lv ${progress.level.level}"
            val footer = when {
                toRace > 0 -> "$toRace days to Eugene"
                toRace == 0L -> "🏁 RACE DAY"
                else -> "🏅 Eugene done"
            }
            val footerShort = if (toRace > 0) "${toRace}d to Eugene" else footer

            val sd = schedule.on(today)
            if (sd == null) {
                val before = today < Plan.start
                return Content(
                    header = "🦆 Road to Eugene", streak = streak,
                    title = if (before) "Plan starts Oct 16" else "Marathoner!",
                    big = if (before) "🦆" else "🏅",
                    detail = if (before) "Get ready to run." else "You finished Eugene.",
                    strength = null, todoShort = "${progress.xp} XP", todoLong = "${progress.xp} XP earned",
                    footer = footer, footerShort = footerShort,
                )
            }

            val day = sd.day
            val calf = data.calf
            val session = strengthSession(day.strength)
            val target = primaryTarget(day, data.settings.marathonPaceSec)
            val cap = data.settings.easyHrCap?.takeIf { day.run.isEasyType }
            val log = data.logs[day.index]
            val missed = log?.status == DayStatus.MISSED
            val stretched = day.index in data.stretchDays
            val todo = buildList {
                if (day.run.isRun && log?.run == null && !missed) add("🏃" to "Run")
                if (day.strength.isPlanned && log?.strength.isNullOrEmpty() && !missed) add("🏋️" to "Lift")
                if (calf.flagged && day.index !in data.calfDays) add("🦵" to "Calf")
                if (!stretched) add("🧘" to "Stretch")
            }
            val (todoShort, todoLong) = when {
                missed && todo.isEmpty() -> "💚 Rest taken" to "💚 Rest taken. Back at it soon."
                todo.isEmpty() -> "✅ All done!" to "✅ All done today! ${progress.xp} XP"
                else -> ("To do: " + todo.joinToString(" ") { it.first }) to
                    ("To do: " + todo.joinToString(" · ") { "${it.first} ${it.second}" })
            }
            return Content(
                header = "🦆 Day ${day.index + 1}",
                streak = streak,
                title = when {
                    day.run.isRun -> workoutTitle(day)
                    day.strength.isPlanned -> "Strength day"
                    else -> "Rest day"
                },
                big = when {
                    day.run.isRun -> "${formatMiles(day.miles)} mi"
                    day.strength.isPlanned -> "🏋️"
                    else -> "😴"
                },
                detail = when {
                    target != null -> "${target.rangeText} · effort ${target.effort}" + (cap?.let { " · HR < $it" } ?: "")
                    session != null -> "${session.minutes} · dumbbells or bodyweight"
                    else -> "Recover. Stretch if you like."
                },
                strength = session?.takeIf { day.run.isRun }?.let { "🏋️ + Strength · ${it.minutes}" },
                todoShort = todoShort,
                todoLong = todoLong,
                footer = footer,
                footerShort = footerShort,
            )
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
