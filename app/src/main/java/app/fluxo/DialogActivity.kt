package app.fluxo

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** Pede uma resposta ao usuário: abre DialogActivity (via Shizuku, para funcionar em segundo plano) e espera. */
object Ask {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private var seq = 0

    fun answer(id: String, v: String?) { pending.remove(id)?.complete(v) }

    suspend fun ask(ctx: Context0, kind: String, title: String, msg: String = "", def: String = "", opts: String = ""): String? {
        val id = "q${++seq}"
        val d = CompletableDeferred<String?>()
        pending[id] = d
        val pkg = ctx.packageName
        val cmd = "am start -n $pkg/.DialogActivity --es id ${Actions.q(id)} --es kind ${Actions.q(kind)} --es title ${Actions.q(title)} " +
            "--es msg ${Actions.q(msg)} --es def ${Actions.q(def)} --es opts ${Actions.q(opts)}"
        Actions.sh(cmd)
        val r = withTimeoutOrNull(300_000) { d.await() }
        pending.remove(id)
        return r
    }
}

typealias Context0 = android.content.Context

class DialogActivity : Activity() {
    private var id = ""
    private var done = false

    private fun finish(v: String?) {
        if (done) return
        done = true
        Ask.answer(id, v)
        finish()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        id = intent.getStringExtra("id").orEmpty()
        val kind = intent.getStringExtra("kind").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        val msg = intent.getStringExtra("msg").orEmpty()
        val def = intent.getStringExtra("def").orEmpty()
        val opts = intent.getStringExtra("opts").orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val c = Calendar.getInstance()
        when (kind) {
            "message" -> AlertDialog.Builder(this).setTitle(title).setMessage(msg)
                .setPositiveButton("OK") { _, _ -> finish("ok") }.setOnCancelListener { finish("ok") }.show()
            "confirm" -> AlertDialog.Builder(this).setTitle(title)
                .setPositiveButton("Sim") { _, _ -> finish("yes") }
                .setNegativeButton("Não") { _, _ -> finish("no") }
                .setOnCancelListener { finish(null) }.show()
            "input" -> {
                val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT; setText(def) }
                AlertDialog.Builder(this).setTitle(title).setView(et)
                    .setPositiveButton("OK") { _, _ -> finish(et.text.toString()) }
                    .setNegativeButton("Cancelar") { _, _ -> finish(null) }
                    .setOnCancelListener { finish(null) }.show()
            }
            "choice" -> AlertDialog.Builder(this).setTitle(title)
                .setItems(opts.toTypedArray()) { _, i -> finish(opts[i]) }
                .setOnCancelListener { finish(null) }.show()
            "time" -> TimePickerDialog(this, { _, h, m -> finish("%02d:%02d".format(h, m)) },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).apply { setTitle(title); setOnCancelListener { finish(null) } }.show()
            "date" -> DatePickerDialog(this, { _, y, m, d -> finish("%04d-%02d-%02d".format(y, m + 1, d)) },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply { setTitle(title); setOnCancelListener { finish(null) } }.show()
            else -> finish(null)
        }
    }

    override fun onDestroy() {
        if (!done) { done = true; Ask.answer(id, null) }
        super.onDestroy()
    }
}
