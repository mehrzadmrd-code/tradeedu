package com.tradeedu

import kotlin.math.*

data class Candle(val o: Double, val h: Double, val l: Double, val c: Double)
data class Pivot(val i: Int, val price: Double, val high: Boolean)
data class Level(val price: Double, val touches: Int)
data class Feedback(val ok: Boolean, val msg: String)

sealed class Drawn {
    data class HLine(val price: Double) : Drawn()
    data class TLine(val i1: Double, val p1: Double, val i2: Double, val p2: Double) : Drawn()
}

fun sampleCandles(n: Int = 120, seed: Int = 7): List<Candle> {
    val r = java.util.Random(seed.toLong()); var p = 100.0
    return List(n) { i ->
        val o = p; val c = o + sin(i / 12.0) * 0.9 + r.nextGaussian() * 0.9
        val h = max(o, c) + r.nextDouble() * 0.7; val l = min(o, c) - r.nextDouble() * 0.7
        p = c; Candle(o, h, l, c)
    }
}

class Analysis(private val cs: List<Candle>) {
    val tol = cs.map { it.h - it.l }.average() * 0.6

    val pivots: List<Pivot> = run {
        val k = 4; val r = mutableListOf<Pivot>()
        for (i in k until cs.size - k) {
            val w = i - k..i + k
            if (w.all { cs[i].h >= cs[it].h }) r += Pivot(i, cs[i].h, true)
            if (w.all { cs[i].l <= cs[it].l }) r += Pivot(i, cs[i].l, false)
        }
        r
    }

    val levels: List<Level> = run {
        val out = mutableListOf<Level>(); var g = mutableListOf<Double>()
        for (p in pivots.map { it.price }.sorted()) {
            if (g.isNotEmpty() && p - g.average() > tol) {
                if (g.size >= 2) out += Level(g.average(), g.size)
                g = mutableListOf()
            }
            g += p
        }
        if (g.size >= 2) out += Level(g.average(), g.size)
        out.sortedByDescending { it.touches }
    }

    fun grade(ls: List<Drawn>): List<Feedback> {
        val r = ls.map { d ->
            when (d) {
                is Drawn.HLine -> {
                    val near = levels.minByOrNull { abs(it.price - d.price) }
                    if (near != null && abs(near.price - d.price) <= tol)
                        Feedback(true, "سطح افقی درست است؛ ${near.touches} برخورد دارد")
                    else Feedback(false, "این سطح به هیچ ناحیه‌ی معتبری (حداقل ۲ سقف یا کف نزدیک هم) وصل نیست")
                }
                is Drawn.TLine -> gradeTrend(d)
            }
        }.toMutableList()
        levels.firstOrNull()?.let { top ->
            if (ls.none { it is Drawn.HLine && abs(it.price - top.price) <= tol }) {
                val s = "%.1f".format(top.price)
                r += Feedback(false, "مهم‌ترین سطح چارت (حدود $s) را جا انداخته‌ای")
            }
        }
        return r
    }

    private fun gradeTrend(d: Drawn.TLine): Feedback {
        val s = (d.p2 - d.p1) / (d.i2 - d.i1)
        fun at(i: Double) = d.p1 + s * (i - d.i1)
        val a = min(d.i1, d.i2); val b = max(d.i1, d.i2)
        val t = pivots.filter { abs(at(it.i.toDouble()) - it.price) <= tol }
        val touches = maxOf(t.count { it.high }, t.count { !it.high })
        if (touches < 2) return Feedback(false, "خط روند فقط از $touches نقطه‌ی سقف/کف عبور می‌کند؛ حداقل ۲ نقطه لازم است")
        val cut = cs.indices.count { i ->
            i >= a && i <= b && at(i.toDouble()).let { y ->
                y > min(cs[i].o, cs[i].c) + tol * 0.2 && y < max(cs[i].o, cs[i].c) - tol * 0.2
            }
        }
        if (cut > 2) return Feedback(false, "خط از وسط بدنه‌ی $cut کندل رد شده است؛ خط روند باید روی سایه‌ها بنشیند")
        return Feedback(true, "خط روند درست است؛ $touches برخورد دارد")
    }
}
