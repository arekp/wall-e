package pl.walle.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

data class Settings(
    val ssid: String = "WALLE",
    val password: String = "",
    val host: String = "192.168.4.1",
    val camHost: String = "192.168.4.10",
)

data class ServoDef(val id: String, val label: String, val min: Int, val max: Int, val start: Int)

/** Zakresy zgodne z docs/electronics.md (sekcja „Kanały serw”). */
val SERVOS = listOf(
    ServoDef("neckLow", "Szyja dolna", 60, 120, 90),
    ServoDef("neckUp", "Szyja górna", 60, 120, 90),
    ServoDef("head", "Obrót głowy", 40, 140, 90),
    ServoDef("armL", "Ramię lewe", 0, 120, 0),
    ServoDef("armR", "Ramię prawe", 0, 120, 0),
    ServoDef("eyeL", "Oko lewe", 30, 150, 90),
    ServoDef("eyeR", "Oko prawe", 30, 150, 90),
)

val POSES = listOf("home" to "Domyślna", "hello" to "Cześć", "sad" to "Smutny", "curious" to "Ciekawski")
val SOUNDS = listOf("walle" to "Wall-E", "eve" to "Eve", "beep" to "Pik")

class WalleViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("walle", Context.MODE_PRIVATE)
    private val wifi = WifiConnector(app)
    val client = WalleClient()
    private val streamer = AudioStreamer { client.sendAudio(it) }

    var settings by mutableStateOf(loadSettings())
        private set
    var wifiConnected by mutableStateOf(false)
        private set
    var wantConnection by mutableStateOf(false)
        private set
    var talking by mutableStateOf(false)
        private set
    var demo by mutableStateOf(false)
        private set
    var camOn by mutableStateOf(true)
    var volume by mutableStateOf(70f)

    private var driveX = 0f
    private var driveY = 0f
    private val lastServoSent = HashMap<String, Long>()

    init {
        // jazda: 20 Hz, dopóki joystick jest wychylony (robot sam zatrzyma się po 500 ms ciszy)
        viewModelScope.launch {
            while (true) {
                delay(50)
                if (driveX != 0f || driveY != 0f) client.send(Cmd.drive(driveX, driveY))
            }
        }
        // status baterii co 3 s
        viewModelScope.launch {
            while (true) {
                delay(3000)
                if (client.state.value == LinkState.CONNECTED) client.send(Cmd.status())
            }
        }
        // automatyczne ponawianie połączenia WebSocket
        viewModelScope.launch {
            while (true) {
                delay(2000)
                if (wantConnection && wifiConnected && client.state.value == LinkState.DISCONNECTED) {
                    client.connect(settings.host)
                }
            }
        }
        // utrata połączenia: zatrzymaj lokalnie mikrofon i jazdę
        viewModelScope.launch {
            client.state.collect {
                if (it != LinkState.CONNECTED) {
                    driveX = 0f
                    driveY = 0f
                    if (talking) {
                        talking = false
                        streamer.stop()
                    }
                }
            }
        }
    }

    private fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            ssid = prefs.getString("ssid", d.ssid) ?: d.ssid,
            password = prefs.getString("password", d.password) ?: d.password,
            host = prefs.getString("host", d.host) ?: d.host,
            camHost = prefs.getString("camHost", d.camHost) ?: d.camHost,
        )
    }

    fun saveSettings(s: Settings) {
        settings = s
        prefs.edit()
            .putString("ssid", s.ssid)
            .putString("password", s.password)
            .putString("host", s.host)
            .putString("camHost", s.camHost)
            .apply()
    }

    fun connect() {
        wantConnection = true
        wifi.connect(settings.ssid, settings.password) { ok ->
            viewModelScope.launch {
                wifiConnected = ok
                if (ok) client.connect(settings.host) else client.disconnect()
            }
        }
    }

    /** Tryb demo: bez Wi-Fi i robota; komendy i mikrofon widać w dzienniku na ekranie. */
    fun connectDemo() {
        wantConnection = true
        demo = true
        client.connectDemo()
    }

    fun disconnect() {
        demo = false
        wantConnection = false
        client.send(Cmd.stop())
        client.disconnect()
        wifi.disconnect()
        wifiConnected = false
    }

    fun setDrive(x: Float, y: Float) {
        val dead = hypot(x, y) < DEADZONE
        driveX = if (dead) 0f else x
        driveY = if (dead) 0f else y
        if (dead) client.send(Cmd.stop())
    }

    fun stop() {
        driveX = 0f
        driveY = 0f
        client.send(Cmd.stop())
    }

    /** `final` = pewne wysłanie wartości końcowej (po puszczeniu suwaka). */
    fun setServo(id: String, angle: Int, final: Boolean = false) {
        val now = System.currentTimeMillis()
        val last = lastServoSent[id] ?: 0L
        if (final || now - last >= SERVO_MIN_INTERVAL_MS) {
            lastServoSent[id] = now
            client.send(Cmd.servo(id, angle))
        }
    }

    fun pose(name: String) {
        client.send(Cmd.pose(name))
    }

    fun sound(name: String) {
        client.send(Cmd.sound(name))
    }

    fun sendVolume() {
        client.send(Cmd.volume(volume.toInt()))
    }

    /** Wymaga przyznanego RECORD_AUDIO (sprawdza UI). */
    fun startTalking() {
        if (talking || client.state.value != LinkState.CONNECTED) return
        talking = true
        client.send(Cmd.audio(true))
        streamer.start(viewModelScope)
    }

    fun stopTalking() {
        if (!talking) return
        talking = false
        streamer.stop()
        client.send(Cmd.audio(false))
    }

    override fun onCleared() {
        streamer.stop()
        client.send(Cmd.stop())
        client.disconnect()
        wifi.disconnect()
    }

    companion object {
        private const val DEADZONE = 0.12f
        private const val SERVO_MIN_INTERVAL_MS = 50L
    }
}
