@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.fluxo

import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager

// ───────────────────────── helpers ─────────────────────────

private fun <T> List<T>.replaced(i: Int, x: T): List<T> = toMutableList().also { it[i] = x }
private fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().also {
    val t = it[a]; it[a] = it[b]; it[b] = t
}

/** Janelas abertas pelo editor. */
sealed interface Dlg {
    /** Escolher um bloco do catálogo e depois preencher os campos. */
    class Pick(val kind: Kind, val onPick: (Spec, Map<String, String>) -> Unit) : Dlg
    /** Editar os campos de um bloco existente. */
    class Params(val spec: Spec, val initial: Map<String, String>, val onOk: (Map<String, String>) -> Unit) : Dlg
    /** Editar a condição de um bloco SE. */
    class CondEdit(val cond: Cond, val onOk: (Cond) -> Unit) : Dlg
}

@Composable
fun DialogHost(dlg: Dlg?, close: () -> Unit) {
    when (dlg) {
        null -> Unit
        is Dlg.Pick -> {
            var chosen by remember(dlg) { mutableStateOf<Spec?>(null) }
            val ch = chosen
            if (ch == null) {
                PickerSheet(dlg.kind, close) { s ->
                    if (s.params.isEmpty()) {
                        dlg.onPick(s, emptyMap()); close()
                    } else chosen = s
                }
            } else {
                ParamDialog(ch, Catalog.defaults(ch), close) { p -> dlg.onPick(ch, p); close() }
            }
        }
        is Dlg.Params -> ParamDialog(dlg.spec, dlg.initial, close) { p -> dlg.onOk(p); close() }
        is Dlg.CondEdit -> CondDialog(dlg.cond, close) { c -> dlg.onOk(c); close() }
    }
}

// ───────────────────────── tela do editor ─────────────────────────

