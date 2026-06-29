package com.example.bismillah_learn_1.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
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
    
    private var isForeground = false

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

    private fun updateNotification(forceType: Int? = null) {
        val bridgeActive = tcpServer != null || udpServer != null
        val videoActive = videoManager?.isStreaming() == true
        
        val contentText = when {
            bridgeActive && videoActive -> "Bridge and Video Stream active"
            bridgeActive -> "USB and Network bridge active"
            videoActive -> "Video Stream active"
            else -> "Service standby"
        }

        val notification = NotificationCompat.Builder(this, "bridge_service")
            .setContentTitle("DroneBridge")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()

        var type = 0
        if (bridgeActive) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        if (videoActive) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        
        // Use forced type if provided (useful during startup)
        val finalType = forceType ?: if (type == 0) {
            // Fallback if nothing active yet but we need to stay in foreground
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            type
        }

        if (!isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, finalType)
            } else {
                startForeground(1, notification)
            }
            isForeground = true
        } else {
            val manager = getSystemService(NotificationManager::class.java)
            // On API 34+, if types are declared, we must specify them or they will be removed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, finalType)
            } else {
                manager.notify(1, notification)
            }
        }
    }

    fun isRunning(): Boolean = tcpServer != null || udpServer != null

    fun startBridge() {
        // Force connectedDevice type during bridge startup
        updateNotification(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)

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
        
        updateNotification()
    }

    fun startStreaming(ip: String, protocol: VideoProtocol = VideoProtocol.MPEG_TS) {
        // Force camera type during video startup
        updateNotification(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        
        videoManager?.startStream(protocol, ip)
        addLog("Streaming started ($protocol) to $ip")
        handler.post { onStreamingChanged?.invoke(true) }
        
        updateNotification()
    }

    fun stopStreaming() {
        videoManager?.stopStream()
        addLog("Streaming stopped")
        handler.post { onStreamingChanged?.invoke(false) }
        checkSelfStop()
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
        
        handler.post {
            onRunningChanged?.invoke(false)
            onStatusChanged?.invoke("Stopped")
            onUsbStateChanged?.invoke(false, "None")
        }
        
        checkSelfStop()
    }

    private fun checkSelfStop() {
        val bridgeActive = tcpServer != null || udpServer != null
        val videoActive = videoManager?.isStreaming() == true
        
        if (!bridgeActive && !videoActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            stopSelf()
        } else {
            updateNotification()
        }
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
        // Ensure we call startForeground within 5 seconds even if nothing else happened
        if (!isForeground) {
            updateNotification()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        videoManager?.release()
        super.onDestroy()
    }
}
