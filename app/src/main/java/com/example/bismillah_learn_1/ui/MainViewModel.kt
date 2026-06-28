package com.example.bismillah_learn_1.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())

    val uiState: StateFlow<UiState> =
        _uiState.asStateFlow()

    fun setUsbDevice(
        connected: Boolean,
        name: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                usbConnected = connected,
                usbDeviceName = name
            )
    }

    fun setRunning(
        running: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                running = running,
                status = if (running)
                    "Running"
                else
                    "Stopped"
            )
    }

    fun usbAddRx(
        bytes: Long
    ) {
        _uiState.value = _uiState.value.copy(usbRxBytes = _uiState.value.usbRxBytes + bytes)
    }

    fun setUsbRx(
        bytes: Long
    ) {
        _uiState.value = _uiState.value.copy(usbRxBytes = bytes)
    }

    fun usbAddTx(
        bytes: Long
    ) {
        _uiState.value = _uiState.value.copy(usbTxBytes = _uiState.value.usbTxBytes + bytes)
    }

    fun setUsbTx(
        bytes: Long
    ) {
        _uiState.value = _uiState.value.copy(usbTxBytes = bytes)
    }

    fun netAddRx(
        bytes: Long
    ) {
        _uiState.value = _uiState.value.copy(netRxBytes = _uiState.value.netRxBytes + bytes)
    }

    fun setNetRx(
        bytes: Long
    ) {
        _uiState.value = _uiState.value.copy(netRxBytes = bytes)
    }

    fun netAddTx(
        bytes: Long
    ) {
        _uiState.value = _uiState.value.copy(netTxBytes = _uiState.value.netTxBytes + bytes)
    }

    fun setNetTx(
        bytes: Long
    ) {
        _uiState.value = _uiState.value.copy(netTxBytes = bytes)
    }

    fun setStatus(
        status: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                status = status
            )
    }

    fun setClientCount(
        count: Int
    ) {

        _uiState.value =
            _uiState.value.copy(
                clientCount = count
            )
    }

    fun addLog(message: String) {

        val newLogs =
            (_uiState.value.logs + message)
                .takeLast(20)

        _uiState.value =
            _uiState.value.copy(
                logs = newLogs
            )
    }

    fun setLogs(logs: List<String>) {
        _uiState.value = _uiState.value.copy(logs = logs.takeLast(20))
    }
}