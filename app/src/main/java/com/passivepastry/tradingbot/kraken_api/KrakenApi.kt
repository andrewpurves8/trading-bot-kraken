package com.passivepastry.tradingbot.kraken_api

import java.io.IOException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException

/**
 * Adapted from https://github.com/nyg/kraken-api-java
 */

/**
 * A KrakenApi instance allows querying the Kraken API.
 *
 * @author nyg
 */
class KrakenApi {
    /** The API key.  */
    private var key: String? = null

    /** The API secret.  */
    private var secret: String? = null
    /**
     * Query a public method of the API with the given parameters.
     *
     * @param method the API method
     * @param parameters the method parameters
     * @return the API response
     * @throws IllegalArgumentException if the API method is null
     * @throws IOException if the request could not be created or executed
     */
    /**
     * Query a public method of the API without any parameters.
     *
     * @param method the public API method
     * @return the API response
     * @throws IOException if the request could not be created or executed
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun queryPublic(method: Method?, parameters: Map<String?, String?>? = null): String {
        val request = ApiRequest()
        request.setMethod(method)
        if (parameters != null) {
            request.setParameters(parameters)
        }
        return request.execute()
    }
    /**
     * Query a private method of the API with the given parameters.
     *
     * @param method the private API method
     * @param otp the one-time password
     * @param parameters the method parameters
     * @return the API response
     * @throws IOException if the request could not be created or executed
     * @throws NoSuchAlgorithmException if the SHA-256 or HmacSha512 algorithm
     * could not be found
     * @throws InvalidKeyException if the HMAC key is invalid
     */
    /**
     * @see .queryPrivate
     */
    /**
     * @see .queryPrivate
     */
    @JvmOverloads
    @Throws(
        IOException::class,
        NoSuchAlgorithmException::class,
        InvalidKeyException::class
    )
    fun queryPrivate(
        method: Method?,
        otp: String? = null,
        parameters: MutableMap<String?, String?>? = null
    ): String {
        var parameters = parameters
        val request = ApiRequest()
        request.setKey(key)

        // clone parameter map
        parameters = if (parameters == null) HashMap() else HashMap(parameters)

        // set OTP parameter
        if (otp != null) {
            parameters[OTP] = otp
        }

        // generate nonce
        val nonce = System.currentTimeMillis().toString() + MICRO_SECONDS
        parameters[NONCE] = nonce

        // set the parameters and retrieve the POST data
        val postData: String = request.setParameters(parameters)

        // create SHA-256 hash of the nonce and the POST data
        val sha256: ByteArray = KrakenUtils.sha256(nonce + postData)

        // set the API method and retrieve the path
        val path: ByteArray = KrakenUtils.stringToBytes(request.setMethod(method))

        // decode the API secret, it's the HMAC key
        val hmacKey: ByteArray = KrakenUtils.base64Decode(secret)

        // create the HMAC message from the path and the previous hash
        val hmacMessage: ByteArray = KrakenUtils.concatArrays(path, sha256)

        // create the HMAC-SHA512 digest, encode it and set it as the request signature
        val hmacDigest: String =
            KrakenUtils.base64Encode(KrakenUtils.hmacSha512(hmacKey, hmacMessage))
        request.setSignature(hmacDigest)
        return request.execute()
    }

    /**
     * @see .queryPrivate
     */
    @Throws(IOException::class, InvalidKeyException::class, NoSuchAlgorithmException::class)
    fun queryPrivate(method: Method?, parameters: MutableMap<String?, String?>?): String {
        return queryPrivate(method, null, parameters)
    }

    /**
     * Sets the API key.
     *
     * @param key the API key
     */
    fun setKey(key: String?) {
        this.key = key
    }

    /**
     * Sets the API secret.
     *
     * @param secret the API secret
     */
    fun setSecret(secret: String?) {
        this.secret = secret
    }

    /**
     * Represents an API method.
     *
     * @author nyg
     */
    enum class Method(methodName: String, isPublic: Boolean) {
        /* Public methods */
        TIME("Time", true),
        ASSETS("Assets", true),
        ASSET_PAIRS("AssetPairs",true),
        TICKER("Ticker", true),
        OHLC("OHLC", true),
        DEPTH("Depth", true),
        TRADES("Trades",true),
        SPREAD("Spread", true),  /* Private methods */
        BALANCE("Balance", false),
        TRADE_BALANCE("TradeBalance", false),
        OPEN_ORDERS("OpenOrders",false),
        CLOSED_ORDERS("ClosedOrders", false),
        QUERY_ORDERS("QueryOrders",false),
        TRADES_HISTORY("TradesHistory", false),
        QUERY_TRADES("QueryTrades",false),
        OPEN_POSITIONS("OpenPositions", false),
        LEDGERS("Ledgers",false),
        QUERY_LEDGERS("QueryLedgers", false),
        TRADE_VOLUME("TradeVolume",false),
        ADD_ORDER("AddOrder", false),
        CANCEL_ORDER("CancelOrder",false),
        DEPOSIT_METHODS("DepositMethods", false),
        DEPOSIT_ADDRESSES("DepositAddresses",false),
        DEPOSIT_STATUS("DepositStatus", false),
        WITHDRAW_INFO("WithdrawInfo",false),
        WITHDRAW("Withdraw", false),
        WITHDRAW_STATUS("WithdrawStatus",false),
        WITHDRAW_CANCEL("WithdrawCancel", false);

        val methodName: String
        val isPublic: Boolean

        init {
            this.methodName = methodName
            this.isPublic = isPublic
        }
    }

    companion object {
        private const val OTP = "otp"
        private const val NONCE = "nonce"
        private const val MICRO_SECONDS = "000"
    }
}
