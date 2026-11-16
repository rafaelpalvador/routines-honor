package app.fluxo

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Roda comandos de shell com a permissão do Shizuku. */
object Sh {
    data class Res(val ok: Boolean, val out: String)

    fun running(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun granted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun available(): Boolean = running() && granted()

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(100) }
    }

    /** `Shizuku.newProcess` é privado nas versões novas da API, por isso usamos reflexão. */
    fun run(cmd: String): Res = runCatching {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        Res(code == 0, (out + err).trim())
    }.getOrElse { Res(false, it.message ?: "erro ao executar") }
}
