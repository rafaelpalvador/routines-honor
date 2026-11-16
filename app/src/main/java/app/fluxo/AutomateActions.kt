package app.fluxo

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal fun streamId(s: String?): Int = when (s) {
    "ring" -> AudioManager.STREAM_RING
    "alarm" -> AudioManager.STREAM_ALARM
    "notification" -> AudioManager.STREAM_NOTIFICATION
    "voice" -> AudioManager.STREAM_VOICE_CALL
    else -> AudioManager.STREAM_MUSIC
}

/** Leitura de um sensor (primeiro valor recebido). */
internal fun readSensor(ctx: Context, kind: String): Float? {
    val type = when (kind) {
        "proximity" -> Sensor.TYPE_PROXIMITY
        "pressure" -> Sensor.TYPE_PRESSURE
        "temperature" -> Sensor.TYPE_AMBIENT_TEMPERATURE
        "humidity" -> Sensor.TYPE_RELATIVE_HUMIDITY
        "magnetic" -> Sensor.TYPE_MAGNETIC_FIELD
        else -> Sensor.TYPE_LIGHT
    }
    val sm = ctx.getSystemService(SensorManager::class.java)
    val s = sm.getDefaultSensor(type) ?: return null
    val latch = CountDownLatch(1)
    var v: Float? = null
    val l = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            v = if (type == Sensor.TYPE_MAGNETIC_FIELD)
                Math.sqrt(e.values.take(3).sumOf { (it * it).toDouble() }).toFloat()
            else e.values[0]
            latch.countDown()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL)
    latch.await(2, TimeUnit.SECONDS)
    sm.unregisterListener(l)
    return v
}

internal fun batteryIntent(ctx: Context): Intent? =
    ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

/** Ações dos blocos avançados (Automate). Devolve null se o id não for daqui. */
object AutomateActions {
    private fun sh(c: String) = Actions.sh(c)
    private fun q(s: String) = Actions.q(s)
    private fun s(p: Map<String, String>, k: String) = p[k].orEmpty().trim()
    private fun on(p: Map<String, String>) = p["state"] != "off"

    private fun save(p: Map<String, String>, vars: MutableMap<String, String>, v: String): String {
        val n = s(p, "save")
        if (n.isNotEmpty()) vars[n] = v
        return if (n.isNotEmpty()) "{$n} = ${v.take(120)}" else "Resultado: ${v.take(120)}"
    }

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null

    private suspend fun ttsOf(ctx: Context): TextToSpeech = tts ?: suspendCancellableCoroutine { c ->
        var t: TextToSpeech? = null
        t = TextToSpeech(ctx.applicationContext) { st ->
            if (st == TextToSpeech.SUCCESS) { t?.language = Locale.getDefault(); tts = t; c.resume(t!!) }
            else c.resume(t ?: throw IllegalStateException("voz indisponível"))
        }
    }

    private fun safePath(path: String): String {
        require(path.isNotBlank() && path != "/" ) { "caminho inválido" }
        return q(path)
    }

