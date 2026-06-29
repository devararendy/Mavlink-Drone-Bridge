package com.example.bismillah_learn_1

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bismillah_learn_1.serial.UsbPermissionReceiver
import com.example.bismillah_learn_1.service.BridgeService
import com.example.bismillah_learn_1.ui.MainViewModel
import com.example.bismillah_learn_1.ui.VideoProtocol

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
            
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
                
                if (cameraGranted) {
                    vm.addLog("Camera Permission Granted")
                    // If the user just granted permission, they probably wanted to start the stream
                    // But we won't auto-start here to avoid confusion, they can click again.
                } else {
                    vm.addLog("Camera Permission Denied")
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
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
                                Text("Video Stream", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = state.videoIp,
                                    onValueChange = { vm.setVideoIp(it) },
                                    label = { Text("QGC IP Address") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text("Protocol:", style = MaterialTheme.typography.labelLarge)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = state.videoProtocol == VideoProtocol.MPEG_TS,
                                        onClick = { vm.setVideoProtocol(VideoProtocol.MPEG_TS) }
                                    )
                                    Text("MPEG-TS (UDP)")
                                    Spacer(modifier = Modifier.width(16.dp))
                                    RadioButton(
                                        selected = state.videoProtocol == VideoProtocol.H264_RAW,
                                        onClick = { vm.setVideoProtocol(VideoProtocol.H264_RAW) }
                                    )
                                    Text("H.264 (RTSP)")
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val cameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            android.Manifest.permission.CAMERA
                                        )

                                        if (cameraPermission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            val permissions = mutableListOf(
                                                android.Manifest.permission.CAMERA,
                                                android.Manifest.permission.RECORD_AUDIO
                                            )
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            permissionLauncher.launch(permissions.toTypedArray())
                                            return@Button
                                        }

                                        if (state.isStreaming) {
                                            bridgeService?.stopStreaming()
                                        } else {
                                            Intent(this@MainActivity, BridgeService::class.java).also { intent ->
                                                startForegroundService(intent)
                                            }
                                            bridgeService?.startStreaming(state.videoIp, state.videoProtocol)
                                        }
                                    }
                                ) {
                                    Text(if (state.isStreaming) "STOP VIDEO" else "START VIDEO")
                                }
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
            onStreamingChanged = { vm.setStreaming(it) }
            
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
                vm.setStreaming(isStreaming())
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