@Composable
fun EditorScreen(initial: Routine, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var r by remember { mutableStateOf(initial) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }

    fun saveAndClose() {
        val isNew = Store.get(r.id) == null
        if (!(isNew && r.triggers.isEmpty() && r.steps.isEmpty())) {
            Store.save(r.copy(name = r.name.ifBlank { "Rotina" }))
        }
        onClose()
    }
    BackHandler { saveAndClose() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Editar rotina") },
            navigationIcon = { IconButton(onClick = { saveAndClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = {
                if (Store.get(r.id) != null) {
                    IconButton(onClick = { Store.delete(r.id); onClose() }) { Icon(Icons.Outlined.Delete, "Apagar rotina") }
                }
                TextButton(onClick = { saveAndClose() }) { Text("Salvar") }
            },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(r.name, { r = r.copy(name = it) }, label = { Text("Nome da rotina") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            SectionTitle("QUANDO", "A rotina roda se QUALQUER um destes gatilhos acontecer.")
            r.triggers.forEachIndexed { i, t ->
                val spec = Catalog.find(Kind.TRIGGER, t.type)
                Card(Modifier.fillMaxWidth().clickable(enabled = spec != null && spec.params.isNotEmpty()) {
                    spec?.let { sp -> dlg = Dlg.Params(sp, t.params) { p -> r = r.copy(triggers = r.triggers.replaced(i, t.copy(params = p))) } }
                }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Catalog.label(Kind.TRIGGER, t.type, t.params), Modifier.weight(1f))
                        IconButton(onClick = { spec?.let { dlg = Dlg.Params(it, t.params) { _ -> } } }) { Icon(Icons.Outlined.Info, "Ajuda") }
                        IconButton(onClick = { r = r.copy(triggers = r.triggers.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.Delete, "Remover") }
                    }
                }
            }
            OutlinedButton(onClick = {
                dlg = Dlg.Pick(Kind.TRIGGER) { sp, p -> r = r.copy(triggers = r.triggers + TriggerDef(sp.id, p)) }
            }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Adicionar gatilho") }

            SectionTitle("FAZER", "Os passos rodam de cima para baixo. Use Se/Senão para decidir o caminho.")
            StepList(r.steps, { dlg = it }) { r = r.copy(steps = it) }
            Spacer(Modifier.width(24.dp))
        }
    }
    DialogHost(dlg) { dlg = null }
}

@Composable
private fun SectionTitle(t: String, hint: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(hint, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Tag(t: String) {
    Text(t, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
}

private fun condText(c: Cond): String =
    if (c.items.isEmpty()) "(sempre verdadeiro)"
    else c.items.joinToString(if (c.op == "or") "  OU  " else "  E  ") {
        (if (it.negate) "NÃO " else "") + Catalog.label(Kind.CONDITION, it.type, it.params)
    }

@Composable
private fun StepList(steps: List<Step>, open: (Dlg) -> Unit, onChange: (List<Step>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, s ->
            val isIf = s.kind == "if"
            val title = if (isIf) "SE  " + condText(s.cond) else Catalog.label(Kind.ACTION, s.type, s.params)
            val spec = if (isIf) null else Catalog.find(Kind.ACTION, s.type)
            Card(
                Modifier.fillMaxWidth().clickable {
                    if (isIf) open(Dlg.CondEdit(s.cond) { c -> onChange(steps.replaced(i, s.copy(cond = c))) })
                    else spec?.let { sp -> open(Dlg.Params(sp, s.params) { p -> onChange(steps.replaced(i, s.copy(params = p))) }) }
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (isIf) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (i > 0) IconButton(onClick = { onChange(steps.swapped(i, i - 1)) }) { Icon(Icons.Filled.KeyboardArrowUp, "Subir") }
                    if (i < steps.lastIndex) IconButton(onClick = { onChange(steps.swapped(i, i + 1)) }) { Icon(Icons.Filled.KeyboardArrowDown, "Descer") }
                    IconButton(onClick = { onChange(steps.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.Delete, "Remover") }
                }
            }
            if (isIf) {
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("ENTÃO (se for verdadeiro)")
                    StepList(s.then, open) { n -> onChange(steps.replaced(i, s.copy(then = n))) }
                    Tag("SENÃO (se for falso)")
                    StepList(s.otherwise, open) { n -> onChange(steps.replaced(i, s.copy(otherwise = n))) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                open(Dlg.Pick(Kind.ACTION) { sp, p -> onChange(steps + Step(type = sp.id, params = p)) })
            }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Ação") }
            OutlinedButton(onClick = {
                val st = Step(kind = "if")
                onChange(steps + st)
                open(Dlg.CondEdit(st.cond) { c -> onChange(steps + st.copy(cond = c)) })
            }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Se / Senão") }
        }
    }
}

// ───────────────────────── caixinha de ajuda ─────────────────────────

@Composable
fun InfoBox(s: Spec) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null)
                Spacer(Modifier.width(8.dp))
                Text(s.title, style = MaterialTheme.typography.titleSmall)
            }
            Labeled("Para que serve", s.summary)
            Labeled("Como usar", s.howTo)
            Labeled("Exemplo", s.example)
            if (s.shizuku) Text("Precisa do Shizuku ativo.", style = MaterialTheme.typography.labelSmall)
            if (s.advanced) Text("Opção avançada.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun InfoDialog(s: Spec, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { InfoBox(s) } },
    )
}

// ───────────────────────── seletor de blocos ─────────────────────────

@Composable
fun PickerSheet(kind: Kind, onDismiss: () -> Unit, onSelect: (Spec) -> Unit) {
    var q by remember { mutableStateOf("") }
    var adv by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Spec?>(null) }
    val list = Catalog.all.filter {
        it.kind == kind && (adv || !it.advanced) &&
            (q.isBlank() || (it.title + " " + it.summary + " " + it.category).contains(q, ignoreCase = true))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(q, { q = it }, label = { Text("Buscar") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Switch(adv, { adv = it })
                Spacer(Modifier.width(8.dp))
                Text("Mostrar opções avançadas")
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                list.groupBy { it.category }.forEach { (cat, specs) ->
                    item(key = "h$cat") {
                        Text(cat, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                    }
                    items(specs, key = { it.kind.name + it.id }) { sp ->
                        ListItem(
                            headlineContent = { Text(sp.title) },
                            supportingContent = { Text(sp.summary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { IconButton(onClick = { info = sp }) { Icon(Icons.Outlined.Info, "Ajuda") } },
                            modifier = Modifier.clickable { onSelect(sp) },
                        )
                    }
                }
            }
        }
    }
    info?.let { InfoDialog(it) { info = null } }
}

// ───────────────────────── campos de um bloco ─────────────────────────

@Composable
fun ParamDialog(spec: Spec, initial: Map<String, String>, onDismiss: () -> Unit, onOk: (Map<String, String>) -> Unit) {
    val values = remember {
        mutableStateMapOf<String, String>().apply { spec.params.forEach { put(it.key, initial[it.key] ?: it.def) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoBox(spec)
                spec.params.forEach { p -> ParamField(p, values[p.key].orEmpty()) { values[p.key] = it } }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(values.toMap()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private val DAY_NAMES = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

@Composable
private fun ParamField(p: P, value: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(p.label, style = MaterialTheme.typography.labelMedium)
        when (p.type) {
            PT.TEXT, PT.NUMBER, PT.MULTILINE -> OutlinedTextField(
                value, onChange, modifier = Modifier.fillMaxWidth(),
                placeholder = { if (p.hint.isNotBlank()) Text(p.hint) },
                singleLine = p.type != PT.MULTILINE,
                keyboardOptions = KeyboardOptions(keyboardType = if (p.type == PT.NUMBER) KeyboardType.Number else KeyboardType.Text),
            )
            PT.CHOICE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.options.forEach { (k, label) ->
                    FilterChip(selected = value == k, onClick = { onChange(k) }, label = { Text(label) })
                }
            }
            PT.TIME -> OutlinedButton(onClick = {
                val t = value.split(":")
                val h = t.getOrNull(0)?.toIntOrNull() ?: 8
                val m = t.getOrNull(1)?.toIntOrNull() ?: 0
                TimePickerDialog(ctx, { _, hh, mm -> onChange("%02d:%02d".format(hh, mm)) }, h, m, true).show()
            }) { Text(value.ifBlank { "Escolher hora" }) }
            PT.DAYS -> {
                val sel = value.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_NAMES.forEachIndexed { i, n ->
                        val d = i + 1
                        FilterChip(
                            selected = d in sel,
                            onClick = { onChange(((if (d in sel) sel - d else sel + d).sorted()).joinToString(",")) },
                            label = { Text(n) },
                        )
                    }
                }
            }
            PT.APP -> {
                var show by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { show = true }) {
                    Text(if (value.isBlank()) "Escolher app" else Dev.appLabel(ctx, value))
                }
                if (show) AppPickerDialog({ onChange(it); show = false }) { show = false }
            }
            PT.BTDEVICE -> {
                val bonded = remember { Dev.bondedNames(ctx) }
                OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(p.hint.ifBlank { "nome do aparelho" }) })
                if (bonded.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        bonded.forEach { n -> FilterChip(selected = value == n, onClick = { onChange(n) }, label = { Text(n) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(i, PackageManager.ResolveInfoFlags.of(0))
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolher app") },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(apps.filter { q.isBlank() || it.first.contains(q, ignoreCase = true) }, key = { it.second }) { (label, pkg) ->
                        ListItem(headlineContent = { Text(label) }, supportingContent = { Text(pkg, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.clickable { onPick(pkg) })
                    }
                }
            }
        },
    )
}

// ───────────────────────── editor de condições ─────────────────────────

@Composable
fun CondDialog(initial: Cond, onDismiss: () -> Unit, onOk: (Cond) -> Unit) {
    var c by remember { mutableStateOf(initial) }
    var sub by remember { mutableStateOf<Dlg?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Condição do SE") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "O bloco SE roda o ENTÃO quando a condição é verdadeira; se for falsa, roda o SENÃO. " +
                        "Com várias condições, escolha E (todas precisam ser verdadeiras) ou OU (basta uma). " +
                        "Marque NÃO para inverter uma condição.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(c.op == "and", { c = c.copy(op = "and") }, { Text("E (todas)") })
                    FilterChip(c.op == "or", { c = c.copy(op = "or") }, { Text("OU (qualquer uma)") })
                }
                c.items.forEachIndexed { i, l ->
                    val spec = Catalog.find(Kind.CONDITION, l.type)
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(l.negate, { c = c.copy(items = c.items.replaced(i, l.copy(negate = it))) })
                            Text("NÃO", style = MaterialTheme.typography.labelSmall)
                            Text(
                                Catalog.label(Kind.CONDITION, l.type, l.params),
                                Modifier.weight(1f).padding(start = 8.dp).clickable(enabled = spec != null) {
                                    spec?.let { sp -> sub = Dlg.Params(sp, l.params) { p -> c = c.copy(items = c.items.replaced(i, l.copy(params = p))) } }
                                },
                            )
                            IconButton(onClick = { c = c.copy(items = c.items.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.Delete, "Remover") }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    sub = Dlg.Pick(Kind.CONDITION) { sp, p -> c = c.copy(items = c.items + CondLeaf(sp.id, p)) }
                }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Condição") }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(c) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    DialogHost(sub) { sub = null }
}
