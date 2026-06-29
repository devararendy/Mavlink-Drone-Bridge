package com.example.bismillah_learn_1.video

import android.content.Context
import android.util.Log
import com.pedro.library.udp.UdpCamera2
import com.pedro.common.ConnectChecker
import com.pedro.library.view.OpenGlView

class VideoManager(private val context: Context) : ConnectChecker {
    private var udpCamera2: UdpCamera2? = null
    private val TAG = "VideoManager"

    fun init(openGlView: OpenGlView? = null) {
        udpCamera2 = if (openGlView != null) {
            UdpCamera2(openGlView, this)
        } else {
            UdpCamera2(context, this)
        }
    }

    fun isStreaming(): Boolean = udpCamera2?.isStreaming ?: false

    fun startStream(ip: String, port: Int = 5600) {
        val url = "udp://$ip:$port"
        Log.d(TAG, "Starting stream to $url")
        
        // Configuration for QGC
        val width = 1280
        val height = 720
        val fps = 30
        val bitrate = 2000 * 1000 // 2Mbps
        val rotation = 0

        try {
            if (udpCamera2?.prepareVideo(width, height, fps, bitrate, rotation) == true) {
                // Audio is usually not needed for drone FPV but we can prepare it
                udpCamera2?.prepareAudio() 
                udpCamera2?.startStream(url)
                Log.d(TAG, "Stream started")
            } else {
                Log.e(TAG, "Error preparing video")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting stream", e)
        }
    }

    fun stopStream() {
        udpCamera2?.stopStream()
        udpCamera2?.stopPreview()
    }
    
    fun release() {
        stopStream()
        udpCamera2 = null
    }

    // ConnectChecker implementation
    override fun onConnectionStarted(url: String) {
        Log.d(TAG, "Connection started: $url")
    }

    override fun onConnectionSuccess() {
        Log.d(TAG, "Connection success")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "Connection failed: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {
        // Log.d(TAG, "New bitrate: $bitrate")
    }

    override fun onDisconnect() {
        Log.d(TAG, "Disconnected")
    }

    override fun onAuthError() {
        Log.e(TAG, "Auth error")
    }

    override fun onAuthSuccess() {
        Log.d(TAG, "Auth success")
    }
}
