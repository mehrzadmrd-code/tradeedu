package com.tradeedu

import kotlin.math.*

data class Candle(val o: Double, val h: Double, val l: Double, val c: Double, val v: Double = 0.0, val t: Long = 0L)
data class Pivot(val i: Int, val price: Double, val high: Boolean)
data class Level(val price: Double, val touches: Int, val lo: Double, val hi: Double)
data class Feedback(val ok: Boolean, val msg: String)

sealed class Drawn {
    data class HLine(val price: Double) : Drawn()
    data class TLine(val i1: Double, val p1: Double, val i2: Double, val p2: Double) : Drawn()
    data class Fib(val i1: Double, val p1: Double, val i2: Double, val p2: Double) : Drawn()
    data class VLine(val i: Double) : Drawn()
    data class Zone(val i1: Double, val p1: Double, val i2: Double, val p2: Double) : Drawn()
}

fun sampleCandles(n: Int = 120, seed: Int = 7): List<Candle> {
    val r = java.util.Random(seed.toLong()); var p = 100.0
    return List(n) { i ->
        val o = p; val c = o + sin(i / 12.0) * 0.9 + r.nextGaussian() * 0.9
        val h = max(o, c) + r.nextDouble() * 0.7; val l = min(o, c) - r.nextDouble() * 0.7
        p = c; Candle(o, h, l, c, 50 + r.nextDouble() * 100, 1_700_000_000_000L + i * 3_600_000L)
    }
}

class Analysis(private val cs: List<Candle>) {
    val atr = cs.map { it.h - it.l }.average().coerceAtLeast(1e-9)
    val tol = atr * 0.5

    val pivots: List<Pivot> = run {
        val k = 5; val r = mutableListOf<Pivot>()
        for (i in k until cs.size - k) {
            val w = i - k..i + k
            if (w.all { cs[i].h >= cs[it].h }) r += Pivot(i, cs[i].h, true)
            if (w.all { cs[i].l <= cs[it].l }) r += Pivot(i, cs[i].l, false)
        }
        r
    }

    val levels: List<Level> = run {
        val out = mutableListOf<Level>(); var g = mutableListOf<Double>()
        fun flush() { if (g.size >= 2) out += Level(g.average(), g.size, g.min(), g.max()) }
        for (p in pivots.map { it.price }.sorted()) {
            if (g.isNotEmpty() && p - g.average() > tol) { flush(); g = mutableListOf() }
            g += p
        }
        flush()
        out.sortedByDescending { it.touches }
    }

    private fun f1(v: Double) = f2(v, 1)
    private fun idx(i: Double) = i.roundToInt().coerceIn(0, cs.size - 1)

    fun grade(ls: List<Drawn>): List<Feedback> {
        val r = ls.map { d ->
            when (d) {
                is Drawn.HLine -> gradeH(d)
                is Drawn.TLine -> gradeTrend(d)
                is Drawn.Zone -> gradeZone(d)
                is Drawn.Fib -> gradeFib(d)
                is Drawn.VLine -> Feedback(true, "ⓘ خط عمودی فقط برای علامت‌گذاری زمان است و تصحیح نمی‌شود.")
            }
        }.toMutableList()
        val covered = { lv: Level ->
            ls.any { (it is Drawn.HLine && abs(it.price - lv.price) <= tol) || (it is Drawn.Zone && lv.price in min(it.p1, it.p2) - tol..max(it.p1, it.p2) + tol) }
        }
        var n = 0
        for (lv in levels.take(4)) {
            if (n < 2 && !covered(lv)) { r += Feedback(false, "سطح مهم ${fp(lv.price)} (${lv.touches} برخورد) را رسم نکرده‌ای."); n++ }
        }
        return r
    }

    private fun gradeH(d: Drawn.HLine): Feedback {
        val near = levels.minByOrNull { abs(it.price - d.price) }
            ?: return Feedback(false, "در این بازه هیچ سطح معتبری پیدا نشد؛ تایم‌فریم را عوض کن یا چارت را جابه‌جا کن.")
        val dist = abs(near.price - d.price); val role = if (d.price < cs.last().c) "حمایت" else "مقاومت"
        val dir = if (near.price > d.price) "بالاتر" else "پایین‌تر"
        return if (dist <= tol) Feedback(true, "$role درست؛ ${near.touches} برخورد دارد و خط ${f1(dist / atr)} برابر دامنه‌ی کندل با مرکز ناحیه فاصله دارد.")
        else if (dist <= tol * 3) Feedback(false, "نزدیک است ولی دقیق نیست؛ نزدیک‌ترین سطح معتبر ${fp(near.price)} است و ${f1(dist / atr)} برابر دامنه‌ی کندل $dir قرار دارد.")
        else Feedback(false, "این خط به هیچ ناحیه‌ی معتبری (حداقل ۲ سقف یا کف نزدیک هم) وصل نیست؛ نزدیک‌ترین سطح ${fp(near.price)} است.")
    }

