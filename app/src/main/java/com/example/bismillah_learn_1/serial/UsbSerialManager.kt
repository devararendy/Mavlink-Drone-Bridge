package com.example.bismillah_learn_1.serial

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import android.widget.Toast
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue

class UsbSerialManager(
    private val context: Context
) {

    private val usbManager =
        context.getSystemService(
            Context.USB_SERVICE
        ) as UsbManager
    private var currentPort:
            UsbSerialPort? = null
    private var readJob: Job? = null
    private val txQueue = LinkedBlockingQueue<ByteArray>()
    private var txJob: Job? = null
    private var watchdogJob: Job? = null
    private var reconnectCallback: ((ByteArray) -> Unit)? = null
    private var lastPermissionRequestTime = 0L

    fun startWatchdog(
        baudRate: Int = 115200,
        onConnected: () -> Unit = {},
        onSearching: () -> Unit = {},
        onData: (ByteArray) -> Unit
    ) {
        reconnectCallback = onData
        if (watchdogJob != null)
            return

        watchdogJob = CoroutineScope(Dispatchers.IO).launch {
            var wasSearching = false
            while (isActive) {
                try {
                    if (!isConnected()) {
                        if (!wasSearching) {
                            onSearching()
                            wasSearching = true
                        }
                        if (connect(baudRate)) {
                            wasSearching = false
                            startReading(onData)
                            onConnected()
                        }
                    } else {
                        wasSearching = false
                    }
                } catch (_: Exception) {
                }
                delay(1000)
            }
        }
    }

    fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    fun enqueueWrite(
        data: ByteArray
    ) {
         txQueue.offer(data)
    }

    fun startWriter() {
        if (txJob != null)
            return

        txJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val data = txQueue.take()

                    currentPort?.write(
                        data,
                        1000
                    )

                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    fun stopReading() {
        readJob?.cancel()
        readJob = null
    }

    fun startReading(
        onData: (ByteArray) -> Unit
    ) {
        if (readJob != null)
            return

        readJob = CoroutineScope(Dispatchers.IO
            ).launch {
                val buffer = ByteArray(4096)
                while (isActive &&
                    currentPort != null
                ) {
                    try {
                        val len =
                            currentPort!!.read(
                                buffer,
                                100
                            )

                        if (len > 0) {
                            onData(buffer.copyOf(len))
                        }

                    } catch (_: Exception) {
                        disconnect()
                        break
                    }
                }
            }
    }

    fun getDeviceName(): String {
        val device = currentPort?.driver?.device
        return device?.let {
            "${it.manufacturerName ?: ""} ${it.productName ?: ""}".trim().ifEmpty { it.deviceName }
        } ?: "No Device"
    }

    fun isConnected(): Boolean {
        return currentPort != null
    }

    fun read(
        buffer: ByteArray
    ): Int {
        return currentPort?.read(
            buffer,
            100
        ) ?: -1
    }

    fun write(
        data: ByteArray
    ) {
        currentPort?.write(
            data,
            1000
        )
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null

        txJob?.cancel()
        txJob = null
        txQueue.clear()

        try {
            currentPort?.close()
        } catch (_: Exception) {
        }

        currentPort = null
    }

    suspend fun connect(
        baudRate: Int = 115200
    ): Boolean {
        val drivers = findDevices()
        if (drivers.isEmpty()) {
            return false
        }

        val driver = drivers[0]
        if (!hasPermission(driver)) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPermissionRequestTime > 5000) {
                lastPermissionRequestTime = currentTime
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Requesting USB permission...", Toast.LENGTH_SHORT).show()
                }
                requestPermission(driver)
            }
            return false
        }

        val connection = usbManager.openDevice(driver.device) ?: return false
        val port = driver.ports[0]

        port.open(connection)

        port.setParameters(
            baudRate,
            8,
            UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE
        )

        port.dtr = true
        port.rts = true

        currentPort = port

        startWriter()
        return true
    }

    fun findDevices():
            List<UsbSerialDriver> {

        return UsbSerialProber
            .getDefaultProber()
            .findAllDrivers(
                usbManager
            )
    }

    fun requestPermission(
        driver: UsbSerialDriver
    ) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

        val intent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(
                    UsbPermissionReceiver
                        .ACTION_USB_PERMISSION
                ),
                flags
            )

        usbManager.requestPermission(
            driver.device,
            intent
        )
    }

    fun hasPermission(
        driver: UsbSerialDriver
    ): Boolean {
        return usbManager.hasPermission(
            driver.device
        )
    }
}