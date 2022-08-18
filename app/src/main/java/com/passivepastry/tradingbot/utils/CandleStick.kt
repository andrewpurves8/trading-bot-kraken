package com.passivepastry.tradingbot.utils

import org.json.JSONArray

class CandleStick(private val initArray: JSONArray) {
    val openTime = initArray.get(0).toString().toLong()
    val open = initArray.get(1).toString().toDouble()
    val high = initArray.get(2).toString().toDouble()
    val low = initArray.get(3).toString().toDouble()
    val close = initArray.get(4).toString().toDouble()
    val vwap = initArray.get(5).toString().toDouble()
    val volume = initArray.get(6).toString().toDouble()
    val count = initArray.get(7).toString().toInt()
}