package com.passivepastry.tradingbot


import android.app.IntentService
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONObject

private const val TAG = "MainService.kt"

class MainService : IntentService("MainService") {
    private val notificationId = 1

    @Throws(JSONException::class)
    fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keysItr: Iterator<String> = this.keys()
        while (keysItr.hasNext()) {
            val key = keysItr.next()
            var value: Any = this.get(key)
            when (value) {
                is JSONArray -> value = value.toList()
                is JSONObject -> value = value.toMap()
            }
            map[key] = value
        }
        return map
    }

    @Throws(JSONException::class)
    fun JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until this.length()) {
            var value: Any = this[i]
            when (value) {
                is JSONArray -> value = value.toList()
                is JSONObject -> value = value.toMap()
            }
            list.add(value)
        }
        return list
    }

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

        TraderKraken(this).checkForTrades()

        Thread.sleep(30_000)
    }
}