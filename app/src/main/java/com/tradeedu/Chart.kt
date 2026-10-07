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
import kotlin.math.floor
import androidx.compose.ui.graphics.Path
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

class Sym(val key: String, val name: String, val bin: String? = null, val okx: String? = null, val kc: String? = null, val yh: String? = null)
val SYMS = listOf(
    Sym("XAUUSD", "طلا (XAU)", bin = "PAXGUSDT", okx = "PAXG-USDT", yh = "XAUUSD=X"),
    Sym("EURUSD", "یورو (EUR)", bin = "EURUSDT", yh = "EURUSD=X"),
    Sym("GBPUSD", "پوند (GBP)", bin = "GBPUSDT", yh = "GBPUSD=X"),
    Sym("USDJPY", "ین (JPY)", yh = "USDJPY=X"),
    Sym("BTCUSD", "بیت‌کوین", bin = "BTCUSDT", okx = "BTC-USDT", kc = "BTC-USDT", yh = "BTC-USD"),
    Sym("ETHUSD", "اتریوم", bin = "ETHUSDT", okx = "ETH-USDT", kc = "ETH-USDT", yh = "ETH-USD"))
val SYMBOLS = SYMS.map { it.key to it.name }
val TFS = listOf("1m", "5m", "15m", "1h", "4h", "1d", "1w")
data class Tick(val last: Double, val pct: Double, val hi: Double, val lo: Double, val vol: Double)

private fun get(u: String): String {
    val c = URL(u).openConnection() as HttpURLConnection
    c.connectTimeout = 4000; c.readTimeout = 6000; c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
    return c.inputStream.bufferedReader().readText()
}
private val BIN_HOSTS = listOf("data-api.binance.vision", "api.binance.com", "api1.binance.com", "api3.binance.com")
private fun parseBin(t: String): List<Candle> { val a = JSONArray(t); return List(a.length()) { val k = a.getJSONArray(it); Candle(k.getString(1).toDouble(), k.getString(2).toDouble(), k.getString(3).toDouble(), k.getString(4).toDouble(), k.getString(5).toDouble(), k.getLong(0)) } }
private fun parseOkx(t: String): List<Candle> { val a = JSONObject(t).getJSONArray("data"); return List(a.length()) { val k = a.getJSONArray(a.length() - 1 - it); Candle(k.getString(1).toDouble(), k.getString(2).toDouble(), k.getString(3).toDouble(), k.getString(4).toDouble(), k.getString(5).toDouble(), k.getString(0).toLong()) } }
private fun parseKc(t: String): List<Candle> { val a = JSONObject(t).getJSONArray("data"); return List(a.length()) { val k = a.getJSONArray(a.length() - 1 - it); Candle(k.getString(1).toDouble(), k.getString(3).toDouble(), k.getString(4).toDouble(), k.getString(2).toDouble(), k.getString(5).toDouble(), k.getString(0).toLong() * 1000) } }
private fun parseYh(t: String): List<Candle> {
    val j = JSONObject(t).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
    val ts = j.getJSONArray("timestamp"); val q = j.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
    val o = q.getJSONArray("open"); val h = q.getJSONArray("high"); val l = q.getJSONArray("low"); val c = q.getJSONArray("close"); val v = q.optJSONArray("volume")
    val out = ArrayList<Candle>()
    for (i in 0 until ts.length()) {
        if (o.isNull(i) || h.isNull(i) || l.isNull(i) || c.isNull(i)) continue
        out.add(Candle(o.getDouble(i), h.getDouble(i), l.getDouble(i), c.getDouble(i), if (v == null || v.isNull(i)) 0.0 else v.getDouble(i), ts.getLong(i) * 1000))
    }
    return out
}
private fun agg(cs: List<Candle>, ms: Long): List<Candle> = cs.groupBy { it.t / ms }.toSortedMap().values.map { g -> Candle(g.first().o, g.maxOf { it.h }, g.minOf { it.l }, g.last().c, g.sumOf { it.v }, g.first().t) }

fun tickFrom(cs: List<Candle>): Tick {
    val l = cs.last(); val w = cs.filter { it.t >= l.t - 86_400_000L }.ifEmpty { cs }
    val base = (cs.lastOrNull { it.t <= l.t - 86_400_000L } ?: cs.first()).c
    return Tick(l.c, (l.c / base - 1) * 100, w.maxOf { it.h }, w.minOf { it.l }, w.sumOf { it.v })
}

