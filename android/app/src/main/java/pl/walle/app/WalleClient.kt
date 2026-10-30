package pl.walle.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED }

data class RobotStatus(val batteryVolts: Double? = null, val cam: Boolean? = null)

/** Klient WebSocket do głównego ESP32. Protokół: docs/protocol.md. */
class WalleClient {
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(5, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _status = MutableStateFlow(RobotStatus())
    val status: StateFlow<RobotStatus> = _status.asStateFlow()

    fun connect(host: String) {
        if (_state.value != LinkState.DISCONNECTED) return
        _state.value = LinkState.CONNECTING
        val request = Request.Builder().url("ws://$host/ws").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = LinkState.CONNECTED
                send(JSONObject().put("c", "status"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    if (json.optString("t") == "status") {
                        _status.value = RobotStatus(
                            batteryVolts = if (json.has("bat")) json.getDouble("bat") else null,
                            cam = if (json.has("cam")) json.getBoolean("cam") else null,
                        )
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) closed()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket) closed()
            }
        })
    }

    private fun closed() {
        socket = null
        _state.value = LinkState.DISCONNECTED
    }

    fun disconnect() {
        socket?.close(1000, "bye")
        closed()
    }

    fun send(json: JSONObject): Boolean = socket?.send(json.toString()) ?: false

    /**
     * Wysyła ramkę dźwięku (PCM16 LE mono 16 kHz). Gdy kolejka wysyłania urośnie
     * (słabe Wi-Fi), ramka jest porzucana, żeby opóźnienie nie rosło.
     */
    fun sendAudio(pcm: ByteArray) {
        val s = socket ?: return
        if (s.queueSize() > MAX_AUDIO_QUEUE_BYTES) return
        s.send(pcm.toByteString())
    }

    companion object {
        private const val MAX_AUDIO_QUEUE_BYTES = 16 * 1024L
    }
}

object Cmd {
    fun drive(x: Float, y: Float): JSONObject =
        JSONObject().put("c", "drive").put("x", x.toDouble()).put("y", y.toDouble())

    fun stop(): JSONObject = JSONObject().put("c", "stop")
    fun servo(id: String, angle: Int): JSONObject =
        JSONObject().put("c", "servo").put("id", id).put("a", angle)

    fun pose(name: String): JSONObject = JSONObject().put("c", "pose").put("n", name)
    fun sound(name: String): JSONObject = JSONObject().put("c", "sound").put("n", name)
    fun audio(on: Boolean): JSONObject = JSONObject().put("c", "audio").put("on", on)
    fun volume(v: Int): JSONObject = JSONObject().put("c", "vol").put("v", v)
    fun status(): JSONObject = JSONObject().put("c", "status")
}
