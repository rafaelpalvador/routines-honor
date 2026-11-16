package app.fluxo

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** Liga/garante o serviço que fica ouvindo os eventos do aparelho. */
object Svc {
    fun ensure(ctx: Context) {
        if (Store.routines.value.none { it.enabled }) return
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, EngineService::class.java)) }
    }
}

/** Serviço em primeiro plano: registra os ouvintes dos gatilhos. */
class EngineService : Service() {
    private val receivers = mutableListOf<BroadcastReceiver>()
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private var telCb: TelephonyCallback? = null
    private var lastLevel = -1
    private var lastNight: Boolean? = null
    private var lastCall = TelephonyManager.CALL_STATE_IDLE
    private var lastSsid = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dyn = mutableListOf<BroadcastReceiver>()
    private var shakeL: SensorEventListener? = null
    private var usageJob: Job? = null
    private var tickJob: Job? = null
    private var locL: LocationListener? = null
    private val inside = ConcurrentHashMap<String, Boolean>()
    private val lastRun = ConcurrentHashMap<String, Long>()
    private val sunDone = ConcurrentHashMap<String, String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("fluxo_service", "Fluxo ativo", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "fluxo_service")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Fluxo está ativo")
            .setContentText("Observando os gatilhos das suas rotinas")
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        registerAll()
        Alarms.rescheduleAll(this)
        scope.launch(Dispatchers.Main) { Store.routines.collect { refreshDynamic() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        dyn.forEach { runCatching { unregisterReceiver(it) } }
        stopShake(); stopPlaces()
        receivers.forEach { runCatching { unregisterReceiver(it) } }
        receivers.clear()
        netCb?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        telCb?.let { runCatching { getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(it) } }
        super.onDestroy()
    }

    private fun fire(type: String, vararg data: Pair<String, String>) {
        Engine.onEvent(applicationContext, type, mapOf(*data))
    }

    private fun reg(vararg actions: String, scheme: String? = null, onR: (Intent) -> Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onR(i)
        }
        val f = IntentFilter().apply { actions.forEach { addAction(it) }; scheme?.let { addDataScheme(it) } }
        ContextCompat.registerReceiver(this, r, f, ContextCompat.RECEIVER_EXPORTED)
        receivers += r
    }

