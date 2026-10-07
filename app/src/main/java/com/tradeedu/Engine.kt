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

class Analysis(val cs: List<Candle>) {
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

    fun auto(I: Inds): TA {
        val a = cs.size - 1; val last = cs[a].c
        val hs = pivots.filter { it.high }.takeLast(2); val ls = pivots.filter { !it.high }.takeLast(2)
        val hh = hs.size == 2 && hs[1].price > hs[0].price + tol * 0.3; val lh = hs.size == 2 && hs[1].price < hs[0].price - tol * 0.3
        val hl = ls.size == 2 && ls[1].price > ls[0].price + tol * 0.3; val ll = ls.size == 2 && ls[1].price < ls[0].price - tol * 0.3
        val up = hh && hl; val down = lh && ll
        val trend = if (up) "صعودی (سقف و کف بالاتر)" else if (down) "نزولی (سقف و کف پایین‌تر)" else "خنثی / رنج"
        val sup = levels.filter { it.price < last }.sortedByDescending { it.price }.take(2)
        val rs = levels.filter { it.price > last }.sortedBy { it.price }.take(2)
        val pts = if (up) ls else if (down) hs else emptyList()
        var line: Drawn.TLine? = null
        if (pts.size == 2 && pts[1].i > pts[0].i) {
            val d = Drawn.TLine(pts[0].i.toDouble(), pts[0].price, pts[1].i.toDouble(), pts[1].price)
            if (gradeTrend(d).ok) { val s = (d.p2 - d.p1) / (d.i2 - d.i1); line = Drawn.TLine(d.i1, d.p1, a.toDouble(), d.p2 + s * (a - d.i2)) }
        }
        val c = cs[a]; val p = cs[a - 1]; val body = abs(c.c - c.o); val rg = (c.h - c.l).coerceAtLeast(1e-9)
        val uw = c.h - max(c.o, c.c); val lw = min(c.o, c.c) - c.l
        val notes = mutableListOf<String>(); var score = 0
        if (up) score += 2 else if (down) score -= 2
        if (c.c > c.o && p.c < p.o && c.c >= p.o && c.o <= p.c) { notes += "انگالف صعودی روی آخرین کندل"; score++ }
        if (c.c < c.o && p.c > p.o && c.c <= p.o && c.o >= p.c) { notes += "انگالف نزولی روی آخرین کندل"; score-- }
        if (body > 0 && lw >= 2 * body && uw <= body) { notes += "چکش/پین‌بار صعودی (سایه‌ی پایین بلند)"; score++ }
        if (body > 0 && uw >= 2 * body && lw <= body) { notes += "ستاره‌ی دنباله‌دار/پین‌بار نزولی (سایه‌ی بالا بلند)"; score-- }
        if (body <= rg * 0.1) notes += "دوجی؛ نشانه‌ی تردید بازار"
        val e = I.e50[a]; val hist = I.mc.third; val rsi = I.rs[a]
        if (last > e) { notes += "قیمت بالای EMA50 (فشار خریدار)"; score++ } else { notes += "قیمت زیر EMA50 (فشار فروشنده)"; score-- }
        if (hist[a] > 0 && hist[a - 1] <= 0) { notes += "تقاطع صعودی MACD"; score++ }
        else if (hist[a] < 0 && hist[a - 1] >= 0) { notes += "تقاطع نزولی MACD"; score-- }
        else if (hist[a] > 0) score++ else score--
        if (!rsi.isNaN()) {
            if (rsi > 70) { notes += "RSI ${f1(rsi)}: اشباع خرید؛ احتمال اصلاح"; score-- }
            else if (rsi < 30) { notes += "RSI ${f1(rsi)}: اشباع فروش؛ احتمال برگشت"; score++ }
        }
        val bias = if (score >= 3) "صعودی" else if (score <= -3) "نزولی" else "خنثی"
        val s1 = sup.firstOrNull(); val r1 = rs.firstOrNull()
        var rrOk = false
        val plan = if (bias == "صعودی" && s1 != null && r1 != null) {
            val sl = s1.lo - atr * 0.5; val rr = (r1.price - s1.price) / (s1.price - sl).coerceAtLeast(1e-9); rrOk = rr >= 1.5
            "سناریوی آموزشی: در پولبک به حمایت ${fp(s1.price)} و با تأیید کندلی خرید؛ حد ضرر ${fp(sl)}، هدف ${fp(r1.price)}، نسبت ریسک به ریوارد ${f1(rr)}."
        } else if (bias == "نزولی" && s1 != null && r1 != null) {
            val sl = r1.hi + atr * 0.5; val rr = (r1.price - s1.price) / (sl - r1.price).coerceAtLeast(1e-9); rrOk = rr >= 1.5
            "سناریوی آموزشی: در پولبک به مقاومت ${fp(r1.price)} و با تأیید کندلی فروش؛ حد ضرر ${fp(sl)}، هدف ${fp(s1.price)}، نسبت ریسک به ریوارد ${f1(rr)}."
        } else "سناریو: تمایل مشخصی نیست؛ منتظر شکست معتبر یا تأیید بمان و وارد نشو."
        val dir = if (bias == "صعودی") 1 else if (bias == "نزولی") -1 else 0
        val near = (sup + rs).minOfOrNull { abs(it.price - last) }
        val checks = listOf(
            (up || down) to "ساختار بازار مشخص است (سقف و کف‌های پایدار)",
            (dir > 0 && up || dir < 0 && down) to "جهت تحلیل هم‌جهت با روند اصلی است",
            (near != null && near <= atr * 2) to "قیمت نزدیک یک سطح معتبر است (ورود دور از سطح ریسک بالایی دارد)",
            (dir != 0 && (dir > 0) == (last > e) && (dir > 0) == (hist[a] > 0)) to "EMA50 و MACD با جهت تحلیل هم‌سو هستند",
            rrOk to "نسبت ریسک به ریوارد حداقل ۱.۵ است")
        return TA(trend, bias, sup, rs, line, notes, checks, plan)
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

data class TA(val trend: String, val bias: String, val supports: List<Level>, val resists: List<Level>, val line: Drawn.TLine?, val notes: List<String>, val checks: List<Pair<Boolean, String>>, val plan: String)
