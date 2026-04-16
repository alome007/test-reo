package dev.uclip.pairing

import dev.uclip.crypto.Crypto
import kotlin.random.Random

/**
 * Stable-ish identity for this device instance: a name to show to users, an
 * opaque id used for keying pairings + mDNS, and the long-term X25519 keypair
 * we bind into every pairing. Persistence across launches arrives with
 * phase 10 hardening — for now we mint a fresh identity on each start.
 */
data class DeviceIdentity(
    val deviceId: String,
    val displayName: String,
    val publicKey: ByteArray,
    val privateKey: ByteArray,
) {
    companion object {
        fun random(platformLabel: String, crypto: Crypto): DeviceIdentity {
            val suffix = Random.nextInt(0, 10_000).toString().padStart(4, '0')
            val kp = crypto.generateKeyPair()
            return DeviceIdentity(
                deviceId = "$platformLabel-$suffix-${Random.nextInt().toUInt().toString(16)}",
                displayName = "$platformLabel $suffix",
                publicKey = kp.publicKey,
                privateKey = kp.privateKey,
            )
        }
    }
}