suspend fun fetchCandles(sym: String, tf: String, limit: Int = 300): List<Candle>? = withContext(Dispatchers.IO) {
    val s = SYMS.firstOrNull { it.key == sym } ?: SYMS[0]
    val okxBar = mapOf("1m" to "1m", "5m" to "5m", "15m" to "15m", "1h" to "1H", "4h" to "4H", "1d" to "1D", "1w" to "1W")[tf]
    val kcType = mapOf("1m" to "1min", "5m" to "5min", "15m" to "15min", "1h" to "1hour", "4h" to "4hour", "1d" to "1day", "1w" to "1week")[tf]
    val yp = mapOf("1m" to ("1m" to "1d"), "5m" to ("5m" to "5d"), "15m" to ("15m" to "5d"), "1h" to ("60m" to "1mo"), "4h" to ("60m" to "3mo"), "1d" to ("1d" to "1y"), "1w" to ("1wk" to "5y"))[tf] ?: ("15m" to "5d")
    fun <T> tr(f: () -> T?): T? = try { f() } catch (e: Exception) { null }
    val all = listOf<Pair<String, () -> List<Candle>?>>(
        "Binance" to { s.bin?.let { b -> BIN_HOSTS.firstNotNullOfOrNull { h -> tr { parseBin(get("https://$h/api/v3/klines?symbol=$b&interval=$tf&limit=$limit")) } } } },
        "OKX" to { s.okx?.let { id -> tr { parseOkx(get("https://www.okx.com/api/v5/market/candles?instId=$id&bar=$okxBar&limit=$limit")) } } },
        "KuCoin" to { s.kc?.let { id -> tr { parseKc(get("https://api.kucoin.com/api/v1/market/candles?type=$kcType&symbol=$id")) } } },
        "Yahoo" to { s.yh?.let { id -> tr { parseYh(get("https://query1.finance.yahoo.com/v8/finance/chart/" + java.net.URLEncoder.encode(id, "UTF-8") + "?interval=${yp.first}&range=${yp.second}")).let { if (tf == "4h") agg(it, 14_400_000L) else it } } } })
    val order = if (S.src.isNotEmpty()) all.sortedBy { if (it.first == S.src) 0 else 1 }
        else if (s.bin == null || s.key == "XAUUSD") all.sortedBy { if (it.first == "Yahoo") 0 else 1 } else all
    for ((n, f) in order) { val r = f(); if (r != null && r.size > 10) { S.src = n; return@withContext r.takeLast(limit) } }
    null
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

fun f2(p: Double, d: Int = 2): String = String.format(Locale.US, "%,.${d}f", p)
fun fp(p: Double) = if (p >= 100) f2(p) else if (p >= 1) f2(p, 4) else f2(p, 6)
fun fv(v: Double) = if (v >= 1e9) f2(v / 1e9) + "B" else if (v >= 1e6) f2(v / 1e6) + "M" else f2(v, 0)
fun sg(v: Double) = (if (v >= 0) "+" else "−") + fp(abs(v))
val AXIS = 64.dp
val TFMS = mapOf("1m" to 60_000L, "5m" to 300_000L, "15m" to 900_000L, "1h" to 3_600_000L, "4h" to 14_400_000L, "1d" to 86_400_000L, "1w" to 604_800_000L)

fun sar(cs: List<Candle>): List<Double> {
    val o = MutableList(cs.size) { Double.NaN }; var up = true; var af = 0.02; var ep = cs[0].h; var s = cs[0].l
    for (i in 1 until cs.size) {
        val p2 = cs[max(0, i - 2)]; val p1 = cs[i - 1]
        s += af * (ep - s)
        if (up) {
            s = min(s, min(p1.l, p2.l))
            if (cs[i].l < s) { up = false; s = ep; ep = cs[i].l; af = 0.02 } else if (cs[i].h > ep) { ep = cs[i].h; af = min(af + 0.02, 0.2) }
        } else {
            s = max(s, max(p1.h, p2.h))
            if (cs[i].h > s) { up = true; s = ep; ep = cs[i].h; af = 0.02 } else if (cs[i].l < ep) { ep = cs[i].l; af = min(af + 0.02, 0.2) }
        }
        o[i] = s
    }
    return o
}

fun kdj(cs: List<Candle>): Triple<List<Double>, List<Double>, List<Double>> {
    val k = MutableList(cs.size) { 50.0 }; val d = MutableList(cs.size) { 50.0 }
    for (i in cs.indices) {
        val s = cs.subList(max(0, i - 8), i + 1); val hh = s.maxOf { it.h }; val ll = s.minOf { it.l }
        val rsv = if (hh == ll) 50.0 else (cs[i].c - ll) / (hh - ll) * 100
        if (i > 0) { k[i] = (2 * k[i - 1] + rsv) / 3; d[i] = (2 * d[i - 1] + k[i]) / 3 }
    }
    return Triple(k, d, k.indices.map { 3 * k[it] - 2 * d[it] })
}

class Inds(cs: List<Candle>) {
    val c = cs.map { it.c }
    val e50 = ema(c, 50); val ma = sma(c, 20); val bb = boll(c); val rs = rsi(c); val mc = macd(c); val kd = kdj(cs); val sr = sar(cs)
}

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
fun SubPane(kind: String, cs: List<Candle>, I: Inds, offF: () -> Float, visF: () -> Float, gc: Color, rc: Color) {
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    Canvas(Modifier.fillMaxWidth().height(72.dp).background(Color.Black)) {
        val off = offF(); val vis = visF()
        val pw = size.width - AXIS.toPx(); val h = size.height; val cw = pw / vis
        val a = max(0, off.toInt()); val b = min(cs.size - 1, (off + vis).toInt() + 1)
        fun x(i: Double) = (((i - off + 0.5) / vis) * pw).toFloat()
        val xs = { i: Double -> x(i) }
        val gl = Color(0xFF2A2E39)
        val label = when (kind) {
            "RSI" -> "RSI(14): " + f2(I.rs[b].let { if (it.isNaN()) 0.0 else it })
            "MACD" -> "MACD(12,26,9): " + f2(I.mc.third[b], 3)
            "KDJ" -> "KDJ(9,3,3): K " + f2(I.kd.first[b]) + " D " + f2(I.kd.second[b])
            else -> "VOL: " + fv(cs[b].v)
        }
        clipRect(0f, 0f, pw, h) {
            when (kind) {
                "VOL" -> {
                    val mx = (a..b).maxOf { cs[it].v }.coerceAtLeast(1e-9)
                    for (i in a..b) { val c = cs[i]; val bh = (c.v / mx).toFloat() * h * 0.85f
                        drawRect(if (c.c >= c.o) gc else rc, Offset(x(i.toDouble()) - cw * 0.35f, h - bh), Size(cw * 0.7f, bh)) }
                }
                "RSI" -> {
                    val ys = { v: Double -> ((100 - v) / 100 * h).toFloat() }
                    drawLine(gl, Offset(0f, ys(30.0)), Offset(pw, ys(30.0)), 1f); drawLine(gl, Offset(0f, ys(70.0)), Offset(pw, ys(70.0)), 1f)
                    series(I.rs, a, b, Color(0xFFAB47BC), xs, ys)
                }
                "KDJ" -> {
                    val ys = { v: Double -> ((110 - v) / 130 * h).toFloat() }
                    series(I.kd.first, a, b, Color(0xFFFFEB3B), xs, ys); series(I.kd.second, a, b, Color(0xFF42A5F5), xs, ys); series(I.kd.third, a, b, Color(0xFFAB47BC), xs, ys)
                }
                else -> {
                    val mx = (a..b).maxOf { max(abs(I.mc.first[it]), max(abs(I.mc.second[it]), abs(I.mc.third[it]))) }.coerceAtLeast(1e-9)
                    val ys = { v: Double -> (h / 2 - v / mx * h * 0.45).toFloat() }
                    for (i in a..b) { val v = I.mc.third[i]; drawRect(if (v >= 0) gc else rc, Offset(x(i.toDouble()) - cw * 0.3f, min(ys(v), ys(0.0))), Size(cw * 0.6f, max(abs(ys(v) - ys(0.0)), 1f))) }
                    series(I.mc.first, a, b, Color(0xFF2962FF), xs, ys); series(I.mc.second, a, b, Color(0xFFFF6D00), xs, ys)
                }
            }
        }
        drawLine(gl, Offset(0f, 0f), Offset(size.width, 0f), 1f)
        paint.color = 0xFF9598A1.toInt(); paint.textSize = 10.sp.toPx(); paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(label, 6.dp.toPx(), 12.dp.toPx(), paint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Practice() {
    var sym by S::sym
    var tf by S::tf
    var cs by S::cs
    var tick by S::tick
    var status by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    val ovs = S.ovs
    val subs = S.subs
    var ctype by S::ctype
    var grid by S::grid
    var now by remember { mutableLongStateOf(0L) }
    var menu by remember { mutableStateOf("") }
    var dlg by remember { mutableStateOf("") }
    var hub by remember { mutableStateOf(false) }
    var vis by S::vis
    var off by S::off
    val lines = S.lines
    var vz by S::vz
    var voff by S::voff
    var sel by remember { mutableIntStateOf(-1) }
    var selH by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("chart", 0) }
    remember {
        if (!S.inited) {
            S.inited = true
            prefs.getString("cfg", null)?.split("|")?.let { p ->
                if (p.size == 6) {
                    S.sym = if (SYMS.any { it.key == p[0] }) p[0] else "XAUUSD"; S.tf = p[1]; S.ctype = p[2].toIntOrNull() ?: 0; S.grid = p[3] == "true"
                    S.ovs.clear(); S.ovs.addAll(p[4].split(",").filter { it.isNotEmpty() })
                    S.subs.clear(); S.subs.addAll(p[5].split(",").filter { it.isNotEmpty() })
                }
            }
        }
        0
    }
    var pending by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var res by remember { mutableStateOf<List<Feedback>>(emptyList()) }
    var graded by remember { mutableStateOf(false) }
    val an = remember(cs) { Analysis(cs) }
    val I = remember(cs) { Inds(cs) }
    val df = remember(tf) { SimpleDateFormat(if (tf.endsWith("d") || tf.endsWith("w")) "yyyy-MM-dd" else "HH:mm", Locale.US) }
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(Unit) { snapshotFlow { "$sym|$tf|$ctype|$grid|${ovs.joinToString(",")}|${subs.joinToString(",")}" }.collect { prefs.edit().putString("cfg", it).apply() } }
    LaunchedEffect(Unit) { snapshotFlow { lines.toList() }.collect { if (S.live && S.key == "$sym|$tf" && cs[0].t > 0) prefs.edit().putString("d_" + S.key, encode(it, cs, TFMS[tf] ?: 60_000L)).apply() } }
    LaunchedEffect(sym, tf) {
        val key = "$sym|$tf"
        if (S.key != key || !S.live) {
            status = "در حال دریافت داده..."
            val r = fetchCandles(sym, tf)
            val ok = r != null && r.size > 30
            val d = if (ok) r!! else sampleCandles()
            lines.clear(); sel = -1; pending = null; res = emptyList(); graded = false
            vis = min(80f, d.size.toFloat()); off = d.size - vis; vz = 1f; voff = 0.0
            cs = d; S.live = ok; S.key = key
            if (ok) lines.addAll(decode(prefs.getString("d_$key", "") ?: "", d, TFMS[tf] ?: 60_000L))
            status = if (ok) "منبع داده: " + S.src else "هیچ منبع داده‌ای در دسترس نبود؛ داده‌ی نمونه نمایش داده می‌شود"
            tick = if (ok) tickFrom(d) else null
        }
        while (S.live) {
            delay(if (S.src == "Yahoo") 6000L else 2000L)
            val r = fetchCandles(sym, tf, 3)
            if (r != null && r.isNotEmpty()) {
                val l = cs.toMutableList(); val atEnd = off + vis >= l.size - 0.5f
                for (k in r) { if (k.t == l.last().t) l[l.lastIndex] = k else if (k.t > l.last().t) { l.add(k); if (atEnd) off += 1f } }
                cs = l
            }
            tick = tickFrom(cs)
        }
    }
    val rng by remember { derivedStateOf {
        val a = off.toInt().coerceIn(0, cs.size - 1); val b = min(cs.size, (off + vis).toInt() + 1)
        val s = cs.subList(a, max(b, a + 1)); val hh = s.maxOf { it.h }; val ll = s.minOf { it.l }
        val m = (hh + ll) / 2 + voff; val hf = (hh - ll) / 2 * 1.3 * vz + 1e-9
        (m - hf) to (m + hf)
    } }
    val gc = Color(0xFF089981); val rc = Color(0xFFF23645)
    val last = tick?.last ?: cs.last().c
    val pct = tick?.pct ?: ((cs.last().c / cs.first().o - 1) * 100)
    val chg = last - last / (1 + pct / 100)
    val hc = if (pct >= 0) gc else rc
    val tools = listOf("", "خط افقی", "خط روند", "ناحیه", "فیبوناچی", "خط عمودی")

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Text(fp(last) + "  " + sg(chg) + " (" + sg(pct) + "%)", color = hc, fontSize = 14.sp)
            if (mode != 0) Text("ابزار «" + tools[mode] + "» فعال است؛ روی چارت لمس کن" + if (mode >= 2 && mode != 5) " (دو نقطه)" else "", fontSize = 11.sp, color = Color(0xFFFFC107))
            else if (status.isNotEmpty()) Text(status, fontSize = 11.sp, color = Color.Gray)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Canvas(Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(cs) {
                detectTapGestures { p ->
                    val pw = size.width - AXIS.toPx(); val ph = size.height - 22.dp.toPx()
                    if (p.x > pw || p.y > ph) return@detectTapGestures
                    val lo = rng.first; val hi = rng.second
                    val px = { i: Double -> ((i - off + 0.5) / vis * pw).toFloat() }; val py = { v: Double -> ((hi - v) / (hi - lo) * ph).toFloat() }
                    if (mode == 0) {
                        sel = -1
                        for (k in lines.indices.reversed()) { val h = hit(lines[k], p.x, p.y, px, py, pw, 22.dp.toPx()); if (h >= 0) { sel = k; selH = h; break } }
                        return@detectTapGestures
                    }
                    val idx = off + p.x / pw * vis - 0.5
                    val price = hi - p.y / ph * (hi - lo)
                    graded = false; res = emptyList()
                    val a = pending
                    if (mode == 1) { lines.add(Drawn.HLine(price)); mode = 0 }
                    else if (mode == 5) { lines.add(Drawn.VLine(idx)); mode = 0 }
                    else if (a == null) pending = idx to price
                    else {
                        if (abs(idx - a.first) >= 2) lines.add(when (mode) { 2 -> Drawn.TLine(a.first, a.second, idx, price); 3 -> Drawn.Zone(a.first, a.second, idx, price); else -> Drawn.Fib(a.first, a.second, idx, price) })
                        pending = null; mode = 0
                    }
                }
            }
            .pointerInput(cs) {
                detectTransformGestures { c, pan, zoom, _ ->
                    val pw = size.width - AXIS.toPx(); val ph = size.height - 22.dp.toPx()
                    fun setVis(nv: Float) { val re = off + vis; vis = nv.coerceIn(15f, cs.size.toFloat()); off = (re - vis).coerceIn(0f, max(0f, cs.size - vis)) }
                    val range = rng.second - rng.first
                    if (c.x > pw) vz = (vz * (1f + pan.y / 400f)).coerceIn(0.2f, 8f)
                    else if (c.y > ph) setVis(vis * (1f - pan.x / 400f))
                    else if (zoom != 1f) setVis(vis / zoom)
                    else if (sel in 0 until lines.size) {
                        lines[sel] = moveD(lines[sel], selH, (pan.x / pw * vis).toDouble(), -(pan.y / ph * range))
                        graded = false; res = emptyList()
                    } else {
                        off = (off - pan.x / pw * vis).coerceIn(0f, max(0f, cs.size - vis))
                        voff += pan.y / ph * range
                    }
                }
            }
        ) {
            val axisW = AXIS.toPx(); val pw = size.width - axisW; val ph = size.height - 22.dp.toPx()
            val lo = rng.first; val hi = rng.second; val cw = pw / vis; val tnow = now
            fun y(p: Double) = ((hi - p) / (hi - lo) * ph).toFloat()
            fun x(i: Double) = (((i - off + 0.5) / vis) * pw).toFloat()
            val xs = { i: Double -> x(i) }; val ys = { v: Double -> y(v) }
            val a = max(0, off.toInt()); val b = min(cs.size - 1, (off + vis).toInt() + 1)
            val nc = drawContext.canvas.nativeCanvas
            val gl = Color(0xFF141414)
            paint.textSize = 12.sp.toPx(); paint.textAlign = android.graphics.Paint.Align.LEFT; paint.color = 0xFFD1D4DC.toInt()
            for (g in 0..7) {
                val p = lo + (hi - lo) * g / 7; val yy = y(p)
                if (grid) drawLine(gl, Offset(0f, yy), Offset(pw, yy), 1f)
                nc.drawText(fp(p), pw + 6.dp.toPx(), yy + 4.dp.toPx(), paint)
            }
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for (k in 0..4) {
                val i = (off + vis * (k + 0.5f) / 5).toInt().coerceIn(0, cs.size - 1)
                if (grid) drawLine(gl, Offset(x(i.toDouble()), 0f), Offset(x(i.toDouble()), ph), 1f)
                if (cs[i].t > 0) nc.drawText(df.format(Date(cs[i].t)), x(i.toDouble()), ph + 16.dp.toPx(), paint)
            }
            clipRect(0f, 0f, pw, ph) {
                when (ctype) {
                    0, 1 -> for (i in a..b) {
                        val c = cs[i]; val col = if (c.c >= c.o) gc else rc; val cx = x(i.toDouble())
                        drawLine(col, Offset(cx, y(c.h)), Offset(cx, y(c.l)), if (ctype == 0) 2f else 3f)
                        if (ctype == 0) { val t = y(max(c.o, c.c)); val bt = y(min(c.o, c.c)); drawRect(col, Offset(cx - cw * 0.35f, t), Size(cw * 0.7f, max(bt - t, 2f))) }
                        else { drawLine(col, Offset(cx - cw * 0.4f, y(c.o)), Offset(cx, y(c.o)), 3f); drawLine(col, Offset(cx, y(c.c)), Offset(cx + cw * 0.4f, y(c.c)), 3f) }
                    }
                    else -> {
                        if (ctype == 3) { val pth = Path(); pth.moveTo(x(a.toDouble()), ph); for (i in a..b) pth.lineTo(x(i.toDouble()), y(cs[i].c)); pth.lineTo(x(b.toDouble()), ph); pth.close(); drawPath(pth, Color(0x332962FF)) }
                        series(I.c, a, b, Color(0xFF2962FF), xs, ys)
                    }
                }
                if ("MA" in ovs) series(I.ma, a, b, Color(0xFFFFEB3B), xs, ys)
                if ("EMA" in ovs) series(I.e50, a, b, Color(0xFFFFA726), xs, ys)
                if ("BOLL" in ovs) { series(I.bb.first, a, b, Color(0xFFFF6D00), xs, ys); series(I.bb.second, a, b, Color(0xFF2962FF), xs, ys); series(I.bb.third, a, b, Color(0xFF2962FF), xs, ys) }
                if ("SAR" in ovs) for (i in a..b) { val v = I.sr[i]; if (!v.isNaN()) drawCircle(Color.White, 2.5.dp.toPx(), Offset(x(i.toDouble()), y(v))) }
                lines.forEachIndexed { k, d ->
                    val g = res.getOrNull(k)
                    val col = if (!graded || g == null || g.msg.startsWith("ⓘ")) Color(0xFFFFC107) else if (g.ok) Color(0xFF4CAF50) else Color(0xFFFF5252)
                    when (d) {
                        is Drawn.HLine -> drawLine(col, Offset(0f, y(d.price)), Offset(pw, y(d.price)), 3f)
                        is Drawn.VLine -> drawLine(col, Offset(x(d.i), 0f), Offset(x(d.i), ph), 3f)
                        is Drawn.TLine -> drawLine(col, Offset(x(d.i1), y(d.p1)), Offset(x(d.i2), y(d.p2)), 3f)
                        is Drawn.Zone -> drawRect(col.copy(alpha = 0.25f), Offset(min(x(d.i1), x(d.i2)), min(y(d.p1), y(d.p2))), Size(abs(x(d.i2) - x(d.i1)), abs(y(d.p2) - y(d.p1))))
                        is Drawn.Fib -> {
                            val xa = min(x(d.i1), x(d.i2)); val xb = max(x(d.i1), x(d.i2)); paint.textAlign = android.graphics.Paint.Align.LEFT; paint.color = 0xFF9598A1.toInt(); paint.textSize = 10.sp.toPx()
                            listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0).forEach { l ->
                                val p = d.p2 + (d.p1 - d.p2) * l; val yy = y(p)
                                drawLine(Color(0xFF787B86), Offset(xa, yy), Offset(xb, yy), 1.5f)
                                nc.drawText(f2(l, 3) + " (" + fp(p) + ")", xa + 4.dp.toPx(), yy - 3.dp.toPx(), paint)
                            }
                        }
                    }
                }
                if (sel in 0 until lines.size) {
                    val ps: List<Offset> = when (val d = lines[sel]) {
                        is Drawn.HLine -> listOf(Offset(pw / 2, y(d.price)))
                        is Drawn.VLine -> listOf(Offset(x(d.i), ph / 2))
                        is Drawn.TLine -> listOf(Offset(x(d.i1), y(d.p1)), Offset(x(d.i2), y(d.p2)))
                        is Drawn.Zone -> listOf(Offset(x(d.i1), y(d.p1)), Offset(x(d.i2), y(d.p2)))
                        is Drawn.Fib -> listOf(Offset(x(d.i1), y(d.p1)), Offset(x(d.i2), y(d.p2)))
                    }
                    ps.forEach { drawCircle(Color.White, 8.dp.toPx(), it); drawCircle(Color(0xFF2962FF), 5.dp.toPx(), it) }
                }
                pending?.let { drawCircle(Color.White, 10f, Offset(x(it.first), y(it.second))) }
                val ly0 = y(cs.last().c).coerceIn(0f, ph)
                drawLine(hc, Offset(0f, ly0), Offset(pw, ly0), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 8f)))
            }
            val hiI = (a..b).maxByOrNull { cs[it].h }!!; val loI = (a..b).minByOrNull { cs[it].l }!!
            paint.color = 0xFFD1D4DC.toInt(); paint.textSize = 10.sp.toPx(); paint.textAlign = android.graphics.Paint.Align.CENTER
            nc.drawText(fp(cs[hiI].h), x(hiI.toDouble()).coerceIn(30f, pw - 30f), y(cs[hiI].h) - 4.dp.toPx(), paint)
            nc.drawText(fp(cs[loI].l), x(loI.toDouble()).coerceIn(30f, pw - 30f), y(cs[loI].l) + 12.dp.toPx(), paint)
            val ly = y(cs.last().c).coerceIn(17.dp.toPx(), ph - 17.dp.toPx())
            drawRect(hc, Offset(pw, ly - 17.dp.toPx()), Size(axisW, 34.dp.toPx()))
            paint.color = 0xFFFFFFFF.toInt(); paint.textSize = 12.sp.toPx(); paint.textAlign = android.graphics.Paint.Align.LEFT
            nc.drawText(fp(cs.last().c), pw + 5.dp.toPx(), ly - 1.dp.toPx(), paint)
            val ms = TFMS[tf] ?: 60_000L
            if (cs.last().t > 0 && tnow > 0) {
                val left = (ms - (tnow - cs.last().t) % ms) / 1000
                val tx = if (left >= 3600) String.format(Locale.US, "%d:%02d:%02d", left / 3600, left / 60 % 60, left % 60) else String.format(Locale.US, "%02d:%02d", left / 60, left % 60)
                nc.drawText(tx, pw + 5.dp.toPx(), ly + 13.dp.toPx(), paint)
            }
        }
        Surface(shape = RoundedCornerShape(50), color = if (lines.isEmpty()) Color(0xFF3A3F47) else Color(0xFF2962FF),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clickable { if (lines.isNotEmpty()) { sel = -1; res = an.grade(lines.toList()); graded = true; dlg = "res" } }) {
            Text("✓ تصحیح" + if (lines.isEmpty()) "" else " (" + lines.size + ")", Modifier.padding(horizontal = 16.dp, vertical = 7.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        }
        subs.forEach { SubPane(it, cs, I, { off }, { vis }, gc, rc) }
        if (sel in 0 until lines.size) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("⧉ کپی", false) { lines.add(moveD(lines[sel], 0, 3.0, -(rng.second - rng.first) * 0.03)); sel = lines.lastIndex; selH = 0; graded = false }
            Chip("🗑 حذف", false) { lines.removeAt(sel); sel = -1; graded = false }
            Chip("✓ تمام", false) { sel = -1 }
            Text("برای جابه‌جایی بکش", fontSize = 11.sp, color = Color.Gray)
        }
        Row(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Text(sym, Modifier.clickable { menu = "sym" }.padding(8.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                DropdownMenu(menu == "sym", { menu = "" }) { SYMBOLS.forEach { (k, n) -> DropdownMenuItem({ Text(n) }, { sym = k; menu = "" }) } }
            }
            Box {
                Text(tf, Modifier.clickable { menu = "tf" }.padding(8.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                DropdownMenu(menu == "tf", { menu = "" }) { TFS.forEach { t -> DropdownMenuItem({ Text(t) }, { tf = t; menu = "" }) } }
            }
            Spacer(Modifier.weight(1f))
            Box {
                Text("✏️", Modifier.clickable { menu = "draw" }.padding(10.dp), fontSize = 20.sp)
                DropdownMenu(menu == "draw", { menu = "" }) {
                    listOf(1, 5, 2, 3, 4).forEach { m -> DropdownMenuItem({ Text(tools[m]) }, { mode = m; pending = null; menu = "" }) }
                    DropdownMenuItem({ Text("✅ تصحیح خودکار") }, { menu = ""; if (lines.isNotEmpty()) { res = an.grade(lines.toList()); graded = true; dlg = "res" } })
                    DropdownMenuItem({ Text("🗑 پاک کردن همه") }, { menu = ""; lines.clear(); res = emptyList(); graded = false })
                }
            }
            Text("ƒx", Modifier.clickable { dlg = "ind" }.padding(10.dp), fontSize = 20.sp)
            Text("⋯", Modifier.clickable { hub = true }.padding(10.dp), fontSize = 22.sp)
            Text("↶", Modifier.clickable { if (lines.isNotEmpty()) lines.removeAt(lines.size - 1); graded = false }.padding(10.dp), fontSize = 22.sp)
        }
    }
    if (hub) ModalBottomSheet(onDismissRequest = { hub = false }) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("مرکز تحلیل", style = MaterialTheme.typography.titleLarge)
            val rows: List<List<Pair<String, (() -> Unit)?>>> = listOf(
                listOf("چیدمان" to null, "تنظیمات چارت" to { dlg = "set" }, "جدید" to { lines.clear(); res = emptyList(); graded = false; pending = null }),
                listOf("ذخیره" to null, "باز کردن" to null, "مقایسه" to null),
                listOf("اندیکاتورها" to { dlg = "ind" }, "نوع چارت" to { dlg = "type" }, "درخت اشیا" to { dlg = "tree" }),
                listOf("هشدارها" to null, "بازپخش کندل" to null, "قالب اندیکاتور" to null),
                listOf("جزئیات نماد" to { dlg = "info" }))
            rows.forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { r.forEach { (n, f) ->
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF2A2F36), modifier = Modifier.weight(1f).clickable(enabled = f != null) { hub = false; f?.invoke() }) {
                    Text(n + if (f == null) "\n(به‌زودی)" else "", Modifier.padding(14.dp), fontSize = 13.sp, color = if (f == null) Color.Gray else Color.White) } } } }
        }
    }
    val ok = { dlg = "" }
    if (dlg == "res") { val gr = res.filter { !it.msg.startsWith("ⓘ") }
        AlertDialog(onDismissRequest = ok, confirmButton = { TextButton(ok) { Text("باشه") } }, title = { Text("نمره: ${gr.count { it.ok }} از ${gr.size}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { res.forEach { Text((if (it.ok) "✅ " else "❌ ") + it.msg.removePrefix("ⓘ "), Modifier.padding(top = 6.dp)) } } }) }
    if (dlg == "ind") AlertDialog(onDismissRequest = ok, confirmButton = { TextButton(ok) { Text("تأیید") } }, title = { Text("اندیکاتورها") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("روی چارت", color = Color.Gray)
            listOf("MA", "EMA", "BOLL", "SAR").forEach { k -> Row(Modifier.fillMaxWidth().clickable { if (k in ovs) ovs.remove(k) else ovs.add(k) }, verticalAlignment = Alignment.CenterVertically) { Checkbox(k in ovs, null); Text("  " + k) } }
            Text("پنل پایین", Modifier.padding(top = 8.dp), color = Color.Gray)
            listOf("VOL", "RSI", "MACD", "KDJ").forEach { k -> Row(Modifier.fillMaxWidth().clickable { if (k in subs) subs.remove(k) else subs.add(k) }, verticalAlignment = Alignment.CenterVertically) { Checkbox(k in subs, null); Text("  " + k) } }
        } })
    if (dlg == "type") AlertDialog(onDismissRequest = ok, confirmButton = {}, title = { Text("نوع چارت") },
        text = { Column { listOf("کندل", "میله (OHLC)", "خطی", "ناحیه‌ای").forEachIndexed { i, n -> TextButton({ ctype = i; dlg = "" }) { Text((if (ctype == i) "● " else "○ ") + n) } } } })
    if (dlg == "tree") AlertDialog(onDismissRequest = ok, confirmButton = { TextButton(ok) { Text("بستن") } }, title = { Text("درخت اشیا") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (lines.isEmpty()) Text("هنوز چیزی رسم نشده")
            lines.forEachIndexed { i, d -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (d) { is Drawn.HLine -> "خط افقی " + fp(d.price); is Drawn.VLine -> "خط عمودی"; is Drawn.TLine -> "خط روند"; is Drawn.Zone -> "ناحیه"; is Drawn.Fib -> "فیبوناچی" }, Modifier.weight(1f))
                TextButton({ lines.removeAt(i); graded = false }) { Text("🗑") } } }
        } })
    if (dlg == "info") AlertDialog(onDismissRequest = ok, confirmButton = { TextButton(ok) { Text("بستن") } }, title = { Text(sym) },
        text = { Column {
            Text("آخرین قیمت: " + fp(last)); Text("تغییر ۲۴ ساعت: " + sg(pct) + "%")
            Text("بیشترین: " + fp(tick?.hi ?: cs.maxOf { it.h })); Text("کمترین: " + fp(tick?.lo ?: cs.minOf { it.l })); Text("حجم ۲۴ ساعت: " + fv(tick?.vol ?: cs.sumOf { it.v }))
        } })
    if (dlg == "set") AlertDialog(onDismissRequest = ok, confirmButton = { TextButton(ok) { Text("تأیید") } }, title = { Text("تنظیمات چارت") },
        text = { Column { Row(verticalAlignment = Alignment.CenterVertically) { Text("نمایش شبکه", Modifier.weight(1f)); Switch(grid, { grid = it }) }; TextButton({ vz = 1f; voff = 0.0; dlg = "" }) { Text("بازنشانی مقیاس عمودی") } } })
}


