package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AtAGlanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_at_a_glance)

            // Format current date e.g. "Sábado, 3 de octubre"
            val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault())
            val formattedDate = dateFormat.format(Date()).replaceFirstChar { it.uppercase() }
            views.setTextViewText(R.id.widget_date, formattedDate)

            // Weather & Temperature
            views.setTextViewText(R.id.widget_weather, "⛅ 22°C")

            // Contextual alert
            views.setTextViewText(
                R.id.widget_event_text,
                "👟 6.420 pasos • Todo al día en tu dispositivo"
            )

            // Click Intent to open App
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
