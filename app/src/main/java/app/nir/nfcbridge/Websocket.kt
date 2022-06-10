package app.nir.nfcbridge

import  android.util.Log
import okhttp3.*
import okio.ByteString
import java.lang.Exception
import  java.util.concurrent.TimeUnit

/*
Adapted from https://camposha.info/android-examples/android-websocket/#gsc.tab=0
 */

interface MessageListener {
    fun onConnectSuccess() // successfully connected
    fun onConnectFailed() // connection failed
    fun onClose() // close
    fun onMessage(text: String?)
}

object Websocket {
    private val TAG = Websocket::class.java.simpleName
    private const val MAX_RECONNECTS = 5  // Maximum number of reconnections
    private const val RECONNECT_INTERVAL_MILLIS: Long = 5000
    private const val WEBSOCKET_TIMEOUT_MILLIS: Long = 5000
    private lateinit var client: OkHttpClient
    private lateinit var request: Request
    private lateinit var messageListener: MessageListener
    private lateinit var mWebSocket: WebSocket
    private var isConnected = false
    private var numFailedConsecutiveConnections = 0

    fun init(url: String, _messageListener: MessageListener) {
        client = OkHttpClient.Builder()
            .writeTimeout(WEBSOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(WEBSOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .connectTimeout(WEBSOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        request = Request.Builder().url(url).build()
        messageListener = _messageListener
    }


    fun connect() {
        if (isConnected()) {
            Log.i(TAG, "web socket connected")
            return
        }
        client.newWebSocket(request, createListener())
    }


    fun reconnect() {
        if (numFailedConsecutiveConnections <= MAX_RECONNECTS) {
            try {
                Thread.sleep(RECONNECT_INTERVAL_MILLIS.toLong())
                connect()
                numFailedConsecutiveConnections++
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        } else {
            Log.i(
                TAG,
                "failed to connect over $MAX_RECONNECTS times, check url or network"
            )
        }
    }

    fun isConnected(): Boolean {
        return isConnected
    }

    /**
     * send a text type message
     * @return whether the message was enqueued successfully
     */
    fun sendMessage(text: String): Boolean {
        return if (!isConnected()) false else mWebSocket.send(text)
    }

    /**
     * send a binary type message
     * @return whether the message was enqueued successfully
     */
    fun sendMessage(byteString: ByteString): Boolean {
        return if (!isConnected()) false else mWebSocket.send(byteString)
    }

    fun close() {
        if (isConnected()) {
            mWebSocket.cancel()
            mWebSocket.close(1001, "The client actively closed the connection")
        }
    }

    private fun createListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response
            ) {
                super.onOpen(webSocket, response)
                Log.d(TAG, "open:$response")
                mWebSocket = webSocket
                isConnected = response.code() == 101
                if (!isConnected) {
                    reconnect()
                } else {
                    Log.i(TAG, "connected successfully")
                    messageListener.onConnectSuccess()
                    numFailedConsecutiveConnections = 0
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                messageListener.onMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                //previously did this - very bad since it's ambiguous to receiver
                //messageListener.onMessage(bytes.base64())
                //just throwing an exception for now
                throw Exception("received unsupported data type")
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {
                super.onClosing(webSocket, code, reason)
                isConnected = false
                // TODO: report reason to client
                messageListener.onClose()
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {
                super.onClosed(webSocket, code, reason)
                isConnected = false
                // TODO: report reason to client
                messageListener.onClose()
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                super.onFailure(webSocket, t, response)
                if (response != null) {
                    Log.i(
                        TAG,
                        "connect failed message：" + response.message()
                    )
                }
                Log.i(
                    TAG,
                    "connect failed throwable：" + t.message
                )
                isConnected = false
                // TODO: report failure response (if it exists) to client
                messageListener.onConnectFailed()
                reconnect()
            }
        }
    }
}