private val S = ChartStore
object ChartStore {
    var inited = false; var live = false; var key = ""; var src = ""
    var sym by mutableStateOf("XAUUSD"); var tf by mutableStateOf("15m")
    var cs by mutableStateOf<List<Candle>>(sampleCandles()); var tick by mutableStateOf<Tick?>(null)
    val ovs = mutableStateListOf<String>(); val subs = mutableStateListOf("VOL")
    var ctype by mutableIntStateOf(0); var grid by mutableStateOf(true)
    var vis by mutableFloatStateOf(80f); var off by mutableFloatStateOf(40f); var vz by mutableFloatStateOf(1f); var voff by mutableDoubleStateOf(0.0)
    val lines = mutableStateListOf<Drawn>()
}

@Composable
fun Chip(t: String, on: Boolean, f: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable { f() },
        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
        Text(t, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, maxLines = 1)
    }
}

fun moveD(d: Drawn, h: Int, di: Double, dp: Double): Drawn = when (d) {
    is Drawn.HLine -> d.copy(price = d.price + dp)
    is Drawn.VLine -> d.copy(i = d.i + di)
    is Drawn.TLine -> when (h) { 1 -> d.copy(i1 = d.i1 + di, p1 = d.p1 + dp); 2 -> d.copy(i2 = d.i2 + di, p2 = d.p2 + dp); else -> d.copy(i1 = d.i1 + di, p1 = d.p1 + dp, i2 = d.i2 + di, p2 = d.p2 + dp) }
    is Drawn.Zone -> when (h) { 1 -> d.copy(i1 = d.i1 + di, p1 = d.p1 + dp); 2 -> d.copy(i2 = d.i2 + di, p2 = d.p2 + dp); else -> d.copy(i1 = d.i1 + di, p1 = d.p1 + dp, i2 = d.i2 + di, p2 = d.p2 + dp) }
    is Drawn.Fib -> when (h) { 1 -> d.copy(i1 = d.i1 + di, p1 = d.p1 + dp); 2 -> d.copy(i2 = d.i2 + di, p2 = d.p2 + dp); else -> d.copy(i1 = d.i1 + di, p1 = d.p1 + dp, i2 = d.i2 + di, p2 = d.p2 + dp) }
}

