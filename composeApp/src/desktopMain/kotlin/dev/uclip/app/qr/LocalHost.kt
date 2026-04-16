package dev.uclip.app.qr

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Best-effort LAN IPv4 lookup for the QR payload. Picks the first non-loopback
 * IPv4 address on an up interface. Falls back to 127.0.0.1 if nothing suitable
 * is found (phone on same device / no network).
 */
object LocalHost {
    fun bestLanHost(): String {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }
            .getOrNull() ?: return "127.0.0.1"
        for (nif in interfaces) {
            if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
            for (addr in nif.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                    return addr.hostAddress ?: continue
                }
            }
        }
        return "127.0.0.1"
    }
}
