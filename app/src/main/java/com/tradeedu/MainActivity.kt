package com.tradeedu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = TgScheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) { App() }
                }
            }
        }
    }
}

@Composable
fun Tab(t: String, on: Boolean, m: Modifier, f: () -> Unit) {
    val p = PaddingValues(4.dp); if (on) Button(f, m, contentPadding = p) { Text(t, maxLines = 1, fontSize = 13.sp) } else OutlinedButton(f, m, contentPadding = p) { Text(t, maxLines = 1, fontSize = 13.sp) }
}

@Composable
fun Practice() {
    val cs = remember { sampleCandles() }
    val an = remember { Analysis(cs) }
    val n = cs.size
    val top = cs.maxOf { it.h }; val bot = cs.minOf { it.l }
    val pad = (top - bot) * 0.05; val hi = top + pad; val lo = bot - pad
    var mode by remember { mutableIntStateOf(1) }
    val lines = remember { mutableStateListOf<Drawn>() }
    var pending by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var res by remember { mutableStateOf<List<Feedback>>(emptyList()) }
    var graded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("تمرین ۱: حمایت/مقاومت و خط روند را رسم کن", style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp).background(Color(0xFF12161C))
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val idx = p.x / size.width * n - 0.5
                        val price = hi - p.y / size.height * (hi - lo)
                        graded = false; res = emptyList()
                        if (mode == 1) lines.add(Drawn.HLine(price))
                        else {
                            val a = pending
                            if (a == null) pending = idx to price
                            else {
                                if (abs(idx - a.first) >= 3) lines.add(Drawn.TLine(a.first, a.second, idx, price))
                                pending = null
                            }
                        }
                    }
                }
        ) {
            val w = size.width; val h = size.height; val cw = w / n
            fun y(p: Double) = ((hi - p) / (hi - lo) * h).toFloat()
            fun x(i: Double) = ((i + 0.5) * cw).toFloat()
            cs.forEachIndexed { i, c ->
                val col = if (c.c >= c.o) Color(0xFF26A69A) else Color(0xFFEF5350)
                drawLine(col, Offset(x(i.toDouble()), y(c.h)), Offset(x(i.toDouble()), y(c.l)), 2f)
                val t = y(max(c.o, c.c)); val b = y(min(c.o, c.c))
                drawRect(col, Offset(x(i.toDouble()) - cw * 0.3f, t), Size(cw * 0.6f, max(b - t, 2f)))
            }
            lines.forEachIndexed { k, d ->
                val col = if (!graded) Color(0xFFFFC107)
                else if (res.getOrNull(k)?.ok == true) Color(0xFF4CAF50) else Color(0xFFFF5252)
                when (d) {
                    is Drawn.HLine -> drawLine(col, Offset(0f, y(d.price)), Offset(w, y(d.price)), 4f)
                    is Drawn.TLine -> {
                        val s = (d.p2 - d.p1) / (d.i2 - d.i1)
                        fun at(i: Double) = d.p1 + s * (i - d.i1)
                        drawLine(col, Offset(x(-0.5), y(at(-0.5))), Offset(x(n - 0.5), y(at(n - 0.5))), 4f)
                    }
                }
            }
            pending?.let { drawCircle(Color.White, 10f, Offset(x(it.first), y(it.second))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tab("خط افقی", mode == 1, Modifier.weight(1f)) { mode = 1; pending = null }
            Tab("خط روند", mode == 2, Modifier.weight(1f)) { mode = 2; pending = null }
            Tab("پاک", false, Modifier.weight(1f)) { lines.clear(); res = emptyList(); graded = false; pending = null }
            Button({ res = an.grade(lines.toList()); graded = true }, Modifier.weight(1f), enabled = lines.isNotEmpty(), contentPadding = PaddingValues(4.dp)) { Text("تصحیح", maxLines = 1, fontSize = 13.sp) }
        }
        if (graded) {
            Text("نمره: ${res.count { it.ok }} از ${lines.size}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                res.forEach { Text((if (it.ok) "✅ " else "❌ ") + it.msg, Modifier.padding(top = 4.dp)) }
            }
        } else Text(if (mode == 2) "برای خط روند دو نقطه را لمس کن" else "برای خط افقی یک نقطه را لمس کن", Modifier.padding(top = 8.dp))
    }
}