val FIBS = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0)

fun hit(d: Drawn, tx: Float, ty: Float, px: (Double) -> Float, py: (Double) -> Float, pw: Float, th: Float): Int {
    fun near(i: Double, p: Double) = abs(px(i) - tx) < th * 1.4f && abs(py(p) - ty) < th * 1.4f
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val dx = x2 - x1; val dy = y2 - y1; val l2 = (dx * dx + dy * dy).coerceAtLeast(1f)
        val u = (((tx - x1) * dx + (ty - y1) * dy) / l2).coerceIn(0f, 1f)
        val cx = x1 + u * dx; val cy = y1 + u * dy
        return sqrt((tx - cx) * (tx - cx) + (ty - cy) * (ty - cy)) < th
    }
    return when (d) {
        is Drawn.HLine -> if (abs(py(d.price) - ty) < th) 0 else -1
        is Drawn.VLine -> if (abs(px(d.i) - tx) < th) 0 else -1
        is Drawn.TLine -> if (near(d.i1, d.p1)) 1 else if (near(d.i2, d.p2)) 2 else if (seg(px(d.i1), py(d.p1), px(d.i2), py(d.p2))) 0 else -1
        is Drawn.Zone -> if (near(d.i1, d.p1)) 1 else if (near(d.i2, d.p2)) 2
            else if (tx in min(px(d.i1), px(d.i2))..max(px(d.i1), px(d.i2)) && ty in min(py(d.p1), py(d.p2))..max(py(d.p1), py(d.p2))) 0 else -1
        is Drawn.Fib -> if (near(d.i1, d.p1)) 1 else if (near(d.i2, d.p2)) 2
            else if (tx in min(px(d.i1), px(d.i2))..max(px(d.i1), px(d.i2)) && FIBS.any { abs(py(d.p2 + (d.p1 - d.p2) * it) - ty) < th }) 0 else -1
    }
}

