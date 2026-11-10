package com.cachewipe.app.engine

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.cachewipe.app.R

/** Quick Settings tile (Pro): one tap opens the app and starts "clear all". */
class CleanTileService : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(AutoClean.cleanAllPendingIntent(this))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(AutoClean.cleanAllIntent(this))
        }
    }
}

/** 1x1 home screen widget (Pro): one tap opens the app and starts "clear all". */
class CleanWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_clean).apply {
            setOnClickPendingIntent(R.id.widget_root, AutoClean.cleanAllPendingIntent(context))
        }
        manager.updateAppWidget(ids, views)
    }
}
