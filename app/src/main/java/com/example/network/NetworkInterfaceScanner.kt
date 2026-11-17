package com.example.network

import com.example.data.ConnectedClient
import com.example.data.NetworkInterfaceSummary
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInterfaceScanner {

    private val hotspotInterfacePrefixes = listOf(
        "ap",
        "softap",
        "swlan",
        "wlan1",
        "p2p-",
        "rndis",
    )

    fun scanActiveInterfaces(): List<NetworkInterfaceSummary> {
        val results = mutableListOf<NetworkInterfaceSummary>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        val isHotspot = hotspotInterfacePrefixes.any { prefix ->
                            nif.name.startsWith(prefix, ignoreCase = true)
                        }
                        results += NetworkInterfaceSummary(
                            name = nif.name,
                            ipv4Address = ip,
                            isHotspotCandidate = isHotspot,
                        )
                    }
                }
            }
        }
        return results.sortedByDescending { it.isHotspotCandidate }
    }

    fun detectSystemSoftApInterface(): NetworkInterfaceSummary? {
        return scanActiveInterfaces().firstOrNull { nif ->
            nif.name.startsWith("ap", ignoreCase = true) ||
                nif.name.startsWith("swlan", ignoreCase = true) ||
                nif.name.startsWith("softap", ignoreCase = true) ||
                nif.name.startsWith("wlan1", ignoreCase = true)
        }
    }

    fun detectP2pGatewayIp(): String? {
        return scanActiveInterfaces()
            .firstOrNull { it.name.startsWith("p2p-", ignoreCase = true) }
            ?.ipv4Address
    }

    fun readArpTableClients(): List<ConnectedClient> {
        val clients = mutableListOf<ConnectedClient>()
        runCatching {
            val file = File("/proc/net/arp")
            if (!file.exists() || !file.canRead()) return emptyList()
            file.readLines().drop(1).forEach { line ->
                val cols = line.trim().split(Regex("\\s+"))
                if (cols.size >= 6) {
                    val ip = cols[0]
                    val flags = cols[2]
                    val mac = cols[3]
                    val iface = cols[5]
                    if (flags != "0x0" && mac != "00:00:00:00:00:00") {
                        clients += ConnectedClient(
                            name = "Клиент ($iface)",
                            ipAddress = ip,
                            macAddress = mac,
                            source = iface,
                        )
                    }
                }
            }
        }
        return clients
    }
}
