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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

val SYMBOLS = listOf("BTCUSDT" to "بیت‌کوین", "ETHUSDT" to "اتریوم", "PAXGUSDT" to "طلا (XAU)", "EURUSDT" to "یورو", "GBPUSDT" to "پوند", "SOLUSDT" to "سولانا")
val TFS = listOf("1m", "5m", "15m", "1h", "4h", "1d", "1w")
data class Tick(val last: Double, val pct: Double, val hi: Double, val lo: Double, val vol: Double)

private fun get(u: String): String {
    val c = URL(u).openConnection() as HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 8000
    return c.inputStream.bufferedReader().readText()
}

suspend fun fetchCandles(sym: String, tf: String): List<Candle>? = withContext(Dispatchers.IO) {
    try {
        val a = JSONArray(get("https://data-api.binance.vision/api/v3/klines?symbol=$sym&interval=$tf&limit=300"))
        List(a.length()) { val k = a.getJSONArray(it); Candle(k.getString(1).toDouble(), k.getString(2).toDouble(), k.getString(3).toDouble(), k.getString(4).toDouble(), k.getString(5).toDouble(), k.getLong(0)) }
    } catch (e: Exception) { null }
}

suspend fun fetchTick(sym: String): Tick? = withContext(Dispatchers.IO) {
    try {
        val o = JSONObject(get("https://data-api.binance.vision/api/v3/ticker/24hr?symbol=$sym"))
        Tick(o.getString("lastPrice").toDouble(), o.getString("priceChangePercent").toDouble(), o.getString("highPrice").toDouble(), o.getString("lowPrice").toDouble(), o.getString("quoteVolume").toDouble())
    } catch (e: Exception) { null }
}

fun ema(v: List<Double>, n: Int): List<Double> { val k = 2.0 / (n + 1); var p = v[0]; return v.map { p = it * k + p * (1 - k); p } }
fun sma(v: List<Double>, n: Int) = v.indices.map { if (it < n - 1) Double.NaN else v.subList(it - n + 1, it + 1).average() }
fun boll(v: List<Double>): Triple<List<Double>, List<Double>, List<Double>> {
    val m = sma(v, 20)
    val sd = v.indices.map { i -> if (i < 19) Double.NaN else sqrt(v.subList(i - 19, i + 1).sumOf { (it - m[i]) * (it - m[i]) } / 20) }
    return Triple(m, m.indices.map { m[it] + 2 * sd[it] }, m.indices.map { m[it] - 2 * sd[it] })
}
fun rsi(v: List<Double>, n: Int = 14): List<Double> {
    val o = MutableList(v.size) { Double.NaN }; var ag = 0.0; var al = 0.0
    for (i in 1 until v.size) {
        val d = v[i] - v[i - 1]; val g = max(d, 0.0); val l = max(-d, 0.0)
        if (i <= n) { ag += g / n; al += l / n } else { ag = (ag * (n - 1) + g) / n; al = (al * (n - 1) + l) / n }
        if (i >= n) o[i] = if (al == 0.0) 100.0 else 100 - 100 / (1 + ag / al)
    }
    return o
}
fun macd(v: List<Double>): Triple<List<Double>, List<Double>, List<Double>> {
    val dif = ema(v, 12).zip(ema(v, 26)) { a, b -> a - b }; val dea = ema(dif, 9)
    return Triple(dif, dea, dif.zip(dea) { a, b -> a - b })
}

fun fp(p: Double) = if (p >= 100) "%.2f".format(p) else if (p >= 1) "%.4f".format(p) else "%.6f".format(p)
fun fv(v: Double) = if (v >= 1e9) "%.2fB".format(v / 1e9) else if (v >= 1e6) "%.2fM".format(v / 1e6) else "%.0f".format(v)

