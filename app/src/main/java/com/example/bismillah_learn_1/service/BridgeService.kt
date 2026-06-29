package com.example.bismillah_learn_1.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.bismillah_learn_1.network.TcpServer
import com.example.bismillah_learn_1.network.UdpServer
import com.example.bismillah_learn_1.serial.UsbSerialManager
import com.example.bismillah_learn_1.video.VideoManager
import com.example.bismillah_learn_1.ui.VideoProtocol

class BridgeService : Service() {
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())

    private var tcpServer: TcpServer? = null
    private var udpServer: UdpServer? = null
    private var usbSerialManager: UsbSerialManager? = null
    private var videoManager: VideoManager? = null

    private var usbRxTotal: Long = 0
    private var usbTxTotal: Long = 0
    private var netRxTotal: Long = 0
    private var netTxTotal: Long = 0
    private val logs = mutableListOf<String>()
    private val statsLock = Any()

    // Callbacks for UI updates
    var onLog: ((String) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onUsbStateChanged: ((Boolean, String) -> Unit)? = null
    var onUsbRx: ((Long) -> Unit)? = null
    var onUsbTx: ((Long) -> Unit)? = null
    var onNetRx: ((Long) -> Unit)? = null
    var onNetTx: ((Long) -> Unit)? = null
    var onClientCountChanged: ((Int) -> Unit)? = null
    var onRunningChanged: ((Boolean) -> Unit)? = null
    var onStreamingChanged: ((Boolean) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): BridgeService = this@BridgeService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        usbSerialManager = UsbSerialManager(this)
        videoManager = VideoManager(this)
        videoManager?.init()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "bridge_service",
            "Bridge Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun getNotification(): Notification {
        return NotificationCompat.Builder(this, "bridge_service")
            .setContentTitle("DroneBridge Running")
            .setContentText("USB and Network bridge is active")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()
    }

    fun isRunning(): Boolean = tcpServer != null || udpServer != null || isStreaming()

    fun startBridge() {
        startForeground(1, getNotification())

        val usbManager = usbSerialManager ?: return

        udpServer = UdpServer(
            port = 14550,
            onDataReceived = { data ->
                usbManager.enqueueWrite(data)
                synchronized(statsLock) {
                    netRxTotal += data.size
                    usbTxTotal += data.size
                }
                handler.post {
                    onNetRx?.invoke(data.size.toLong())
                    onUsbTx?.invoke(data.size.toLong())
                }
            },
            onClientConnected = { addr, prt ->
                val msg = "UDP Client Connected: $addr:$prt"
                addLog(msg)
                handler.post { onLog?.invoke(msg) }
            }
        )

        tcpServer = TcpServer(
            port = 5760,
            onClientConnected = {
                handler.post {
                    onStatusChanged?.invoke("Client Connected")
                    onClientCountChanged?.invoke(tcpServer?.clientCount() ?: 0)
                }
            },
            onClientDisconnected = {
                handler.post {
                    onStatusChanged?.invoke("Client Disconnected")
                    onClientCountChanged?.invoke(tcpServer?.clientCount() ?: 0)
                }
            },
            onDataReceived = { data ->
                usbManager.enqueueWrite(data)
                synchronized(statsLock) {
                    netRxTotal += data.size
                    usbTxTotal += data.size
                }
                handler.post {
                    onNetRx?.invoke(data.size.toLong())
                    onUsbTx?.invoke(data.size.toLong())
                }
            }
        )

        tcpServer?.start()
        udpServer?.start()

        startSerialConnection()
        
        handler.post { 
            onRunningChanged?.invoke(true)
            onStatusChanged?.invoke("Running")
        }
    }

    fun startStreaming(ip: String, protocol: VideoProtocol = VideoProtocol.MPEG_TS) {
        videoManager?.startStream(protocol, ip)
        addLog("Streaming started ($protocol) to $ip")
        handler.post { onStreamingChanged?.invoke(true) }
    }

    fun stopStreaming() {
        videoManager?.stopStream()
        addLog("Streaming stopped")
        handler.post { onStreamingChanged?.invoke(false) }
    }

    fun isStreaming(): Boolean = videoManager?.isStreaming() ?: false

    private fun startSerialConnection() {
        val usbManager = usbSerialManager ?: return
        usbManager.startWatchdog(
            baudRate = 115200,
            onConnected = {
                val msg = "Serial Connected"
                addLog(msg)
                handler.post {
                    onLog?.invoke(msg)
                    onStatusChanged?.invoke("USB Connected")
                    onUsbStateChanged?.invoke(true, usbManager.getDeviceName())
                }
            },
            onSearching = {
                val msg = "Searching USB..."
                addLog(msg)
                handler.post {
                    onStatusChanged?.invoke("USB Disconnected")
                    onUsbStateChanged?.invoke(false, "None")
                    onLog?.invoke(msg)
                }
            }
        ) { data ->
            tcpServer?.broadcast(data)
            udpServer?.broadcast(data)
            synchronized(statsLock) {
                usbRxTotal += data.size
                netTxTotal += data.size
            }
            handler.post {
                onUsbRx?.invoke(data.size.toLong())
                onNetTx?.invoke(data.size.toLong())
            }
        }
    }

    fun stopBridge() {
        tcpServer?.stop()
        udpServer?.stop()
        tcpServer = null
        udpServer = null
        usbSerialManager?.stopWatchdog()
        usbSerialManager?.stopReading()
        usbSerialManager?.disconnect()
        videoManager?.stopStream()
        
        handler.post {
            onRunningChanged?.invoke(false)
            onStatusChanged?.invoke("Stopped")
            onUsbStateChanged?.invoke(false, "None")
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun addLog(msg: String) {
        synchronized(logs) {
            logs.add(msg)
            if (logs.size > 100) logs.removeAt(0)
        }
    }

    fun getLogs(): List<String> = synchronized(logs) { logs.toList() }
    fun getUsbRxTotal() = synchronized(statsLock) { usbRxTotal }
    fun getUsbTxTotal() = synchronized(statsLock) { usbTxTotal }
    fun getNetRxTotal() = synchronized(statsLock) { netRxTotal }
    fun getNetTxTotal() = synchronized(statsLock) { netTxTotal }
    fun getClientCount() = tcpServer?.clientCount() ?: 0

    fun getUsbSerialManager() = usbSerialManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        videoManager?.release()
        super.onDestroy()
    }
}
