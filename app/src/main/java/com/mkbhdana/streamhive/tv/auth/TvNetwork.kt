package com.mkbhdana.streamhive.tv.auth

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket

/** Networking helpers for the TV second-screen login server. */
object TvNetwork {

    /** Resolve a LAN-reachable IPv4 address for the QR login URL, or null if offline. */
    fun localIpv4(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    /** Grab a free TCP port the login server can bind to. */
    fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