fun DrawScope.series(s: List<Double>, a: Int, b: Int, col: Color, x: (Double) -> Float, y: (Double) -> Float) {
    var px = 0f; var py = 0f; var has = false
    for (i in a..b) {
        val v = s[i]
        if (v.isNaN()) { has = false; continue }
        val cx = x(i.toDouble()); val cy = y(v)
        if (has) drawLine(col, Offset(px, py), Offset(cx, cy), 2.5f)
        px = cx; py = cy; has = true
    }
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
    var tick by remember { mutableStateOf<Tick?>(null) }
    var status by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(1) }
    var ov by remember { mutableStateOf("EMA") }
    var sub by remember { mutableStateOf("VOL") }
    var menu by remember { mutableStateOf(false) }
    var vis by remember { mutableFloatStateOf(80f) }
    var off by remember { mutableFloatStateOf(40f) }
    val lines = remember { mutableStateListOf<Drawn>() }
    var pending by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var res by remember { mutableStateOf<List<Feedback>>(emptyList()) }
    var graded by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf(false) }
    val an = remember(cs) { Analysis(cs) }
    val closes = remember(cs) { cs.map { it.c } }
    val e50 = remember(cs) { ema(closes, 50) }
    val ma20 = remember(cs) { sma(closes, 20) }
    val bb = remember(cs) { boll(closes) }
    val rs = remember(cs) { rsi(closes) }
    val mc = remember(cs) { macd(closes) }
    val df = remember(tf) { SimpleDateFormat(if (tf.endsWith("d") || tf.endsWith("w")) "yyyy-MM-dd" else "MM-dd HH:mm", Locale.US) }
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    LaunchedEffect(sym, tf) {
        status = "در حال دریافت داده..."
        val r = fetchCandles(sym, tf)
        val ok = r != null && r.size > 30
        val d = if (ok) r!! else sampleCandles()
        lines.clear(); res = emptyList(); graded = false; pending = null
        vis = min(80f, d.size.toFloat()); off = d.size - vis; cs = d
        status = if (ok) "" else "اتصال برقرار نشد؛ داده‌ی نمونه (شاید VPN لازم باشد)"
        tick = if (ok) fetchTick(sym) else null
    }
    val rng by remember { derivedStateOf {
        val a = off.toInt().coerceIn(0, cs.size - 1); val b = min(cs.size, (off + vis).toInt() + 1)
        val s = cs.subList(a, max(b, a + 1)); val hh = s.maxOf { it.h }; val ll = s.minOf { it.l }; val p = (hh - ll) * 0.08
        (ll - p) to (hh + p)
    } }
    val last = tick?.last ?: cs.last().c
    val pct = tick?.pct ?: ((cs.last().c / cs.first().o - 1) * 100)
    val up = pct >= 0
    val gc = Color(0xFF26A69A); val rc = Color(0xFFEF5350); val hc = if (up) gc else rc

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Box {
                    Text(SYMBOLS.first { it.first == sym }.second + " / USDT  ▾", Modifier.clickable { menu = true }, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    DropdownMenu(menu, { menu = false }) { SYMBOLS.forEach { (k, n) -> DropdownMenuItem({ Text(n) }, { sym = k; menu = false }) } }
                }
                Text(fp(last), color = hc, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text((if (up) "+" else "") + "%.2f%%".format(pct), color = hc, fontSize = 13.sp)
            }
            Column {
                Text("بیشترین ۲۴ ساعت: " + fp(tick?.hi ?: cs.maxOf { it.h }), fontSize = 12.sp)
                Text("کمترین ۲۴ ساعت: " + fp(tick?.lo ?: cs.minOf { it.l }), fontSize = 12.sp)
                Text("حجم ۲۴ ساعت: " + fv(tick?.vol ?: cs.sumOf { it.v }), fontSize = 12.sp)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TFS.forEach { Chip(it, tf == it) { tf = it } }
            if (status.isNotEmpty()) Text(status, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(start = 6.dp))
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp).background(Color(0xFF0B0E11))
            .pointerInput(cs) {
                detectTapGestures { p ->
                    val pw = size.width - 58.dp.toPx(); val ph = size.height - 20.dp.toPx()
                    if (p.x > pw || p.y > ph) return@detectTapGestures
                    val idx = off + p.x / pw * vis - 0.5
                    val price = rng.second - p.y / ph * (rng.second - rng.first)
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
                    val pw = size.width - 58.dp.toPx()
                    vis = (vis / zoom).coerceIn(15f, cs.size.toFloat())
                    off = (off - pan.x / pw * vis).coerceIn(0f, max(0f, cs.size - vis))
                }
            }
        ) {
            val axisW = 58.dp.toPx(); val pw = size.width - axisW; val ph = size.height - 20.dp.toPx()
            val lo = rng.first; val hi = rng.second; val cw = pw / vis
            fun y(p: Double) = ((hi - p) / (hi - lo) * ph).toFloat()
            fun x(i: Double) = (((i - off + 0.5) / vis) * pw).toFloat()
            val a = max(0, off.toInt()); val b = min(cs.size - 1, (off + vis).toInt() + 1)
            val nc = drawContext.canvas.nativeCanvas
            paint.textSize = 10.sp.toPx(); paint.textAlign = android.graphics.Paint.Align.LEFT; paint.color = 0xFF8A94A0.toInt()
            for (g in 0..4) {
                val p = lo + (hi - lo) * g / 4; val yy = y(p)
                drawLine(Color(0xFF1C2127), Offset(0f, yy), Offset(pw, yy), 1f)
                nc.drawText(fp(p), pw + 6.dp.toPx(), yy + 4.dp.toPx(), paint)
            }
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for (k in 0..3) {
                val i = (off + vis * (k + 0.5f) / 4).toInt().coerceIn(0, cs.size - 1)
                if (cs[i].t > 0) nc.drawText(df.format(Date(cs[i].t)), x(i.toDouble()), ph + 14.dp.toPx(), paint)
            }
            clipRect(0f, 0f, pw, ph) {
                for (i in a..b) {
                    val c = cs[i]; val col = if (c.c >= c.o) gc else rc; val cx = x(i.toDouble())
                    drawLine(col, Offset(cx, y(c.h)), Offset(cx, y(c.l)), 2f)
                    val t = y(max(c.o, c.c)); val bt = y(min(c.o, c.c))
                    drawRect(col, Offset(cx - cw * 0.35f, t), Size(cw * 0.7f, max(bt - t, 2f)))
                }
                val xs = { i: Double -> x(i) }; val ys = { v: Double -> y(v) }
                when (ov) {
                    "EMA" -> series(e50, a, b, Color(0xFFFFA726), xs, ys)
                    "MA" -> series(ma20, a, b, Color(0xFFFFEB3B), xs, ys)
                    "BOLL" -> { series(bb.first, a, b, Color(0xFFFFEB3B), xs, ys); series(bb.second, a, b, Color(0xFF42A5F5), xs, ys); series(bb.third, a, b, Color(0xFFAB47BC), xs, ys) }
                }
                lines.forEachIndexed { k, d ->
                    val col = if (!graded) Color(0xFFFFC107) else if (res.getOrNull(k)?.ok == true) Color(0xFF4CAF50) else Color(0xFFFF5252)
                    when (d) {
                        is Drawn.HLine -> drawLine(col, Offset(0f, y(d.price)), Offset(pw, y(d.price)), 4f)
                        is Drawn.TLine -> {
                            val s = (d.p2 - d.p1) / (d.i2 - d.i1)
                            fun at(i: Double) = d.p1 + s * (i - d.i1)
                            val i0 = off - 1.0; val i1 = off + vis + 1.0
                            drawLine(col, Offset(x(i0), y(at(i0))), Offset(x(i1), y(at(i1))), 4f)
                        }
                        is Drawn.Zone -> drawRect(col.copy(alpha = 0.25f), Offset(min(x(d.i1), x(d.i2)), min(y(d.p1), y(d.p2))), Size(abs(x(d.i2) - x(d.i1)), abs(y(d.p2) - y(d.p1))))
                    }
                }
                pending?.let { drawCircle(Color.White, 10f, Offset(x(it.first), y(it.second))) }
                val ly = y(cs.last().c).coerceIn(0f, ph)
                drawLine(hc, Offset(0f, ly), Offset(pw, ly), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
            val hiI = (a..b).maxByOrNull { cs[it].h }!!; val loI = (a..b).minByOrNull { cs[it].l }!!
            paint.color = 0xFFCFD8DC.toInt()
            nc.drawText(fp(cs[hiI].h), x(hiI.toDouble()).coerceIn(30f, pw - 30f), y(cs[hiI].h) - 4.dp.toPx(), paint)
            nc.drawText(fp(cs[loI].l), x(loI.toDouble()).coerceIn(30f, pw - 30f), y(cs[loI].l) + 12.dp.toPx(), paint)
            val ly = y(cs.last().c).coerceIn(9.dp.toPx(), ph - 9.dp.toPx())
            drawRect(hc, Offset(pw, ly - 9.dp.toPx()), Size(axisW, 18.dp.toPx()))
            paint.color = 0xFFFFFFFF.toInt(); paint.textAlign = android.graphics.Paint.Align.LEFT
            nc.drawText(fp(cs.last().c), pw + 4.dp.toPx(), ly + 4.dp.toPx(), paint)
        }
        Canvas(Modifier.fillMaxWidth().height(80.dp).background(Color(0xFF0B0E11))) {
            val pw = size.width - 58.dp.toPx(); val h = size.height; val cw = pw / vis
            val a = max(0, off.toInt()); val b = min(cs.size - 1, (off + vis).toInt() + 1)
            fun x(i: Double) = (((i - off + 0.5) / vis) * pw).toFloat()
            val xs = { i: Double -> x(i) }
            val label = when (sub) { "RSI" -> "RSI(14): " + "%.2f".format(rs[b].let { if (it.isNaN()) 0.0 else it }); "MACD" -> "MACD(12,26,9): " + "%.3f".format(mc.third[b]); else -> "VOL: " + fv(cs[b].v) }
            clipRect(0f, 0f, pw, h) {
                when (sub) {
                    "VOL" -> { val mx = (a..b).maxOf { cs[it].v }.coerceAtLeast(1e-9)
                        for (i in a..b) { val c = cs[i]; val bh = (c.v / mx).toFloat() * h * 0.85f
                            drawRect(if (c.c >= c.o) gc else rc, Offset(x(i.toDouble()) - cw * 0.35f, h - bh), Size(cw * 0.7f, bh)) } }
                    "RSI" -> { val ys = { v: Double -> ((100 - v) / 100 * h).toFloat() }
                        drawLine(Color(0xFF2A3038), Offset(0f, ys(30.0)), Offset(pw, ys(30.0)), 1f); drawLine(Color(0xFF2A3038), Offset(0f, ys(70.0)), Offset(pw, ys(70.0)), 1f)
                        series(rs, a, b, Color(0xFFAB47BC), xs, ys) }
                    else -> { val mx = (a..b).maxOf { max(abs(mc.first[it]), max(abs(mc.second[it]), abs(mc.third[it]))) }.coerceAtLeast(1e-9)
                        val ys = { v: Double -> (h / 2 - v / mx * h * 0.45).toFloat() }
                        for (i in a..b) { val v = mc.third[i]; drawRect(if (v >= 0) gc else rc, Offset(x(i.toDouble()) - cw * 0.3f, min(ys(v), ys(0.0))), Size(cw * 0.6f, max(abs(ys(v) - ys(0.0)), 1f))) }
                        series(mc.first, a, b, Color(0xFFFFEB3B), xs, ys); series(mc.second, a, b, Color(0xFF42A5F5), xs, ys) }
                }
            }
            paint.color = 0xFF8A94A0.toInt(); paint.textSize = 10.sp.toPx(); paint.textAlign = android.graphics.Paint.Align.LEFT
            drawContext.canvas.nativeCanvas.drawText(label, 6.dp.toPx(), 12.dp.toPx(), paint)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("EMA", "MA", "BOLL").forEach { Chip(it, ov == it) { ov = if (ov == it) "" else it } }
            listOf("VOL", "MACD", "RSI").forEach { Chip(it, sub == it) { sub = it } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("✏️ خط افقی", mode == 1) { mode = 1; pending = null }
            Chip("📈 خط روند", mode == 2) { mode = 2; pending = null }
            Chip("▭ ناحیه", mode == 3) { mode = 3; pending = null }
            Chip("🗑 پاک", false) { lines.clear(); res = emptyList(); graded = false; pending = null }
            Chip("✅ تصحیح", false) { if (lines.isNotEmpty()) { res = an.grade(lines.toList()); graded = true; dlg = true } }
        }
    }
    if (dlg) AlertDialog(onDismissRequest = { dlg = false }, confirmButton = { TextButton({ dlg = false }) { Text("باشه") } },
        title = { Text("نمره: ${res.count { it.ok }} از ${res.size}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { res.forEach { Text((if (it.ok) "✅ " else "❌ ") + it.msg, Modifier.padding(top = 6.dp)) } } })
}
