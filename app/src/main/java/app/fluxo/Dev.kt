package app.fluxo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** Leituras do estado atual do aparelho (usadas pelas condições e pelo serviço). */
object Dev {
    /** Nomes de aparelhos Bluetooth conectados (mantido pelo EngineService). */
    val btNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun battery(ctx: Context): Int =
        ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun charging(ctx: Context): Boolean = ctx.getSystemService(BatteryManager::class.java).isCharging

    /** null = sem Wi-Fi; "" = Wi-Fi conectado mas sem permissão para ler o nome. */
    @Suppress("DEPRECATION")
    fun wifiSsid(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val raw = runCatching {
            ctx.applicationContext.getSystemService(WifiManager::class.java).connectionInfo?.ssid
        }.getOrNull() ?: return ""
        val s = raw.trim('"')
        return if (s == "<unknown ssid>") "" else s
    }

    fun headphones(ctx: Context): Boolean {
        val types = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        return ctx.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    }

    fun airplane(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun night(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun powerSave(ctx: Context): Boolean = ctx.getSystemService(PowerManager::class.java).isPowerSaveMode

    @SuppressLint("MissingPermission")
    fun btConnected(ctx: Context, name: String): Boolean {
        if (name.isBlank()) { if (btNames.isNotEmpty()) return true } else if (btNames.contains(name)) return true
        val ad = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        val devs = runCatching { ad.bondedDevices }.getOrNull() ?: return false
        return devs.any { d ->
            (name.isBlank() || runCatching { d.name }.getOrNull() == name) &&
                runCatching { d.javaClass.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)
        }
    }

    @SuppressLint("MissingPermission")
    fun bondedNames(ctx: Context): List<String> = runCatching {
        ctx.getSystemService(BluetoothManager::class.java).adapter.bondedDevices.mapNotNull { it.name }.sorted()
    }.getOrDefault(emptyList())

    fun appLabel(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Dia da semana ISO: 1 = segunda ... 7 = domingo. */
    fun isoDay(c: Calendar = Calendar.getInstance()): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

    fun minutes(hhmm: String): Int = hhmm.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
}
