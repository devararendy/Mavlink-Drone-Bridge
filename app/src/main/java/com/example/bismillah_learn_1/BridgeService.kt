package com.example.bismillah_learn_1

import android.app.Service
import android.content.Intent
import android.os.IBinder

class BridgeService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // For a Foreground Service, you would typically call startForeground() here
        return START_STICKY
    }
}