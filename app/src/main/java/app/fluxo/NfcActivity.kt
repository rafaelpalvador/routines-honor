package app.fluxo

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle

/** Recebe uma tag NFC encostada no aparelho e dispara o gatilho "NFC lida". */
class NfcActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        handle()
        finish()
    }

    private fun handle() {
        @Suppress("DEPRECATION")
        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        val id = tag.id.joinToString("") { "%02X".format(it) }
        var text = ""
        runCatching {
            Ndef.get(tag)?.cachedNdefMessage?.records?.forEach { r ->
                val pl = r.payload
                if (pl.isNotEmpty()) text += String(pl, 1, pl.size - 1, Charsets.UTF_8)
            }
        }
        Store.init(applicationContext)
        RunLog.add("📡 NFC lida: $id")
        Engine.onEvent(applicationContext, "nfc", mapOf("id" to id, "var.nfc_id" to id, "var.nfc_texto" to text))
    }
}
