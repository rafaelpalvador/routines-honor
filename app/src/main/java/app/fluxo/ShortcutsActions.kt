package app.fluxo

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Base64
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

object Geo {
    fun parse(s: String?): Pair<Double, Double>? {
        val p = s.orEmpty().split(",").map { it.trim().toDoubleOrNull() }
        return if (p.size == 2 && p[0] != null && p[1] != null) p[0]!! to p[1]!! else null
    }

    @Suppress("MissingPermission")
    fun last(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java)
        return listOf("fused", "gps", "network", "passive").mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    fun dist(lat: Double, lon: Double, loc: Location): Float {
        val r = FloatArray(1)
        Location.distanceBetween(lat, lon, loc.latitude, loc.longitude, r)
        return r[0]
    }
}

/** Nascer e pôr do sol (algoritmo "Almanac"), em milissegundos UTC. */
object Sun {
    fun times(lat: Double, lon: Double, cal: Calendar = Calendar.getInstance()): Pair<Long, Long>? {
        val rise = calc(lat, lon, cal, true) ?: return null
        val set = calc(lat, lon, cal, false) ?: return null
        return rise to set
    }

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    private fun calc(lat: Double, lon: Double, cal: Calendar, rise: Boolean): Long? {
        val n = cal.get(Calendar.DAY_OF_YEAR)
        val lngHour = lon / 15.0
        val t = n + ((if (rise) 6 else 18) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634
        l = ((l % 360) + 360) % 360
        var ra = deg(atan(0.91764 * tan(rad(l))))
        ra = ((ra % 360) + 360) % 360
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(90.833)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rise) 360 - deg(acos(cosH)) else deg(acos(cosH))) / 15.0
        val tt = h + ra - 0.06571 * t - 6.622
        var ut = (tt - lngHour) % 24
        if (ut < 0) ut += 24
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = cal.timeInMillis
        utc.set(Calendar.HOUR_OF_DAY, 0); utc.set(Calendar.MINUTE, 0); utc.set(Calendar.SECOND, 0); utc.set(Calendar.MILLISECOND, 0)
        // o dia local pode ser o dia UTC anterior/seguinte: ajusta pela data local
        utc.set(Calendar.YEAR, cal.get(Calendar.YEAR)); utc.set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
        var ms = utc.timeInMillis + (ut * 3600000).toLong()
        val noon = cal.clone() as Calendar
        noon.set(Calendar.HOUR_OF_DAY, 12); noon.set(Calendar.MINUTE, 0)
        if (ms < noon.timeInMillis - 18 * 3600000L) ms += 24 * 3600000L
        if (ms > noon.timeInMillis + 18 * 3600000L) ms -= 24 * 3600000L
        return ms
    }
}

/** Avaliador de expressões simples: números, textos, true/false, + - * / %, comparações, && || !. */
object Expr {
    fun eval(src: String): Any = Parser(src.trim()).run { val v = or(); v }
    fun truthy(v: Any): Boolean = when (v) {
        is Boolean -> v
        is Double -> v != 0.0
        is String -> v.isNotEmpty() && v != "false" && v != "0"
        else -> false
    }

    private fun num(v: Any): Double? = when (v) {
        is Double -> v
        is Boolean -> if (v) 1.0 else 0.0
        is String -> v.trim().replace(",", ".").toDoubleOrNull()
        else -> null
    }

