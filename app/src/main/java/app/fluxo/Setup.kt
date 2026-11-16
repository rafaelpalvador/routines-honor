@file:OptIn(ExperimentalMaterial3Api::class)

package app.fluxo

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

private data class Item(
    val title: String,
    val desc: String,
    val ok: Boolean?,          // null = não dá para verificar
    val button: String,
    val action: () -> Unit,
)

@Composable
fun SetupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun open(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    val pkgUri = Uri.parse("package:${ctx.packageName}")

    @Suppress("UNUSED_VARIABLE") val refresh = tick // força recomposição ao voltar das configurações

    val rows = listOf(
        Item(
            "Shizuku",
            if (!Sh.running()) "O Shizuku não está rodando. Abra o app Shizuku e inicie o serviço (depuração sem fio)."
            else "Dá ao Fluxo o poder de ligar/desligar Wi-Fi, dados, Não incomodar, brilho e mais.",
            Sh.available(),
            if (Sh.running()) "Pedir permissão" else "Abrir Shizuku",
        ) {
            if (Sh.running()) Sh.requestPermission()
            else ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { open(it) }
        },
        Item("Notificações", "Para avisos e para mostrar que o Fluxo está ativo.",
            has(Manifest.permission.POST_NOTIFICATIONS), "Permitir") { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        Item("Localização", "O Android só informa o nome do Wi-Fi se o app tiver localização. Necessário para gatilhos de Wi-Fi por nome.",
            has(Manifest.permission.ACCESS_FINE_LOCATION), "Permitir") { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        Item("Localização em segundo plano", "Para os gatilhos 'Chegar/sair de um lugar'. Com o Shizuku é 1 toque; sem ele, escolha 'Permitir o tempo todo' nas configurações.",
            has(Manifest.permission.ACCESS_BACKGROUND_LOCATION), if (Sh.available()) "Conceder (Shizuku)" else "Abrir configurações") {
            if (Sh.available()) Thread { Sh.run("pm grant ${ctx.packageName} android.permission.ACCESS_BACKGROUND_LOCATION"); tick++ }.start()
            else open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
        },
        Item("Acesso ao uso de apps", "Para o gatilho 'App aberto/fechado'.",
            ctx.getSystemService(android.app.AppOpsManager::class.java)
                .unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), ctx.packageName) == android.app.AppOpsManager.MODE_ALLOWED,
            if (Sh.available()) "Conceder (Shizuku)" else "Abrir configurações") {
            if (Sh.available()) Thread { Sh.run("appops set ${ctx.packageName} GET_USAGE_STATS allow"); tick++ }.start()
            else open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        },
        Item("Acesso às notificações", "Para o gatilho 'Notificação recebida'.",
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName),
            if (Sh.available()) "Conceder (Shizuku)" else "Abrir configurações") {
            if (Sh.available()) Thread { Sh.run("cmd notification allow_listener ${ctx.packageName}/${ctx.packageName}.NotifService"); tick++ }.start()
            else open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },
        Item("Receber SMS", "Para o gatilho 'SMS recebido' (depois de permitir, feche e abra o Fluxo).",
            has(Manifest.permission.RECEIVE_SMS), "Permitir") { permLauncher.launch(Manifest.permission.RECEIVE_SMS) },
        Item("Bluetooth", "Para saber qual aparelho Bluetooth conectou.",
            has(Manifest.permission.BLUETOOTH_CONNECT), "Permitir") { permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
        Item("Telefone", "Para gatilhos de chamada (tocando, atendida, terminou).",
            has(Manifest.permission.READ_PHONE_STATE), "Permitir") { permLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
        Item("Câmera (lanterna)", "Necessária para ligar a lanterna.",
            has(Manifest.permission.CAMERA), "Permitir") { permLauncher.launch(Manifest.permission.CAMERA) },
        Item("SMS (opcional)", "Só para o bloco 'Enviar SMS'.",
            has(Manifest.permission.SEND_SMS), "Permitir") { permLauncher.launch(Manifest.permission.SEND_SMS) },
        Item("Calendário", "Para 'Evento do calendário' e os blocos de calendário.",
            has(Manifest.permission.READ_CALENDAR) && has(Manifest.permission.WRITE_CALENDAR), "Permitir") { permLauncher.launch(Manifest.permission.READ_CALENDAR); permLauncher.launch(Manifest.permission.WRITE_CALENDAR) },
        Item("Contatos (opcional)", "Só para 'Buscar contato'.",
            has(Manifest.permission.READ_CONTACTS), "Permitir") { permLauncher.launch(Manifest.permission.READ_CONTACTS) },
        Item("Bluetooth (procurar)", "Só para 'Procurar aparelhos Bluetooth'.",
            has(Manifest.permission.BLUETOOTH_SCAN), "Permitir") { permLauncher.launch(Manifest.permission.BLUETOOTH_SCAN) },
        Item("Fotos (opcional)", "Para ler/editar imagens (OCR, QR, papel de parede).",
            has(Manifest.permission.READ_MEDIA_IMAGES), "Permitir") { permLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES) },
        Item("Arquivos de áudio (opcional)", "Só para o bloco 'Tocar um som'.",
            has(Manifest.permission.READ_MEDIA_AUDIO), "Permitir") { permLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO) },
        Item("Alarmes e lembretes", "Necessário para o gatilho de horário rodar na hora certa.",
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(), "Abrir") {
            open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))
        },
        Item("Sem otimização de bateria", "Evita que o sistema pare o Fluxo em segundo plano.",
            ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName), "Permitir") {
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
        },
        Item("Acesso ao Não incomodar", "Necessário para o bloco 'Modo de som' colocar em Silencioso.",
            ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted, "Abrir") {
            open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        },
        Item(
            "Honor: início de apps",
            "No Honor, abra Configurações → Bateria → Início de apps (App launch) → Fluxo → Gerenciar manualmente e ligue as TRÊS opções: " +
                "Início automático, Início secundário e Executar em segundo plano. Sem isso o sistema pode matar o Fluxo e as rotinas param.",
            null, "Abrir info do app",
        ) { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)) },
    )

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Configuração") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
        )
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rows, key = { it.title }) { it ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (it.ok) {
                            true -> MaterialTheme.colorScheme.primaryContainer
                            false -> MaterialTheme.colorScheme.errorContainer
                            null -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(it.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(
                                when (it.ok) { true -> "✔ OK"; false -> "Pendente"; null -> "Manual" },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Text(it.desc, style = MaterialTheme.typography.bodySmall)
                        if (it.ok != true) OutlinedButton(onClick = it.action) { Text(it.button) }
                    }
                }
            }
        }
    }
}
