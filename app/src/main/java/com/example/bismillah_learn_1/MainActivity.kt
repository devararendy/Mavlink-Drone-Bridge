package com.example.bismillah_learn_1

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bismillah_learn_1.serial.UsbPermissionReceiver
import com.example.bismillah_learn_1.service.BridgeService
import com.example.bismillah_learn_1.ui.MainViewModel
import com.example.bismillah_learn_1.ui.UiState
import com.example.bismillah_learn_1.ui.VideoProtocol
import com.example.bismillah_learn_1.ui.theme.Bismillahlearn1Theme

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
            bindService(intent, connection, BIND_AUTO_CREATE)
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
            Bismillahlearn1Theme {
                DroneBridgeScreen(
                    vm = vm,
                    onStartBridge = { handleStartBridge() },
                    onStopBridge = { bridgeService?.stopBridge() },
                    onStartStream = { ip, protocol -> handleStartStream(ip, protocol) },
                    onStopStream = { bridgeService?.stopStreaming() }
                )
            }
        }
    }

    private fun handleStartBridge() {
        val service = bridgeService ?: return
        val usbManager = service.getUsbSerialManager() ?: return

        val devices = usbManager.findDevices()
        if (devices.isEmpty()) {
            vm.addLog("No USB device found")
            return
        }

        val driver = devices[0]
        if (!usbManager.hasPermission(driver)) {
            usbManager.requestPermission(driver)
        } else {
            vm.addLog("Permission already granted")
            Intent(this, BridgeService::class.java).also { intent ->
                startForegroundService(intent)
            }
            service.startBridge()
        }
    }

    private fun handleStartStream(ip: String, protocol: VideoProtocol) {
        Intent(this, BridgeService::class.java).also { intent ->
            startForegroundService(intent)
        }
        bridgeService?.startStreaming(ip, protocol)
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
            if (isRunning() || isStreaming()) {
                vm.setRunning(isRunning())
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
            } catch (_: Exception) {
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroneBridgeScreen(
    vm: MainViewModel,
    onStartBridge: () -> Unit,
    onStopBridge: () -> Unit,
    onStartStream: (String, VideoProtocol) -> Unit,
    onStopStream: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            vm.addLog("Camera Permission Granted")
            // Re-trigger stream start if permission just granted? 
            // Better to let user click again or handle it here if we know they wanted to start.
            onStartStream(state.videoIp, state.videoProtocol)
        } else {
            vm.addLog("Camera Permission Denied")
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Flight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("DroneBridge", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Connection Status Card
            StatusCard(state)

            // 2. Data Traffic Card
            TrafficCard(state)

            // 3. Video Stream Card
            VideoCard(
                state = state,
                onVideoIpChange = { vm.setVideoIp(it) },
                onProtocolChange = { vm.setVideoProtocol(it) },
                onToggleStream = {
                    if (state.isStreaming) {
                        onStopStream()
                    } else {
                        val cameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
                            val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(permissions.toTypedArray())
                        } else {
                            onStartStream(state.videoIp, state.videoProtocol)
                        }
                    }
                }
            )

            // 4. Main Control
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = { if (state.running) onStopBridge() else onStartBridge() },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(if (state.running) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.running) "Stop Bridge" else "Start Bridge",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // 5. Logs Card
            LogsCard(state.logs)
        }
    }
}

@Composable
fun StatusCard(state: UiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("System Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusIndicator(state.running)
            }
            
            Spacer(Modifier.height(12.dp))
            
            StatusItem(
                icon = if (state.usbConnected) Icons.Default.Usb else Icons.Default.UsbOff,
                label = "USB Device",
                value = state.usbDeviceName,
                tint = if (state.usbConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            
            StatusItem(
                icon = Icons.Default.SettingsEthernet,
                label = "TCP Port",
                value = state.tcpPort.toString()
            )
            
            StatusItem(
                icon = Icons.Default.NetworkCheck,
                label = "UDP Port",
                value = state.udpPort.toString()
            )

            StatusItem(
                icon = Icons.Default.Groups,
                label = "Clients",
                value = state.clientCount.toString()
            )
        }
    }
}

@Composable
fun StatusIndicator(running: Boolean) {
    Surface(
        color = if (running) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (running) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (running) "RUNNING" else "STOPPED",
                style = MaterialTheme.typography.labelLarge,
                color = if (running) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusItem(icon: ImageVector, label: String, value: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TrafficCard(state: UiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TrafficIndicator(
                label = "USB RX",
                value = formatBytes(state.usbRxBytes),
                icon = Icons.Default.VerticalAlignBottom,
                color = MaterialTheme.colorScheme.primary
            )
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterVertically))
            TrafficIndicator(
                label = "USB TX",
                value = formatBytes(state.usbTxBytes),
                icon = Icons.Default.VerticalAlignTop,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun TrafficIndicator(label: String, value: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun VideoCard(
    state: UiState,
    onVideoIpChange: (String) -> Unit,
    onProtocolChange: (VideoProtocol) -> Unit,
    onToggleStream: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Video Streaming", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            
            OutlinedTextField(
                value = state.videoIp,
                onValueChange = onVideoIpChange,
                label = { Text("Destination IP (QGC/Tower)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                singleLine = true
            )
            
            Spacer(Modifier.height(12.dp))
            
            Text("Protocol", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.videoProtocol == VideoProtocol.MPEG_TS,
                    onClick = { onProtocolChange(VideoProtocol.MPEG_TS) },
                    label = { Text("MPEG-TS (UDP)") },
                    leadingIcon = if (state.videoProtocol == VideoProtocol.MPEG_TS) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = state.videoProtocol == VideoProtocol.H264_RAW,
                    onClick = { onProtocolChange(VideoProtocol.H264_RAW) },
                    label = { Text("H.264 (RTSP)") },
                    leadingIcon = if (state.videoProtocol == VideoProtocol.H264_RAW) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggleStream,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(if (state.isStreaming) Icons.Default.VideocamOff else Icons.Default.Videocam, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isStreaming) "Stop Streaming" else "Start Streaming")
            }
        }
    }
}

@Composable
fun LogsCard(logs: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .height(150.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("System Logs", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            logs.forEach { log ->
                Text(
                    text = "> $log",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = Color(0xFF00FF00)
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
        bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024f * 1024f))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }
}
