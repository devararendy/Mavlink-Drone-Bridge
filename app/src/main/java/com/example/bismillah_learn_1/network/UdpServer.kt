package com.example.bismillah_learn_1.network

import java.net.*
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

class UdpServer(
    private val port: Int = 14550,
    private val onClientConnected:
        (InetAddress, Int) -> Unit = { _, _ -> },
    private val onDataReceived:
        (ByteArray) -> Unit = {}
) {
    private var socket:
            DatagramSocket? = null

    private var serverJob:
            Job? = null

    private val clients =
        ConcurrentHashMap<
                String,
                Pair<InetAddress, Int>
                >()

    fun stop() {
        serverJob?.cancel()
        socket?.close()
        clients.clear()
        serverJob = null
    }

    fun broadcast(
        data: ByteArray
    ) {
        clients.values.forEach {
            try {
                socket?.send(
                    DatagramPacket(
                        data,
                        data.size,
                        it.first,
                        it.second
                    )
                )

            } catch (_: Exception) {
            }
        }
    }

    fun start() {
        if (serverJob != null)
            return

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            socket = DatagramSocket(port)
            val buffer = ByteArray(4096)
            while (isActive) {
                try {
                    val packet =
                        DatagramPacket(
                            buffer,
                            buffer.size
                        )

                    socket!!.receive(
                        packet
                    )

                    val key = "${packet.address.hostAddress}:${packet.port}"
                    if (!clients.containsKey(key)) {
                        clients[key] =
                            Pair(
                                packet.address,
                                packet.port
                            )

                        onClientConnected(
                            packet.address,
                            packet.port
                        )
                    }

                    onDataReceived(
                        packet.data.copyOf(
                            packet.length
                        )
                    )

                } catch (_: Exception) {
                }
            }
        }
    }
}