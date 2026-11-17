package com.example.network

import android.net.Network
import com.example.data.ProxyTelemetry
import com.example.data.RouterProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight real HTTP + HTTPS (CONNECT) forward proxy bound to the hotspot gateway
 * that routes client TCP sockets directly through the phone's 4G SIM cellular Network.
 */
class CellularProxyServer(
    private val scope: CoroutineScope,
    private val cellularNetworkProvider: () -> Network?,
) {
    private val _telemetry = MutableStateFlow(ProxyTelemetry())
    val telemetry: StateFlow<ProxyTelemetry> = _telemetry.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val activeCount = AtomicInteger(0)

    fun start(port: Int = RouterProfile.DEFAULT_PROXY_PORT, bindIp: String = "0.0.0.0") {
        stop()
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val bindAddr = runCatching { InetAddress.getByName(bindIp) }.getOrNull()
                val socket = if (bindAddr != null && bindIp != "0.0.0.0") {
                    runCatching { ServerSocket(port, 32, bindAddr) }
                        .getOrElse { ServerSocket(port, 32) }
                } else {
                    ServerSocket(port, 32)
                }
                serverSocket = socket
                val hasCellular = cellularNetworkProvider() != null
                _telemetry.value = ProxyTelemetry(
                    isRunning = true,
                    listenAddress = bindIp,
                    port = socket.localPort,
                    activeConnections = 0,
                    totalForwardedBytes = 0L,
                    boundToCellularNetwork = hasCellular,
                )

                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            } catch (_: Throwable) {
                _telemetry.update { it.copy(isRunning = false) }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeCount.set(0)
        _telemetry.update {
            it.copy(isRunning = false, activeConnections = 0)
        }
    }

    private suspend fun handleClient(client: Socket) {
        val currentActive = activeCount.incrementAndGet()
        _telemetry.update {
            it.copy(
                activeConnections = currentActive,
                boundToCellularNetwork = cellularNetworkProvider() != null,
            )
        }

        var upstream: Socket? = null
        try {
            client.soTimeout = 30_000
            val clientIn = BufferedInputStream(client.getInputStream())
            val clientOut = BufferedOutputStream(client.getOutputStream())

            val headerBlock = readHeaderBlock(clientIn) ?: return
            val lines = headerBlock.split("\r\n")
            val requestLine = lines.firstOrNull() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 3) return

            val method = parts[0].uppercase()
            val target = parts[1]
            val version = parts[2]

            if (method == "CONNECT") {
                val hostPort = target.split(":")
                val host = hostPort[0]
                val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
                upstream = openCellularSocket(host, port)
                clientOut.write("$version 200 Connection Established\r\n\r\n".toByteArray())
                clientOut.flush()
                relayBidirectional(clientIn, clientOut, upstream.getInputStream(), upstream.getOutputStream())
            } else {
                val uri = runCatching { URI(target) }.getOrNull()
                val hostHeader = lines.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim()
                val host = uri?.host ?: hostHeader?.substringBefore(":") ?: return
                val port = when {
                    uri?.port != null && uri.port > 0 -> uri.port
                    hostHeader?.contains(":") == true -> hostHeader.substringAfter(":").toIntOrNull() ?: 80
                    else -> 80
                }
                val path = buildString {
                    append(uri?.rawPath?.ifEmpty { "/" } ?: "/")
                    if (!uri?.rawQuery.isNullOrEmpty()) {
                        append("?").append(uri?.rawQuery)
                    }
                }
                upstream = openCellularSocket(host, port)
                val upOut = BufferedOutputStream(upstream.getOutputStream())
                val rebuiltHeaders = buildString {
                    append("$method $path $version\r\n")
                    for (line in lines.drop(1)) {
                        if (line.isBlank()) continue
                        if (line.startsWith("Proxy-Connection:", ignoreCase = true)) continue
                        append(line).append("\r\n")
                    }
                    append("Connection: close\r\n\r\n")
                }
                upOut.write(rebuiltHeaders.toByteArray())
                upOut.flush()
                relayBidirectional(clientIn, clientOut, upstream.getInputStream(), upOut)
            }
        } catch (_: Throwable) {
            // Connection closed or unreachable upstream
        } finally {
            runCatching { upstream?.close() }
            runCatching { client.close() }
            val remaining = activeCount.decrementAndGet().coerceAtLeast(0)
            _telemetry.update { it.copy(activeConnections = remaining) }
        }
    }

    private fun openCellularSocket(host: String, port: Int): Socket {
        val cellularNet = cellularNetworkProvider()
        val socket = cellularNet?.socketFactory?.createSocket(host, port) ?: Socket(host, port)
        socket.soTimeout = 30_000
        return socket
    }

    private fun readHeaderBlock(input: InputStream): String? {
        val buffer = ArrayList<Byte>(512)
        var prev1 = 0
        var prev2 = 0
        var prev3 = 0
        while (buffer.size < 16384) {
            val b = input.read()
            if (b == -1) break
            buffer.add(b.toByte())
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                return String(buffer.toByteArray(), Charsets.ISO_8859_1)
            }
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }
        return null
    }

    private suspend fun relayBidirectional(
        clientIn: InputStream,
        clientOut: OutputStream,
        upIn: InputStream,
        upOut: OutputStream,
    ) {
        kotlinx.coroutines.coroutineScope {
            val upJob = launch(Dispatchers.IO) {
                copyStream(clientIn, upOut)
            }
            val downJob = launch(Dispatchers.IO) {
                copyStream(upIn, clientOut)
            }
            upJob.join()
            downJob.join()
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buf = ByteArray(8192)
        try {
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                output.write(buf, 0, read)
                output.flush()
                _telemetry.update {
                    it.copy(totalForwardedBytes = it.totalForwardedBytes + read)
                }
            }
        } catch (_: Throwable) {
            // Stream ended
        }
    }
}
