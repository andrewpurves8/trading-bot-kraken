package com.passivepastry.tradingbot

import android.app.IntentService
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "MainService.kt"

class MainService : IntentService("MainService") {
    private val notificationId = 1

    override fun onHandleIntent(intent: Intent?) {
        val currentDateTime = LocalDateTime.now()
        val currentDateTimeFormatted = currentDateTime.format(DateTimeFormatter.ISO_TIME)

        val builder = NotificationCompat.Builder(this, MainActivity.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Time is $currentDateTimeFormatted")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notification = builder.build()

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, notification)
        }

        startForeground(notificationId, notification)

        Trader(this).checkForTrades()

        Thread.sleep(30_000)
    }
}