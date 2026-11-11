package pl.walle.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val connected = link == LinkState.CONNECTED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBar(
                link = link,
                battery = status.batteryVolts,
                onSettings = { showSettings = true },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("🕹️", fontSize = 22.sp) },
                    label = { Text("Jazda i głos") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("🦾", fontSize = 22.sp) },
                    label = { Text("Serwa") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ConnectionCard(
                link = link,
                wantConnection = vm.wantConnection,
                demo = vm.demo,
                onConnect = vm::connect,
                onDemo = vm::connectDemo,
                onDisconnect = vm::disconnect,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            when (tab) {
                0 -> DriveTab(vm, connected)
                else -> ServoTab(vm, connected)
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

// ───────────────────────── nagłówek i połączenie ─────────────────────────

@Composable
private fun TopBar(link: LinkState, battery: Double?, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RobotFace(link == LinkState.CONNECTED, Modifier.size(44.dp))
        Text(
            "WALL·E",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            maxLines = 1,
        )
        if (battery != null) BatteryChip(battery)
        TextButton(onClick = onSettings) { Text("⚙  Ustawienia", maxLines = 1) }
    }
}

@Composable
private fun BatteryChip(volts: Double) {
    val low = volts < LOW_BATTERY_V
    val bg = if (low) WalleColors.Bad else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (low) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔋 %.2f V".format(volts), color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun ConnectionCard(
    link: LinkState,
    wantConnection: Boolean,
    demo: Boolean,
    onConnect: () -> Unit,
    onDemo: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (dot, title, hint) = when (link) {
        LinkState.CONNECTED ->
            if (demo) Triple(WalleColors.Warn, "Tryb demo", "Symulacja – robot nie jest podłączony")
            else Triple(WalleColors.Ok, "Połączony", "Robot odpowiada")
        LinkState.CONNECTING -> Triple(WalleColors.Warn, "Łączenie…", "Nawiązuję połączenie z robotem")
        LinkState.DISCONNECTED ->
            if (wantConnection) Triple(WalleColors.Warn, "Szukam robota…", "Włącz robota i sprawdź sieć Wi-Fi")
            else Triple(MaterialTheme.colorScheme.outline, "Rozłączony", "Połącz się z robotem albo wypróbuj demo")
    }
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (wantConnection) {
                    OutlinedButton(onClick = onDisconnect) { Text("Rozłącz") }
                }
            }
            if (!wantConnection) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConnect, modifier = Modifier.weight(1f).height(48.dp)) { Text("Połącz z robotem") }
                    FilledTonalButton(onClick = onDemo, modifier = Modifier.weight(1f).height(48.dp)) { Text("Tryb demo") }
                }
            }
        }
    }
}

// ───────────────────────── zakładka: jazda i głos ─────────────────────────

private val POSE_ICONS = mapOf("home" to "🏠", "hello" to "👋", "sad" to "😢", "curious" to "🤔")
private val SOUND_ICONS = mapOf("walle" to "🤖", "eve" to "💙", "beep" to "🔔")

