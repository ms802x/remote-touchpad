package com.remotetouchpad.app

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * Disables Nagle's algorithm on every socket OkHttp opens for this client.
 * Nagle batches/delays small TCP writes, which is exactly our traffic
 * pattern (a stream of tiny move/key JSON messages) - without this, input
 * can lag by tens of milliseconds per burst.
 */
private class NoDelaySocketFactory : SocketFactory() {
    override fun createSocket(): Socket = Socket().apply { tcpNoDelay = true }
    override fun createSocket(host: String?, port: Int): Socket =
        Socket(host, port).apply { tcpNoDelay = true }
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        Socket(host, port, localHost, localPort).apply { tcpNoDelay = true }
    override fun createSocket(host: InetAddress?, port: Int): Socket =
        Socket(host, port).apply { tcpNoDelay = true }
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        Socket(address, port, localAddress, localPort).apply { tcpNoDelay = true }
}

class RemoteConnection(
    private val onStateChanged: (connected: Boolean) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .socketFactory(NoDelaySocketFactory())
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

    fun sendKeyCode(code: String, repeat: Int = 1) {
        send(JSONObject().put("type", "key").put("code", code).put("repeat", repeat))
    }

    private fun send(json: JSONObject) {
        socket?.send(json.toString())
    }
}
