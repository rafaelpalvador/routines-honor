package app.fluxo

import android.app.Activity
import android.os.Bundle

/** Atalho na tela inicial: roda uma rotina direto, sem abrir o app. */
class RunActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getStringExtra("rid").orEmpty()
        Store.init(applicationContext)
        Store.routines.value.firstOrNull { it.id == id }?.let { Engine.run(applicationContext, it, "atalho") }
        Svc.ensure(applicationContext)
        finish()
    }
}