    suspend fun run(ctx: Context, id: String, p: Map<String, String>, vars: MutableMap<String, String>): String? = when (id) {
        // ── Apps
        "app_kill_bg" -> { sh("am kill ${q(s(p, "pkg"))}"); "Encerrou em segundo plano: ${Dev.appLabel(ctx, s(p, "pkg"))}" }
        "app_clear_data" -> { sh("pm clear ${q(s(p, "pkg"))}"); "Dados apagados: ${Dev.appLabel(ctx, s(p, "pkg"))}" }
        "app_trim_cache" -> { sh("pm trim-caches 999999999999"); "Cache limpo" }
        "app_list" -> {
            val out = sh("pm list packages ${if (p["scope"] == "all") "" else "-3"}")
            save(p, vars, out.lines().map { it.removePrefix("package:") }.filter { it.isNotBlank() }.joinToString(","))
        }
        "app_notif_set" -> { sh("cmd appops set ${q(s(p, "pkg"))} POST_NOTIFICATION ${if (on(p)) "allow" else "deny"}"); "Notificações de ${Dev.appLabel(ctx, s(p, "pkg"))}: ${if (on(p)) "ligadas" else "desligadas"}" }
        "appop_set" -> { sh("cmd appops set ${q(s(p, "pkg"))} ${q(s(p, "op"))} ${s(p, "mode")}"); "AppOp ${s(p, "op")} = ${s(p, "mode")}" }
        "broadcast_send" -> {
            var c = "am broadcast -a ${q(s(p, "action"))}"
            if (s(p, "pkg").isNotEmpty()) c += " -p ${q(s(p, "pkg"))}"
            if (s(p, "extra").isNotEmpty()) c += " --es extra ${q(s(p, "extra"))}"
            sh(c); "Broadcast enviado: ${s(p, "action")}"
        }
        "service_start" -> { sh("am start-foreground-service -n ${q(s(p, "component"))}"); "Serviço iniciado: ${s(p, "component")}" }

        // ── Bateria e energia
        "battery_info" -> {
            val i = batteryIntent(ctx)
            val v = when (p["field"]) {
                "voltage" -> i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.toString()
                "level" -> i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)?.toString()
                "health" -> i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)?.toString()
                "plugged" -> i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.toString()
                else -> i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.let { (it / 10.0).toString() }
            } ?: "?"
            save(p, vars, v)
        }
        "doze_set" -> { sh(if (on(p)) "dumpsys deviceidle force-idle" else "dumpsys deviceidle unforce"); "Doze ${if (on(p)) "forçado" else "liberado"}" }
        "keep_awake" -> { sh("svc power stayon ${s(p, "mode").ifBlank { "ac" }}"); "Tela ligada: ${s(p, "mode")}" }
        "reboot" -> { sh("svc power reboot"); "Reiniciando" }
        "shutdown" -> { sh("svc power shutdown"); "Desligando" }
        "screen_power" -> { sh("input keyevent ${if (on(p)) 224 else 223}"); "Tela ${if (on(p)) "acesa" else "apagada"}" }

        // ── Câmera e som
        "media_key" -> { sh("input keyevent ${s(p, "key").ifBlank { "85" }}"); "Controle de mídia" }
        "stream_mute" -> {
            val am = ctx.getSystemService(AudioManager::class.java)
            am.adjustStreamVolume(streamId(p["stream"]), if (p["mode"] == "unmute") AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE, 0)
            "Volume ${p["stream"]}: ${if (p["mode"] == "unmute") "liberado" else "silenciado"}"
        }
        "mic_mute" -> { ctx.getSystemService(AudioManager::class.java).isMicrophoneMute = p["mode"] != "unmute"; "Microfone ${if (p["mode"] == "unmute") "liberado" else "silenciado"}" }
        "speakerphone" -> {
            @Suppress("DEPRECATION")
            ctx.getSystemService(AudioManager::class.java).isSpeakerphoneOn = on(p)
            "Viva-voz ${if (on(p)) "ligado" else "desligado"}"
        }
        "screenshot" -> { val f = s(p, "path").ifBlank { "/sdcard/Pictures/fluxo.png" }; sh("screencap -p ${q(f)}"); "Captura salva em $f" }
        "sound_play" -> {
            player?.release()
            val mp = MediaPlayer()
            mp.setDataSource(s(p, "path")); mp.prepare(); mp.start()
            player = mp
            if (p["wait"] == "yes") while (mp.isPlaying) delay(200)
            "Tocando ${s(p, "path")}"
        }
        "sound_stop" -> { player?.release(); player = null; "Som parado" }
        "tone_play" -> {
            val t = when (p["tone"]) {
                "ack" -> ToneGenerator.TONE_PROP_ACK
                "nack" -> ToneGenerator.TONE_PROP_NACK
                "alert" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            val ms = s(p, "ms").toIntOrNull() ?: 300
            val g = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            g.startTone(t, ms); delay(ms + 100L); g.release()
            "Bipe"
        }
        "speak" -> {
            val t = ttsOf(ctx)
            t.speak(s(p, "text"), TextToSpeech.QUEUE_FLUSH, null, "fluxo")
            if (p["wait"] == "yes") { delay(300); while (t.isSpeaking) delay(200) }
            "Falou: ${s(p, "text").take(60)}"
        }
        "speak_stop" -> { tts?.stop(); "Fala parada" }
        "vibrate" -> {
            val ms = s(p, "ms").toLongOrNull() ?: 500L
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            "Vibrou ${ms}ms"
        }
        "vibrate_stop" -> { ctx.getSystemService(VibratorManager::class.java).defaultVibrator.cancel(); "Vibração parada" }

        // ── Conexões
        "usb_tether" -> { sh("svc usb setFunctions ${if (on(p)) "rndis" else "none"}"); "Tethering USB ${if (on(p)) "ligado" else "desligado"}" }
        "restrict_bg_data" -> { sh("cmd netpolicy set restrict-background ${on(p)}"); "Economia de dados ${if (on(p)) "ligada" else "desligada"}" }
        "wifi_connect" -> {
            val sec = s(p, "sec").ifBlank { "wpa2" }
            sh("cmd wifi connect-network ${q(s(p, "ssid"))} $sec" + if (sec != "open") " ${q(s(p, "pass"))}" else "")
            "Conectando a ${s(p, "ssid")}"
        }
        "http_request" -> {
            val out = withContext(Dispatchers.IO) {
                val c = URL(s(p, "url")).openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 15000
                c.requestMethod = p["method"] ?: "GET"
                if (c.requestMethod == "POST") { c.doOutput = true; c.outputStream.use { it.write(p["body"].orEmpty().toByteArray()) } }
                val code = c.responseCode
                val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
                c.disconnect()
                "$code|$body"
            }
            val code = out.substringBefore("|")
            save(p, vars, out.substringAfter("|")).let { "HTTP $code · $it" }
        }
        "wol_send" -> withContext(Dispatchers.IO) {
            val mac = s(p, "mac").split(":", "-").map { it.toInt(16).toByte() }.toByteArray()
            require(mac.size == 6) { "MAC inválido" }
            val data = ByteArray(6) { 0xFF.toByte() } + ByteArray(96) { mac[it % 6] }
            DatagramSocket().use { it.broadcast = true; it.send(DatagramPacket(data, data.size, InetAddress.getByName(s(p, "host").ifBlank { "255.255.255.255" }), s(p, "port").toIntOrNull() ?: 9)) }
            "Pacote Wake-on-LAN enviado"
        }

        // ── Conteúdo
        "alarm_add" -> {
            val (h, m) = s(p, "time").ifBlank { "07:00" }.split(":").map { it.toInt() }
            sh("am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR $h --ei android.intent.extra.alarm.MINUTES $m --ez android.intent.extra.alarm.SKIP_UI true --es android.intent.extra.alarm.MESSAGE ${q(s(p, "msg"))}")
            "Alarme criado para %02d:%02d".format(h, m)
        }
        "timer_add" -> { sh("am start -a android.intent.action.SET_TIMER --ei android.intent.extra.alarm.LENGTH ${s(p, "seconds").ifBlank { "60" }} --ez android.intent.extra.alarm.SKIP_UI true"); "Timer criado: ${s(p, "seconds")}s" }
        "calendar_add" -> { sh("am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/event --es title ${q(s(p, "title"))} --es description ${q(s(p, "desc"))}"); "Evento aberto no calendário" }
        "content_query" -> save(p, vars, sh("content query ${s(p, "args")}"))
        "content_insert" -> { sh("content insert ${s(p, "args")}"); "Inserido" }
        "content_delete" -> { sh("content delete ${s(p, "args")}"); "Apagado" }
        "content_update" -> { sh("content update ${s(p, "args")}"); "Atualizado" }

        // ── Data e hora
        "tz_get" -> save(p, vars, sh("getprop persist.sys.timezone"))
        "tz_set" -> { sh("cmd alarm set-timezone ${q(s(p, "tz"))}"); "Fuso: ${s(p, "tz")}" }

        // ── Arquivos
        "file_copy" -> { sh("cp -r ${safePath(s(p, "src"))} ${safePath(s(p, "dst"))}"); "Copiado" }
        "file_move" -> { sh("mv ${safePath(s(p, "src"))} ${safePath(s(p, "dst"))}"); "Movido" }
        "file_delete" -> { sh("rm -rf ${safePath(s(p, "path"))}"); "Apagado: ${s(p, "path")}" }
        "file_mkdir" -> { sh("mkdir -p ${safePath(s(p, "path"))}"); "Pasta criada" }
        "file_write" -> { sh("printf '%s\\n' ${q(p["text"].orEmpty())} ${if (p["mode"] == "w") ">" else ">>"} ${safePath(s(p, "path"))}"); "Escreveu em ${s(p, "path")}" }
        "file_read" -> save(p, vars, sh("cat ${safePath(s(p, "path"))}").take(4000))
        "file_list" -> save(p, vars, sh("ls -1 ${safePath(s(p, "path"))}").lines().joinToString(","))
        "file_unzip" -> { sh("unzip -o ${safePath(s(p, "zip"))} -d ${safePath(s(p, "dst"))}"); "Extraído" }

        // ── Fluxo
        "run_routine" -> {
            val r = Store.routines.value.firstOrNull { it.name.equals(s(p, "name"), ignoreCase = true) } ?: throw IllegalArgumentException("rotina '${s(p, "name")}' não encontrada")
            Engine.run(ctx, r, "chamada por outra rotina"); "Chamou a rotina ${r.name}"
        }

        // ── Interface
        "clipboard_set" -> {
            withContext(Dispatchers.Main) { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Fluxo", p["text"].orEmpty())) }
            "Texto copiado"
        }
        "toast" -> { Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, p["text"].orEmpty(), Toast.LENGTH_LONG).show() }; "Aviso: ${p["text"].orEmpty().take(60)}" }
        "key_send" -> { sh("input keyevent ${s(p, "key").ifBlank { "3" }}"); "Tecla ${s(p, "key")}" }
        "text_send" -> { sh("input text ${q(p["text"].orEmpty().replace(" ", "%s"))}"); "Digitou o texto" }
        "tap" -> { sh("input tap ${s(p, "x")} ${s(p, "y")}"); "Toque em ${s(p, "x")},${s(p, "y")}" }
        "swipe" -> { sh("input swipe ${s(p, "x1")} ${s(p, "y1")} ${s(p, "x2")} ${s(p, "y2")} ${s(p, "ms").ifBlank { "300" }}"); "Deslizou" }
        "car_mode" -> { sh("cmd uimode car ${if (on(p)) "yes" else "no"}"); "Modo carro ${if (on(p)) "ligado" else "desligado"}" }
        "orientation_set" -> {
            when (p["mode"]) {
                "portrait" -> sh("settings put system accelerometer_rotation 0; settings put system user_rotation 0")
                "landscape" -> sh("settings put system accelerometer_rotation 0; settings put system user_rotation 1")
                else -> sh("settings put system accelerometer_rotation 1")
            }
            "Orientação: ${p["mode"]}"
        }
        "ime_set" -> { sh("ime set ${q(s(p, "id"))}"); "Teclado trocado" }

        // ── Local
        "location_set" -> { sh("cmd location set-location-enabled ${on(p)}"); "Localização ${if (on(p)) "ligada" else "desligada"}" }

        // ── Mensagens
        "compose_sms" -> { sh("am start -a android.intent.action.SENDTO -d ${q("smsto:" + s(p, "number"))} --es sms_body ${q(p["text"].orEmpty())}"); "SMS preparado" }
        "compose_email" -> {
            sh("am start -a android.intent.action.SENDTO -d mailto: --esa android.intent.extra.EMAIL ${q(s(p, "to"))} --es android.intent.extra.SUBJECT ${q(s(p, "subject"))} --es android.intent.extra.TEXT ${q(p["text"].orEmpty())}")
            "E-mail preparado"
        }
        "sms_send" -> {
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                throw IllegalStateException("falta a permissão de SMS (tela Configuração)")
            ctx.getSystemService(SmsManager::class.java).sendTextMessage(s(p, "number"), null, p["text"].orEmpty(), null, null)
            "SMS enviado para ${s(p, "number")}"
        }

        // ── Sensores
        "sensor_read" -> save(p, vars, readSensor(ctx, p["sensor"] ?: "light")?.toString() ?: throw IllegalStateException("sensor indisponível"))

        // ── Ajustes
        "settings_get" -> save(p, vars, sh("settings get ${p["ns"] ?: "system"} ${q(s(p, "key"))}"))
        "sysprop_get" -> save(p, vars, sh("getprop ${q(s(p, "key"))}"))

        // ── Telefonia
        "call_answer" -> { sh("input keyevent 5"); "Atendeu a chamada" }
        "call_end" -> { sh("input keyevent 6"); "Encerrou a chamada" }
        "call_number" -> { sh("am start -a android.intent.action.CALL -d ${q("tel:" + s(p, "number"))}"); "Ligando para ${s(p, "number")}" }
        "dial_number" -> { sh("am start -a android.intent.action.DIAL -d ${q("tel:" + s(p, "number"))}"); "Discador aberto" }

        else -> null
    }
}
