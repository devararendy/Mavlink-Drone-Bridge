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

        tcpServer =
            TcpServer(
                port = 5760,
                onClientConnected = {
                    runOnUiThread {
                        vm.setStatus(
                            "Client Connected"
                        )
                        vm.setClientCount(
                            tcpServer?.clientCount() ?: 0
                        )
                    }
                },

                onClientDisconnected = {
                    runOnUiThread {
                        vm.setStatus(
                            "Client Disconnected"
                        )
                        vm.setClientCount(
                            tcpServer?.clientCount() ?: 0
                        )
                    }
                },

                onDataReceived = {
                    runOnUiThread {
                        vm.addRx(
                            it.size.toLong()
                        )
                    }

                    usbManager.write(it)
                    vm.addTx(
                        it.size.toLong()
                    )
                }
            )

        setContent {
            val state by
            vm.uiState.collectAsState()

            MaterialTheme {

                Surface(
                    modifier =
                        Modifier.fillMaxSize()
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
                            text =
                                "DroneBridge",
                            style =
                                MaterialTheme
                                    .typography
                                    .headlineMedium
                        )

                        Card(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Column(
                                modifier =
                                    Modifier.padding(
                                        16.dp
                                    )
                            ) {

                                Text(
                                    "USB Device"
                                )

                                Text(
                                    state.usbDeviceName
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(
                                            8.dp
                                        )
                                )

                                Text(
                                    "TCP Port: ${
                                        state.tcpPort
                                    }"
                                )

                                Text(
                                    "Status: ${
                                        state.status
                                    }"
                                )

                                Text(
                                    "Clients: ${
                                        state.clientCount
                                    }"
                                )
                            }
                        }

                        Card(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Column(
                                modifier =
                                    Modifier.padding(
                                        16.dp
                                    )
                            ) {

                                Text(
                                    "RX Bytes: ${
                                        state.rxBytes
                                    }"
                                )

                                Text(
                                    "TX Bytes: ${
                                        state.txBytes
                                    }"
                                )
                            }
                        }

                        Card(
                            modifier =
                                Modifier.fillMaxWidth()
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
                            modifier =
                                Modifier.fillMaxWidth(),

                            onClick = {
                                val devices = usbManager.findDevices()
                                if (devices.isEmpty()) {
                                    vm.addLog("No USB device found")
                                    return@Button
                                }
                                
                                val driver = devices[0]
                                if (!usbManager.hasPermission(driver)) {
                                    usbReceiver?.let { 
                                        try { unregisterReceiver(it) } catch(e: Exception) {}
                                    }
                                    usbReceiver = UsbPermissionReceiver.register(this@MainActivity) { granted ->
                                        if (granted) {
                                            vm.addLog("USB Permission Granted")
                                            // Handle connection here
                                            if (
                                                usbManager.connect(
                                                    115200
                                                )
                                            ) {

                                                vm.addLog(
                                                    "Serial Connected"
                                                )

                                                vm.setStatus(
                                                    "USB Connected"
                                                )

                                            } else {
                                                vm.addLog(
                                                    "Serial Failed"
                                                )
                                            }
                                        } else {
                                            vm.addLog("USB Permission Denied")
                                        }
                                    }
                                    usbManager.requestPermission(driver)
                                } else {
                                    vm.addLog("Permission already granted")
                                    // Handle connection here
                                    if (
                                        usbManager.connect(
                                            115200
                                        )
                                    ) {

                                        vm.addLog(
                                            "Serial Connected"
                                        )

                                        vm.setStatus(
                                            "USB Connected"
                                        )

                                        usbManager.startReading {
                                            runOnUiThread {
                                                vm.addRx(
                                                    it.size.toLong()
                                                )

                                                vm.addLog(
                                                    "USB RX: " + it.decodeToString()
                                                )

                                                tcpServer?.broadcast(it)
                                            }
                                        }

                                    } else {
                                        vm.addLog(
                                            "Serial Failed"
                                        )
                                    }
                                }

                                tcpServer?.start()
                                vm.setRunning(true)
                                vm.setStatus(
                                    "Running"
                                )
                            }
                        ) {
                            Text(
                                "START"
                            )
                        }

                        OutlinedButton(
                            modifier =
                                Modifier.fillMaxWidth(),

                            onClick = {
                                tcpServer?.stop()
                                vm.setRunning(false)
                                vm.setStatus(
                                    "Stopped"
                                )
                            }
                        ) {
                            Text(
                                "STOP"
                            )
                        }
                    }
                }
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
