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

val TgScheme = darkColorScheme(
    primary = Color(0xFF5EB5F7), onPrimary = Color(0xFF0E1621),
    background = Color(0xFF0E1621), onBackground = Color.White,
    surface = Color(0xFF17212B), onSurface = Color.White,
    secondaryContainer = Color(0xFF2B5278)
)

@Composable
fun App() {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("دوره‌ها", "تمرین چارت", "آمار پیشرفت")
    val icons = listOf("📚", "📈", "📊")
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            Text("TradeEdu", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
            Text("درس‌های کامل‌شده: ${Progress.done.size}", Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            names.forEachIndexed { i, t ->
                NavigationDrawerItem(label = { Text(icons[i] + "  " + t) }, selected = tab == i,
                    onClick = { tab = i; scope.launch { drawer.close() } }, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }) {
        Scaffold(bottomBar = {
            NavigationBar {
                names.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(icons[i]) }, label = { Text(t, maxLines = 1) })
                }
            }
        }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ scope.launch { drawer.open() } }) { Text("☰", style = MaterialTheme.typography.titleLarge) }
                    Text(names[tab], style = MaterialTheme.typography.titleMedium)
                }
                when (tab) { 0 -> Courses(); 1 -> Practice(); else -> Stats() }
            }
        }
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