    fun fmt(v: Any): String = when (v) {
        is Double -> if (v == floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun at(t: String): Boolean { ws(); return s.startsWith(t, i) }
        fun or(): Any {
            var l = and()
            while (at("||")) { i += 2; val r = and(); l = truthy(l) || truthy(r) }
            return l
        }
        fun and(): Any {
            var l = cmp()
            while (at("&&")) { i += 2; val r = cmp(); l = truthy(l) && truthy(r) }
            return l
        }
        fun cmp(): Any {
            val l = add()
            for (op in listOf("==", "!=", "<=", ">=", "<", ">")) {
                if (at(op)) {
                    i += op.length
                    val r = add()
                    val a = num(l); val b = num(r)
                    val c = if (a != null && b != null) a.compareTo(b) else fmt(l).compareTo(fmt(r))
                    return when (op) { "==" -> c == 0; "!=" -> c != 0; "<=" -> c <= 0; ">=" -> c >= 0; "<" -> c < 0; else -> c > 0 }
                }
            }
            return l
        }
        fun add(): Any {
            var l = mul()
            while (true) {
                if (at("+")) { i++; val r = mul(); val a = num(l); val b = num(r); l = if (a != null && b != null) a + b else fmt(l) + fmt(r) }
                else if (at("-")) { i++; val r = mul(); l = (num(l) ?: 0.0) - (num(r) ?: 0.0) }
                else return l
            }
        }
        fun mul(): Any {
            var l = unary()
            while (true) {
                if (at("*")) { i++; l = (num(l) ?: 0.0) * (num(unary()) ?: 0.0) }
                else if (at("/")) { i++; val d = num(unary()) ?: 0.0; l = if (d == 0.0) 0.0 else (num(l) ?: 0.0) / d }
                else if (at("%")) { i++; val d = num(unary()) ?: 0.0; l = if (d == 0.0) 0.0 else (num(l) ?: 0.0) % d }
                else return l
            }
        }
        fun unary(): Any {
            ws()
            if (at("!") && !s.startsWith("!=", i)) { i++; return !truthy(unary()) }
            if (at("-")) { i++; return -(num(unary()) ?: 0.0) }
            return primary()
        }
        fun primary(): Any {
            ws()
            if (i >= s.length) return ""
            val c = s[i]
            if (c == '(') { i++; val v = or(); ws(); if (i < s.length && s[i] == ')') i++; return v }
            if (c == '"' || c == '\'') {
                val q = c; i++
                val st = i
                while (i < s.length && s[i] != q) i++
                val v = s.substring(st, i); if (i < s.length) i++
                return v
            }
            if (c.isDigit() || c == '.') {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                return s.substring(st, i).toDoubleOrNull() ?: 0.0
            }
            val st = i
            while (i < s.length && !s[i].isWhitespace() && s[i] !in "()&|!=<>+-*/%") i++
            val w = s.substring(st, i)
            return when (w) { "true" -> true; "false" -> false; else -> w }
        }
    }
}

/** Ações e condições no estilo "Atalhos". */
object ShortcutsActions {
    private fun s(p: Map<String, String>, k: String) = p[k].orEmpty()
    private fun t(p: Map<String, String>, k: String) = p[k].orEmpty().trim()

    private fun save(p: Map<String, String>, vars: MutableMap<String, String>, v: String): String {
        val n = t(p, "save")
        if (n.isNotEmpty()) vars[n] = v
        return if (n.isNotEmpty()) "{$n} = ${v.take(120)}" else "Resultado: ${v.take(120)}"
    }

    private fun items(v: String?, sep: String = ","): MutableList<String> =
        v.orEmpty().split(sep).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

