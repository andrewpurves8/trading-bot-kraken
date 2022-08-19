package com.passivepastry.tradingbot.kraken_api

import com.passivepastry.tradingbot.kraken_api.KrakenApi.Method
import com.passivepastry.tradingbot.kraken_api.KrakenUtils.urlEncode
import java.io.*
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Adapted from https://github.com/nyg/kraken-api-java
 */

/**
 * Represents an HTTPS request for querying the Kraken API.
 *
 * @author nyg
 */
internal class ApiRequest {
    /** The request URL.  */
    private var url: URL? = null

    /** The request message signature.  */
    private var signature: String? = null

    /** The API key.  */
    private var key: String? = null

    /** The request's POST data.  */
    private var postData: StringBuilder? = null

    /** Tells whether the API method is public or private.  */
    private var isPublic = false

    /**
     * Executes the request and returns its response.
     *
     * @return the request's response
     * @throws IOException if the underlying [HttpsURLConnection] could
     * not be set up or executed
     */
    @Throws(IOException::class)
    fun execute(): String {
        var connection: HttpsURLConnection? = null
        try {
            connection = url!!.openConnection() as HttpsURLConnection
            connection.requestMethod = REQUEST_POST
            connection.addRequestProperty(REQUEST_USER_AGENT, USER_AGENT)

            // set key & signature is method is private
            if (!isPublic) {
                check(!(key == null || signature == null || postData == null)) { ERROR_INCOMPLETE_PRIVATE_METHOD }
                connection.addRequestProperty(REQUEST_API_KEY, key)
                connection.addRequestProperty(REQUEST_API_SIGN, signature)
            }

            // write POST data to request
            if (postData != null && postData.toString().isNotEmpty()) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream).use { out ->
                    out.write(
                        postData.toString()
                    )
                }
            }
            BufferedReader(InputStreamReader(connection.inputStream)).use { `in` ->
                val response = StringBuilder()
                var line: String?
                while (`in`.readLine().also { line = it } != null) {
                    response.append(line)
                }
                return response.toString()
            }
        } finally {
            connection!!.disconnect()
        }
    }

    /**
     * Sets the API method of the request.
     *
     * @param method the API method
     * @return the path of the request taking the method into account
     * @throws MalformedURLException if the request URL could not be created
     * with the method name
     */
    @Throws(MalformedURLException::class)
    fun setMethod(method: Method?): String {
        requireNotNull(method) { ERROR_NULL_METHOD }
        isPublic = method.isPublic
        url = URL((if (isPublic) PUBLIC_URL else PRIVATE_URL) + method.methodName)
        return url!!.path
    }

    /**
     * Sets the parameters of the API method. Only supports "1-dimension" map.
     * Nulls for keys or values are converted to the string "null".
     *
     * @param parameters a map containing parameter names and values.
     * @return the parameters in POST data format, or null if the parameters are
     * null or empty
     * @throws UnsupportedEncodingException if the named encoding is not
     * supported
     * @throws IllegalArgumentException if the map is null of empty
     */
    @Throws(UnsupportedEncodingException::class)
    fun setParameters(parameters: Map<String?, String?>?): String {
        require(!(parameters == null || parameters.isEmpty())) { ERROR_NO_PARAMETERS }

        postData = java.lang.StringBuilder()

        for (entry in parameters.entries.iterator()) {
            postData!!.append(entry.key)
                .append(EQUAL_SIGN)
                .append(urlEncode(entry.value))
                .append(AMPERSAND)
        }

        return postData.toString()
    }

    /**
     * Sets the value of the API-Key request property.
     *
     * @param key the key
     * @throws IllegalArgumentException if the key is null
     */
    fun setKey(key: String?) {
        requireNotNull(key) { ERROR_NULL_KEY }
        this.key = key
    }

    /**
     * Sets the value of the API-Sign request property.
     *
     * @param signature the signature
     * @throws IllegalArgumentException if the signature is null
     */
    fun setSignature(signature: String?) {
        requireNotNull(signature) { ERROR_NULL_SIGNATURE }
        this.signature = signature
    }

    companion object {
        private const val ERROR_NULL_METHOD = "The API method can't be null."
        private const val ERROR_NULL_SIGNATURE = "The signature can't be null."
        private const val ERROR_NULL_KEY = "The key can't be null."
        private const val ERROR_NO_PARAMETERS = "The parameters can't be null or empty."
        private const val ERROR_INCOMPLETE_PRIVATE_METHOD =
            "A private method request requires the API key, the message signature and the method parameters."
        private const val USER_AGENT = "passivepastry.tradingbot"
        private const val REQUEST_API_SIGN = "API-Sign"
        private const val REQUEST_API_KEY = "API-Key"
        private const val REQUEST_USER_AGENT = "User-Agent"
        private const val REQUEST_POST = "POST"
        private const val PUBLIC_URL = "https://api.kraken.com/0/public/"
        private const val PRIVATE_URL = "https://api.kraken.com/0/private/"
        private const val AMPERSAND = "&"
        private const val EQUAL_SIGN = "="
    }
}
