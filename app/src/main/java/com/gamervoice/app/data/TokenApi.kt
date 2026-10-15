package com.gamervoice.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable data class TokenRequest(val roomName: String, val participantName: String)
@Serializable data class TokenResponse(val token: String = "", val url: String = "", val error: String? = null, val code: String? = null)

/** Errors shown to the user in plain language. */
sealed class JoinError(message: String) : Exception(message) {
    object RoomFull : JoinError("This room is full (max 10 people). Try another code.")
    object NoInternet : JoinError("No internet connection. Check Wi-Fi or mobile data.")
    object ServerDown : JoinError("The voice server isn't responding. Try again in a minute.")
    class BadInput(msg: String) : JoinError(msg)
}

/** Requests a LiveKit access token from our own token server. The API secret never lives in the app. */
class TokenApi {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 }
        expectSuccess = false
    }

    suspend fun fetchToken(serverUrl: String, room: String, name: String): TokenResponse {
        val res = try {
            client.post("$serverUrl/token") {
                contentType(ContentType.Application.Json)
                setBody(TokenRequest(room, name))
            }
        } catch (e: IOException) {
            throw if (e is java.net.UnknownHostException) JoinError.NoInternet else JoinError.ServerDown
        } catch (e: Exception) {
            throw JoinError.ServerDown
        }
        val body = runCatching { res.body<TokenResponse>() }.getOrNull()
        return when (res.status.value) {
            200 -> body?.takeIf { it.token.isNotEmpty() } ?: throw JoinError.ServerDown
            409 -> throw JoinError.RoomFull
            400 -> throw JoinError.BadInput(body?.error ?: "Invalid name or room code.")
            429 -> throw JoinError.BadInput("Too many attempts. Wait a moment and try again.")
            else -> throw JoinError.ServerDown
        }
    }
}