@Composable
private fun DriveTab(vm: WalleViewModel, connected: Boolean) {
    val ctx = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val micGranted = {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val streamUrl = if (vm.wifiConnected && vm.camOn && !vm.demo) "http://${vm.settings.camHost}:81/stream" else null
        CameraView(
            url = streamUrl,
            placeholder = if (vm.demo) "Kamera niedostępna w trybie demo" else "Brak obrazu z kamery",
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)),
        )

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Joystick(Modifier.size(168.dp), onMove = vm::setDrive)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WalleColors.Bad, contentColor = Color.White),
                        ) { Text("STOP", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔊", fontSize = 20.sp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Głośność robota: ${vm.volume.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = vm.volume,
                            onValueChange = { vm.volume = it },
                            onValueChangeFinished = vm::sendVolume,
                            valueRange = 0f..100f,
                            enabled = connected,
                        )
                    }
                }
            }
        }

        SectionTitle("Pozy")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            POSES.forEach { (id, label) ->
                ActionTile(POSE_ICONS[id] ?: "•", label, connected, Modifier.weight(1f)) { vm.pose(id) }
            }
        }

        SectionTitle("Dźwięki")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SOUNDS.forEach { (id, label) ->
                ActionTile(SOUND_ICONS[id] ?: "•", label, connected, Modifier.weight(1f)) { vm.sound(id) }
            }
        }

        if (vm.demo) {
            val log by vm.client.log.collectAsState()
            SectionTitle("Podgląd komend (demo)")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1E1E1E)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (log.isEmpty()) Text("Poruszaj joystickiem lub naciśnij pozę…", color = Color(0xFF9E9E9E), style = MaterialTheme.typography.bodySmall)
                log.forEach {
                    Text(it, color = Color(0xFF9CCC65), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun ActionTile(icon: String, label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(icon, fontSize = 26.sp)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───────────────────────── zakładka: serwa ─────────────────────────

@Composable
private fun ServoTab(vm: WalleViewModel, connected: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilledTonalButton(
            onClick = { vm.pose("home") },
            enabled = connected,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("🏠  Pozycja domyślna") }

        SERVOS.forEach { s ->
            var value by remember { mutableFloatStateOf(s.start.toFloat()) }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.label,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${value.toInt()}°",
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Slider(
                        value = value,
                        onValueChange = {
                            value = it
                            vm.setServo(s.id, it.toInt())
                        },
                        onValueChangeFinished = { vm.setServo(s.id, value.toInt(), final = true) },
                        valueRange = s.min.toFloat()..s.max.toFloat(),
                        enabled = connected,
                    )
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${s.min}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("${s.max}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ───────────────────────── sterowanie ─────────────────────────

@Composable
private fun TalkButton(
    talking: Boolean,
    enabled: Boolean,
    onPress: suspend PressGestureScope.() -> Unit,
) {
    val target = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        talking -> WalleColors.Ok
        else -> MaterialTheme.colorScheme.primary
    }
    val bg by animateColorAsState(target, label = "talk")
    val fg = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(bg)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onPress = { onPress() })
            }
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🎤", fontSize = 18.sp)
        Text(
            if (talking) "Mówisz…" else "Przytrzymaj i mów",
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Joystick(modifier: Modifier = Modifier, onMove: (x: Float, y: Float) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    val move by rememberUpdatedState(onMove)
    val scheme = MaterialTheme.colorScheme
    val trackColor = scheme.surfaceVariant
    val ringColor = scheme.outlineVariant
    val arrowColor = scheme.onSurfaceVariant.copy(alpha = 0.55f)
    val knobColor by animateColorAsState(if (active) WalleColors.Yellow else scheme.primary, label = "knob")

    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val radius = size.width / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                fun update(pos: Offset) {
                    var d = pos - center
                    val len = d.getDistance()
                    if (len > radius) d = d * (radius / len)
                    knob = d
                    move(d.x / radius, -d.y / radius)
                }

                val down = awaitFirstDown()
                down.consume()
                active = true
                update(down.position)
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    update(change.position)
                    change.consume()
                }
                active = false
                knob = Offset.Zero
                move(0f, 0f)
            }
        },
    ) {
        val r = size.minDimension / 2f
        drawCircle(trackColor, radius = r)
        drawCircle(ringColor, radius = r - 1.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
        drawCircle(ringColor, radius = r * 0.55f, style = Stroke(width = 1.dp.toPx()))
        // strzałki kierunków
        val a = r * 0.86f
        val s = r * 0.1f
        arrow(center + Offset(0f, -a), 0, s, arrowColor)
        arrow(center + Offset(a, 0f), 1, s, arrowColor)
        arrow(center + Offset(0f, a), 2, s, arrowColor)
        arrow(center + Offset(-a, 0f), 3, s, arrowColor)
        // gałka
        val kr = r * 0.34f
        val kc = center + knob
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = kr, center = kc + Offset(0f, 3.dp.toPx()))
        drawCircle(
            Brush.radialGradient(
                listOf(knobColor.copy(alpha = 1f), knobColor.copy(alpha = 0.85f)),
                center = kc - Offset(kr * 0.3f, kr * 0.3f),
                radius = kr * 1.4f,
            ),
            radius = kr,
            center = kc,
        )
        drawCircle(Color.White.copy(alpha = 0.35f), radius = kr * 0.55f, center = kc - Offset(kr * 0.25f, kr * 0.25f), style = Stroke(width = 2.dp.toPx()))
    }
}

/** dir: 0 = góra, 1 = prawo, 2 = dół, 3 = lewo */
private fun DrawScope.arrow(at: Offset, dir: Int, s: Float, color: Color) {
    val p = Path()
    when (dir) {
        0 -> { p.moveTo(at.x, at.y - s); p.lineTo(at.x - s, at.y + s); p.lineTo(at.x + s, at.y + s) }
        1 -> { p.moveTo(at.x + s, at.y); p.lineTo(at.x - s, at.y - s); p.lineTo(at.x - s, at.y + s) }
        2 -> { p.moveTo(at.x, at.y + s); p.lineTo(at.x - s, at.y - s); p.lineTo(at.x + s, at.y - s) }
        else -> { p.moveTo(at.x - s, at.y); p.lineTo(at.x + s, at.y - s); p.lineTo(at.x + s, at.y + s) }
    }
    p.close()
    drawPath(p, color)
}

// ───────────────────────── kamera i twarz robota ─────────────────────────

@Composable
private fun CameraView(url: String?, placeholder: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF14100D)), contentAlignment = Alignment.Center) {
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
            Box(Modifier.fillMaxSize().background(Color(0xFF14100D)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RobotFace(false, Modifier.size(56.dp))
                    Text(placeholder, color = Color(0xFFD6C3B4), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        } else {
            Text(
                "● NA ŻYWO",
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Uproszczona twarz Wall-E: żółta obudowa i lornetkowe oczy; niebieskie, gdy robot żyje. */
@Composable
private fun RobotFace(awake: Boolean, modifier: Modifier = Modifier) {
    val iris by animateColorAsState(if (awake) WalleColors.EyeBlue else Color(0xFF7B6F66), label = "iris")
    Canvas(modifier) {
        val s = size.minDimension
        drawRoundRect(WalleColors.Yellow, size = Size(s, s), cornerRadius = CornerRadius(s * 0.26f))
        drawRoundRect(
            WalleColors.Rust,
            topLeft = Offset(s * 0.1f, s * 0.2f),
            size = Size(s * 0.8f, s * 0.42f),
            cornerRadius = CornerRadius(s * 0.14f),
        )
        for (cx in listOf(0.3f, 0.7f)) {
            val c = Offset(s * cx, s * 0.41f)
            drawCircle(WalleColors.Ink, radius = s * 0.19f, center = c)
            drawCircle(Color(0xFFEFE6DA), radius = s * 0.145f, center = c)
            drawCircle(iris, radius = s * 0.1f, center = c)
            drawCircle(WalleColors.Ink, radius = s * 0.045f, center = c)
            drawCircle(Color.White, radius = s * 0.02f, center = c + Offset(-s * 0.03f, -s * 0.03f))
        }
        for (i in 0..2) {
            val y = s * (0.72f + i * 0.08f)
            drawLine(WalleColors.Ink, Offset(s * 0.34f, y), Offset(s * 0.66f, y), strokeWidth = s * 0.035f)
        }
    }
}

// ───────────────────────── ustawienia ─────────────────────────

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
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ssid, { ssid = it }, label = { Text("Sieć Wi-Fi robota (SSID)") }, singleLine = true)
                OutlinedTextField(
                    password, { password = it },
                    label = { Text("Hasło Wi-Fi (min. 8 znaków)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(host, { host = it }, label = { Text("Adres robota") }, singleLine = true)
                OutlinedTextField(camHost, { camHost = it }, label = { Text("Adres kamery") }, singleLine = true)
                TextButton(onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Sprawdź aktualizację aplikacji") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Settings(ssid.trim(), password, host.trim(), camHost.trim())) }) {
                Text("Zapisz")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

private const val LOW_BATTERY_V = 7.0
private const val RELEASES_URL = "https://github.com/arekp/wall-e/releases/latest"
