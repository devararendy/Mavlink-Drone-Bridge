package com.example.bismillah_learn_1.network

import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class TcpServer(
    private val port: Int = 5760,
    private val onClientConnected: (String) -> Unit = {},
    private val onClientDisconnected: (String) -> Unit = {},
    private val onDataReceived: (ByteArray) -> Unit = {}
) {

    private var serverSocket: ServerSocket? = null

    private val clients =
        CopyOnWriteArrayList<Socket>()

    private var serverJob: Job? = null

    fun start() {
        if (serverJob != null)
            return

        serverJob = CoroutineScope(Dispatchers.IO).launch {

            serverSocket = ServerSocket(port)
            while (isActive) {
                try {
                    val client = serverSocket!!.accept()
                    clients.add(client)
                    onClientConnected(client.inetAddress.hostAddress ?: "unknown")
                    startReader(client)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun startReader(
        socket: Socket
    ) {

        CoroutineScope(
            Dispatchers.IO
        ).launch {
            val input: InputStream = socket.getInputStream()
            val buffer = ByteArray(4096)

            try {
                while (socket.isConnected) {
                    val len = input.read(buffer)
                    if (len <= 0)
                        break

                    onDataReceived(buffer.copyOf(len))
                }

            } catch (_: Exception) {
            }

            clients.remove(socket)
            onClientDisconnected(socket.inetAddress.hostAddress ?: "unknown"
            )

            socket.close()
        }
    }

    fun broadcast(
        data: ByteArray
    ) {
        clients.forEach {
            try {
                val output: OutputStream = it.getOutputStream()

                output.write(data)
                output.flush()

            } catch (_: Exception) {
            }
        }
    }

    fun stop() {
        try {
            serverJob?.cancel()
            clients.forEach {
                it.close()
            }

            clients.clear()
            serverSocket?.close()

        } catch (_: Exception) {
        }

        serverJob = null
    }

    fun clientCount(): Int {
        return clients.size
    }
}