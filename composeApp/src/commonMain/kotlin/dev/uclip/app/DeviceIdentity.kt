package dev.uclip.app

import kotlin.random.Random

/**
 * In-memory device identity. Regenerated on each startup for now; a stable
 * deviceId + display name will land with pairing persistence in phase 4.
 */
data class DeviceIdentity(val deviceId: String, val displayName: String) {
    companion object {
        fun random(platformLabel: String): DeviceIdentity {
            val suffix = Random.nextInt(0, 10_000).toString().padStart(4, '0')
            return DeviceIdentity(
                deviceId = "$platformLabel-$suffix-${Random.nextInt().toUInt().toString(16)}",
                displayName = "$platformLabel $suffix",
            )
        }
    }
}