    fun run(ctx: Context, id: String, p: Map<String, String>, vars: MutableMap<String, String>): String? = when (id) {
        "text_replace" -> save(p, vars, if (p["regex"] == "yes") s(p, "text").replace(Regex(s(p, "find")), s(p, "repl")) else s(p, "text").replace(s(p, "find"), s(p, "repl")))
        "text_split" -> {
            val parts = s(p, "text").split(s(p, "sep").ifEmpty { "," })
            val n = t(p, "index").toIntOrNull() ?: 1
            val v = if (n > 0) parts.getOrNull(n - 1) else parts.getOrNull(parts.size + n)
            save(p, vars, v.orEmpty())
        }
        "text_case" -> save(p, vars, when (p["mode"]) {
            "lower" -> s(p, "text").lowercase()
            "title" -> s(p, "text").lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            else -> s(p, "text").uppercase()
        })
        "text_trim" -> save(p, vars, s(p, "text").trim())
        "text_substring" -> {
            val st = ((t(p, "start").toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
            val len = t(p, "len").toIntOrNull() ?: 1
            val x = s(p, "text")
            save(p, vars, if (st >= x.length) "" else x.substring(st, (st + len).coerceAtMost(x.length)))
        }
        "text_length" -> save(p, vars, s(p, "text").length.toString())
        "text_match" -> save(p, vars, Regex(s(p, "regex")).find(s(p, "text"))?.value.orEmpty())
        "text_base64" -> save(p, vars, if (p["mode"] == "dec") String(Base64.decode(s(p, "text"), Base64.DEFAULT)) else Base64.encodeToString(s(p, "text").toByteArray(), Base64.NO_WRAP))
        "text_urlencode" -> save(p, vars, URLEncoder.encode(s(p, "text"), "UTF-8"))
        "text_hash" -> save(p, vars, MessageDigest.getInstance(p["algo"] ?: "SHA-256").digest(s(p, "text").toByteArray()).joinToString("") { "%02x".format(it) })

        "calc" -> save(p, vars, Expr.fmt(Expr.eval(s(p, "expr"))))
        "round" -> {
            val d = t(p, "digits").toIntOrNull() ?: 0
            val v = t(p, "value").replace(",", ".").toDoubleOrNull() ?: throw IllegalArgumentException("número inválido")
            save(p, vars, if (d <= 0) Math.round(v).toString() else "%.${d}f".format(Locale.US, v))
        }
        "random_number" -> {
            val a = t(p, "min").toIntOrNull() ?: 1
            val b = t(p, "max").toIntOrNull() ?: 10
            save(p, vars, (minOf(a, b)..maxOf(a, b)).random().toString())
        }
        "date_now" -> {
            val c = Calendar.getInstance()
            val n = t(p, "add").toIntOrNull() ?: 0
            c.add(when (p["unit"]) { "m" -> Calendar.MINUTE; "h" -> Calendar.HOUR_OF_DAY; else -> Calendar.DAY_OF_YEAR }, n)
            save(p, vars, SimpleDateFormat(s(p, "format").ifBlank { "dd/MM/yyyy HH:mm" }, Locale.getDefault()).format(Date(c.timeInMillis)))
        }
        "date_diff" -> {
            val target = Calendar.getInstance()
            val (h, m) = t(p, "time").ifBlank { "23:00" }.split(":").map { it.toInt() }
            target.set(Calendar.HOUR_OF_DAY, h); target.set(Calendar.MINUTE, m); target.set(Calendar.SECOND, 0)
            if (target.timeInMillis <= System.currentTimeMillis()) target.add(Calendar.DAY_OF_YEAR, 1)
            save(p, vars, ((target.timeInMillis - System.currentTimeMillis()) / 60000).toString())
        }

        "list_add" -> { val l = items(vars[t(p, "list")]); l.add(s(p, "item")); vars[t(p, "list")] = l.joinToString(","); "Lista ${t(p, "list")}: ${l.size} item(ns)" }
        "list_remove" -> { val l = items(vars[t(p, "list")]); l.remove(s(p, "item").trim()); vars[t(p, "list")] = l.joinToString(","); "Lista ${t(p, "list")}: ${l.size} item(ns)" }
        "list_get" -> {
            val l = items(vars[t(p, "list")])
            val idx = t(p, "index").toIntOrNull()
            save(p, vars, (if (idx == null) l.randomOrNull() else if (idx > 0) l.getOrNull(idx - 1) else l.getOrNull(l.size + idx)).orEmpty())
        }
        "list_count" -> save(p, vars, items(vars[t(p, "list")]).size.toString())
        "var_increment" -> {
            val cur = vars[t(p, "name")]?.toDoubleOrNull() ?: 0.0
            val v = cur + (t(p, "by").replace(",", ".").toDoubleOrNull() ?: 1.0)
            vars[t(p, "name")] = Expr.fmt(v); "{${t(p, "name")}} = ${Expr.fmt(v)}"
        }
        "var_clear" -> { vars.remove(t(p, "name")); "Variável apagada" }

        "location_get" -> {
            val l = Geo.last(ctx) ?: throw IllegalStateException("sem localização (permita Localização na tela Configuração)")
            save(p, vars, "%.5f,%.5f".format(Locale.US, l.latitude, l.longitude))
        }
        else -> null
    }

    fun cond(ctx: Context, type: String, p: Map<String, String>, vars: Map<String, String>): Boolean? = when (type) {
        "compare" -> {
            val a = s(p, "left").trim(); val b = s(p, "right").trim()
            val na = a.replace(",", ".").toDoubleOrNull(); val nb = b.replace(",", ".").toDoubleOrNull()
            fun c(): Int = if (na != null && nb != null) na.compareTo(nb) else a.compareTo(b, ignoreCase = true)
            when (p["op"]) {
                "==" -> c() == 0
                "!=" -> c() != 0
                ">" -> c() > 0
                "<" -> c() < 0
                ">=" -> c() >= 0
                "<=" -> c() <= 0
                "starts" -> a.startsWith(b, ignoreCase = true)
                "ends" -> a.endsWith(b, ignoreCase = true)
                "regex" -> Regex(b).containsMatchIn(a)
                "empty" -> a.isEmpty()
                "notempty" -> a.isNotEmpty()
                else -> a.contains(b, ignoreCase = true)
            }
        }
        "expression" -> Expr.truthy(Expr.eval(s(p, "expr")))
        "daylight" -> {
            val g = Geo.parse(p["loc"])
            val tm = g?.let { Sun.times(it.first, it.second) }
            val now = System.currentTimeMillis()
            val day = tm != null && now >= tm.first && now < tm.second
            if (p["state"] == "no") !day else day
        }
        "at_place" -> {
            val g = Geo.parse(p["loc"])
            val l = Geo.last(ctx)
            val inside = g != null && l != null && Geo.dist(g.first, g.second, l) <= (t(p, "radius").toFloatOrNull() ?: 150f)
            if (p["state"] == "no") !inside else inside
        }
        else -> null
    }
}
