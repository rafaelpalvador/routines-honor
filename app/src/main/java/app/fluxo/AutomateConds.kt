package app.fluxo

import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager

/** Condições dos blocos avançados (Automate). Devolve null se o tipo não for daqui. */
object AutomateConds {
    private fun s(p: Map<String, String>, k: String) = p[k].orEmpty().trim()
    private fun yes(p: Map<String, String>, v: Boolean) = if (p["state"] == "no") !v else v
    private fun cmp(p: Map<String, String>, actual: Double): Boolean {
        val x = s(p, "value").toDoubleOrNull() ?: return false
        return when (p["op"]) {
            "below" -> actual < x
            "equal" -> actual == x
            else -> actual > x
        }
    }
    private fun sh(c: String) = Actions.sh(c)
    private fun q(x: String) = Actions.q(x)

    fun eval(ctx: Context, type: String, p: Map<String, String>): Boolean? = when (type) {
        "app_installed" -> yes(p, sh("pm path ${q(s(p, "pkg"))}").isNotBlank())
        "app_foreground" -> yes(p, sh("dumpsys activity activities | grep -E 'topResumedActivity|ResumedActivity'").contains(s(p, "pkg")))
        "app_notif_enabled" -> yes(p, sh("cmd appops get ${q(s(p, "pkg"))} POST_NOTIFICATION").contains("allow"))
        "appop_is" -> sh("cmd appops get ${q(s(p, "pkg"))} ${q(s(p, "op"))}").contains(s(p, "mode"))

        "doze_active" -> yes(p, sh("dumpsys deviceidle get deep").trim() == "IDLE")
        "screen_on" -> yes(p, ctx.getSystemService(PowerManager::class.java).isInteractive)
        "plugged_source" -> {
            val pl = batteryIntent(ctx)?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val r = when (p["source"]) {
                "ac" -> pl and BatteryManager.BATTERY_PLUGGED_AC != 0
                "usb" -> pl and BatteryManager.BATTERY_PLUGGED_USB != 0
                "wireless" -> pl and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0
                else -> pl != 0
            }
            yes(p, r)
        }

        "stream_muted" -> yes(p, ctx.getSystemService(AudioManager::class.java).isStreamMute(streamId(p["stream"])))
        "volume_level" -> {
            val am = ctx.getSystemService(AudioManager::class.java)
            val st = streamId(p["stream"])
            val max = am.getStreamMaxVolume(st).coerceAtLeast(1)
            cmp(p, am.getStreamVolume(st) * 100.0 / max)
        }
        "media_playing" -> yes(p, ctx.getSystemService(AudioManager::class.java).isMusicActive)
        "mic_muted" -> yes(p, ctx.getSystemService(AudioManager::class.java).isMicrophoneMute)
        "speaker_on" -> {
            @Suppress("DEPRECATION")
            yes(p, ctx.getSystemService(AudioManager::class.java).isSpeakerphoneOn)
        }

        "bt_enabled" -> yes(p, ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true)
        "wifi_enabled" -> yes(p, ctx.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled)
        "mobile_data_enabled" -> yes(p, Settings.Global.getInt(ctx.contentResolver, "mobile_data", 1) == 1)
        "nfc_enabled" -> yes(p, NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true)
        "wifi_signal" -> {
            @Suppress("DEPRECATION")
            val rssi = ctx.applicationContext.getSystemService(WifiManager::class.java).connectionInfo?.rssi ?: -127
            cmp(p, rssi.toDouble())
        }
        "ping" -> {
            val ok = runCatching {
                Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", "2", s(p, "host"))).waitFor() == 0
            }.getOrDefault(false)
            yes(p, ok)
        }

        "file_exists" -> yes(p, sh("[ -e ${q(s(p, "path"))} ] && echo yes").contains("yes"))
        "storage_space" -> {
            val st = StatFs(Environment.getDataDirectory().path)
            cmp(p, st.availableBytes / 1_000_000_000.0)
        }

        "android_version" -> cmp(p, (Build.VERSION.RELEASE.substringBefore('.').toIntOrNull() ?: 0).toDouble())

        "device_locked" -> yes(p, ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        "device_secure" -> yes(p, ctx.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        "orientation_is" -> {
            val rot = ctx.getSystemService(DisplayManager::class.java).getDisplay(0)?.rotation ?: 0
            val land = rot == 1 || rot == 3
            if (p["mode"] == "landscape") land else !land
        }

        "location_enabled" -> yes(p, ctx.getSystemService(LocationManager::class.java).isLocationEnabled)

        "sensor_value" -> readSensor(ctx, p["sensor"] ?: "light")?.let { cmp(p, it.toDouble()) } ?: false

        "ringer_is" -> {
            val m = ctx.getSystemService(AudioManager::class.java).ringerMode
            when (p["mode"]) {
                "silent" -> m == AudioManager.RINGER_MODE_SILENT
                "vibrate" -> m == AudioManager.RINGER_MODE_VIBRATE
                else -> m == AudioManager.RINGER_MODE_NORMAL
            }
        }
        "brightness_level" -> cmp(p, Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0) * 100.0 / 255.0)
        "dnd_is" -> yes(p, ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)

        "call_state_is" -> {
            @Suppress("DEPRECATION")
            val cs = ctx.getSystemService(TelephonyManager::class.java).callState
            when (p["mode"]) {
                "ringing" -> cs == TelephonyManager.CALL_STATE_RINGING
                "offhook" -> cs == TelephonyManager.CALL_STATE_OFFHOOK
                else -> cs == TelephonyManager.CALL_STATE_IDLE
            }
        }
        "operator_is" -> ctx.getSystemService(TelephonyManager::class.java).networkOperatorName.contains(s(p, "name"), ignoreCase = true)
        "roaming" -> yes(p, ctx.getSystemService(TelephonyManager::class.java).isNetworkRoaming)

        else -> null
    }
}
