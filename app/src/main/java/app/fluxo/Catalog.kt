package app.fluxo

enum class Kind { TRIGGER, CONDITION, ACTION }

enum class PT { TEXT, NUMBER, CHOICE, TIME, DAYS, APP, BTDEVICE, MULTILINE, LOCATION }

/** Um campo que o usuário preenche. */
data class P(
    val key: String,
    val label: String,
    val type: PT = PT.TEXT,
    val options: List<Pair<String, String>> = emptyList(),
    val def: String = "",
    val hint: String = "",
)

/**
 * Descreve UM bloco (gatilho, condição ou ação).
 * A tela inteira é gerada a partir daqui: para adicionar uma opção nova
 * basta criar um Spec neste arquivo (e, se for ação, tratar o `id` em Actions.kt).
 */
data class Spec(
    val kind: Kind,
    val id: String,
    val category: String,
    val title: String,
    val summary: String,   // Para que serve
    val howTo: String,     // Como usar
    val example: String,   // Exemplo
    val params: List<P> = emptyList(),
    val advanced: Boolean = false,
    val shizuku: Boolean = false,
)

object Catalog {
    private val ONOFF = listOf("on" to "Ligar", "off" to "Desligar")
    private val YESNO = listOf("yes" to "Sim", "no" to "Não")
    private val PLUG = listOf("plug" to "Conectar", "unplug" to "Desconectar")
    private val CONN = listOf("connect" to "Conectar", "disconnect" to "Desconectar")
    private val ONOFF_S = listOf("on" to "Ligado", "off" to "Desligado")
    private const val ALLDAYS = "1,2,3,4,5,6,7"

    private fun trig(id: String, cat: String, title: String, summary: String, howTo: String, example: String, vararg p: P, advanced: Boolean = false) =
        Spec(Kind.TRIGGER, id, cat, title, summary, howTo, example, p.toList(), advanced)

    private fun cond(id: String, cat: String, title: String, summary: String, howTo: String, example: String, vararg p: P, advanced: Boolean = false) =
        Spec(Kind.CONDITION, id, cat, title, summary, howTo, example, p.toList(), advanced)

    private fun act(id: String, cat: String, title: String, summary: String, howTo: String, example: String, vararg p: P, shizuku: Boolean = false, advanced: Boolean = false) =
        Spec(Kind.ACTION, id, cat, title, summary, howTo, example, p.toList(), advanced, shizuku)

