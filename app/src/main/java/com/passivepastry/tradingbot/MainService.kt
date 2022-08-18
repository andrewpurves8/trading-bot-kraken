package com.passivepastry.tradingbot

//import com.passivepastry.tradingbot.BuildConfig

import android.app.IntentService
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.passivepastry.tradingbot.kraken_api.KrakenApi
import com.passivepastry.tradingbot.utils.CandleStick
import org.json.JSONArray
import org.json.JSONException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import java.lang.Exception


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

//        TraderKraken(this).checkForTrades()
//        calcKST()
        getBalance("XETH")
        getBalance("USDT")


        Thread.sleep(30_000)
    }

    fun calcKST() {
        val apiKey = BuildConfig.API_KEY
        val privateKey = BuildConfig.PRIVATE_KEY

        val api = KrakenApi()
        api.setKey(apiKey)
        api.setSecret(privateKey)

        val response: String
        val input: MutableMap<String?, String?> = HashMap()

        input["pair"] = "ETHUSDT"
        input["interval"] = "60"
        response = api.queryPublic(KrakenApi.Method.OHLC, input)

        val responseObj = JSONObject(response)
        val resultObj = responseObj.getJSONObject("result")
        val tickerJsonArray = resultObj.getJSONArray("ETHUSDT")
//        val tickerList = tickerJsonArray.toList()
//        [xxx, open, high, low, close]

//        val candleSticks = mutableListOf<CandleStick>()
        val closes = mutableListOf<Double>()
        for (i in 0 until tickerJsonArray.length()) {
            val close = tickerJsonArray.getJSONArray(i).get(4).toString().toDouble()
            closes.add(close)
        }

        /////////////////////////////////////////////////

        val rocLen1 = 10
        val rocLen2 = 15
        val rocLen3 = 20
        val rocLen4 = 30
        val smaLen1 = 10
        val smaLen2 = 10
        val smaLen3 = 10
        val smaLen4 = 15
        val sigLen = 9
        val numSamples = 2 // Excludes current, uncommitted sample

        val smaRoc1 = smaRoc(closes, rocLen1, smaLen1, sigLen + numSamples)
        val smaRoc2 = smaRoc(closes, rocLen2, smaLen2, sigLen + numSamples)
        val smaRoc3 = smaRoc(closes, rocLen3, smaLen3, sigLen + numSamples)
        val smaRoc4 = smaRoc(closes, rocLen4, smaLen4, sigLen + numSamples)

        if (smaRoc1.size != sigLen + numSamples ||
            smaRoc1.size != smaRoc2.size ||
            smaRoc2.size != smaRoc3.size ||
            smaRoc3.size != smaRoc4.size) {
            throw Exception("smaRoc series not of equal sizes")
        }

        val kstList = mutableListOf<Double>()
        for (i in 0 until sigLen + numSamples) {
            val kstVal = smaRoc1[i] + 2 * smaRoc2[i] + 3 * smaRoc3[i] + 4 * smaRoc4[i]
            kstList.add(kstVal)
        }

        val kstMostRecent = kstList[kstList.size - 2]
        val kstPrevious = kstList[kstList.size - 3]
        val sig = sma(kstList, sigLen, numSamples + 1)
        val sigMostRecent = sig[numSamples - 1]
        val sigPrevious = sig[numSamples - 2]

        if (kstMostRecent > sigMostRecent && kstPrevious < sigPrevious) {
            Log.d(TAG, "Action: BUY")
        } else if (kstMostRecent < sigMostRecent && kstPrevious > sigPrevious) {
            Log.d(TAG, "Action: SELL")
        } else {
            Log.d(TAG, "Action: HODL")
        }
    }

    fun smaRoc(values: List<Double>, rocLen: Int, smaLen: Int, targetLen: Int): List<Double> {
        return sma(roc(values, rocLen, smaLen + targetLen), smaLen, targetLen)
    }

    fun sma(values: List<Double>, smaLen: Int, targetLen: Int): List<Double> {
        val smaList = mutableListOf<Double>()

        for (i in targetLen - 1 downTo 0) {
            var total = 0.0
            for (j in 1 until smaLen + 1) {
                total += values[values.size - j - i]
            }
            smaList.add(total / smaLen)
        }

        Log.d(TAG, "SMA length ${smaList.size}")
        return smaList
    }

    fun roc(values: List<Double>, rocLen: Int, targetLen: Int): List<Double> {
        val rocList = mutableListOf<Double>()
        val lastIndex = values.size - 1

        for (i in targetLen - 1 downTo 0) {
            rocList.add((values[lastIndex - i] - values[lastIndex - rocLen  - i]) / values[lastIndex - rocLen - i] * 100)
        }

        Log.d(TAG, "ROC output length ${rocList.size}")
        return rocList
    }

    fun getBalance(asset: String) {
        val apiKey = BuildConfig.API_KEY
        val privateKey = BuildConfig.PRIVATE_KEY

        val api = KrakenApi()
        api.setKey(apiKey)
        api.setSecret(privateKey)

        val input: MutableMap<String?, String?> = HashMap()
        val response = api.queryPrivate(KrakenApi.Method.BALANCE, input)
        val responseObj = JSONObject(response)
        val resultObj = responseObj.getJSONObject("result")
        val balance = resultObj.get(asset)
        Log.d(TAG, "$asset balance: $balance")
    }

//    fun buy()
}