@file:OptIn(ExperimentalMaterial3Api::class)

package app.fluxo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Svc.ensure(this)
        setContent { FluxoTheme { FluxoApp() } }
    }
}

@Composable
fun FluxoTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}

sealed interface Screen {
    data object Home : Screen
    data class Edit(val r: Routine) : Screen
    data object Setup : Screen
    data object Log : Screen
}

@Composable
fun FluxoApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            onEdit = { screen = Screen.Edit(it) },
            onSetup = { screen = Screen.Setup },
            onLog = { screen = Screen.Log },
        )
        is Screen.Edit -> EditorScreen(s.r) { screen = Screen.Home }
        Screen.Setup -> SetupScreen { screen = Screen.Home }
        Screen.Log -> LogScreen { screen = Screen.Home }
    }
}

@Composable
fun HomeScreen(onEdit: (Routine) -> Unit, onSetup: () -> Unit, onLog: () -> Unit) {
    val ctx = LocalContext.current
    val routines by Store.routines.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fluxo", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = onLog) { Icon(Icons.Outlined.History, "Registro") }
                    IconButton(onClick = onSetup) { Icon(Icons.Outlined.Settings, "Configuração") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(Routine()) }) { Icon(Icons.Filled.Add, "Nova rotina") }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!Sh.available()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().clickable(onClick = onSetup),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Shizuku não está pronto", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Várias ações (Wi-Fi, dados, Não incomodar, brilho...) precisam dele. Toque aqui para configurar.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (routines.isEmpty()) {
                item {
                    Text(
                        "Nenhuma rotina ainda. Toque em + para criar. Cada rotina tem um QUANDO (gatilhos) e um FAZER (ações, com blocos Se/Senão).",
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(routines, key = { it.id }) { r ->
                Card(
                    Modifier.fillMaxWidth().clickable { onEdit(r) },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (r.enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Switch(checked = r.enabled, onCheckedChange = { Store.save(r.copy(enabled = it)) })
                        }
                        if (r.triggers.isEmpty()) {
                            Text("Sem gatilho — só roda no ▶", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                r.triggers.take(3).forEach { t ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconChip(Catalog.find(Kind.TRIGGER, t.type)?.category)
                                        Spacer(Modifier.width(10.dp))
                                        Text(Catalog.label(Kind.TRIGGER, t.type, t.params), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (r.triggers.size > 3) Text("+${r.triggers.size - 3} gatilhos", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${r.steps.size} passo(s)", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            FilledTonalButton(onClick = { Engine.run(ctx, r, "manual") }) {
                                Icon(Icons.Filled.PlayArrow, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Rodar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogScreen(onBack: () -> Unit) {
    val log by RunLog.entries.collectAsState()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Registro") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            if (log.isEmpty()) item { Text("Nada rodou ainda. Toque em ▶ em uma rotina para testar.") }
            items(log) { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}