    private fun registerAll() {
        // Carregador
        reg(Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED) { i ->
            fire("charger", "state" to if (i.action == Intent.ACTION_POWER_CONNECTED) "plug" else "unplug")
        }
        // Nível da bateria (dispara ao cruzar o valor)
        reg(Intent.ACTION_BATTERY_CHANGED) { i ->
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / scale
            if (lastLevel >= 0 && lvl != lastLevel) fire("battery", "level" to "$lvl", "prev" to "$lastLevel")
            lastLevel = lvl
        }
        // Fone com fio
        reg(AudioManager.ACTION_HEADSET_PLUG) { i ->
            fire("headphones", "state" to if (i.getIntExtra("state", 0) == 1) "plug" else "unplug")
        }
        // Bluetooth
        reg(BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED) { i ->
            val d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            val name = runCatching { d?.name }.getOrNull().orEmpty()
            val on = i.action == BluetoothDevice.ACTION_ACL_CONNECTED
            if (on) Dev.btNames.add(name) else Dev.btNames.remove(name)
            fire("bluetooth", "state" to if (on) "connect" else "disconnect", "name" to name)
        }
        // Poupança de energia
        reg("android.os.action.POWER_SAVE_MODE_CHANGED") {
            fire("power_save", "state" to if (Dev.powerSave(this)) "on" else "off")
        }
        // Modo avião
        reg(Intent.ACTION_AIRPLANE_MODE_CHANGED) { i ->
            fire("airplane", "state" to if (i.getBooleanExtra("state", false)) "on" else "off")
        }
        // Tela
        reg(Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT) { i ->
            fire("screen", "state" to when (i.action) {
                Intent.ACTION_SCREEN_ON -> "on"
                Intent.ACTION_SCREEN_OFF -> "off"
                else -> "unlock"
            })
        }
        // Modo escuro (mudança de configuração)
        lastNight = Dev.night(this)
        reg(Intent.ACTION_CONFIGURATION_CHANGED) {
            val n = Dev.night(this)
            if (lastNight != null && lastNight != n) fire("dark_mode", "state" to if (n) "on" else "off")
            lastNight = n
        }
        // Wi-Fi
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        lastSsid = Dev.wifiSsid(this@EngineService) ?: ""
                        fire("wifi", "state" to "connect", "name" to lastSsid)
                    }, 1500)
                }

                override fun onLost(network: Network) {
                    fire("wifi", "state" to "disconnect", "name" to lastSsid)
                }
            }
            cm.registerNetworkCallback(req, cb)
            netCb = cb
        }
        // Não incomodar
        reg(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
            val on = getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            fire("dnd", "state" to if (on) "on" else "off")
        }
        // Modo de som
        reg(AudioManager.RINGER_MODE_CHANGED_ACTION) {
            val m = when (getSystemService(AudioManager::class.java).ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "normal"
            }
            fire("ringer", "state" to m)
        }
        // Volume
        reg("android.media.VOLUME_CHANGED_ACTION") { fire("volume", "state" to "changed") }
        // Bluetooth / Wi-Fi ligar-desligar
        reg(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) { i ->
            when (i.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)) {
                android.bluetooth.BluetoothAdapter.STATE_ON -> fire("bt_state", "state" to "on")
                android.bluetooth.BluetoothAdapter.STATE_OFF -> fire("bt_state", "state" to "off")
            }
        }
        reg(WifiManager.WIFI_STATE_CHANGED_ACTION) { i ->
            when (i.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)) {
                WifiManager.WIFI_STATE_ENABLED -> fire("wifi_state", "state" to "on")
                WifiManager.WIFI_STATE_DISABLED -> fire("wifi_state", "state" to "off")
            }
        }
        reg(Intent.ACTION_TIMEZONE_CHANGED) { fire("timezone", "state" to "changed") }
        // Apps instalados/removidos
        reg(Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED, scheme = "package") { i ->
            if (i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return@reg
            val pkg = i.data?.schemeSpecificPart.orEmpty()
            fire("app_install", "state" to if (i.action == Intent.ACTION_PACKAGE_ADDED) "install" else "remove", "pkg" to pkg, "var.app" to pkg)
        }
        // SMS
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            reg(Telephony.Sms.Intents.SMS_RECEIVED_ACTION) { i ->
                val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i)
                val from = msgs.firstOrNull()?.originatingAddress.orEmpty()
                val body = msgs.joinToString("") { it.messageBody.orEmpty() }
                fire("sms", "from" to from, "text" to body, "var.sms_de" to from, "var.sms_texto" to body)
            }
        }
        // Chamadas
        runCatching {
            val tm = getSystemService(TelephonyManager::class.java)
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> fire("call", "state" to "ringing")
                        TelephonyManager.CALL_STATE_OFFHOOK -> fire("call", "state" to "started")
                        TelephonyManager.CALL_STATE_IDLE ->
                            if (lastCall != TelephonyManager.CALL_STATE_IDLE) fire("call", "state" to "ended")
                    }
                    lastCall = state
                }
            }
            tm.registerTelephonyCallback(mainExecutor, cb)
            telCb = cb
        }
    }

    // ───── gatilhos que só ficam ligados se alguma rotina usa ─────
    private fun trigs(type: String): List<TriggerDef> =
        Store.routines.value.filter { it.enabled }.flatMap { r -> r.triggers.filter { it.type == type } }

    private fun refreshDynamic() {
        // broadcasts personalizados
        dyn.forEach { runCatching { unregisterReceiver(it) } }
        dyn.clear()
        trigs("broadcast").map { it.params["action"].orEmpty().trim() }.filter { it.isNotEmpty() }.distinct().forEach { action ->
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    fire("broadcast", "action" to action, "var.broadcast_texto" to (i.getStringExtra("extra") ?: ""))
                }
            }
            runCatching { ContextCompat.registerReceiver(this, r, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED); dyn += r }
        }
        if (trigs("shake").isNotEmpty()) startShake() else stopShake()
        if (trigs("place").isNotEmpty()) startPlaces() else stopPlaces()
        if (trigs("app_open").isNotEmpty()) startUsage() else { usageJob?.cancel(); usageJob = null }
        if (trigs("interval").isNotEmpty() || trigs("sun").isNotEmpty()) startTick() else { tickJob?.cancel(); tickJob = null }
    }

    private fun startShake() {
        if (shakeL != null) return
        val sm = getSystemService(SensorManager::class.java)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        var last = 0L
        var hits = 0
        var first = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = Math.sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble()) / 9.81
                val now = System.currentTimeMillis()
                if (g > 2.7 && now - last > 250) {
                    last = now
                    if (now - first > 1200) { hits = 0; first = now }
                    hits++
                    if (hits >= 2) { hits = 0; fire("shake", "state" to "shake") }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME)
        shakeL = l
    }

    private fun stopShake() {
        shakeL?.let { runCatching { getSystemService(SensorManager::class.java).unregisterListener(it) } }
        shakeL = null
    }

    private fun startUsage() {
        if (usageJob?.isActive == true) return
        usageJob = scope.launch {
            val usm = getSystemService(UsageStatsManager::class.java)
            var last = System.currentTimeMillis()
            var cur = ""
            while (isActive) {
                val now = System.currentTimeMillis()
                var top: String? = null
                runCatching {
                    val ev = usm.queryEvents(last - 2000, now)
                    val e = UsageEvents.Event()
                    while (ev.hasNextEvent()) {
                        ev.getNextEvent(e)
                        if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) top = e.packageName
                    }
                }
                last = now
                val t = top
                if (t != null && t != cur && t != packageName) {
                    if (cur.isNotEmpty()) fire("app_open", "pkg" to cur, "state" to "close", "var.app" to cur)
                    cur = t
                    fire("app_open", "pkg" to t, "state" to "open", "var.app" to t)
                }
                delay(1500)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startPlaces() {
        if (locL != null) return
        val lm = getSystemService(LocationManager::class.java)
        val l = LocationListener { loc ->
            trigs("place").forEach { t ->
                val g = Geo.parse(t.params["loc"]) ?: return@forEach
                val rad = t.params["radius"]?.trim()?.toFloatOrNull() ?: 150f
                val now = Geo.dist(g.first, g.second, loc) <= rad
                val prev = inside.put(t.id, now)
                if (prev != null && prev != now) fire("place", "tid" to t.id, "state" to if (now) "arrive" else "leave")
            }
        }
        val provider = if (lm.getProviders(true).contains(LocationManager.FUSED_PROVIDER)) LocationManager.FUSED_PROVIDER else LocationManager.NETWORK_PROVIDER
        runCatching { lm.requestLocationUpdates(provider, 60_000L, 30f, l, Looper.getMainLooper()); locL = l }
    }

    private fun stopPlaces() {
        locL?.let { runCatching { getSystemService(LocationManager::class.java).removeUpdates(it) } }
        locL = null
    }

    private fun startTick() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                trigs("interval").forEach { t ->
                    val every = (t.params["minutes"]?.trim()?.toLongOrNull() ?: 30L).coerceAtLeast(1) * 60_000L
                    val last = lastRun.getOrPut(t.id) { now }
                    if (now - last >= every) { lastRun[t.id] = now; fire("interval", "tid" to t.id, "state" to "tick") }
                }
                trigs("sun").forEach { t ->
                    val g = Geo.parse(t.params["loc"]) ?: return@forEach
                    val tm = Sun.times(g.first, g.second) ?: return@forEach
                    val rise = t.params["when"] == "sunrise"
                    val target = (if (rise) tm.first else tm.second) + (t.params["offset"]?.trim()?.toLongOrNull() ?: 0L) * 60_000L
                    val key = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(now))
                    if (now in target until target + 60_000L && sunDone[t.id] != key) {
                        sunDone[t.id] = key
                        fire("sun", "tid" to t.id, "state" to (if (rise) "sunrise" else "sunset"))
                    }
                }
                delay(20_000)
            }
        }
    }
}

