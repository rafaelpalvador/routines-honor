package app.fluxo

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Lê as notificações que chegam (gatilho "Notificação recebida"). */
class NotifService : NotificationListenerService() {
    companion object { @Volatile var instance: NotifService? = null }
    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { instance = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        Store.init(applicationContext)
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        Engine.onEvent(
            applicationContext, "notification",
            mapOf(
                "pkg" to sbn.packageName, "title" to title, "text" to text,
                "var.notif_app" to sbn.packageName, "var.notif_titulo" to title, "var.notif_texto" to text,
            ),
        )
    }
}
