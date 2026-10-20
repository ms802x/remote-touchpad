package com.remotetouchpad.app

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RemoteConnection(
    private val onStateChanged: (connected: Boolean) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var lastIp: String? = null
    private var reconnectWanted = false
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = Runnable {
        lastIp?.let { connect(it) }
    }

    private val moveHandler = Handler(Looper.getMainLooper())

    // Scroll/zoom are coalesced into discrete "clicks" (pynput's scroll unit)
    // since that's a stepped unit anyway; move is sent immediately below.
    private var pendingScrollPixels = 0f
    private var scrollFlushScheduled = false
    private val pixelsPerClick = 22f
    private val flushScrollRunnable = Runnable {
        scrollFlushScheduled = false
        val clicks = (pendingScrollPixels / pixelsPerClick).toInt()
        if (clicks != 0) {
            pendingScrollPixels -= clicks * pixelsPerClick
            send(JSONObject().put("type", "scroll").put("clicks", clicks))
        }
    }

    fun connect(ip: String) {
        lastIp = ip
        reconnectWanted = true
        reconnectHandler.removeCallbacks(reconnectRunnable)

        val request = Request.Builder().url("ws://$ip:8765").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStateChanged(true)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStateChanged(false)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStateChanged(false)
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (reconnectWanted) {
            reconnectHandler.removeCallbacks(reconnectRunnable)
            reconnectHandler.postDelayed(reconnectRunnable, 4000L)
        }
    }

    fun disconnect() {
        reconnectWanted = false
        reconnectHandler.removeCallbacks(reconnectRunnable)
        socket?.close(1000, "Bye")
        socket = null
    }

    fun sendMove(dx: Float, dy: Float) {
        send(JSONObject().put("type", "move").put("dx", dx).put("dy", dy))
    }

    fun sendScroll(dy: Float) {
        // Natural scrolling: drag down moves content down, like a touchscreen.
        pendingScrollPixels -= dy
        if (!scrollFlushScheduled) {
            scrollFlushScheduled = true
            moveHandler.postDelayed(flushScrollRunnable, 16L)
        }
    }

    fun sendZoomClicks(clicks: Int) {
        send(JSONObject().put("type", "zoom").put("clicks", clicks))
    }

    fun sendClick(button: String) {
        send(JSONObject().put("type", "click").put("button", button))
    }

    fun sendText(text: String) {
        send(JSONObject().put("type", "key").put("text", text))
    }

    fun sendKeyCode(code: String) {
        send(JSONObject().put("type", "key").put("code", code))
    }

    private fun send(json: JSONObject) {
        socket?.send(json.toString())
    }
}
