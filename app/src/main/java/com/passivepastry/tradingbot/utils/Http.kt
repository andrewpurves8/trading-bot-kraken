package com.passivepastry.tradingbot.utils

import okhttp3.*
import org.apache.commons.codec.binary.Hex
import java.net.URLEncoder
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Http {

//    private val apiKey = getString(R.string.api_key)
//    private val privateKey = getString(R.string.private_key)
    private val apiKey = ""
    private val privateKey = ""

    fun httpRequestBuilder(
        baseUrl: String,
        paramMap: Map<String, String>,
        authorise: Boolean
    ) : Request.Builder {
        val urlParams = paramMap.map {(k, v) -> "${(k.utf8())}=${v.utf8()}"}
            .joinToString("&")

        val message       = urlParams

        val encoder: Base64.Encoder = Base64.getEncoder()
        val encoded: String = encoder.encodeToString(privateKey.toByteArray())
        val secret_buffer = encoded;
//        val md = MessageDigest.getInstance("SHA-256")
//        val input = secret_buffer.toByteArray(Charsets.UTF_8)
//        val bytes = md.digest(input)
        
//        val hash          = new crypto.createHash('sha256');
//        val hmac          = new crypto.createHmac('sha512', secret_buffer);
//        val hash_digest   = hash.update(nonce + message).digest('binary');
//        val hmac_digest   = hmac.update(path + hash_digest, 'binary').digest('base64');

        val builder = Request.Builder()
        var fullUrl = "$baseUrl?$urlParams"

        if (authorise) {
            val sha256Hmac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(privateKey.toByteArray(), "HmacSHA256")
            sha256Hmac.init(secretKey)
            val signature = Hex.encodeHexString(sha256Hmac.doFinal(urlParams.toByteArray()))
            fullUrl += "&signature=$signature"

            builder.addHeader("X-MBX-APIKEY", apiKey)
        }

        builder.url(fullUrl)
        return builder
    }

    fun httpGet(
        baseUrl: String,
        paramMap: Map<String, String>,
        authorise: Boolean) : Response
    {
        val builder = httpRequestBuilder(baseUrl, paramMap, authorise)
        val request = builder
            .get()
            .build()

        val client = OkHttpClient()
        return client.newCall(request).execute()
    }

    fun httpPost(
        baseUrl: String,
        paramMap: Map<String, String>,
        authorise: Boolean) : Response
    {
        val builder = httpRequestBuilder(baseUrl, paramMap, authorise)
        val request = builder
            .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), ""))
            .build()

        val client = OkHttpClient()
        return client.newCall(request).execute()
    }

    private fun String.utf8(): String = URLEncoder.encode(this, "UTF-8")
}