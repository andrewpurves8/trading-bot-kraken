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

class TraderBinance(private val context: Context) {
    private val http = Http()

    private val sharedPreferences = context.getSharedPreferences("TradingBotPrefs", Context.MODE_PRIVATE)

    fun checkForTrades() {
        val currentDateTime = LocalDateTime.now()
        val currentDateTimeFormatted = currentDateTime.format(DateTimeFormatter.ISO_TIME)
        val ema = getEma()
        val latestSar = getPSAR()

        val editor = sharedPreferences.edit()
        editor.putString("close", "${getClose()}")
        editor.putString("time", currentDateTimeFormatted)
        editor.putString("ema", "$ema")
        editor.putString("psar", "$latestSar")

        if (sarLatestChange == 1) {
            // Sell
            editor.putString("action", "Sell")

            if (getClose() < ema) {
                editor.putString("decision", "Sell")
                sell()
            }
            else
                editor.putString("decision", "Do nothing")
        } else if (sarLatestChange == 2) {
            // Buy
            editor.putString("action", "Buy")

            if (getClose() > ema) {
                editor.putString("decision", "Buy")
                buy()
            }
            else
                editor.putString("decision", "Do nothing")
        } else {
            // No change
            editor.putString("action", "N/A")
            editor.putString("decision", "Do nothing")
        }

        editor.commit()

        for (i in 0 until sars.size) {
            Log.d(TAG, "$i ${sars[i]}")
        }
        Log.d(TAG, "$latestSar")
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

    private fun sell() {
        val accountInfo = getAccountInfo()
        val accountInfoJson = JSONObject(accountInfo.body()!!.string())
        val balances = accountInfoJson["balances"] as JSONArray

        var ethBalance = ""
        for (i in 0 until balances.length()) {
            val balance = balances[i] as JSONObject
            if (balance["asset"].toString() == "ETH") {
                ethBalance = balance["free"].toString()
            }
        }

        if (ethBalance.isBlank()) return

        if (ethBalance.toDouble() < 0.01) return

        ethBalance = ethBalance.toDouble().floorDecimals(4).toString()

        val paramMap = mapOf(
            "symbol" to "ETHUSDT",
            "side" to "SELL",
            "type" to "MARKET",
            "quantity" to ethBalance,
            "timestamp" to "${System.currentTimeMillis()}")

        val response = http.httpPost(
//            "https://api.binance.com/api/v3/order/test",
            "https://api.binance.com/api/v3/order",
            paramMap,
            true)

        Log.d(TAG, response.code().toString())
        Log.d(TAG, response.body()!!.string())
    }

    private fun buy() {
        val accountInfo = getAccountInfo()
        val accountInfoJson = JSONObject(accountInfo.body()!!.string())
        val balances = accountInfoJson["balances"] as JSONArray

        var usdtBalance = ""
        for (i in 0 until balances.length()) {
            val balance = balances[i] as JSONObject
            if (balance["asset"].toString() == "USDT") {
                usdtBalance = balance["free"].toString()
            }
        }

        if (usdtBalance.isBlank()) return

        if (usdtBalance.toDouble() < 11.0) return

        usdtBalance = usdtBalance.toDouble().floorDecimals(2).toString()

        val paramMap = mapOf(
            "symbol" to "ETHUSDT",
            "side" to "BUY",
            "type" to "MARKET",
            "quoteOrderQty" to usdtBalance,
            "timestamp" to "${System.currentTimeMillis()}")

        val response = http.httpPost(
//            "https://api.binance.com/api/v3/order/test",
            "https://api.binance.com/api/v3/order",
            paramMap,
            true)

        Log.d(TAG, response.code().toString())
        Log.d(TAG, response.body()!!.string())
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

    private fun getPSAR(): Double {
        val candleSticks = mutableListOf<CandleStick>()

        val paramMap = mapOf(
            "symbol" to "ETHUSDT",
            "interval" to "1h",
            "limit" to "1000")

        val response = http.httpGet(
            "https://api.binance.com/api/v3/klines",
            paramMap,
            false)

        val klinesString = response.body()!!.string()

        val klinesJson = JSONArray(klinesString)
        // Ignore newest candlestick as it hasn't been finalised yet
        for (i in 0 until klinesJson.length() - 1) {
            val jsonKline = klinesJson.get(i) as JSONArray
            val candlestick = CandleStick(jsonKline)
            candleSticks.add(candlestick)
        }

        var latestSar = 0.0
        for (i in 0 until candleSticks.size) {
            latestSar = calcPSAR(i, candleSticks)
        }

        return latestSar
    }

    private fun getEma() : Double {
        val candleSticks = mutableListOf<CandleStick>()

        val paramMap = mapOf(
            "symbol" to "ETHUSDT",
            "interval" to "1h",
            "limit" to "501")

        val response = http.httpGet(
            "https://api.binance.com/api/v3/klines",
            paramMap,
            false)

        val klinesString = response.body()!!.string()

        val klinesJson = JSONArray(klinesString)
        // Ignore newest candlestick as it hasn't been finalised yet
        for (i in 0 until klinesJson.length() - 1) {
            val jsonKline = klinesJson.get(i) as JSONArray
            val candlestick = CandleStick(jsonKline)
            candleSticks.add(candlestick)
        }

        val ema = calcEma50(candleSticks)

        return ema
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

    private val sars = mutableListOf<Double>()
    // 0 is no change
    // 1 is uptrend -> downtrend (sell)
    // 2 is downtrend -> uptrend (buy)
    private var sarLatestChange = 0

    private var accelerationFactor: Double = 0.02
    private var maxAcceleration: Double = 0.2
    private var accelerationIncrement: Double = 0.02
    private var accelarationStart: Double = 0.02


    private var currentTrend // true if uptrend, false otherwise
            = false
    private var startTrendIndex = 0 // index of start tick of the current trend

    private var currentExtremePoint // the extreme point of the current calculation
            : Double = 0.0
    private var minMaxExtremePoint // depending on trend the maximum or minimum extreme point value of trend
            : Double = 0.0

    private fun calcPSAR(index: Int, candleSticks: List<CandleStick>): Double {
        var sar: Double = Double.NaN
        if (index == 0) {
            sars.add(sar)
            return sar // no trend detection possible for the first value
        } else if (index == 1) { // start trend detection
            currentTrend = candleSticks[0].close < candleSticks[index].close
            if (!currentTrend) { // down trend
                sar = candleSticks[index].high // put sar on max price of candlestick
                currentExtremePoint = sar
                minMaxExtremePoint = currentExtremePoint
                Log.d(TAG, "$index downtrend $sar (close ${candleSticks[index].close})")
            } else { // up trend
                sar = candleSticks[index].low // put sar on min price of candlestick
                currentExtremePoint = sar
                minMaxExtremePoint = currentExtremePoint
                Log.d(TAG, "$index uptrend $sar (close ${candleSticks[index].close})")
            }
            sars.add(sar)
            return sar
        }

        val priorSar: Double = sars[index - 1]
        if (currentTrend) { // if up trend
            val priorSarNotInPreviousBody = min(candleSticks[index - 2].low, priorSar)

            currentTrend = candleSticks[index].low > priorSarNotInPreviousBody
            if (!currentTrend) { // check if sar touches the min price
                currentExtremePoint = candleSticks[index].low
                accelerationFactor = accelarationStart
                sar =
                    minMaxExtremePoint + accelerationFactor * (currentExtremePoint - minMaxExtremePoint)
//                Log.d(TAG, "$minMaxExtremePoint $accelerationFactor $currentExtremePoint")
                startTrendIndex = index
                minMaxExtremePoint = currentExtremePoint
                Log.d(TAG, "$index uptrend -> downtrend $sar (close ${candleSticks[index].close})")
                sarLatestChange = 1
            } else { // up trend is going on
                currentExtremePoint = getMax(candleSticks, startTrendIndex, index)
                if (currentExtremePoint > minMaxExtremePoint) {
                    incrementAcceleration()
                    minMaxExtremePoint = currentExtremePoint
//                    Log.d(TAG, "$priorSar $accelerationFactor $currentExtremePoint")
                }
                sar =
                    priorSarNotInPreviousBody + accelerationFactor * (currentExtremePoint - priorSarNotInPreviousBody)
                Log.d(TAG, "$index uptrend continued $sar (close ${candleSticks[index].close})")
                sarLatestChange = 0
            }
        } else { // downtrend
            val priorSarNotInPreviousBody = max(candleSticks[index - 2].high, priorSar)

            currentTrend = candleSticks[index].high >= priorSarNotInPreviousBody
            if (currentTrend) { // check if switch to up trend
                currentExtremePoint = candleSticks[index].high
                accelerationFactor = accelarationStart
                sar =
                    minMaxExtremePoint - accelerationFactor * (minMaxExtremePoint - currentExtremePoint)
                startTrendIndex = index
                minMaxExtremePoint = currentExtremePoint
                Log.d(TAG, "$index downtrend -> uptrend $sar (close ${candleSticks[index].close})")
                sarLatestChange = 2
            } else { // down trend is going on
                currentExtremePoint = getMin(candleSticks, startTrendIndex, index)
                if (currentExtremePoint < minMaxExtremePoint) {
                    incrementAcceleration()
                    minMaxExtremePoint = currentExtremePoint
                }
                sar =
                    priorSarNotInPreviousBody + accelerationFactor * (currentExtremePoint - priorSarNotInPreviousBody)
//                Log.d(TAG, "$currentExtremePoint $startTrendIndex $index $minMaxExtremePoint $priorSar $sar $accelerationFactor")
                Log.d(TAG, "$index downtrend continued $sar (close ${candleSticks[index].close})")
                sarLatestChange = 0
            }
        }
        Log.d(TAG, ".")
        sars.add(sar)
        return sar
    }

    /**
     * Increments the acceleration factor.
     */
    private fun incrementAcceleration() {
        accelerationFactor =
            if (accelerationFactor >= maxAcceleration)
                maxAcceleration
            else
                accelerationFactor + accelerationIncrement
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