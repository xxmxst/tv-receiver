package com.example.tvreceiver

data class CastMediaRequest(
    val url: String,
    val mimeType: String? = null,
    val title: String? = null,
    val headers: Map<String, String> = emptyMap()
)