    val all: List<Spec> = listOf(
        // ───────────────────────── GATILHOS (QUANDO) ─────────────────────────
        trig("manual", "Manual", "Manual (botão ▶)",
            "Roda só quando você toca no botão ▶ da rotina.",
            "Não precisa configurar nada. Na lista de rotinas, toque em ▶. Ótimo para testar uma rotina ou para rotinas que você quer disparar na hora.",
            "Rotina 'Modo reunião': Não incomodar + volume 0. Você toca em ▶ quando entra na reunião."),
        trig("time", "Tempo", "Horário",
            "Roda no horário escolhido, todo dia ou só em alguns dias da semana.",
            "Escolha a hora e marque os dias. Precisa da permissão 'Alarmes e lembretes' (tela Configuração).",
            "Seg a Sex às 22:00: ligar Não incomodar e baixar o brilho.",
            P("time", "Horário", PT.TIME, def = "08:00"),
            P("days", "Dias", PT.DAYS, def = ALLDAYS)),
        trig("wifi", "Conexões", "Wi-Fi",
            "Dispara quando o celular conecta ou desconecta de uma rede Wi-Fi.",
            "Digite o nome exato da rede (SSID). Deixe vazio para valer para qualquer rede. Precisa da permissão de localização, que o Android exige para revelar o nome da rede.",
            "Conectou no Wi-Fi 'Casa' → desligar os dados móveis.",
            P("network", "Nome da rede", PT.TEXT, hint = "vazio = qualquer rede"),
            P("when", "Quando", PT.CHOICE, CONN, "connect")),
        trig("bluetooth", "Conexões", "Bluetooth",
            "Dispara quando um aparelho Bluetooth conecta ou desconecta (fone, relógio, carro, controle).",
            "Escolha um aparelho já pareado ou deixe vazio para qualquer aparelho. Precisa da permissão de Bluetooth.",
            "Conectou no fone → volume de mídia em 40%.",
            P("device", "Aparelho", PT.BTDEVICE, hint = "vazio = qualquer"),
            P("when", "Quando", PT.CHOICE, CONN, "connect")),
        trig("airplane", "Conexões", "Modo avião",
            "Dispara quando o modo avião é ligado ou desligado.",
            "Escolha se quer reagir ao ligar ou ao desligar.",
            "Saiu do modo avião → ligar o Wi-Fi.",
            P("when", "Quando", PT.CHOICE, ONOFF_S, "off")),
        trig("headphones", "Dispositivo", "Fone com fio",
            "Dispara quando um fone de cabo (P2 ou USB-C) é ligado ou removido.",
            "Escolha conectar ou desconectar.",
            "Tirou o fone do cabo → pausar com volume de mídia 0.",
            P("when", "Quando", PT.CHOICE, PLUG, "plug")),
        trig("charger", "Dispositivo", "Carregador",
            "Dispara quando o carregador é ligado ou desligado.",
            "Escolha conectar ou desconectar.",
            "Ligou no carregador → desativar a poupança de energia.",
            P("when", "Quando", PT.CHOICE, PLUG, "plug")),
        trig("battery", "Dispositivo", "Nível da bateria",
            "Dispara no momento em que a bateria cruza um valor (cai abaixo ou sobe acima).",
            "Escolha 'abaixo de' ou 'acima de' e o percentual. Só dispara na hora em que cruza o valor, não toda hora.",
            "Bateria abaixo de 20% → ligar poupança de energia e baixar o brilho.",
            P("when", "Quando ficar", PT.CHOICE, listOf("below" to "Abaixo de", "above" to "Acima de"), "below"),
            P("level", "Percentual", PT.NUMBER, def = "20")),
        trig("power_save", "Dispositivo", "Poupança de energia",
            "Dispara quando a poupança de energia é ligada ou desligada.",
            "Escolha ligada ou desligada.",
            "Poupança ligou → desligar o Wi-Fi.",
            P("when", "Quando", PT.CHOICE, ONOFF_S, "on")),
        trig("dark_mode", "Dispositivo", "Modo escuro",
            "Dispara quando o tema do sistema muda para escuro ou claro.",
            "Escolha ligado (escuro) ou desligado (claro).",
            "Ficou escuro → baixar o brilho.",
            P("when", "Quando", PT.CHOICE, ONOFF_S, "on")),
        trig("screen", "Dispositivo", "Tela",
            "Dispara quando a tela liga, desliga ou é desbloqueada.",
            "Escolha o evento. 'Desbloqueada' dispara quando você passa a digital ou a senha.",
            "Tela desligou → desligar a lanterna.",
            P("when", "Quando", PT.CHOICE, listOf("on" to "Tela ligou", "off" to "Tela desligou", "unlock" to "Desbloqueada"), "unlock")),
        trig("call", "Chamadas", "Chamada",
            "Dispara quando uma ligação toca, é atendida ou termina.",
            "Escolha o momento. Precisa da permissão de Telefone.",
            "Ligação terminou → voltar o volume de mídia.",
            P("when", "Quando", PT.CHOICE, listOf("ringing" to "Tocando", "started" to "Atendida", "ended" to "Terminou"), "ended")),

        // ───────────────────────── CONDIÇÕES (SE) ─────────────────────────
        cond("battery", "Dispositivo", "Bateria",
            "Verdadeiro se a bateria está abaixo ou acima de um valor agora.",
            "Escolha 'abaixo de' ou 'acima de' e o percentual.",
            "SE bateria abaixo de 30% ENTÃO ligar poupança.",
            P("op", "Está", PT.CHOICE, listOf("below" to "Abaixo de", "above" to "Acima de"), "below"),
            P("level", "Percentual", PT.NUMBER, def = "30")),
        cond("charging", "Dispositivo", "Carregando",
            "Verdadeiro se o celular está (ou não) no carregador.",
            "Escolha Sim para 'está carregando'. Use o campo NÃO do bloco para inverter.",
            "SE carregando ENTÃO não abaixar o brilho.",
            P("state", "Está carregando?", PT.CHOICE, YESNO, "yes")),
        cond("wifi", "Conexões", "Conectado ao Wi-Fi",
            "Verdadeiro se o celular está conectado ao Wi-Fi (ou a uma rede específica).",
            "Digite o nome da rede ou deixe vazio para qualquer Wi-Fi.",
            "SE conectado ao Wi-Fi 'Casa' ENTÃO desligar dados móveis, SENÃO ligar.",
            P("network", "Nome da rede", PT.TEXT, hint = "vazio = qualquer"),
            P("state", "Está conectado?", PT.CHOICE, YESNO, "yes")),
        cond("bluetooth", "Conexões", "Bluetooth conectado",
            "Verdadeiro se um aparelho Bluetooth está conectado agora.",
            "Escolha o aparelho ou deixe vazio para qualquer um.",
            "SE o fone está conectado ENTÃO volume 40%, SENÃO volume 70%.",
            P("device", "Aparelho", PT.BTDEVICE, hint = "vazio = qualquer"),
            P("state", "Está conectado?", PT.CHOICE, YESNO, "yes")),
        cond("time_range", "Tempo", "Entre horários",
            "Verdadeiro se a hora de agora está dentro de um intervalo (pode passar da meia-noite).",
            "Escolha a hora inicial e a final.",
            "SE entre 22:00 e 07:00 ENTÃO Não incomodar.",
            P("from", "De", PT.TIME, def = "22:00"),
            P("to", "Até", PT.TIME, def = "07:00")),
        cond("weekday", "Tempo", "Dia da semana",
            "Verdadeiro se hoje é um dos dias escolhidos.",
            "Marque os dias.",
            "SE hoje é dia útil ENTÃO ligar a rotina de trabalho.",
            P("days", "Dias", PT.DAYS, def = "1,2,3,4,5")),
        cond("headphones", "Dispositivo", "Fone com fio conectado",
            "Verdadeiro se há um fone de cabo conectado.",
            "Escolha Sim ou Não.",
            "SE fone com fio ENTÃO manter o volume baixo.",
            P("state", "Está conectado?", PT.CHOICE, YESNO, "yes")),
        cond("airplane", "Conexões", "Modo avião ligado",
            "Verdadeiro se o modo avião está ligado.",
            "Escolha Sim ou Não.",
            "SE modo avião ligado ENTÃO não tentar ligar dados móveis.",
            P("state", "Está ligado?", PT.CHOICE, YESNO, "yes")),
        cond("dark_mode", "Dispositivo", "Modo escuro ligado",
            "Verdadeiro se o tema escuro está ativo.",
            "Escolha Sim ou Não.",
            "SE modo escuro ENTÃO brilho 20%, SENÃO brilho 50%.",
            P("state", "Está ligado?", PT.CHOICE, YESNO, "yes")),
        cond("power_save", "Dispositivo", "Poupança de energia ligada",
            "Verdadeiro se a poupança de energia está ligada.",
            "Escolha Sim ou Não.",
            "SE poupança desligada ENTÃO ligar quando a bateria < 20%.",
            P("state", "Está ligada?", PT.CHOICE, YESNO, "yes")),
        cond("variable", "Avançado", "Variável",
            "Compara uma variável guardada por 'Definir variável' com um valor.",
            "Digite o nome da variável, escolha o operador e o valor. Funciona com números e textos.",
            "SE contador > 3 ENTÃO parar a rotina.",
            P("name", "Nome da variável"),
            P("op", "Operador", PT.CHOICE, listOf("==" to "igual a", "!=" to "diferente de", ">" to "maior que", "<" to "menor que"), "=="),
            P("value", "Valor"),
            advanced = true),

        // ───────────────────────── AÇÕES (FAZER) ─────────────────────────
        act("wifi", "Conexões", "Wi-Fi",
            "Liga ou desliga o Wi-Fi.", "Escolha ligar ou desligar.", "Saí de casa → desligar o Wi-Fi.",
            P("state", "Estado", PT.CHOICE, ONOFF, "on"), shizuku = true),
        act("mobile_data", "Conexões", "Dados móveis",
            "Liga ou desliga os dados do chip.", "Escolha ligar ou desligar.", "Conectou no Wi-Fi de casa → desligar dados móveis.",
            P("state", "Estado", PT.CHOICE, ONOFF, "off"), shizuku = true),
        act("bluetooth", "Conexões", "Bluetooth",
            "Liga ou desliga o Bluetooth.", "Escolha ligar ou desligar.", "Cheguei ao carro → ligar o Bluetooth.",
            P("state", "Estado", PT.CHOICE, ONOFF, "on"), shizuku = true),
        act("airplane", "Conexões", "Modo avião",
            "Liga ou desliga o modo avião.", "Escolha ligar ou desligar.", "Às 23:00 → ligar modo avião, às 06:30 → desligar.",
            P("state", "Estado", PT.CHOICE, ONOFF, "on"), shizuku = true),
        act("nfc", "Conexões", "NFC",
            "Liga ou desliga o NFC.", "Escolha ligar ou desligar.", "Saí de casa → desligar NFC para economizar bateria.",
            P("state", "Estado", PT.CHOICE, ONOFF, "off"), shizuku = true),
        act("private_dns", "Conexões", "DNS privado",
            "Muda o DNS privado do sistema (por exemplo, para um que bloqueia anúncios).",
            "Escolha Desligado, Automático ou Servidor e, neste caso, digite o endereço do servidor.",
            "Fora de casa → usar o servidor 'dns.exemplo.net'.",
            P("mode", "Modo", PT.CHOICE, listOf("off" to "Desligado", "auto" to "Automático", "host" to "Servidor"), "auto"),
            P("host", "Servidor", PT.TEXT, hint = "só no modo Servidor"), shizuku = true),

        act("ringer", "Som", "Modo de som",
            "Muda entre Som, Vibrar e Silencioso.",
            "Escolha o modo. Para Silencioso funcionar em alguns casos, dê o acesso a 'Não incomodar' na tela Configuração.",
            "Cheguei no trabalho → Vibrar.",
            P("mode", "Modo", PT.CHOICE, listOf("sound" to "Som", "vibrate" to "Vibrar", "silent" to "Silencioso"), "vibrate")),
        act("volume", "Som", "Volume",
            "Define o volume de um tipo de som em porcentagem.",
            "Escolha o tipo (mídia, toque, notificação, alarme, chamada) e o percentual de 0 a 100.",
            "Conectou no fone → volume de mídia 40%.",
            P("stream", "Tipo", PT.CHOICE, listOf("media" to "Mídia", "ring" to "Toque", "notification" to "Notificação", "alarm" to "Alarme", "call" to "Chamada"), "media"),
            P("percent", "Percentual", PT.NUMBER, def = "50")),
        act("dnd", "Som", "Não incomodar",
            "Liga ou desliga o Não incomodar e escolhe o que ainda pode tocar.",
            "Desligado = tudo toca. Prioridade = só o que você permitiu nas configurações. Só alarmes = silêncio exceto alarmes. Silêncio total = nada toca.",
            "Às 22:00 → Não incomodar 'Só alarmes'.",
            P("mode", "Modo", PT.CHOICE, listOf("off" to "Desligado", "priority" to "Prioridade", "alarms" to "Só alarmes", "none" to "Silêncio total"), "priority"), shizuku = true),

        act("brightness", "Tela", "Brilho",
            "Define o brilho em porcentagem (desliga o brilho automático).",
            "Digite de 1 a 100. Em alguns celulares a escala interna pode variar; teste com ▶.",
            "Noite → brilho 15%.",
            P("percent", "Percentual", PT.NUMBER, def = "40"), shizuku = true),
        act("auto_brightness", "Tela", "Brilho automático",
            "Liga ou desliga o brilho automático.", "Escolha ligar ou desligar.", "Saí do modo leitura → ligar brilho automático.",
            P("state", "Estado", PT.CHOICE, ONOFF, "on"), shizuku = true),
        act("screen_timeout", "Tela", "Tempo até a tela apagar",
            "Define depois de quantos segundos a tela se apaga sozinha.", "Digite os segundos (15, 30, 60, 120...).", "Abri o app de leitura → 5 minutos (300 s).",
            P("seconds", "Segundos", PT.NUMBER, def = "30"), shizuku = true),
        act("dark_mode", "Tela", "Modo escuro",
            "Liga, desliga ou deixa automático o tema escuro do sistema.", "Escolha Escuro, Claro ou Automático.", "Às 19:00 → modo escuro.",
            P("mode", "Tema", PT.CHOICE, listOf("yes" to "Escuro", "no" to "Claro", "auto" to "Automático"), "yes"), shizuku = true),
        act("refresh_rate", "Tela", "Taxa de atualização",
            "Força a tela a usar 60 Hz (economiza bateria) ou 120 Hz (mais fluido).",
            "Escolha o valor. Em alguns celulares o sistema pode ignorar; teste com ▶.",
            "Bateria baixa → 60 Hz.",
            P("hz", "Hz", PT.CHOICE, listOf("60" to "60 Hz", "120" to "120 Hz"), "60"), shizuku = true),

        act("power_save", "Bateria", "Poupança de energia",
            "Liga ou desliga a poupança de energia do Android.", "Escolha ligar ou desligar.", "Bateria abaixo de 20% → ligar.",
            P("state", "Estado", PT.CHOICE, ONOFF, "on"), shizuku = true),

        act("open_app", "Apps", "Abrir app",
            "Abre um aplicativo.", "Toque em 'Escolher app'.", "Conectou no carro → abrir o app de música.",
            P("pkg", "App", PT.APP), shizuku = true),
        act("notify", "Apps", "Mostrar notificação",
            "Mostra uma notificação do Fluxo com o texto que você escolher.",
            "Digite título e texto. Dá para usar {bateria}, {hora}, {wifi} e suas variáveis, ex.: {contador}.",
            "Bateria abaixo de 20% → 'Bateria em {bateria}%'.",
            P("title", "Título", def = "Fluxo"),
            P("text", "Texto")),
        act("flashlight", "Apps", "Lanterna",
            "Liga ou desliga a lanterna.", "Escolha ligar ou desligar.", "Alarme tocando → piscar lanterna (ligar, esperar, desligar).",
            P("state", "Estado", PT.CHOICE, ONOFF, "on")),
        act("force_stop", "Apps", "Forçar parada de um app",
            "Fecha totalmente um app (como 'Forçar parada' nas configurações).", "Toque em 'Escolher app'. Cuidado: o app perde o que não foi salvo.", "Saí do trabalho → fechar o app de chat do trabalho.",
            P("pkg", "App", PT.APP), shizuku = true),

        act("wait", "Lógica", "Esperar",
            "Pausa a rotina por alguns segundos antes do próximo passo.", "Digite os segundos.", "Ligar lanterna → esperar 2 s → desligar lanterna.",
            P("seconds", "Segundos", PT.NUMBER, def = "5")),
        act("set_var", "Lógica", "Definir variável",
            "Guarda um valor com um nome, para usar depois em condições ('Variável') ou em textos como {nome}.",
            "Digite o nome e o valor. O valor pode usar outras variáveis, ex.: {bateria}.",
            "Definir 'modo' = 'casa' e depois usar SE variável modo == casa.",
            P("name", "Nome"), P("value", "Valor")),
        act("stop", "Lógica", "Parar a rotina",
            "Interrompe a rotina neste ponto (o resto não roda).", "Use dentro de um SE para sair mais cedo.", "SE já é tarde ENTÃO parar a rotina."),
        act("log", "Lógica", "Escrever no registro",
            "Escreve uma mensagem na tela Registro, útil para entender o que a rotina fez.", "Digite a mensagem (aceita {variáveis}).", "'Passei pelo caminho do Wi-Fi de casa'.",
            P("message", "Mensagem")),

        // ─── Avançadas (Automate entra aqui) ───
        act("shell", "Avançado", "Comando de shell",
            "Roda um comando de terminal com o poder do Shizuku. Serve para qualquer definição que ainda não tem bloco próprio.",
            "Digite o comando (aceita {variáveis}). Use com cuidado: um comando errado pode mudar configurações do celular. Teste com ▶ e veja o resultado no Registro.",
            "settings put system screen_off_timeout 60000  (tela apaga em 60 s).",
            P("command", "Comando", PT.MULTILINE), shizuku = true, advanced = true),
        act("settings_put", "Avançado", "Alterar definição do sistema",
            "Muda um valor em Settings (system, secure ou global). É o jeito de mexer em opções escondidas, como as da Honor.",
            "Escolha a tabela, a chave e o valor. Para descobrir chaves, rode 'settings list system' no bloco Comando de shell e veja o Registro.",
            "Tabela system, chave screen_brightness_mode, valor 0.",
            P("ns", "Tabela", PT.CHOICE, listOf("system" to "system", "secure" to "secure", "global" to "global"), "system"),
            P("key", "Chave"), P("value", "Valor"), shizuku = true, advanced = true),
        act("open_url", "Avançado", "Abrir link",
            "Abre um endereço (site ou link de app) no navegador ou no app que o suporta.",
            "Digite o endereço completo, com https://.", "Cheguei em casa → abrir a página do painel de casa.",
            P("url", "Endereço", def = "https://"), shizuku = true, advanced = true),
    ) + ShortcutsCatalog.all + AutomateCatalog.all + MoreCatalog.all

    fun find(kind: Kind, id: String): Spec? = all.firstOrNull { it.kind == kind && it.id == id }

    fun defaults(s: Spec): Map<String, String> = s.params.associate { it.key to it.def }

    fun daysLabel(v: String): String {
        val n = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
        val d = v.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
        return when {
            d.size == 7 -> "todos os dias"
            d == listOf(1, 2, 3, 4, 5) -> "dias úteis"
            d == listOf(6, 7) -> "fim de semana"
            else -> d.joinToString(" ") { n.getOrElse(it - 1) { "?" } }
        }
    }

    private fun display(p: P, v: String): String = when (p.type) {
        PT.CHOICE -> p.options.firstOrNull { it.first == v }?.second ?: v
        PT.DAYS -> daysLabel(v)
        else -> v
    }

    /** Texto curto mostrado nos cartões, ex.: "Volume: Mídia · 40". */
    fun label(kind: Kind, id: String, params: Map<String, String>): String {
        val s = find(kind, id) ?: return id
        val vals = s.params.mapNotNull { p ->
            val v = params[p.key].orEmpty()
            if (v.isBlank()) null else display(p, v)
        }
        return if (vals.isEmpty()) s.title else s.title + ": " + vals.joinToString(" · ")
    }
}
