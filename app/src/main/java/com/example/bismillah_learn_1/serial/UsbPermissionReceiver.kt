package com.example.bismillah_learn_1.serial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build

class UsbPermissionReceiver(
    private val callback: (Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != ACTION_USB_PERMISSION)
            return

        synchronized(this) {
            val granted =
                intent.getBooleanExtra(
                    UsbManager.EXTRA_PERMISSION_GRANTED,
                    false
                )
            callback(granted)
        }
    }

    companion object {
        const val ACTION_USB_PERMISSION =
            "com.example.bismillah_learn_1.USB_PERMISSION"

        fun register(context: Context, callback: (Boolean) -> Unit): UsbPermissionReceiver {
            val receiver = UsbPermissionReceiver(callback)
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            return receiver
        }
    }
}
