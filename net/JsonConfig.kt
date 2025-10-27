package de.hsfl.mobilecomputing.chatws.net

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration used across the library to mirror backend payload semantics.
 * Unknown fields are ignored and nulls are omitted to remain backward compatible with the server.
 */
val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
}
