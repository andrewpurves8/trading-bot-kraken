package com.passivepastry.tradingbot

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val TAG = "MainActivity.kt"
private const val millisPerMinute = 1000 * 60
private const val millisPerHour = millisPerMinute * 60

class MainActivity : AppCompatActivity() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "TradingBotChannel"
    }



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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        scheduleAlarm()
//        val serviceIntent = Intent(this, MainService::class.java)
//        startForegroundService(serviceIntent)

        val textViewTime = findViewById<View>(R.id.text_view_time) as TextView
        val textViewKst = findViewById<View>(R.id.text_view_kst) as TextView
        val textViewSig = findViewById<View>(R.id.text_view_sig) as TextView
        val textViewAction = findViewById<View>(R.id.text_view_action) as TextView

        val sharedPreferences = getSharedPreferences("TradingBotPrefs", Context.MODE_PRIVATE)
        val time = sharedPreferences.getString("time", "N/A")
        val kst = sharedPreferences.getString("kst", "N/A")
        val sig = sharedPreferences.getString("sig", "N/A")
        val action = sharedPreferences.getString("action", "N/A")

        textViewTime.text = "Time: $time"
        textViewKst.text = "KST: $kst"
        textViewSig.text = "Sig: $sig"
        textViewAction.text = "Action: $action"
    }

    fun scheduleAlarm() {
        val intent = Intent(applicationContext, AlarmReceiver::class.java)
        val pIntent = PendingIntent.getBroadcast(
            this,
            AlarmReceiver.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val currentMillis = System.currentTimeMillis() // alarm is set right away
        val millisPastTheHour = currentMillis % millisPerHour
        val millisAtNextHour = currentMillis + millisPerHour - millisPastTheHour + millisPerMinute

        val alarm = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager

         alarm.setRepeating(
             AlarmManager.RTC_WAKEUP, millisAtNextHour,
             AlarmManager.INTERVAL_HOUR, pIntent)
    }

    private fun createNotificationChannel() {
        val descriptionText = "Trading bot notification channel"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_ID,
            importance
        ).apply {
            description = descriptionText
        }

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}