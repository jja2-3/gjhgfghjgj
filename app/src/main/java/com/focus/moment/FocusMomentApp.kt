package com.focus.moment

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class FocusMomentApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("focus_timer", "专注计时", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
