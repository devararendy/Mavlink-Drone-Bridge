package com.example.bismillah_learn_1.video

import android.content.Context
import android.util.Log
import com.example.bismillah_learn_1.ui.VideoProtocol
import com.pedro.library.udp.UdpCamera2
import com.pedro.library.rtsp.RtspCamera2
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.common.ConnectChecker
import com.pedro.library.view.OpenGlView

class VideoManager(private val context: Context) : ConnectChecker {
    private var udpCamera2: UdpCamera2? = null
    private var rtspCamera2: RtspCamera2? = null
    private var rtspServerCamera2: RtspServerCamera2? = null
    private var currentProtocol = VideoProtocol.MPEG_TS
    
    private val tag = "VideoManager"

    fun init(openGlView: OpenGlView? = null) {
        // Initialization happens dynamically in ensureEncoder
    }

    private fun ensureEncoder(protocol: VideoProtocol, openGlView: OpenGlView? = null) {
        if (currentProtocol != protocol) {
            release()
            currentProtocol = protocol
        }

        when (protocol) {
            VideoProtocol.MPEG_TS -> {
                if (udpCamera2 == null) {
                    udpCamera2 = if (openGlView != null) UdpCamera2(openGlView, this) else UdpCamera2(context, this)
                }
            }
            VideoProtocol.H264_RAW -> {
                if (rtspServerCamera2 == null) {
                    // RtspServerCamera2 requires a ConnectChecker and a port
                    rtspServerCamera2 = if (openGlView != null) {
                        RtspServerCamera2(openGlView, this, 8554)
                    } else {
                        RtspServerCamera2(context, this, 8554)
                    }
                }
            }
        }
    }

    fun isStreaming(): Boolean {
        return udpCamera2?.isStreaming == true || 
               rtspCamera2?.isStreaming == true || 
               rtspServerCamera2?.isStreaming == true
    }

    fun startStream(protocol: VideoProtocol, ip: String, port: Int = 5600) {
        ensureEncoder(protocol)
        
        val width = 1280
        val height = 720
        val fps = 30
        val bitrate = 2000 * 1000
        val rotation = 0

        try {
            when (protocol) {
                VideoProtocol.MPEG_TS -> {
                    val url = "udp://$ip:$port"
                    if (udpCamera2?.prepareVideo(width, height, fps, bitrate, rotation) == true) {
//                        udpCamera2?.prepareAudio()
                        udpCamera2?.startStream(url)
                    }
                }
                VideoProtocol.H264_RAW -> {
                    if (rtspServerCamera2?.prepareVideo(width, height, fps, bitrate, rotation) == true) {
//                        rtspServerCamera2?.prepareAudio()
                        rtspServerCamera2?.startStream() // Server starts and waits for connections
                        Log.d(tag, "RTSP Server started on port 8554")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting stream", e)
        }
    }

    fun stopStream() {
        udpCamera2?.stopStream()
        udpCamera2?.stopPreview()
        rtspCamera2?.stopStream()
        rtspCamera2?.stopPreview()
        rtspServerCamera2?.stopStream()
        rtspServerCamera2?.stopPreview()
    }
    
    fun release() {
        stopStream()
        udpCamera2 = null
        rtspCamera2 = null
        rtspServerCamera2 = null
    }

    // ConnectChecker implementation
    override fun onConnectionStarted(url: String) { Log.d(tag, "Started: $url") }
    override fun onConnectionSuccess() { Log.d(tag, "Success") }
    override fun onConnectionFailed(reason: String) { Log.e(tag, "Failed: $reason") }
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() { Log.d(tag, "Disconnected") }
    override fun onAuthError() { Log.e(tag, "Auth error") }
    override fun onAuthSuccess() { Log.d(tag, "Auth success") }
}
