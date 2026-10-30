package pl.walle.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Color as AColor
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import pl.walle.app.LinkState
import pl.walle.app.POSES
import pl.walle.app.SERVOS
import pl.walle.app.SOUNDS
import pl.walle.app.Settings
import pl.walle.app.WalleViewModel

@Composable
fun WalleApp(vm: WalleViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val link by vm.client.state.collectAsState()
    val status by vm.client.status.collectAsState()

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Header(
                link = link,
                battery = status.batteryVolts,
                wantConnection = vm.wantConnection,
                demo = vm.demo,
                onConnect = vm::connect,
                onDemo = vm::connectDemo,
                onDisconnect = vm::disconnect,
                onSettings = { showSettings = true },
            )
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Jazda i głos") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Serwa") })
            }
            when (tab) {
                0 -> DriveTab(vm, link == LinkState.CONNECTED)
                else -> ServoTab(vm)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            initial = vm.settings,
            onDismiss = { showSettings = false },
            onSave = {
                vm.saveSettings(it)
                showSettings = false
            },
        )
    }
}

@Composable
private fun Header(
    link: LinkState,
    battery: Double?,
    wantConnection: Boolean,
    demo: Boolean,
    onConnect: () -> Unit,
    onDemo: () -> Unit,
    onDisconnect: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Wall-E", style = MaterialTheme.typography.titleLarge)
            val stateText = when (link) {
                LinkState.CONNECTED -> if (demo) "tryb demo (symulacja)" else "połączony"
                LinkState.CONNECTING -> "łączenie…"
                LinkState.DISCONNECTED -> if (wantConnection) "szukam robota…" else "rozłączony"
            }
            val bat = battery?.let { " · bateria %.2f V".format(it) } ?: ""
            Text(
                stateText + bat,
                color = if (battery != null && battery < LOW_BATTERY_V) Color(0xFFC62828) else Color.Unspecified,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onSettings) { Text("Ustawienia") }
        if (wantConnection) {
            OutlinedButton(onClick = onDisconnect) { Text("Rozłącz") }
        } else {
            OutlinedButton(onClick = onDemo) { Text("Demo") }
            Button(onClick = onConnect) { Text("Połącz") }
        }
    }
}

@Composable
private fun DriveTab(vm: WalleViewModel, connected: Boolean) {
    val ctx = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val micGranted = {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CameraView(
            url = if (vm.wifiConnected && vm.camOn && !vm.demo) "http://${vm.settings.camHost}:81/stream" else null,
            placeholder = if (vm.demo) "Kamera: niedostępna w trybie demo" else "Kamera wyłączona / brak połączenia",
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Joystick(Modifier.size(190.dp), onMove = vm::setDrive)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TalkButton(
                    talking = vm.talking,
                    enabled = connected,
                    onPress = {
                        if (micGranted()) {
                            vm.startTalking()
                            try {
                                awaitRelease()
                            } finally {
                                vm.stopTalking()
                            }
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
                Button(
                    onClick = vm::stop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                ) { Text("STOP") }
                Text("Głośność robota", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = vm.volume,
                    onValueChange = { vm.volume = it },
                    onValueChangeFinished = vm::sendVolume,
                    valueRange = 0f..100f,
                )
            }
        }
        Text("Pozy", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            POSES.forEach { (id, label) ->
                OutlinedButton(onClick = { vm.pose(id) }, modifier = Modifier.weight(1f)) {
                    Text(label, maxLines = 1)
                }
            }
        }
        Text("Dźwięki", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SOUNDS.forEach { (id, label) ->
                OutlinedButton(onClick = { vm.sound(id) }, modifier = Modifier.weight(1f)) {
                    Text(label, maxLines = 1)
                }
            }
        }
        if (vm.demo) {
            val log by vm.client.log.collectAsState()
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF212121), RoundedCornerShape(8.dp)).padding(8.dp),
            ) {
                log.forEach {
                    Text(it, color = Color(0xFF9CCC65), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ServoTab(vm: WalleViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SERVOS.forEach { s ->
            var value by remember { mutableFloatStateOf(s.start.toFloat()) }
            Text("${s.label}: ${value.toInt()}°", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = value,
                onValueChange = {
                    value = it
                    vm.setServo(s.id, it.toInt())
                },
                onValueChangeFinished = { vm.setServo(s.id, value.toInt(), final = true) },
                valueRange = s.min.toFloat()..s.max.toFloat(),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TalkButton(
    talking: Boolean,
    enabled: Boolean,
    onPress: suspend androidx.compose.foundation.gestures.PressGestureScope.() -> Unit,
) {
    val bg = when {
        !enabled -> Color(0xFF9E9E9E)
        talking -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(bg, RoundedCornerShape(36.dp))
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onPress = { onPress() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (talking) "MÓWISZ…" else "Przytrzymaj i mów",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun Joystick(modifier: Modifier = Modifier, onMove: (x: Float, y: Float) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableFloatStateOf(1f) }
    val move by rememberUpdatedState(onMove)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val knobColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier
            .onSizeChanged { radius = it.width / 2f }
            .pointerInput(Unit) {
                fun update(pos: Offset) {
                    var d = pos - Offset(size.width / 2f, size.height / 2f)
                    val len = d.getDistance()
                    if (len > radius) d = d * (radius / len)
                    knob = d
                    move(d.x / radius, -d.y / radius)
                }

                fun reset() {
                    knob = Offset.Zero
                    move(0f, 0f)
                }
                detectDragGestures(
                    onDragStart = { update(it) },
                    onDragEnd = { reset() },
                    onDragCancel = { reset() },
                ) { change, _ ->
                    change.consume()
                    update(change.position)
                }
            },
    ) {
        drawCircle(trackColor, radius = size.minDimension / 2f)
        drawCircle(knobColor, radius = size.minDimension / 6f, center = center + knob)
    }
}

@Composable
private fun CameraView(url: String?, placeholder: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AColor.BLACK)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                }
            },
            update = { web ->
                val wanted = url ?: ""
                if (web.tag != wanted) {
                    web.tag = wanted
                    if (url == null) {
                        web.loadUrl("about:blank") // odłącza strumień, oszczędza pasmo
                    } else {
                        val html = "<html><body style='margin:0;background:#000'>" +
                            "<img src='$url' style='width:100%;height:100%;object-fit:contain'/>" +
                            "</body></html>"
                        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    }
                }
            },
        )
        if (url == null) {
            Text(placeholder, color = Color.White)
        }
    }
}

@Composable
private fun SettingsDialog(initial: Settings, onDismiss: () -> Unit, onSave: (Settings) -> Unit) {
    var ssid by remember { mutableStateOf(initial.ssid) }
    var password by remember { mutableStateOf(initial.password) }
    var host by remember { mutableStateOf(initial.host) }
    var camHost by remember { mutableStateOf(initial.camHost) }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ustawienia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ssid, { ssid = it }, label = { Text("Sieć Wi-Fi robota (SSID)") }, singleLine = true)
                OutlinedTextField(
                    password, { password = it },
                    label = { Text("Hasło Wi-Fi (min. 8 znaków)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(host, { host = it }, label = { Text("Adres robota") }, singleLine = true)
                OutlinedTextField(camHost, { camHost = it }, label = { Text("Adres kamery") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Settings(ssid.trim(), password, host.trim(), camHost.trim())) }) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Aktualizacja") }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        },
    )
}

private const val LOW_BATTERY_V = 7.0
private const val RELEASES_URL = "https://github.com/arekp/wall-e/releases/latest"
