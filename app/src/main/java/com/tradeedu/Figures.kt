package com.tradeedu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun Figure(k: String) {
    val g = Color(0xFF26A69A); val r = Color(0xFFEF5350); val y = Color(0xFFFFC107); val s = Color(0xFF90A4AE)
    Canvas(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFF12161C), RoundedCornerShape(12.dp))) {
        val w = size.width; val h = size.height
        fun cd(fx: Float, o: Float, c: Float, hi: Float, lo: Float, col: Color) {
            val x = w * fx
            drawLine(col, Offset(x, h * hi), Offset(x, h * lo), 4f)
            drawRect(col, Offset(x - 16f, h * min(o, c)), Size(32f, max(h * abs(o - c), 3f)))
        }
        fun poly(p: List<Pair<Float, Float>>) {
            for (i in 1 until p.size) drawLine(Color.White, Offset(w * p[i - 1].first, h * p[i - 1].second), Offset(w * p[i].first, h * p[i].second), 5f)
        }
        when (k) {
            "candle" -> { cd(.3f, .75f, .3f, .15f, .9f, g); cd(.7f, .3f, .75f, .15f, .9f, r) }
            "types" -> {
                cd(.12f, .8f, .2f, .2f, .8f, g); cd(.31f, .5f, .52f, .15f, .85f, s); cd(.5f, .3f, .2f, .18f, .85f, g)
                cd(.69f, .7f, .8f, .15f, .82f, r); cd(.88f, .45f, .55f, .2f, .8f, s)
            }
            "sr" -> {
                drawLine(r, Offset(0f, h * .2f), Offset(w, h * .2f), 4f); drawLine(g, Offset(0f, h * .68f), Offset(w, h * .68f), 4f)
                poly(listOf(.05f to .68f, .2f to .2f, .35f to .68f, .5f to .2f, .65f to .68f, .8f to .2f, .95f to .55f))
            }
            "trend" -> {
                drawLine(y, Offset(w * .05f, h * .8f), Offset(w * .95f, h * .32f), 4f)
                poly(listOf(.05f to .8f, .2f to .45f, .32f to .65f, .5f to .28f, .62f to .5f, .8f to .15f, .92f to .3f))
            }
        }
    }
}
