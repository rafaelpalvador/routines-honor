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
import java.util.Calendar

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        receivers.forEach { runCatching { unregisterReceiver(it) } }
        receivers.clear()
        netCb?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        telCb?.let { runCatching { getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(it) } }
        super.onDestroy()
    }

    private fun fire(type: String, vararg data: Pair<String, String>) {
        Engine.onEvent(applicationContext, type, mapOf(*data))
    }

    private fun reg(vararg actions: String, onR: (Intent) -> Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onR(i)
        }
        val f = IntentFilter().apply { actions.forEach { addAction(it) } }
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
        Alarms.rescheduleAll(context)
        Svc.ensure(context)
    }
}
