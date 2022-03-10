package com.passivepastry.tradingbot

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context

private const val TAG = "AlarmReceiver.kt"

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val serviceIntent = Intent(context, MainService::class.java)
        context.startForegroundService(serviceIntent)
    }

    companion object {
        const val REQUEST_CODE = 12345
    }
}