package app.fluxo

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** Avalia condições ("SE"). */
object Conditions {
    fun eval(ctx: Context, c: Cond, vars: Map<String, String>): Boolean {
        if (c.items.isEmpty()) return true
        val rs = c.items.map { leaf(ctx, it, vars) }
        return if (c.op == "or") rs.any { it } else rs.all { it }
    }

    private fun leaf(ctx: Context, l: CondLeaf, vars: Map<String, String>): Boolean {
        val r = runCatching { raw(ctx, l.type, l.params, vars) }.getOrDefault(false)
        return if (l.negate) !r else r
    }

    private fun yes(p: Map<String, String>, actual: Boolean) = if (p["state"] == "no") !actual else actual

    private fun raw(ctx: Context, type: String, p: Map<String, String>, vars: Map<String, String>): Boolean = when (type) {
        "battery" -> {
            val lvl = Dev.battery(ctx)
            val x = p["level"]?.trim()?.toIntOrNull() ?: 0
            if (p["op"] == "above") lvl > x else lvl < x
        }
        "charging" -> yes(p, Dev.charging(ctx))
        "wifi" -> {
            val ssid = Dev.wifiSsid(ctx)
            val want = p["network"].orEmpty().trim()
            val match = if (want.isBlank()) ssid != null else ssid == want
            yes(p, match)
        }
        "bluetooth" -> yes(p, Dev.btConnected(ctx, p["device"].orEmpty().trim()))
        "time_range" -> {
            val c = Calendar.getInstance()
            val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            val a = Dev.minutes(p["from"] ?: "00:00")
            val b = Dev.minutes(p["to"] ?: "00:00")
            if (a <= b) now in a until b else (now >= a || now < b)
        }
        "weekday" -> (p["days"] ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }.contains(Dev.isoDay())
        "headphones" -> yes(p, Dev.headphones(ctx))
        "airplane" -> yes(p, Dev.airplane(ctx))
        "dark_mode" -> yes(p, Dev.night(ctx))
        "power_save" -> yes(p, Dev.powerSave(ctx))
        "variable" -> {
            val a = vars[p["name"].orEmpty().trim()].orEmpty()
            val b = p["value"].orEmpty()
            val na = a.toDoubleOrNull()
            val nb = b.toDoubleOrNull()
            when (p["op"]) {
                "!=" -> a != b
                ">" -> na != null && nb != null && na > nb
                "<" -> na != null && nb != null && na < nb
                else -> if (na != null && nb != null) na == nb else a == b
            }
        }
        else -> false
    }
}

/** Roda rotinas e decide quais rotinas disparam a cada evento. */
object Engine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = ConcurrentHashMap.newKeySet<String>()
    val vars = ConcurrentHashMap<String, String>()

    /** Chamado pelo EngineService quando algo acontece no aparelho. */
    fun onEvent(ctx: Context, type: String, data: Map<String, String>) {
        Store.routines.value.filter { it.enabled }.forEach { r ->
            if (r.triggers.any { matches(it, type, data) }) run(ctx, r, "gatilho: ${Catalog.find(Kind.TRIGGER, type)?.title ?: type}")
        }
    }

    private fun matches(t: TriggerDef, type: String, d: Map<String, String>): Boolean {
        if (t.type != type) return false
        val p = t.params
        return when (type) {
            "wifi", "bluetooth" -> {
                val want = p[if (type == "wifi") "network" else "device"].orEmpty().trim()
                p["when"] == d["state"] && (want.isBlank() || want == d["name"])
            }
            "battery" -> {
                val x = p["level"]?.trim()?.toIntOrNull() ?: return false
                val n = d["level"]?.toIntOrNull() ?: return false
                val pr = d["prev"]?.toIntOrNull() ?: return false
                if (p["when"] == "above") (pr < x && n >= x) else (pr >= x && n < x)
            }
            else -> p["when"] == d["state"]
        }
    }

    fun run(ctx: Context, r: Routine, why: String) {
        val app = ctx.applicationContext
        if (!running.add(r.id)) {
            RunLog.add("⏭ ${r.name}: já está rodando")
            return
        }
        scope.launch {
            RunLog.add("▶ ${r.name} ($why)")
            try {
                runSteps(app, r.steps)
                RunLog.add("✔ ${r.name} terminou")
            } catch (e: StopSignal) {
                RunLog.add("⏹ ${r.name} parou")
            } catch (e: Exception) {
                RunLog.add("✗ ${r.name}: ${e.message}")
            } finally {
                running.remove(r.id)
            }
        }
    }

    private suspend fun runSteps(ctx: Context, steps: List<Step>) {
        for (s in steps) {
            if (s.kind == "if") {
                val ok = Conditions.eval(ctx, s.cond, vars)
                RunLog.add("   SE → ${if (ok) "verdadeiro" else "falso"}")
                runSteps(ctx, if (ok) s.then else s.otherwise)
            } else {
                val title = Catalog.find(Kind.ACTION, s.type)?.title ?: s.type
                try {
                    val msg = Actions.run(ctx, s.type, s.params.mapValues { sub(ctx, it.value) }, vars)
                    RunLog.add("   ✓ $msg")
                } catch (e: StopSignal) {
                    throw e
                } catch (e: Exception) {
                    RunLog.add("   ✗ $title: ${e.message}")
                }
            }
        }
    }

    /** Troca {bateria}, {hora}, {wifi} e {suaVariavel} pelos valores atuais. */
    private fun sub(ctx: Context, s: String): String {
        if (!s.contains('{')) return s
        return Regex("\\{(\\w+)\\}").replace(s) { m ->
            when (val k = m.groupValues[1]) {
                "bateria" -> runCatching { Dev.battery(ctx).toString() }.getOrDefault("?")
                "hora" -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                "wifi" -> Dev.wifiSsid(ctx) ?: ""
                else -> vars[k] ?: m.value
            }
        }
    }
}
