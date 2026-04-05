package com.flowvoice.android.api

import com.google.gson.annotations.SerializedName

data class TranscribeResponse(
    val transcript: String,
    val language: String,
    @SerializedName("duration_secs") val durationSecs: Double
)