fun encode(ls: List<Drawn>, cs: List<Candle>, ms: Long): String = ls.joinToString(";") { d ->
    fun t(i: Double): Long { val k = floor(i).toInt().coerceIn(0, cs.size - 1); return cs[k].t + ((i - k) * ms).toLong() }
    when (d) {
        is Drawn.HLine -> "H,${d.price}"
        is Drawn.VLine -> "V,${t(d.i)}"
        is Drawn.TLine -> "T,${t(d.i1)},${d.p1},${t(d.i2)},${d.p2}"
        is Drawn.Zone -> "Z,${t(d.i1)},${d.p1},${t(d.i2)},${d.p2}"
        is Drawn.Fib -> "F,${t(d.i1)},${d.p1},${t(d.i2)},${d.p2}"
    }
}

fun decode(s: String, cs: List<Candle>, ms: Long): List<Drawn> = s.split(";").filter { it.isNotBlank() }.mapNotNull { r ->
    try {
        val p = r.split(",")
        fun i(k: Int): Double {
            val t = p[k].toLong(); var lo = 0; var hi = cs.size - 1
            while (lo < hi) { val m = (lo + hi + 1) / 2; if (cs[m].t <= t) lo = m else hi = m - 1 }
            return lo + (t - cs[lo].t).toDouble() / ms
        }
        when (p[0]) {
            "H" -> Drawn.HLine(p[1].toDouble()); "V" -> Drawn.VLine(i(1))
            "T" -> Drawn.TLine(i(1), p[2].toDouble(), i(3), p[4].toDouble()); "Z" -> Drawn.Zone(i(1), p[2].toDouble(), i(3), p[4].toDouble())
            "F" -> Drawn.Fib(i(1), p[2].toDouble(), i(3), p[4].toDouble()); else -> null
        }
    } catch (e: Exception) { null }
}
