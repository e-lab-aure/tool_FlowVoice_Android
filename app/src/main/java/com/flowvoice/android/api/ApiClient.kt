package com.flowvoice.android.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()

    /**
     * Sends WAV audio bytes to the transcription server and returns the result.
     * Must be called from a background thread (e.g. Dispatchers.IO).
     *
     * @throws IOException on network error or non-2xx HTTP response
     */
    fun transcribe(
        host: String,
        port: Int,
        audioBytes: ByteArray,
        language: String,
        preprocess: Boolean
    ): TranscribeResponse {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                "recording.wav",
                audioBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("language", language)
            .addFormDataPart("preprocess", preprocess.toString())
            .build()

        val request = Request.Builder()
            .url("http://$host:$port/api/transcribe-live")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: throw IOException("Empty response body")
            if (!response.isSuccessful) {
                val errorMsg = runCatching {
                    gson.fromJson(bodyStr, Map::class.java)["error"] as? String
                }.getOrNull() ?: "HTTP ${response.code}"
                throw IOException(errorMsg)
            }
            return gson.fromJson(bodyStr, TranscribeResponse::class.java)
        }
    }

    /**
     * Fetches the supported language map from the server.
     * Used to test the connection and populate the language spinner.
     * Must be called from a background thread.
     *
     * @throws IOException on network error or non-2xx HTTP response
     */
    fun getLanguages(host: String, port: Int): Map<String, String> {
        val request = Request.Builder()
            .url("http://$host:$port/languages")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val bodyStr = response.body?.string() ?: throw IOException("Empty response body")
            val type = object : TypeToken<Map<String, String>>() {}.type
            return gson.fromJson(bodyStr, type)
        }
    }
}
