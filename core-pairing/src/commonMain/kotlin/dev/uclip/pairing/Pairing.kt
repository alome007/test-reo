package dev.uclip.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class QrPayload(
    val version: Int = 1,
    val deviceId: String,
    val displayName: String,
    val pubKey: String,
    val host: String,
    val port: Int,
    val token: String,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun encode(p: QrPayload): String = json.encodeToString(serializer(), p)
        fun decode(s: String): QrPayload = json.decodeFromString(serializer(), s)
    }
}

@Serializable
data class Pairing(
    val deviceId: String,
    val displayName: String,
    val peerPubKey: String,
    val sharedKey: String,
    val pairedAtEpochMs: Long,
)

sealed interface PairingState {
    data object Idle : PairingState
    data class Advertising(val payload: QrPayload) : PairingState
    data class Scanning(val progress: Float) : PairingState
    data class Confirming(val deviceId: String) : PairingState
    data class Paired(val pairing: Pairing) : PairingState
    data class Error(val message: String) : PairingState
}
