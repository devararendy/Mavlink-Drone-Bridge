package com.example.bismillah_learn_1

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bismillah_learn_1.serial.UsbPermissionReceiver
import com.example.bismillah_learn_1.service.BridgeService
import com.example.bismillah_learn_1.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var usbReceiver: UsbPermissionReceiver? = null
    private var bridgeService: BridgeService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BridgeService.LocalBinder
            bridgeService = binder.getService()
            isBound = true
            setupServiceCallbacks()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bridgeService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind to the service
        Intent(this, BridgeService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        usbReceiver = UsbPermissionReceiver.register(this) { granted ->
            if (granted) {
                vm.addLog("USB Permission Granted")
                Intent(this, BridgeService::class.java).also { intent ->
                    startForegroundService(intent)
                }
                bridgeService?.startBridge()
            } else {
                vm.addLog("USB Permission Denied")
            }
        }

        setContent {
            val state by vm.uiState.collectAsState()

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "DroneBridge",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("USB Device: ${state.usbDeviceName}")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("TCP Port: ${state.tcpPort}")
                                Text("Status: ${state.status}")
                                Text("Clients: ${state.clientCount}")
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("USB RX Bytes: ${state.usbRxBytes}")
                                Text("USB TX Bytes: ${state.usbTxBytes}")
                                Text("Net RX Bytes: ${state.netRxBytes}")
                                Text("Net TX Bytes: ${state.netTxBytes}")
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .height(200.dp)
                                    .verticalScroll(rememberScrollState())
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
                                val service = bridgeService ?: return@Button
                                val usbManager = service.getUsbSerialManager() ?: return@Button

                                if (service.isRunning()) {
                                    vm.addLog("Service already running")
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
                                    Intent(this@MainActivity, BridgeService::class.java).also { intent ->
                                        startForegroundService(intent)
                                    }
                                    service.startBridge()
                                }
                            }
                        ) {
                            Text("START")
                        }

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                bridgeService?.stopBridge()
                            }
                        ) {
                            Text("STOP")
                        }
                    }
                }
            }
        }
    }

    private fun setupServiceCallbacks() {
        bridgeService?.apply {
            onLog = { vm.addLog(it) }
            onStatusChanged = { vm.setStatus(it) }
            onUsbStateChanged = { connected, name -> vm.setUsbDevice(connected, name) }
            onUsbRx = { vm.usbAddRx(it) }
            onUsbTx = { vm.usbAddTx(it) }
            onNetRx = { vm.netAddRx(it) }
            onNetTx = { vm.netAddTx(it) }
            onClientCountChanged = { vm.setClientCount(it) }
            onRunningChanged = { vm.setRunning(it) }
            
            // If already running, sync UI
            if (isRunning()) {
                vm.setRunning(true)
                vm.setStatus("Running")
                vm.setUsbDevice(getUsbSerialManager()?.isConnected() == true, getUsbSerialManager()?.getDeviceName() ?: "None")
                vm.setUsbRx(getUsbRxTotal())
                vm.setUsbTx(getUsbTxTotal())
                vm.setNetRx(getNetRxTotal())
                vm.setNetTx(getNetTxTotal())
                vm.setClientCount(getClientCount())
                vm.setLogs(getLogs())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        usbReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
    }
}
