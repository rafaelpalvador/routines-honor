package app.fluxo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import kotlinx.coroutines.delay

class StopSignal : Exception()

/** Executa as ações. Para criar uma ação nova: adicione o Spec no Catalog e um `"id" -> ...` aqui. */
object Actions {

    internal fun sh(cmd: String): String {
        if (!Sh.running()) throw IllegalStateException("Shizuku não está rodando")
        if (!Sh.granted()) throw IllegalStateException("Fluxo ainda não tem permissão no Shizuku")
        val r = Sh.run(cmd)
        if (!r.ok) throw IllegalStateException(r.out.ifBlank { "o comando falhou" })
        return r.out
    }

    private fun on(p: Map<String, String>) = p["state"] != "off"
    private fun en(p: Map<String, String>) = if (on(p)) "enable" else "disable"
    private fun lig(p: Map<String, String>) = if (on(p)) "ligado" else "desligado"
    internal fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
    private fun int(p: Map<String, String>, k: String, d: Int) = p[k]?.trim()?.toIntOrNull() ?: d

    suspend fun run(ctx: Context, id: String, p: Map<String, String>, vars: MutableMap<String, String>): String = when (id) {
        // ── Conexões
        "wifi" -> { sh("svc wifi ${en(p)}"); "Wi-Fi ${lig(p)}" }
        "mobile_data" -> { sh("svc data ${en(p)}"); "Dados móveis ${lig(p)}" }
        "bluetooth" -> { sh("svc bluetooth ${en(p)}"); "Bluetooth ${lig(p)}" }
        "airplane" -> { sh("cmd connectivity airplane-mode ${en(p)}"); "Modo avião ${lig(p)}" }
        "nfc" -> { sh("svc nfc ${en(p)}"); "NFC ${lig(p)}" }
        "private_dns" -> {
            when (p["mode"]) {
                "off" -> sh("settings put global private_dns_mode off")
                "host" -> sh("settings put global private_dns_specifier ${q(p["host"].orEmpty())}; settings put global private_dns_mode hostname")
                else -> sh("settings put global private_dns_mode opportunistic")
            }
            "DNS privado: ${p["mode"]}"
        }

        // ── Som
        "ringer" -> {
            val am = ctx.getSystemService(AudioManager::class.java)
            am.ringerMode = when (p["mode"]) {
                "silent" -> AudioManager.RINGER_MODE_SILENT
                "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            "Modo de som: ${p["mode"]}"
        }
        "volume" -> {
            val am = ctx.getSystemService(AudioManager::class.java)
            val stream = when (p["stream"]) {
                "ring" -> AudioManager.STREAM_RING
                "notification" -> AudioManager.STREAM_NOTIFICATION
                "alarm" -> AudioManager.STREAM_ALARM
                "call" -> AudioManager.STREAM_VOICE_CALL
                else -> AudioManager.STREAM_MUSIC
            }
            val pct = int(p, "percent", 50).coerceIn(0, 100)
            am.setStreamVolume(stream, am.getStreamMaxVolume(stream) * pct / 100, 0)
            "Volume ${p["stream"]} em $pct%"
        }
        "dnd" -> {
            val m = p["mode"] ?: "priority"
            sh("cmd notification set_dnd $m")
            "Não incomodar: $m"
        }

        // ── Tela
        "brightness" -> {
            val pct = int(p, "percent", 40).coerceIn(1, 100)
            val v = (pct * 255 / 100).coerceAtLeast(1)
            sh("settings put system screen_brightness_mode 0; settings put system screen_brightness $v")
            "Brilho em $pct%"
        }
        "auto_brightness" -> {
            sh("settings put system screen_brightness_mode ${if (on(p)) 1 else 0}")
            "Brilho automático ${lig(p)}"
        }
        "screen_timeout" -> {
            val s = int(p, "seconds", 30).coerceAtLeast(5)
            sh("settings put system screen_off_timeout ${s * 1000}")
            "Tela apaga em $s s"
        }
        "dark_mode" -> {
            sh("cmd uimode night ${p["mode"] ?: "yes"}")
            "Modo escuro: ${p["mode"]}"
        }
        "refresh_rate" -> {
            val hz = p["hz"] ?: "60"
            sh("settings put system peak_refresh_rate $hz; settings put system min_refresh_rate $hz")
            "Taxa de atualização: $hz Hz"
        }

        // ── Bateria
        "power_save" -> {
            sh("settings put global low_power ${if (on(p)) 1 else 0}")
            "Poupança de energia ${lig(p)}"
        }

        // ── Apps
        "open_app" -> {
            val pkg = p["pkg"].orEmpty().ifBlank { throw IllegalArgumentException("escolha um app") }
            sh("monkey -p ${q(pkg)} -c android.intent.category.LAUNCHER 1")
            "Abriu ${Dev.appLabel(ctx, pkg)}"
        }
        "force_stop" -> {
            val pkg = p["pkg"].orEmpty().ifBlank { throw IllegalArgumentException("escolha um app") }
            sh("am force-stop ${q(pkg)}")
            "Fechou ${Dev.appLabel(ctx, pkg)}"
        }
        "notify" -> {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("fluxo_msg", "Avisos do Fluxo", NotificationManager.IMPORTANCE_DEFAULT))
            val n = Notification.Builder(ctx, "fluxo_msg")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(p["title"].orEmpty().ifBlank { "Fluxo" })
                .setContentText(p["text"].orEmpty())
                .build()
            nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
            "Notificação enviada"
        }
        "flashlight" -> {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val cid = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            cm.setTorchMode(cid, on(p))
            "Lanterna ${lig(p)}"
        }

        // ── Lógica
        "wait" -> {
            val s = int(p, "seconds", 5).coerceAtLeast(0)
            delay(s * 1000L)
            "Esperou $s s"
        }
        "set_var" -> {
            val n = p["name"].orEmpty().trim()
            if (n.isBlank()) throw IllegalArgumentException("nome vazio")
            vars[n] = p["value"].orEmpty()
            "Variável $n = ${p["value"]}"
        }
        "stop" -> throw StopSignal()
        "log" -> p["message"].orEmpty()

        // ── Avançadas
        "shell" -> {
            val out = sh(p["command"].orEmpty())
            if (out.isBlank()) "Comando executado" else "Comando: ${out.take(300)}"
        }
        "settings_put" -> {
            sh("settings put ${p["ns"] ?: "system"} ${q(p["key"].orEmpty())} ${q(p["value"].orEmpty())}")
            "settings ${p["ns"]} ${p["key"]} = ${p["value"]}"
        }
        "open_url" -> {
            sh("am start -a android.intent.action.VIEW -d ${q(p["url"].orEmpty())}")
            "Abriu ${p["url"]}"
        }

        else -> AutomateActions.run(ctx, id, p, vars) ?: MoreActions.run(ctx, id, p, vars) ?: ShortcutsActions.run(ctx, id, p, vars) ?: throw IllegalArgumentException("ação desconhecida: $id")
    }
}
