package com.aproxy.app.util

import java.net.Inet4Address
import java.net.NetworkInterface

data class LocalAddress(
    val interfaceName: String,
    val address: String,
)

object NetworkUtils {

    /**
     * IPv4 addresses that other devices on the hotspot can reach. Hotspot
     * interfaces (ap*, wlan*) are sorted first.
     */
    fun addresses(): List<LocalAddress> {
        val result = mutableListOf<LocalAddress>()
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .forEach { networkInterface ->
                    networkInterface.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                        .forEach { address ->
                            address.hostAddress?.let {
                                result += LocalAddress(networkInterface.name, it)
                            }
                        }
                }
        }
        return result.sortedBy { rank(it.interfaceName) }
    }

    fun primaryAddress(): String? = addresses().firstOrNull()?.address

    private fun rank(name: String): Int = when {
        name.startsWith("ap") -> 0
        name.startsWith("wlan") -> 1
        name.startsWith("eth") -> 2
        name.startsWith("rndis") || name.startsWith("usb") -> 3
        else -> 4
    }
}