    private fun gradeTrend(d: Drawn.TLine): Feedback {
        val slope = (d.p2 - d.p1) / (d.i2 - d.i1)
        fun at(i: Double) = d.p1 + slope * (i - d.i1)
        val a = idx(floor(min(d.i1, d.i2))); val b = idx(ceil(max(d.i1, d.i2)))
        val t = pivots.filter { it.i in a - 1..b + 1 && abs(at(it.i.toDouble()) - it.price) <= tol }
        val lows = t.count { !it.high }; val highs = t.count { it.high }
        val support = lows >= highs; val touches = max(lows, highs)
        val kind = if (support) (if (slope > 0) "خط روند صعودی" else "خط حمایت") else (if (slope < 0) "خط روند نزولی" else "خط مقاومت")
        if (touches < 2) return Feedback(false, "$kind: فقط $touches نقطه‌ی سقف/کف روی خط است؛ حداقل ۲ نقطه لازم است. دو سر خط را روی دو کف (برای صعودی) یا دو سقف (برای نزولی) بگذار.")
        var br = 0; var cut = 0
        for (i in a..b) {
            val v = at(i.toDouble()); val c = cs[i]
            if (if (support) c.c < v - tol * 0.3 else c.c > v + tol * 0.3) br++
            if (min(c.o, c.c) < v - tol * 0.2 && max(c.o, c.c) > v + tol * 0.2) cut++
        }
        if (br > 1) return Feedback(false, "$kind: قیمت $br بار خلاف خط بسته شده؛ خط شکسته شده و روند معتبری نیست. خط را روی سایه‌های بیرونی‌تر بکش.")
        if (cut > 2) return Feedback(false, "$kind: خط از وسط بدنه‌ی $cut کندل رد شده؛ خط روند باید روی سایه‌ها بنشیند نه داخل بدنه‌ها.")
        return Feedback(true, "$kind درست؛ $touches برخورد" + if (touches >= 3) " (اعتبار بالا)." else "؛ برخورد سوم اعتبار آن را بالاتر می‌برد.")
    }

    private fun gradeZone(d: Drawn.Zone): Feedback {
        val lo = min(d.p1, d.p2); val hi = max(d.p1, d.p2); val w = hi - lo
        val inside = levels.filter { it.price in lo - tol * 0.5..hi + tol * 0.5 }
        val pv = pivots.count { it.price in lo..hi }
        return if (inside.isEmpty() && pv < 2) Feedback(false, "ناحیه: هیچ سطح معتبری (حداقل ۲ برخورد) داخل این ناحیه نیست؛ ناحیه را روی خوشه‌ی سقف‌ها یا کف‌ها بگذار.")
        else if (w > atr * 3) Feedback(false, "ناحیه خیلی پهن است (${f1(w / atr)} برابر دامنه‌ی کندل)؛ ناحیه‌ی خوب حداکثر ۳ برابر دامنه‌ی کندل است.")
        else if (w < tol * 0.5) Feedback(false, "ناحیه خیلی باریک است؛ باید سایه‌ها و بدنه‌ی برخوردها را بپوشاند، نه فقط یک خط.")
        else Feedback(true, "ناحیه درست؛ $pv سقف/کف را می‌پوشاند و عرضش ${f1(w / atr)} برابر دامنه‌ی کندل است.")
    }

    private fun gradeFib(d: Drawn.Fib): Feedback {
        val a = idx(floor(min(d.i1, d.i2))); val b = idx(ceil(max(d.i1, d.i2)))
        val top = max(d.p1, d.p2); val bot = min(d.p1, d.p2)
        val segHi = (a..b).maxOf { cs[it].h }; val segLo = (a..b).minOf { cs[it].l }
        if (top - bot < atr * 3) return Feedback(false, "فیبوناچی: حرکت انتخاب‌شده خیلی کوچک است؛ یک موج مشخص (حداقل ۳ برابر دامنه‌ی کندل) انتخاب کن.")
        if (segHi - top > tol * 1.5) return Feedback(false, "فیبوناچی: بالاترین نقطه‌ی بین دو سر ${fp(segHi)} است و بالاتر از سقف انتخابی توست. نقطه‌ی بالا را روی سقف واقعی موج بگذار.")
        if (bot - segLo > tol * 1.5) return Feedback(false, "فیبوناچی: پایین‌ترین نقطه‌ی بین دو سر ${fp(segLo)} است و پایین‌تر از کف انتخابی توست. نقطه‌ی پایین را روی کف واقعی موج بگذار.")
        val first = if (d.i1 <= d.i2) d.p1 else d.p2
        val up = first < (top + bot) / 2
        return Feedback(true, "فیبوناچی درست؛ دو سر روی سقف و کف واقعی یک ${if (up) "موج صعودی" else "موج نزولی"} به اندازه‌ی ${fp(top - bot)} قرار دارد.")
    }
}
