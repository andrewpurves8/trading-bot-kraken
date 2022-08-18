package com.passivepastry.tradingbot

import android.content.Context
import android.util.Log
import org.json.JSONArray
import com.passivepastry.tradingbot.utils.CandleStick
import com.passivepastry.tradingbot.utils.Http
import okhttp3.Response
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

private const val TAG = "TradeTask.kt"

class TraderKraken(private val context: Context) {
    private val http = Http()

    private val sharedPreferences = context.getSharedPreferences("TradingBotPrefs", Context.MODE_PRIVATE)

    fun checkForTrades() {
//        val currentDateTime = LocalDateTime.now()
//        val currentDateTimeFormatted = currentDateTime.format(DateTimeFormatter.ISO_TIME)
//        val ema = getEma()
//        val latestSar = getPSAR()
//
//        val editor = sharedPreferences.edit()
//        editor.putString("close", "${getClose()}")
//        editor.putString("time", currentDateTimeFormatted)
//        editor.putString("ema", "$ema")
//        editor.putString("psar", "$latestSar")
//
//        if (sarLatestChange == 1) {
//            // Sell
//            editor.putString("action", "Sell")
//
//            if (getClose() < ema) {
//                editor.putString("decision", "Sell")
//                sell()
//            }
//            else
//                editor.putString("decision", "Do nothing")
//        } else if (sarLatestChange == 2) {
//            // Buy
//            editor.putString("action", "Buy")
//
//            if (getClose() > ema) {
//                editor.putString("decision", "Buy")
//                buy()
//            }
//            else
//                editor.putString("decision", "Do nothing")
//        } else {
//            // No change
//            editor.putString("action", "N/A")
//            editor.putString("decision", "Do nothing")
//        }
//
//        editor.commit()
//
//        for (i in 0 until sars.size) {
//            Log.d(TAG, "$i ${sars[i]}")
//        }
//        Log.d(TAG, "$latestSar")
    }

    private fun Double.floorDecimals(decimals: Int): Double {
        var multiplier = 1
        repeat(decimals) { multiplier *= 10 }
        return floor(this * multiplier) / multiplier.toDouble()
    }

    fun Double.roundDecimals(decimals: Int): Double {
        var multiplier = 1
        repeat(decimals) { multiplier *= 10 }
        return round(this * multiplier) / multiplier.toDouble()
    }

    private fun getAccountInfo() : Response {
        val paramMap = mapOf(
            "timestamp" to "${System.currentTimeMillis()}")

        return http.httpGet(
            "https://api.binance.com/api/v3/account",
            paramMap,
            true)
    }

    private fun getClose(): Double {
        val paramMap = mapOf(
            "symbol" to "ETHUSDT",
            "interval" to "1h",
            "limit" to "2")

        val response = http.httpGet(
            "https://api.binance.com/api/v3/klines",
            paramMap,
            false)

        val klinesString = response.body()!!.string()
        val klinesJson = JSONArray(klinesString)
        val jsonKline = klinesJson.get(0) as JSONArray

        return CandleStick(jsonKline).close
    }


    private fun calcEma50(candleSticks: List<CandleStick>) : Double {
        var ema = calcInitialSma50(candleSticks)
        val smooth = 2.0 / (1 + 50)
        for (i in 50 until candleSticks.size) {
            ema = candleSticks[i].close * smooth + ema * (1 - smooth)
        }
        return ema
    }

    private fun calcInitialSma50(candleSticks: List<CandleStick>) : Double {
        var total = 0.0

        for (i in 0 until 50) {
            total += candleSticks[i].close
        }

        val sma = total / 50

        return sma
    }


    private fun getMin(candleSticks: List<CandleStick>, startIndex: Int, endIndex: Int) : Double {
        var min = Double.MAX_VALUE
        for (i in startIndex .. endIndex) {
            if (candleSticks[i].low < min)
                min = candleSticks[i].low
        }
        return min
    }

    private fun getMax(candleSticks: List<CandleStick>, startIndex: Int, endIndex: Int) : Double {
        var max = Double.MIN_VALUE
        for (i in startIndex .. endIndex) {
            if (candleSticks[i].high > max)
                max = candleSticks[i].high
        }
        return max
    }
}