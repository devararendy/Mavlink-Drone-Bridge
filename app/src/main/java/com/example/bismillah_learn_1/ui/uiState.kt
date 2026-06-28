package com.example.bismillah_learn_1.ui

data class UiState(
    val usbConnected: Boolean = false,
    val usbDeviceName: String = "No Device",
    val tcpPort: Int = 5760,
    val running: Boolean = false,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val clientCount: Int = 0,
    val status: String = "Idle",

    val logs: List<String> = emptyList()
)