package com.tradeedu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val SYMBOLS = listOf("BTCUSDT" to "بیت‌کوین", "ETHUSDT" to "اتریوم", "PAXGUSDT" to "طلا", "EURUSDT" to "یورو")
val TFS = listOf("5m", "15m", "1h", "4h", "1d")

suspend fun fetchCandles(sym: String, tf: String): List<Candle>? = withContext(Dispatchers.IO) {
    try {
        val c = URL("https://data-api.binance.vision/api/v3/klines?symbol=$sym&interval=$tf&limit=300").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        val a = JSONArray(c.inputStream.bufferedReader().readText())
        List(a.length()) { val k = a.getJSONArray(it); Candle(k.getString(1).toDouble(), k.getString(2).toDouble(), k.getString(3).toDouble(), k.getString(4).toDouble()) }
    } catch (e: Exception) { null }
}

@Composable
fun Chip(t: String, on: Boolean, f: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable { f() },
        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
        Text(t, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun Practice() {
    var sym by remember { mutableStateOf(SYMBOLS[0].first) }
    var tf by remember { mutableStateOf("1h") }
    var cs by remember { mutableStateOf(sampleCandles()) }
    var status by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(1) }
    var vis by remember { mutableFloatStateOf(80f) }
    var off by remember { mutableFloatStateOf(40f) }
    val lines = remember { mutableStateListOf<Drawn>() }
    var pending by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var res by remember { mutableStateOf<List<Feedback>>(emptyList()) }
    var graded by remember { mutableStateOf(false) }
    val an = remember(cs) { Analysis(cs) }
    LaunchedEffect(sym, tf) {
        status = "در حال دریافت داده..."
        val r = fetchCandles(sym, tf)
        val ok = r != null && r.size > 30
        val d = if (ok) r!! else sampleCandles()
        lines.clear(); res = emptyList(); graded = false; pending = null
        vis = min(80f, d.size.toFloat()); off = d.size - vis; cs = d
        status = if (ok) "" else "اتصال برقرار نشد؛ داده‌ی نمونه نمایش داده می‌شود (شاید VPN لازم باشد)"
    }
    val rng by remember { derivedStateOf {
        val a = off.toInt().coerceIn(0, cs.size - 1); val b = min(cs.size, (off + vis).toInt() + 1)
        val s = cs.subList(a, max(b, a + 1)); val hh = s.maxOf { it.h }; val ll = s.minOf { it.l }; val p = (hh - ll) * 0.06
        (ll - p) to (hh + p)
    } }
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SYMBOLS.forEach { (k, n) -> Chip(n, sym == k) { sym = k } }
            TFS.forEach { Chip(it, tf == it) { tf = it } }
        }
        if (status.isNotEmpty()) Text(status, fontSize = 12.sp, color = Color.Gray)
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp).background(Color(0xFF12161C))
            .pointerInput(cs) {
                detectTapGestures { p ->
                    val idx = off + p.x / size.width * vis - 0.5
                    val price = rng.second - p.y / size.height * (rng.second - rng.first)
                    graded = false; res = emptyList()
                    val a = pending
                    if (mode == 1) lines.add(Drawn.HLine(price))
                    else if (a == null) pending = idx to price
                    else {
                        if (abs(idx - a.first) >= 2) lines.add(if (mode == 2) Drawn.TLine(a.first, a.second, idx, price) else Drawn.Zone(a.first, a.second, idx, price))
                        pending = null
                    }
                }
            }
            .pointerInput(cs) {
                detectTransformGestures { _, pan, zoom, _ ->
                    vis = (vis / zoom).coerceIn(15f, cs.size.toFloat())
                    off = (off - pan.x / size.width * vis).coerceIn(0f, max(0f, cs.size - vis))
                }
            }
        ) {
            val w = size.width; val h = size.height; val lo = rng.first; val hi = rng.second; val cw = w / vis
            fun y(p: Double) = ((hi - p) / (hi - lo) * h).toFloat()
            fun x(i: Double) = (((i - off + 0.5) / vis) * w).toFloat()
            for (i in max(0, off.toInt())..min(cs.size - 1, (off + vis).toInt() + 1)) {
                val c = cs[i]; val col = if (c.c >= c.o) Color(0xFF26A69A) else Color(0xFFEF5350); val cx = x(i.toDouble())
                drawLine(col, Offset(cx, y(c.h)), Offset(cx, y(c.l)), 2f)
                val t = y(max(c.o, c.c)); val b = y(min(c.o, c.c))
                drawRect(col, Offset(cx - cw * 0.35f, t), Size(cw * 0.7f, max(b - t, 2f)))
            }
            lines.forEachIndexed { k, d ->
                val col = if (!graded) Color(0xFFFFC107) else if (res.getOrNull(k)?.ok == true) Color(0xFF4CAF50) else Color(0xFFFF5252)
                when (d) {
                    is Drawn.HLine -> drawLine(col, Offset(0f, y(d.price)), Offset(w, y(d.price)), 4f)
                    is Drawn.TLine -> {
                        val s = (d.p2 - d.p1) / (d.i2 - d.i1)
                        fun at(i: Double) = d.p1 + s * (i - d.i1)
                        val i0 = off - 1.0; val i1 = off + vis + 1.0
                        drawLine(col, Offset(x(i0), y(at(i0))), Offset(x(i1), y(at(i1))), 4f)
                    }
                    is Drawn.Zone -> {
                        val tl = Offset(min(x(d.i1), x(d.i2)), min(y(d.p1), y(d.p2)))
                        drawRect(col.copy(alpha = 0.25f), tl, Size(abs(x(d.i2) - x(d.i1)), abs(y(d.p2) - y(d.p1))))
                    }
                }
            }
            pending?.let { drawCircle(Color.White, 10f, Offset(x(it.first), y(it.second))) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("خط افقی", mode == 1) { mode = 1; pending = null }
            Chip("خط روند", mode == 2) { mode = 2; pending = null }
            Chip("ناحیه", mode == 3) { mode = 3; pending = null }
            Chip("پاک کردن", false) { lines.clear(); res = emptyList(); graded = false; pending = null }
        }
        Button({ res = an.grade(lines.toList()); graded = true }, Modifier.fillMaxWidth().padding(vertical = 6.dp), enabled = lines.isNotEmpty()) { Text("تصحیح خودکار") }
        if (graded) {
            Text("نمره: ${res.count { it.ok }} از ${res.size}", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.heightIn(max = 110.dp).verticalScroll(rememberScrollState())) {
                res.forEach { Text((if (it.ok) "✅ " else "❌ ") + it.msg, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)) }
            }
        } else Text("دو انگشت: زوم و جابه‌جایی؛ یک لمس: رسم (برای خط روند و ناحیه دو نقطه)", fontSize = 12.sp, color = Color.Gray)
    }
}
