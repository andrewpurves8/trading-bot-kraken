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

//    class ResponseOhlc {
//        val error: List<String>? = null
//
//        class Result {
//            val ticker: String? = null
//            val testt = JSONObject()
//            class Ohlc {
//
//            }
//        }
//    }

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

        // TraderKraken(this).checkForTrades()
        calcKST()

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

        val smaRoc1 = smaRoc(closes, rocLen1, smaLen1, sigLen + 1)
        val smaRoc2 = smaRoc(closes, rocLen2, smaLen2, sigLen + 1)
        val smaRoc3 = smaRoc(closes, rocLen3, smaLen3, sigLen + 1)
        val smaRoc4 = smaRoc(closes, rocLen4, smaLen4, sigLen + 1)

        if (smaRoc1.size != sigLen + 1 ||
            smaRoc1.size != smaRoc2.size ||
            smaRoc2.size != smaRoc3.size ||
            smaRoc3.size != smaRoc4.size) {
            throw Exception("smaRoc series not of equal sizes")
        }

        val kstList = mutableListOf<Double>()
        for (i in 0 until sigLen + 1) {
            val kstVal = smaRoc1[i] + 2 * smaRoc2[i] + 3 * smaRoc3[i] + 4 * smaRoc4[i]
            kstList.add(kstVal)
        }

        val kst = kstList[sigLen]
        val sig = sma(kstList, sigLen, 2)[1]
        val kstPrev = kstList[sigLen - 1]
        val sigPrev = sma(kstList, sigLen, 2)[0]

        Log.d(TAG, "KST: $kst")
        Log.d(TAG, "SIG: $sig")
        Log.d(TAG, "KST PREV: $kstPrev")
        Log.d(TAG, "SIG PREV: $sigPrev")
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
}

// Pine code for KST:

//indicator(title="Know Sure Thing", shorttitle="KST", format=format.price, precision=4, timeframe="", timeframe_gaps=true)
//roclen1 = input.int(10, minval=1, title = "ROC Length #1")
//roclen2 = input.int(15, minval=1, title = "ROC Length #2")
//roclen3 = input.int(20, minval=1, title = "ROC Length #3")
//roclen4 = input.int(30, minval=1, title = "ROC Length #4")
//smalen1 = input.int(10, minval=1, title = "SMA Length #1")
//smalen2 = input.int(10, minval=1, title = "SMA Length #2")
//smalen3 = input.int(10, minval=1, title = "SMA Length #3")
//smalen4 = input.int(15, minval=1, title = "SMA Length #4")
//siglen = input.int(9, minval=1, title = "Signal Line Length")
//smaroc(roclen, smalen) => ta.sma(ta.roc(close, roclen), smalen)
//kst = smaroc(roclen1, smalen1) + 2 * smaroc(roclen2, smalen2) + 3 * smaroc(roclen3, smalen3) + 4 * smaroc(roclen4, smalen4)
//sig = ta.sma(kst, siglen)
//plot(kst, color=#009688, title="KST")
//plot(sig, color=#F44336, title="Signal")
//hline(0, title="Zero", color = #787B86)