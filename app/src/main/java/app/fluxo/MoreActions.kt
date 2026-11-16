package app.fluxo

import android.Manifest
import android.app.WallpaperManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.wifi.WifiManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object MoreActions {
    @Volatile private var lastWifi: List<String> = emptyList()

    private fun s(p: Map<String, String>, k: String) = p[k].orEmpty()
    private fun t(p: Map<String, String>, k: String) = p[k].orEmpty().trim()

    private fun save(p: Map<String, String>, vars: MutableMap<String, String>, v: String, key: String = "save"): String {
        val n = t(p, key)
        if (n.isNotEmpty()) vars[n] = v
        return if (n.isNotEmpty()) "{$n} = ${v.take(120)}" else "Resultado: ${v.take(120)}"
    }

    private fun granted(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun need(ctx: Context, perm: String, nome: String) {
        if (!granted(ctx, perm)) throw IllegalStateException("falta permissão: $nome (tela Configuração)")
    }

    private suspend fun answer(ctx: Context, kind: String, p: Map<String, String>, vars: MutableMap<String, String>, msg: String = "", def: String = "", opts: String = ""): String {
        val r = Ask.ask(ctx, kind, s(p, "title"), msg, def, opts)
        if (r == null) {
            if (p["onCancel"] == "stop") throw StopSignal()
            if (kind != "message") save(p, vars, "")
            return "Cancelado"
        }
        if (kind == "message") return "Alerta mostrado"
        return save(p, vars, r)
    }

    // ───────── calendário ─────────
    fun calendarStarting(ctx: Context, atMs: Long, windowMs: Long): List<Pair<String, String>> {
        if (!granted(ctx, Manifest.permission.READ_CALENDAR)) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        runCatching {
            val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(b, atMs - windowMs)
            ContentUris.appendId(b, atMs + windowMs)
            ctx.contentResolver.query(b.build(), arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val begin = c.getLong(2)
                    if (begin in (atMs - windowMs)..(atMs + windowMs)) out += c.getLong(0).toString() to (c.getString(1) ?: "")
                }
            }
        }
        return out
    }

    // ───────── imagem ─────────
    /** Copia a imagem para a pasta do app (o Shizuku consegue ler onde o app não consegue). */
    private fun loadBitmap(ctx: Context, path: String): Bitmap {
        BitmapFactory.decodeFile(path)?.let { return it }
        val tmp = File(ctx.cacheDir, "img_${System.nanoTime()}")
        Actions.sh("cp ${Actions.q(path)} ${Actions.q(tmp.absolutePath)} && chmod 666 ${Actions.q(tmp.absolutePath)}")
        val b = BitmapFactory.decodeFile(tmp.absolutePath)
        tmp.delete()
        return b ?: throw IllegalStateException("não consegui abrir a imagem: $path")
    }

    private suspend fun ocr(bmp: Bitmap): String = suspendCancellableCoroutine { c ->
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        rec.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { c.resume(it.text) }
            .addOnFailureListener { c.resumeWithException(it) }
    }

    private suspend fun barcode(bmp: Bitmap): String = suspendCancellableCoroutine { c ->
        BarcodeScanning.getClient().process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { c.resume(it.joinToString("\n") { b -> b.rawValue.orEmpty() }) }
            .addOnFailureListener { c.resumeWithException(it) }
    }

    // ───────── scans ─────────
    private suspend fun scanWifi(ctx: Context): List<String> {
        need(ctx, Manifest.permission.ACCESS_FINE_LOCATION, "Localização")
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION") runCatching { wm.startScan() }
        delay(3500)
        val names = wm.scanResults.map { it.SSID }.filter { it.isNotBlank() }.distinct()
        lastWifi = names
        return names
    }

    @Suppress("MissingPermission")
    private suspend fun scanBt(ctx: Context): List<String> {
        need(ctx, Manifest.permission.BLUETOOTH_SCAN, "Bluetooth (procurar)")
        val ad = BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("sem Bluetooth")
        val found = java.util.concurrent.CopyOnWriteArrayList<String>()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                @Suppress("DEPRECATION")
                val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                runCatching { d?.name }.getOrNull()?.let { if (it.isNotBlank() && it !in found) found += it }
            }
        }
        ctx.registerReceiver(r, IntentFilter(BluetoothDevice.ACTION_FOUND))
        try {
            ad.startDiscovery()
            delay(8000)
        } finally {
            runCatching { ad.cancelDiscovery() }
            runCatching { ctx.unregisterReceiver(r) }
        }
        return found.toList()
    }

    private fun decimal(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    // ───────── condições ─────────
    fun cond(ctx: Context, type: String, p: Map<String, String>): Boolean? = when (type) {
        "sync_on" -> (ContentResolver.getMasterSyncAutomatically() == (p["state"] != "no"))
        "wifi_nearby" -> {
            val names = runCatching { kotlinx.coroutines.runBlocking { scanWifi(ctx) } }.getOrDefault(lastWifi)
            names.any { it.equals(t(p, "ssid"), ignoreCase = true) } == (p["state"] != "no")
        }
        "bt_nearby" -> {
            val names = runCatching { kotlinx.coroutines.runBlocking { scanBt(ctx) } }.getOrDefault(emptyList())
            names.any { it.contains(t(p, "name"), ignoreCase = true) } == (p["state"] != "no")
        }
        else -> null
    }

    // ───────── ações ─────────
    suspend fun run(ctx: Context, id: String, p: Map<String, String>, vars: MutableMap<String, String>): String? = when (id) {
        "dialog_message" -> answer(ctx, "message", p, vars, msg = s(p, "msg"))
        "dialog_confirm" -> answer(ctx, "confirm", p, vars)
        "dialog_input" -> answer(ctx, "input", p, vars, def = s(p, "def"))
        "dialog_choice" -> answer(ctx, "choice", p, vars, opts = s(p, "opts"))
        "time_pick" -> answer(ctx, "time", p, vars)
        "date_pick" -> answer(ctx, "date", p, vars)

        "dict_put" -> { vars["${t(p, "name")}.${t(p, "key")}"] = s(p, "value"); "${t(p, "name")}.${t(p, "key")} = ${s(p, "value")}" }
        "dict_get" -> save(p, vars, vars["${t(p, "name")}.${t(p, "key")}"].orEmpty())
        "dict_remove" -> { vars.remove("${t(p, "name")}.${t(p, "key")}"); "Chave removida" }
        "list_set" -> {
            val l = vars[t(p, "list")].orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            val i = (t(p, "index").toIntOrNull() ?: 1) - 1
            if (i !in l.indices) throw IllegalArgumentException("posição fora da lista")
            l[i] = s(p, "item").trim()
            vars[t(p, "list")] = l.joinToString(",")
            "Item ${i + 1} trocado"
        }

        "notif_dismiss_all" -> {
            val n = NotifService.instance ?: throw IllegalStateException("falta o Acesso às notificações")
            n.cancelAllNotifications(); "Notificações limpas"
        }
        "notif_dismiss_app" -> {
            val n = NotifService.instance ?: throw IllegalStateException("falta o Acesso às notificações")
            val pk = t(p, "pkg")
            n.activeNotifications.filter { it.packageName == pk }.forEach { n.cancelNotification(it.key) }
            "Notificações de $pk limpas"
        }
        "notif_count" -> {
            val n = NotifService.instance ?: throw IllegalStateException("falta o Acesso às notificações")
            val pk = t(p, "pkg")
            save(p, vars, n.activeNotifications.count { pk.isEmpty() || it.packageName == pk }.toString())
        }

        "calendar_insert" -> {
            need(ctx, Manifest.permission.WRITE_CALENDAR, "Calendário")
            val cr = ctx.contentResolver
            var cal = -1L
            cr.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}", null, null)?.use { if (it.moveToFirst()) cal = it.getLong(0) }
            if (cal < 0) throw IllegalStateException("nenhum calendário disponível")
            val start = System.currentTimeMillis() + (t(p, "in").toLongOrNull() ?: 30L) * 60_000L
            val end = start + (t(p, "dur").toLongOrNull() ?: 60L) * 60_000L
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cal)
                put(CalendarContract.Events.TITLE, s(p, "title"))
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, end)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            cr.insert(CalendarContract.Events.CONTENT_URI, v) ?: throw IllegalStateException("não consegui criar")
            "Evento criado: ${s(p, "title")}"
        }
        "calendar_next" -> {
            need(ctx, Manifest.permission.READ_CALENDAR, "Calendário")
            val now = System.currentTimeMillis()
            val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(b, now); ContentUris.appendId(b, now + 24 * 3_600_000L)
            var title = ""; var begin = 0L
            ctx.contentResolver.query(b.build(), arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN), null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use {
                if (it.moveToFirst()) { title = it.getString(0).orEmpty(); begin = it.getLong(1) }
            }
            val hhmm = if (begin > 0) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(begin)) else ""
            if (t(p, "save_time").isNotEmpty()) vars[t(p, "save_time")] = hhmm
            save(p, vars, title)
        }
        "contact_query" -> {
            need(ctx, Manifest.permission.READ_CONTACTS, "Contatos")
            var num = ""
            ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%${t(p, "name")}%"), null)?.use { if (it.moveToFirst()) num = it.getString(0).orEmpty() }
            save(p, vars, num)
        }
        "sync_set" -> { ContentResolver.setMasterSyncAutomatically(p["state"] != "off"); "Sincronização ${if (p["state"] == "off") "desligada" else "ligada"}" }

        "screen_ocr" -> {
            val f = File(ctx.cacheDir, "shot.png")
            Actions.sh("screencap -p ${Actions.q(f.absolutePath)} && chmod 666 ${Actions.q(f.absolutePath)}")
            val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: throw IllegalStateException("captura falhou")
            val r = withContext(Dispatchers.Default) { ocr(bmp) }
            f.delete()
            save(p, vars, r)
        }
        "ocr_image" -> save(p, vars, ocr(withContext(Dispatchers.IO) { loadBitmap(ctx, t(p, "path")) }))
        "barcode_scan" -> save(p, vars, barcode(withContext(Dispatchers.IO) { loadBitmap(ctx, t(p, "path")) }))
        "image_edit" -> withContext(Dispatchers.IO) {
            val src = loadBitmap(ctx, t(p, "path"))
            val v = t(p, "value")
            val out: Bitmap = when (p["op"]) {
                "flip_h" -> Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { preScale(-1f, 1f) }, true)
                "flip_v" -> Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { preScale(1f, -1f) }, true)
                "scale" -> {
                    val f = (v.toFloatOrNull() ?: 100f) / 100f
                    Bitmap.createScaledBitmap(src, (src.width * f).toInt().coerceAtLeast(1), (src.height * f).toInt().coerceAtLeast(1), true)
                }
                "crop" -> {
                    val n = v.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (n.size < 4) throw IllegalArgumentException("use x,y,largura,altura")
                    val x = n[0].coerceIn(0, src.width - 1); val y = n[1].coerceIn(0, src.height - 1)
                    Bitmap.createBitmap(src, x, y, n[2].coerceAtMost(src.width - x), n[3].coerceAtMost(src.height - y))
                }
                else -> Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(v.toFloatOrNull() ?: 90f) }, true)
            }
            val dest = t(p, "out").ifEmpty { t(p, "path").substringBeforeLast('.') + "_editada.png" }
            val tmp = File(ctx.cacheDir, "edit.png")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Actions.sh("cp ${Actions.q(tmp.absolutePath)} ${Actions.q(dest)}")
            tmp.delete()
            "Imagem salva em $dest"
        }
        "image_color" -> {
            val b = withContext(Dispatchers.IO) { loadBitmap(ctx, t(p, "path")) }
            val x = (t(p, "x").toIntOrNull() ?: 0).coerceIn(0, b.width - 1)
            val y = (t(p, "y").toIntOrNull() ?: 0).coerceIn(0, b.height - 1)
            save(p, vars, "#%06X".format(b.getPixel(x, y) and 0xFFFFFF))
        }
        "wallpaper_set" -> withContext(Dispatchers.IO) {
            val b = loadBitmap(ctx, t(p, "path"))
            val wm = WallpaperManager.getInstance(ctx)
            val flag = when (p["where"]) {
                "home" -> WallpaperManager.FLAG_SYSTEM
                "lock" -> WallpaperManager.FLAG_LOCK
                else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            wm.setBitmap(b, null, true, flag)
            "Papel de parede trocado"
        }

        "weather_get" -> {
            val g = Geo.parse(p["loc"]) ?: throw IllegalArgumentException("defina o lugar")
            val body = withContext(Dispatchers.IO) {
                val u = URL("https://api.open-meteo.com/v1/forecast?latitude=${g.first}&longitude=${g.second}&current=temperature_2m,weather_code,wind_speed_10m")
                val c = u.openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 10_000
                try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
            }
            val cur = JSONObject(body).getJSONObject("current")
            if (t(p, "save_code").isNotEmpty()) vars[t(p, "save_code")] = cur.getInt("weather_code").toString()
            if (t(p, "save_wind").isNotEmpty()) vars[t(p, "save_wind")] = decimal(cur.getDouble("wind_speed_10m"))
            save(p, vars, decimal(cur.getDouble("temperature_2m")))
        }
        "ip_local" -> {
            val ip = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }?.hostAddress.orEmpty()
            save(p, vars, ip)
        }
        "wifi_scan" -> save(p, vars, scanWifi(ctx).joinToString(","))
        "bt_scan" -> save(p, vars, scanBt(ctx).joinToString(","))
        else -> null
    }
}
