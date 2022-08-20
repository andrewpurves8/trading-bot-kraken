package com.passivepastry.tradingbot

import android.content.Context
import com.passivepastry.tradingbot.kraken_api.KrakenApi
import org.json.JSONObject
import java.lang.Exception
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "TradeTask.kt"

class TraderKraken(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("TradingBotPrefs", Context.MODE_PRIVATE)

    fun checkForTrades() {
        val apiKey = BuildConfig.API_KEY
        val privateKey = BuildConfig.PRIVATE_KEY

        val api = KrakenApi()
        api.setKey(apiKey)
        api.setSecret(privateKey)

        val closes = getCloses(api)

        val action = calcKst(closes)
        if (action == "buy")  {
            buy(api, closes[closes.size - 1])
        } else if (action == "sell") {
            sell(api)
        }
    }

    private fun getCloses(api: KrakenApi): List<Double> {
        val response: String
        val input: MutableMap<String?, String?> = HashMap()

        input["pair"] = "ETHUSDT"
        input["interval"] = "60"
        response = api.queryPublic(KrakenApi.Method.OHLC, input)

        val responseObj = JSONObject(response)
        val resultObj = responseObj.getJSONObject("result")
        val tickerJsonArray = resultObj.getJSONArray("ETHUSDT")

        val closes = mutableListOf<Double>()
        for (i in 0 until tickerJsonArray.length()) {
            // OHLC entries are in the follow order: [time, open, high, low, close]
            val close = tickerJsonArray.getJSONArray(i).get(4).toString().toDouble()
            closes.add(close)
        }

        return closes
    }

    private fun calcKst(closes: List<Double>): String {
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

        var action = "hodl"
        if (kstMostRecent > sigMostRecent && kstPrevious < sigPrevious) {
            action = "buy"
        } else if (kstMostRecent < sigMostRecent && kstPrevious > sigPrevious) {
            action = "sell"
        }

        val editor = sharedPreferences.edit()
        val currentDateTime = LocalDateTime.now()
        val currentDateTimeFormatted = currentDateTime.format(DateTimeFormatter.ISO_TIME)
        editor.putString("time", currentDateTimeFormatted)
        editor.putString("kst", "$kstMostRecent")
        editor.putString("sig", "$sigMostRecent")
        editor.putString("action", action)
        editor.apply()

        return action
    }

    private fun getBalance(api: KrakenApi, asset: String): Double {
        val input: MutableMap<String?, String?> = HashMap()
        val response = api.queryPrivate(KrakenApi.Method.BALANCE, input)
        val responseObj = JSONObject(response)
        val resultObj = responseObj.getJSONObject("result")
        val balance = resultObj.get(asset)
        return balance.toString().toDouble()
    }

    private fun buy(api: KrakenApi, lastClose: Double) {
        val response: String
        val input: MutableMap<String?, String?> = HashMap()

        val usdtBalance = getBalance(api, "USDT")
        val ethToBuy = usdtBalance / lastClose * 0.997

        input["pair"] = "ETHUSDT"
        input["ordertype"] = "market"
        input["type"] = "buy"
        input["volume"] = ethToBuy.toBigDecimal().toPlainString()
        response = api.queryPrivate(KrakenApi.Method.ADD_ORDER, input)

        val responseObj = JSONObject(response)
    }

    private fun sell(api: KrakenApi) {
        val response: String
        val input: MutableMap<String?, String?> = HashMap()

        val ethBalance = getBalance(api, "XETH")
        val ethToSell = ethBalance * 0.997

        input["pair"] = "ETHUSDT"
        input["ordertype"] = "market"
        input["type"] = "sell"
        input["volume"] = ethToSell.toBigDecimal().toPlainString()
        response = api.queryPrivate(KrakenApi.Method.ADD_ORDER, input)

        val responseObj = JSONObject(response)
    }

    private fun smaRoc(values: List<Double>, rocLen: Int, smaLen: Int, targetLen: Int): List<Double> {
        return sma(roc(values, rocLen, smaLen + targetLen), smaLen, targetLen)
    }

    private fun sma(values: List<Double>, smaLen: Int, targetLen: Int): List<Double> {
        val smaList = mutableListOf<Double>()

        for (i in targetLen - 1 downTo 0) {
            var total = 0.0
            for (j in 1 until smaLen + 1) {
                total += values[values.size - j - i]
            }
            smaList.add(total / smaLen)
        }

        return smaList
    }

    private fun roc(values: List<Double>, rocLen: Int, targetLen: Int): List<Double> {
        val rocList = mutableListOf<Double>()
        val lastIndex = values.size - 1

        for (i in targetLen - 1 downTo 0) {
            rocList.add((values[lastIndex - i] - values[lastIndex - rocLen  - i]) / values[lastIndex - rocLen - i] * 100)
        }

        return rocList
    }
}