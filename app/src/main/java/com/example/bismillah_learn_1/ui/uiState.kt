package com.example.bismillah_learn_1.ui

enum class VideoProtocol {
    MPEG_TS,
    H264_RAW
}

data class UiState(
    val usbConnected: Boolean = false,
    val usbDeviceName: String = "No Device",
    val tcpPort: Int = 5760,
    val running: Boolean = false,
    val usbRxBytes: Long = 0,
    val usbTxBytes: Long = 0,
    val netRxBytes: Long = 0,
    val netTxBytes: Long = 0,
    val clientCount: Int = 0,
    val status: String = "Idle",
    val videoIp: String = "192.168.1.1",
    val videoProtocol: VideoProtocol = VideoProtocol.MPEG_TS,
    val isStreaming: Boolean = false,
    val logs: List<String> = emptyList()
)