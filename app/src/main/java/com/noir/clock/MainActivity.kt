package com.noir.clock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF050505),
                    surface = Color(0xFF0C0C0E),
                    primary = Color(0xFF8FB8FF),
                ),
            ) { NoirApp() }
        }
    }

    override fun onResume() {
        super.onResume()
        ClockWidgetProvider.refreshAll(this)
    }
}

private val Ink = Color(0xFFF4F4F4)
private val Bg = Color(0xFF050505)
private val PanelBg = Color(0xFF0C0C0E)

// ---------------------------------------------------------------- time sources

@Composable
private fun rememberClockMillis(smooth: Boolean): State<Long> =
    produceState(System.currentTimeMillis(), smooth) {
        while (true) {
            if (smooth) {
                withFrameNanos { }
            } else {
                delay(1000L - System.currentTimeMillis() % 1000L)
            }
            value = System.currentTimeMillis()
        }
    }

@Composable
private fun rememberDateText(pattern: String): State<String> =
    produceState("") {
        while (true) {
            value = SimpleDateFormat(pattern, Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())
            delay(30_000L)
        }
    }

// ---------------------------------------------------------------- root

@Composable
fun NoirApp() {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(ClockPrefs.load(ctx)) }

    fun commit(n: ClockSettings) {
        s = n
        ClockPrefs.save(ctx, n)
        ClockWidgetProvider.refreshAll(ctx)
    }

    val accent by animateColorAsState(Color(s.accentArgb), tween(450), label = "accent")
    val digital = s.style == STYLE_DIGITAL

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text("NOIR", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Medium)
            Text("Clock", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Light)
        }

        val cardShape = RoundedCornerShape(32.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(cardShape)
                .background(Brush.linearGradient(listOf(Color(0xFF131316), Color(0xFF060607))))
                .border(1.dp, Color.White.copy(alpha = 0.08f), cardShape),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = s.style, animationSpec = tween(450), label = "style") { st ->
                if (st == STYLE_DIGITAL) DigitalPreview(s, accent) else AnalogPreview(s)
            }
        }

        StyleSwitch(s.style) { commit(s.copy(style = it, layout = 0)) }

        val panelShape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(panelShape)
                .background(PanelBg)
                .border(1.dp, Color.White.copy(alpha = 0.07f), panelShape)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            SettingRow("24-hour time") { Toggle(s.is24h, accent) { commit(s.copy(is24h = it)) } }
            Hairline()
            SettingRow(
                "Seconds",
                if (digital) "Ticks live on the widget" else "Sweeping hand in preview. The home-screen dial redraws each minute to save battery",
            ) { Toggle(s.seconds, accent) { commit(s.copy(seconds = it)) } }
            Hairline()
            SettingRow("Show date") { Toggle(s.showDate, accent) { commit(s.copy(showDate = it)) } }
            Hairline()
            SettingRow("Accent") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEachIndexed { i, a ->
                        val selected = i == s.accent
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(a.argb))
                                .border(if (selected) 2.dp else 0.dp, Color.White, CircleShape)
                                .clickable { commit(s.copy(accent = i)) },
                        )
                    }
                }
            }
            Hairline()
            Column(Modifier.padding(vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Size", color = Color(0xFFEDEDED), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("${s.sizePct}%", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                }
                Slider(
                    value = s.sizePct.toFloat(),
                    onValueChange = { s = s.copy(sizePct = it.roundToInt()) },
                    onValueChangeFinished = { commit(s) },
                    valueRange = 70f..115f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = accent,
                        inactiveTrackColor = Color(0xFF222226),
                    ),
                )
            }
            Hairline()
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Layout", color = Color(0xFFEDEDED), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                val names = if (digital) listOf("Classic", "Stacked", "Left") else listOf("Ticks", "Dots", "Minimal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    names.forEachIndexed { i, n ->
                        val sel = s.layout == i
                        val chip = RoundedCornerShape(50)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(chip)
                                .background(if (sel) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                                .border(1.dp, if (sel) accent else Color.White.copy(alpha = 0.10f), chip)
                                .clickable { commit(s.copy(layout = i)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(n, color = if (sel) Color.White else Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        AddWidgetButton(accent)
        Text(
            "Tap the widget on your home screen to come back here. Changes apply instantly.",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color(0xFFEDEDED), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.40f), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        trailing()
    }
}

@Composable
private fun Toggle(checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = accent,
            checkedBorderColor = accent,
            uncheckedThumbColor = Color(0xFF8A8A8E),
            uncheckedTrackColor = Color(0xFF1A1A1D),
            uncheckedBorderColor = Color(0xFF2A2A2E),
        ),
    )
}

@Composable
private fun StyleSwitch(style: Int, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Color(0xFF0E0E10))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(4.dp),
    ) {
        val segW: Dp = maxWidth / 2
        val offset by animateDpAsState(if (style == STYLE_DIGITAL) 0.dp else segW, spring(dampingRatio = 0.8f, stiffness = 380f), label = "seg")
        Box(
            Modifier
                .offset(x = offset)
                .width(segW)
                .fillMaxHeight()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.11f)),
        )
        Row(Modifier.fillMaxSize()) {
            listOf("Digital" to STYLE_DIGITAL, "Analog" to STYLE_ANALOG).forEach { (label, v) ->
                Box(
                    Modifier
                        .width(segW)
                        .fillMaxHeight()
                        .clip(shape)
                        .clickable { onChange(v) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (style == v) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddWidgetButton(accent: Color) {
    val ctx = LocalContext.current
    val mgr = remember { AppWidgetManager.getInstance(ctx) }
    val supported = remember { mgr.isRequestPinAppWidgetSupported }
    val shape = RoundedCornerShape(50)
    if (supported) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(shape)
                .background(accent)
                .clickable {
                    mgr.requestPinAppWidget(ComponentName(ctx, ClockWidgetProvider::class.java), null, null)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("Add widget to home screen", color = Bg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    } else {
        Text("Long-press your home screen, then Widgets, then Noir Clock.", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- previews

@Composable
private fun DigitalPreview(s: ClockSettings, accent: Color) {
    val now by rememberClockMillis(false)
    val date by rememberDateText("EEE, d MMM")
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h24 = cal.get(Calendar.HOUR_OF_DAY)
    val hours = if (s.is24h) "%02d".format(h24) else (if (h24 % 12 == 0) 12 else h24 % 12).toString()
    val mins = "%02d".format(cal.get(Calendar.MINUTE))
    val secs = "%02d".format(cal.get(Calendar.SECOND))
    val ampm = if (h24 < 12) "AM" else "PM"

    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "colon",
    )

    val fs = (66f * s.sizePct / 100f).sp
    val glow = Shadow(color = accent.copy(alpha = 0.45f), offset = Offset.Zero, blurRadius = 38f)
    val timeStyle = TextStyle(
        fontSize = fs,
        lineHeight = fs,
        fontWeight = FontWeight.Light,
        color = Ink,
        letterSpacing = 1.sp,
        shadow = glow,
    )
    val dateStyle = TextStyle(fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.55f))

    @Composable
    fun Side(horizontal: Boolean) {
        val items: @Composable () -> Unit = {
            if (s.seconds) Text(secs, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (!s.is24h) Text(ampm, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        }
        if (horizontal) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items() }
        } else {
            Column(Modifier.padding(start = 8.dp)) { items() }
        }
    }

    when (s.layout) {
        1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hours, style = timeStyle)
            Text(mins, style = timeStyle.copy(color = accent))
            if (s.seconds || !s.is24h) {
                Box(Modifier.padding(top = 6.dp)) { Side(true) }
            }
            if (s.showDate) Text(date, style = dateStyle, modifier = Modifier.padding(top = 6.dp))
        }

        2 -> Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start,
        ) {
            Box(Modifier.width(22.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(hours, style = timeStyle)
                Text(":", style = timeStyle.copy(color = accent.copy(alpha = blink)))
                Text(mins, style = timeStyle)
                if (s.seconds || !s.is24h) Side(false)
            }
            if (s.showDate) Text(date, style = dateStyle, modifier = Modifier.padding(top = 4.dp))
        }

        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hours, style = timeStyle)
                Text(":", style = timeStyle.copy(color = accent.copy(alpha = blink)))
                Text(mins, style = timeStyle)
                if (s.seconds || !s.is24h) Side(false)
            }
            if (s.showDate) Text(date, style = dateStyle, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun AnalogPreview(s: ClockSettings) {
    val now by rememberClockMillis(s.seconds)
    val date by rememberDateText("EEE d")
    Canvas(
        Modifier
            .fillMaxHeight(0.94f)
            .aspectRatio(1f),
    ) {
        val t = now
        val d = if (s.showDate) date else null
        drawIntoCanvas { c ->
            AnalogRenderer.draw(c.nativeCanvas, size.minDimension, t, s, s.seconds, d, AnalogRenderer.dialScale(s.sizePct))
        }
    }
}
