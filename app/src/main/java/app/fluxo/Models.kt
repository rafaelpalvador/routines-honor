package app.fluxo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Um gatilho ("Quando"). `type` é o id no Catalog. */
@Serializable
data class TriggerDef(
    val type: String,
    val params: Map<String, String> = emptyMap(),
    val id: String = UUID.randomUUID().toString(),
)

/** Uma condição simples. `negate` = NÃO. */
@Serializable
data class CondLeaf(
    val type: String,
    val params: Map<String, String> = emptyMap(),
    val negate: Boolean = false,
)

/** Grupo de condições ligadas por E ("and") ou OU ("or"). */
@Serializable
data class Cond(
    val op: String = "and",
    val items: List<CondLeaf> = emptyList(),
)

/** Um passo do "Fazer": ação simples (kind="action") ou bloco Se/Senão (kind="if"). */
@Serializable
data class Step(
    val id: String = UUID.randomUUID().toString(),
    val kind: String = "action",
    val type: String = "",
    val params: Map<String, String> = emptyMap(),
    val cond: Cond = Cond(),
    val then: List<Step> = emptyList(),
    val otherwise: List<Step> = emptyList(),
)

@Serializable
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Nova rotina",
    val enabled: Boolean = true,
    val triggers: List<TriggerDef> = emptyList(),
    val steps: List<Step> = emptyList(),
)

/** Guarda as rotinas em um arquivo JSON. */
object Store {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()
    private var file: File? = null
    private var app: Context? = null

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        app = ctx.applicationContext
        val f = File(ctx.filesDir, "routines.json")
        file = f
        runCatching {
            if (f.exists()) _routines.value = json.decodeFromString<List<Routine>>(f.readText())
        }
    }

    fun get(id: String): Routine? = _routines.value.firstOrNull { it.id == id }

    fun save(r: Routine) {
        val l = _routines.value.toMutableList()
        val i = l.indexOfFirst { it.id == r.id }
        if (i >= 0) l[i] = r else l.add(r)
        commit(l)
    }

    fun delete(id: String) = commit(_routines.value.filterNot { it.id == id })

    private fun commit(l: List<Routine>) {
        _routines.value = l
        runCatching { file?.writeText(json.encodeToString(l)) }
        app?.let { ctx ->
            runCatching { Alarms.rescheduleAll(ctx) }
            runCatching { Svc.ensure(ctx) }
        }
    }
}

/** Registro do que as rotinas fizeram (aparece na tela "Registro"). */
object RunLog {
    private val _e = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _e.asStateFlow()

    fun add(msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _e.value = (listOf("$t  $msg") + _e.value).take(300)
    }
}
