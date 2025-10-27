package de.hsfl.mobilecomputing.chatws.net

import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
}

