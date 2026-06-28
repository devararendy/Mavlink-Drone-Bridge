package com.example.bismillah_learn_1.serial

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
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
                        break
                    }
                }
            }
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
        txJob?.cancel()
        txJob = null
        txQueue.clear()

        try {
            currentPort?.close()
        } catch (_: Exception) {
        }

        currentPort = null
    }

    fun connect(
        baudRate: Int = 115200
    ): Boolean {
        val drivers =
            findDevices()

        if (drivers.isEmpty())
            return false

        val driver =
            drivers[0]

        if (!hasPermission(driver))
            return false

        val connection =
            usbManager.openDevice(
                driver.device
            ) ?: return false

        val port =
            driver.ports[0]

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

        val intent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(
                    UsbPermissionReceiver
                        .ACTION_USB_PERMISSION
                ),
                PendingIntent.FLAG_IMMUTABLE
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