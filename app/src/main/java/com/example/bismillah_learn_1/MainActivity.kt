package com.example.bismillah_learn_1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bismillah_learn_1.ui.MainViewModel
import com.example.bismillah_learn_1.network.TcpServer
import com.example.bismillah_learn_1.serial.UsbPermissionReceiver
import com.example.bismillah_learn_1.serial.UsbSerialManager
import androidx.activity.viewModels
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var tcpServer: TcpServer? = null
    private var usbReceiver: UsbPermissionReceiver? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val usbManager = UsbSerialManager(this)

        usbReceiver = UsbPermissionReceiver.register(this) { granted ->
            if (granted) {
                vm.addLog("USB Permission Granted")
                startSerialConnection(usbManager)
            } else {
                vm.addLog("USB Permission Denied")
            }
        }

        tcpServer =
            TcpServer(
                port = 5760,
                onClientConnected = { updateClientInfo("Client Connected") },
                onClientDisconnected = { updateClientInfo("Client Disconnected") },

                onDataReceived = {
                    usbManager.enqueueWrite(it)
                    runOnUiThread {
                        vm.netAddRx(it.size.toLong())
                        vm.usbAddTx(it.size.toLong())
                    }
                }
            )

        setContent {
            val state by
            vm.uiState.collectAsState()

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),

                        verticalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            )
                    ) {

                        Text(
                            text = "DroneBridge",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)
                            ) {
                                Text("USB Device: ${state.usbDeviceName}")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("TCP Port: ${state.tcpPort}")
                                Text("Status: ${state.status}")
                                Text("Clients: ${state.clientCount}")
                            }
                        }

                        Card( modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("USB RX Bytes: ${state.usbRxBytes}")
                                Text("USB TX Bytes: ${state.usbTxBytes}")
                                Text("Net RX Bytes: ${state.netRxBytes}")
                                Text("Net TX Bytes: ${state.netTxBytes}")
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Column(
                                modifier =
                                    Modifier
                                        .padding(16.dp)
                                        .height(200.dp)
                                        .verticalScroll(
                                            rememberScrollState()
                                        )
                            ) {
                                Text("Logs")
                                state.logs.forEach {
                                    Text(it)
                                }
                            }
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),

                            onClick = {
                                if (usbManager.isConnected()) {
                                    vm.addLog("USB already connected")
                                    return@Button
                                }

                                val devices = usbManager.findDevices()
                                if (devices.isEmpty()) {
                                    vm.addLog("No USB device found")
                                    return@Button
                                }
                                
                                val driver = devices[0]
                                if (!usbManager.hasPermission(driver)) {
                                    usbManager.requestPermission(driver)
                                } else {
                                    vm.addLog("Permission already granted")
                                    startSerialConnection(usbManager)
                                }

                                tcpServer?.start()
                                vm.setRunning(true)
                                vm.setStatus("Running")
                            }
                        ) {
                            Text("START")
                        }

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),

                            onClick = {
                                tcpServer?.stop()
                                usbManager.stopWatchdog()
                                usbManager.stopReading()
                                usbManager.disconnect()
                                vm.setRunning(false)
                                vm.setStatus("Stopped")
                                vm.setUsbDevice(false, "None")
                            }
                        ) {
                            Text("STOP")
                        }
                    }
                }
            }
        }
    }

    private fun updateClientInfo(status: String) {
        runOnUiThread {
            vm.setStatus(status)
            vm.setClientCount(tcpServer?.clientCount() ?: 0)
        }
    }

    private fun startSerialConnection(usbManager: UsbSerialManager) {
        usbManager.startWatchdog(
            baudRate = 115200,

            onConnected = {
                runOnUiThread {
                    vm.addLog("Serial Connected")
                    vm.setStatus("USB Connected")
                    vm.setUsbDevice(true, usbManager.getDeviceName())
                }
            },

            onSearching = {
                runOnUiThread {
                    vm.setStatus("USB Disconnected")
                    vm.setUsbDevice(false, "None")
                    vm.addLog("Searching USB...")
                }
            }

        ) { data ->
            tcpServer?.broadcast(data)
            runOnUiThread {
                vm.usbAddRx(data.size.toLong())
                vm.netAddTx(data.size.toLong())
                vm.addLog("USB RX: ${data.decodeToString()}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tcpServer?.stop()
        usbReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
    }
}