/** Gatilho de horário: usa alarmes exatos do Android. */
object Alarms {
    private const val PREFS = "alarms"
    private const val KEY = "codes"

    fun rescheduleAll(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Cancela os alarmes anteriores
        prefs.getStringSet(KEY, emptySet()).orEmpty().forEach { code ->
            val pi = pending(app, code.toInt(), "")
            am.cancel(pi)
        }

        val codes = mutableSetOf<String>()
        Store.routines.value.filter { it.enabled }.forEach { r ->
            r.triggers.filter { it.type == "time" }.forEach { t ->
                val whenMs = nextFire(t.params["time"] ?: "08:00", t.params["days"] ?: "1,2,3,4,5,6,7") ?: return@forEach
                val code = (r.id + t.id).hashCode()
                val pi = pending(app, code, r.id)
                if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
                codes += code.toString()
            }
        }
        prefs.edit().putStringSet(KEY, codes).apply()
    }

    private fun pending(ctx: Context, code: Int, routineId: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, AlarmReceiver::class.java).putExtra("routineId", routineId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Próximo momento (em ms) que bate com a hora e os dias escolhidos. */
    fun nextFire(time: String, days: String): Long? {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val set = days.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        if (set.isEmpty()) return null
        val now = System.currentTimeMillis()
        for (add in 0..7) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, add)
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis > now && Dev.isoDay(c) in set) return c.timeInMillis
        }
        return null
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getStringExtra("routineId").orEmpty()
        Store.get(id)?.takeIf { it.enabled }?.let { Engine.run(context, it, "horário") }
        Alarms.rescheduleAll(context)
        Svc.ensure(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Engine.onEvent(context, "boot", mapOf("state" to "boot"))
        Alarms.rescheduleAll(context)
        Svc.ensure(context)
    }
}
