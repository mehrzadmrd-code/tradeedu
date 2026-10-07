package com.tradeedu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

val TgScheme = darkColorScheme(
    primary = Color(0xFF5EB5F7), onPrimary = Color(0xFF0E1621),
    background = Color(0xFF0E1621), onBackground = Color.White,
    surface = Color(0xFF17212B), onSurface = Color.White,
    secondaryContainer = Color(0xFF2B5278)
)

object ThemeStore { var mode by mutableIntStateOf(2); var tab by mutableIntStateOf(0) }

val TgLight = lightColorScheme(
    primary = Color(0xFF2481CC), onPrimary = Color.White,
    background = Color(0xFFF1F3F5), onBackground = Color(0xFF111111),
    surface = Color.White, onSurface = Color(0xFF111111),
    secondaryContainer = Color(0xFFD6E9F8)
)

@Composable
fun TvIcon(k: Int, col: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension / 24f
        fun P(x: Float, y: Float) = Offset(x * s, y * s)
        val w = 1.8f * s
        val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val r = CornerRadius(1f * s)
        when (k) {
            0 -> {
                drawRoundRect(col, P(5f, 3f), Size(14f * s, 18f * s), CornerRadius(2f * s), style = st)
                drawLine(col, P(9f, 3f), P(9f, 21f), w, StrokeCap.Round)
                drawLine(col, P(12f, 8f), P(16f, 8f), w, StrokeCap.Round); drawLine(col, P(12f, 12f), P(16f, 12f), w, StrokeCap.Round)
            }
            1 -> {
                drawLine(col, P(8f, 3f), P(8f, 21f), w, StrokeCap.Round); drawRoundRect(col, P(5.5f, 7f), Size(5f * s, 9f * s), r, style = st)
                drawLine(col, P(16f, 5f), P(16f, 19f), w, StrokeCap.Round); drawRoundRect(col, P(13.5f, 9f), Size(5f * s, 7f * s), r, style = st)
            }
            2 -> {
                drawLine(col, P(4f, 21f), P(20f, 21f), w, StrokeCap.Round)
                drawRoundRect(col, P(5f, 13f), Size(3.5f * s, 8f * s), r, style = st); drawRoundRect(col, P(10.2f, 9f), Size(3.5f * s, 12f * s), r, style = st)
                drawRoundRect(col, P(15.5f, 4f), Size(3.5f * s, 17f * s), r, style = st)
            }
            else -> {
                drawCircle(col, 3f * s, P(12f, 12f), style = st); drawCircle(col, 7f * s, P(12f, 12f), style = st)
                for (i in 0 until 8) {
                    val an = i * Math.PI / 4; val c = cos(an).toFloat(); val sn = sin(an).toFloat()
                    drawLine(col, P(12f + 7f * c, 12f + 7f * sn), P(12f + 10f * c, 12f + 10f * sn), w * 1.5f, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
fun App() {
    var tab by ThemeStore::tab
    val names = listOf("دوره‌ها", "تمرین چارت", "آمار پیشرفت", "تنظیمات")
    val bar = if (tab == 1) Color.Black else MaterialTheme.colorScheme.surface
    val fg = if (tab == 1) Color.White else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(bar).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tab == 1) {
                Spacer(Modifier.weight(1f))
                val cs = ChartStore.cs; val tk = ChartStore.tick
                val last = tk?.last ?: cs.last().c; val pct = tk?.pct ?: ((cs.last().c / cs.first().o - 1) * 100)
                val chg = last - last / (1 + pct / 100)
                Column(horizontalAlignment = Alignment.End) {
                    Text(SYMBOLS.firstOrNull { it.first == ChartStore.sym }?.second ?: ChartStore.sym, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(fp(last) + "  " + sgp(chg, last) + " (" + sgc(pct) + "%)", color = if (pct >= 0) Color(0xFF089981) else Color(0xFFF23645), fontSize = 12.sp, lineHeight = 14.sp)
                    }
                }
            } else Text(names[tab], color = fg, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> Courses()
                1 -> CompositionLocalProvider(LocalContentColor provides Color.White) { Practice() }
                2 -> Stats()
                else -> SettingsScreen()
            }
        }
        Row(Modifier.fillMaxWidth().background(bar).height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            names.indices.forEach { i ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable { tab = i }, contentAlignment = Alignment.Center) {
                    TvIcon(i, if (tab == i) MaterialTheme.colorScheme.primary else Color.Gray)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("تم برنامه", style = MaterialTheme.typography.titleMedium)
        listOf("روشن", "تیره", "خودکار (مطابق سیستم)").forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().clickable { ThemeStore.mode = i; ctx.getSharedPreferences("app", 0).edit().putInt("theme", i).apply() }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(ThemeStore.mode == i, null); Text("  " + n)
            }
        }
        Text("نکته: صفحه‌ی چارت همیشه تیره نمایش داده می‌شود.", fontSize = 12.sp, color = Color.Gray)
        Text("منبع داده‌ی چارت", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        listOf("" to "خودکار (پیشنهادی)", "Binance" to "Binance", "OKX" to "OKX", "KuCoin" to "KuCoin", "Gate" to "Gate.io", "Yahoo" to "Yahoo Finance").forEach { (k, n) ->
            Row(Modifier.fillMaxWidth().clickable { ChartStore.pref = k; ChartStore.src = ""; ChartStore.key = ""; ChartStore.live = false; ctx.getSharedPreferences("app", 0).edit().putString("src", k).apply() }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(ChartStore.pref == k, null); Text("  " + n)
            }
        }
        Text("اگر نمادی بدون VPN به‌روز نمی‌شود، منبع دیگری را انتخاب کن.", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun Courses() {
    var ci by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COURSES.forEachIndexed { i, c ->
                Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.clickable { ci = i; sel = null },
                    color = if (i == ci) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                    Text(c.name, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
        val c = COURSES[ci]
        val o = sel
        if (o == null) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(c.desc, color = Color.Gray)
                c.lessons.forEachIndexed { li, l ->
                    val done = "$ci-$li" in Progress.done
                    val unlocked = li == 0 || "$ci-${li - 1}" in Progress.done
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = unlocked) { sel = li }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (done) "✅" else if (unlocked) "▶️" else "🔒")
                            Spacer(Modifier.width(12.dp)); Text(l.title)
                        }
                    }
                }
            }
        } else LessonView(c.lessons[o], "$ci-$o") { sel = null }
    }
}

@Composable
fun LessonView(l: LessonData, key: String, back: () -> Unit) {
    val ans = remember { mutableStateMapOf<Int, Int>() }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(back) { Text("← بازگشت") }
        Text(l.title, style = MaterialTheme.typography.titleLarge)
        if (l.fig.isNotEmpty()) Figure(l.fig)
        Text(l.text)
        l.quiz.forEachIndexed { qi, q ->
            Text("${qi + 1}. ${q.q}", style = MaterialTheme.typography.titleSmall)
            q.opts.forEachIndexed { oi, t ->
                val a = ans[qi]
                val col = when { a == null -> null; oi == q.ans -> Color(0xFF4CAF50); oi == a -> Color(0xFFFF5252); else -> null }
                OutlinedButton({ if (a == null) ans[qi] = oi }, Modifier.fillMaxWidth()) { Text(t, color = col ?: MaterialTheme.colorScheme.onSurface) }
            }
        }
        if (ans.size == l.quiz.size) {
            val s = l.quiz.indices.count { ans[it] == l.quiz[it].ans }
            val pass = s * 2 >= l.quiz.size
            Text("نمره: $s از ${l.quiz.size}", style = MaterialTheme.typography.titleMedium)
            Button({ Progress.scores[key] = s; if (pass && key !in Progress.done) Progress.done.add(key); back() }) { Text(if (pass) "تمام؛ درس بعدی" else "بازگشت") }
        }
    }
}

@Composable
fun Stats() {
    val total = COURSES.sumOf { it.lessons.size }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("درس‌های کامل‌شده: ${Progress.done.size} از $total", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { Progress.done.size / total.toFloat() }, modifier = Modifier.fillMaxWidth())
        COURSES.forEachIndexed { ci, c -> Text("${c.name}: ${c.lessons.indices.count { "$ci-$it" in Progress.done }} از ${c.lessons.size}") }
    }
}
