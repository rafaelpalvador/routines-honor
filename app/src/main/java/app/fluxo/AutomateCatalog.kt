package app.fluxo

/**
 * Blocos avançados inspirados no Automate.
 * Todos aparecem só com "Mostrar opções avançadas" ligado.
 * Para adicionar um bloco: crie o Spec aqui e a lógica em AutomateActions.kt (ação) ou AutomateConds.kt (condição).
 */
object AutomateCatalog {
    private val PKG = P("pkg", "App", PT.APP)
    private fun txt(k: String, l: String, def: String = "", hint: String = "") = P(k, l, PT.TEXT, def = def, hint = hint)
    private fun num(k: String, l: String, def: String = "", hint: String = "") = P(k, l, PT.NUMBER, def = def, hint = hint)
    private fun ch(k: String, l: String, o: List<Pair<String, String>>, def: String) = P(k, l, PT.CHOICE, o, def)
    private val SAVE = txt("save", "Guardar resultado na variável", hint = "ex.: resultado (depois use {resultado})")
    private val ONOFF = listOf("on" to "Ligar", "off" to "Desligar")
    private val YESNO = listOf("yes" to "Sim", "no" to "Não")
    private val CMP = listOf("above" to "maior que", "below" to "menor que", "equal" to "igual a")
    private val STREAMS = listOf("music" to "Mídia", "ring" to "Toque", "alarm" to "Alarme", "notification" to "Notificação", "voice" to "Chamada")
    private val STATE = ch("state", "Resposta esperada", YESNO, "yes")

    private fun A(id: String, cat: String, title: String, auto: String, summary: String, howTo: String, example: String, vararg p: P, shz: Boolean = true) =
        Spec(Kind.ACTION, id, cat, title, "$summary (Equivale ao bloco \"$auto\" do Automate.)", howTo, example, p.toList(), advanced = true, shizuku = shz)

    private fun C(id: String, cat: String, title: String, auto: String, summary: String, howTo: String, example: String, vararg p: P, shz: Boolean = false) =
        Spec(Kind.CONDITION, id, cat, title, "$summary (Equivale ao bloco \"$auto\" do Automate.)", howTo, example, p.toList(), advanced = true, shizuku = shz)

    val all: List<Spec> = listOf(
        // ───────────── APPS ─────────────
        A("app_kill_bg", "Apps", "Encerrar app em segundo plano", "App kill background",
            "Encerra só os processos que o app tem rodando em segundo plano, sem fechar a tela dele.",
            "Escolha o app. É mais leve que 'Forçar parada'.", "Ao sair de casa, encerrar o app de TV em segundo plano.", PKG),
        A("app_clear_data", "Apps", "Apagar dados de um app", "App clear cache",
            "Apaga TODOS os dados do app (como reinstalar). Cuidado: você perde login e configurações dele.",
            "Escolha o app. Use só se tiver certeza.", "Todo domingo, limpar os dados de um app de teste.", PKG),
        A("app_trim_cache", "Apps", "Limpar cache de todos os apps", "App clear cache",
            "Libera o espaço do cache de todos os apps de uma vez.",
            "Não precisa configurar. O Android pode demorar alguns segundos.", "Toda noite às 03:00, limpar o cache.", shz = true),
        A("app_list", "Apps", "Listar apps instalados", "App list",
            "Pega a lista de apps instalados e guarda numa variável (separados por vírgula).",
            "Escolha 'Só os meus apps' ou 'Todos' e dê um nome para a variável.", "Guardar a lista em {apps} e mostrar numa notificação.",
            ch("scope", "Quais apps", listOf("user" to "Só os meus apps", "all" to "Todos"), "user"), SAVE),
        A("app_notif_set", "Apps", "Notificações de um app: ligar/desligar", "App notification set state",
            "Liga ou desliga as notificações de um app específico.",
            "Escolha o app e Ligar ou Desligar.", "No horário de trabalho, desligar as notificações do app de jogos.", PKG, ch("state", "Estado", ONOFF, "off")),
        A("appop_set", "Apps", "Permissão interna de app (AppOp)", "AppOp mode set",
            "Muda uma permissão interna ('app operation') de um app, como localização ou sobreposição de tela.",
            "Escolha o app, o nome da operação (ex.: COARSE_LOCATION, SYSTEM_ALERT_WINDOW) e o modo (allow, deny, ignore).",
            "Negar a localização de um app durante o trabalho.", PKG, txt("op", "Operação", hint = "ex.: COARSE_LOCATION"),
            ch("mode", "Modo", listOf("allow" to "Permitir", "deny" to "Negar", "ignore" to "Ignorar", "default" to "Padrão"), "deny")),
        A("broadcast_send", "Apps", "Enviar broadcast", "Broadcast send",
            "Envia uma mensagem do sistema (broadcast) que outros apps podem escutar, como o Tasker ou apps de automação.",
            "Preencha a ação (ex.: com.exemplo.MEU_EVENTO). O app destino e um texto extra são opcionais.",
            "Ao chegar em casa, enviar um broadcast para acender as luzes por outro app.",
            txt("action", "Ação"), PKG, txt("extra", "Texto extra (opcional)")),
        A("service_start", "Apps", "Iniciar serviço de app", "Service start",
            "Inicia um serviço (parte invisível) de outro app.",
            "Digite o componente no formato pacote/classe.", "Iniciar o serviço de sincronização de um app.",
            txt("component", "Componente", hint = "com.exemplo/.MeuServico")),
        C("app_installed", "Apps", "App instalado?", "App installed?",
            "Verifica se um app está instalado no aparelho.", "Escolha o app e a resposta esperada.", "SE o app do banco está instalado ENTÃO ...",
            PKG, STATE, shz = true),
        C("app_foreground", "Apps", "App em primeiro plano?", "App in foreground?",
            "Verifica se um app está aberto na tela agora.", "Escolha o app.", "SE o YouTube está na tela ENTÃO aumentar o brilho.",
            PKG, STATE, shz = true),
        C("app_notif_enabled", "Apps", "Notificações do app ligadas?", "App notification enabled?",
            "Verifica se o app pode mostrar notificações.", "Escolha o app.", "SE o app de mensagens pode notificar ENTÃO ...", PKG, STATE, shz = true),
        C("appop_is", "Apps", "Permissão interna (AppOp) está assim?", "AppOp mode?",
            "Confere o modo de uma permissão interna de um app.", "Escolha o app, a operação e o modo esperado.", "SE a localização do app está em 'allow' ENTÃO ...",
            PKG, txt("op", "Operação", hint = "ex.: COARSE_LOCATION"), ch("mode", "Modo", listOf("allow" to "Permitir", "deny" to "Negar", "ignore" to "Ignorar"), "allow"), shz = true),

        // ───────────── BATERIA E ENERGIA ─────────────
        A("battery_info", "Bateria e energia", "Ler dados da bateria", "Battery properties",
            "Lê temperatura, tensão, nível ou saúde da bateria e guarda numa variável.",
            "Escolha o dado e o nome da variável.", "Guardar a temperatura em {temp} e avisar se passar de 40.",
            ch("field", "Dado", listOf("temperature" to "Temperatura (°C)", "voltage" to "Tensão (mV)", "level" to "Nível (%)", "health" to "Saúde (código Android)", "plugged" to "Tipo de carregador (0=nenhum 1=tomada 2=USB 4=sem fio)"), "temperature"),
            SAVE, shz = false),
        A("doze_set", "Bateria e energia", "Modo soneca (Doze)", "Device doze mode set state",
            "Força o aparelho a entrar (ou sair) do modo de economia profunda 'Doze'.", "Escolha Ligar ou Desligar.", "À noite, forçar o Doze para gastar menos bateria.",
            ch("state", "Estado", ONOFF, "on")),
        A("keep_awake", "Bateria e energia", "Manter a tela ligada", "Device keep awake",
            "Impede a tela de apagar enquanto o aparelho está carregando.", "Escolha quando manter acesa.", "Na mesa de trabalho, manter a tela acesa enquanto carrega.",
            ch("mode", "Manter acesa", listOf("false" to "Nunca (normal)", "ac" to "Carregando na tomada", "usb" to "Carregando por USB", "true" to "Sempre carregando"), "ac")),
        A("reboot", "Bateria e energia", "Reiniciar o aparelho", "Device reboot",
            "Reinicia o aparelho.", "Não precisa configurar. Cuidado: tudo que está aberto fecha.", "Toda segunda às 04:00, reiniciar o aparelho."),
        A("shutdown", "Bateria e energia", "Desligar o aparelho", "Device shutdown",
            "Desliga o aparelho.", "Não precisa configurar. Você terá que ligar manualmente depois.", "Bateria abaixo de 2% → desligar."),
        A("screen_power", "Bateria e energia", "Ligar/apagar a tela", "Display power mode set",
            "Acende ou apaga a tela, como o botão de energia.", "Escolha Ligar ou Desligar.", "Cobri o sensor de proximidade → apagar a tela.",
            ch("state", "Tela", ONOFF, "off")),
        C("doze_active", "Bateria e energia", "Modo soneca (Doze) ativo?", "Device doze mode active?",
            "Verifica se o aparelho está em Doze.", "Escolha a resposta esperada.", "SE o aparelho está em Doze ENTÃO ...", STATE, shz = true),
        C("screen_on", "Bateria e energia", "Tela ligada?", "Device interactive?",
            "Verifica se a tela está acesa.", "Escolha a resposta esperada.", "SE a tela está acesa ENTÃO não mexer em nada.", STATE),
        C("plugged_source", "Bateria e energia", "Tipo de carregador", "Power source plugged?",
            "Verifica se há carregador e de que tipo.", "Escolha o tipo.", "SE está em carregador sem fio ENTÃO ...",
            ch("source", "Tipo", listOf("any" to "Qualquer", "ac" to "Tomada", "usb" to "USB", "wireless" to "Sem fio"), "any"), STATE),

        // ───────────── CÂMERA E SOM ─────────────
        A("media_key", "Câmera e som", "Controle de mídia", "Audio player control",
            "Aperta play, pausa, próxima ou anterior no player que estiver tocando.", "Escolha o botão.", "Conectou o fone → dar play.",
            ch("key", "Botão", listOf("85" to "Play/Pausa", "126" to "Play", "127" to "Pausa", "87" to "Próxima", "88" to "Anterior", "86" to "Parar"), "85")),
        A("stream_mute", "Câmera e som", "Silenciar/liberar um volume", "Audio stream set mute",
            "Silencia (mute) ou libera o som de um tipo de volume.", "Escolha o volume e a ação.", "Silenciar o volume do toque na reunião.",
            ch("stream", "Volume", STREAMS, "ring"), ch("mode", "Ação", listOf("mute" to "Silenciar", "unmute" to "Liberar"), "mute"), shz = false),
        A("mic_mute", "Câmera e som", "Silenciar/liberar microfone", "Microphone set mute",
            "Silencia ou libera o microfone do aparelho.", "Escolha a ação.", "Na reunião, silenciar o microfone.",
            ch("mode", "Ação", listOf("mute" to "Silenciar", "unmute" to "Liberar"), "mute"), shz = false),
        A("speakerphone", "Câmera e som", "Viva-voz", "Speakerphone set state",
            "Liga ou desliga o viva-voz.", "Escolha Ligar ou Desligar. Só faz efeito em chamada.", "Atendi no carro → viva-voz ligado.",
            ch("state", "Estado", ONOFF, "on"), shz = false),
        A("screenshot", "Câmera e som", "Captura de tela", "Screenshot",
            "Tira uma foto da tela e salva num arquivo.", "Digite onde salvar. Pode usar {hora} no nome.", "Todo dia às 09:00, salvar uma captura.",
            txt("path", "Salvar em", "/sdcard/Pictures/fluxo.png")),
        A("sound_play", "Câmera e som", "Tocar um som", "Sound play",
            "Toca um arquivo de áudio.", "Digite o caminho do arquivo (ex.: /sdcard/Music/aviso.mp3). Permita 'Arquivos de áudio' na tela Configuração.",
            "Cheguei em casa → tocar um aviso sonoro.", txt("path", "Arquivo de áudio", hint = "/sdcard/Music/aviso.mp3"),
            ch("wait", "Esperar terminar?", YESNO, "no"), shz = false),
        A("sound_stop", "Câmera e som", "Parar som", "Sound stop", "Para o som tocado pelo bloco 'Tocar um som'.", "Não precisa configurar.", "Parar o aviso depois de 10 segundos.", shz = false),
        A("tone_play", "Câmera e som", "Tocar um bipe", "Tone play",
            "Toca um bipe curto.", "Escolha o tipo e a duração.", "Terminou a tarefa → bipe de confirmação.",
            ch("tone", "Bipe", listOf("beep" to "Bipe", "ack" to "Confirmação", "nack" to "Erro", "alert" to "Alerta"), "beep"), num("ms", "Duração (ms)", "300"), shz = false),
        A("speak", "Câmera e som", "Falar um texto", "Speak",
            "O aparelho fala um texto em voz alta.", "Digite o texto. Pode usar {bateria}, {hora} etc.", "Ao acordar: 'Bom dia, são {hora}'.",
            txt("text", "Texto"), ch("wait", "Esperar terminar?", YESNO, "no"), shz = false),
        A("speak_stop", "Câmera e som", "Parar de falar", "Speak stop", "Interrompe a fala em andamento.", "Não precisa configurar.", "Cheguei em casa → parar de falar.", shz = false),
        A("vibrate", "Câmera e som", "Vibrar", "Vibrate", "Faz o aparelho vibrar.", "Escolha a duração.", "Terminou o café → vibrar 1 segundo.",
            num("ms", "Duração (ms)", "500"), shz = false),
        A("vibrate_stop", "Câmera e som", "Parar de vibrar", "Vibrate stop", "Interrompe a vibração.", "Não precisa configurar.", "Parar de vibrar depois de atender.", shz = false),
        C("stream_muted", "Câmera e som", "Volume silenciado?", "Audio stream muted?", "Verifica se um tipo de volume está silenciado.", "Escolha o volume.",
            "SE o toque está silenciado ENTÃO ...", ch("stream", "Volume", STREAMS, "ring"), STATE),
        C("volume_level", "Câmera e som", "Nível do volume", "Audio volume?", "Compara o nível de um volume (0 a 100%).", "Escolha o volume, a comparação e o valor.",
            "SE o volume de mídia está acima de 80% ENTÃO baixar.", ch("stream", "Volume", STREAMS, "music"), ch("op", "É", CMP, "above"), num("value", "Valor (%)", "50")),
        C("media_playing", "Câmera e som", "Tocando mídia?", "Media playing?", "Verifica se algum áudio ou vídeo está tocando.", "Escolha a resposta esperada.",
            "SE está tocando música ENTÃO não suspender a rotina.", STATE),
        C("mic_muted", "Câmera e som", "Microfone silenciado?", "Microphone muted?", "Verifica se o microfone está silenciado.", "Escolha a resposta esperada.", "SE o microfone está mudo ENTÃO ...", STATE),
        C("speaker_on", "Câmera e som", "Viva-voz ligado?", "Speakerphone on?", "Verifica se o viva-voz está ligado.", "Escolha a resposta esperada.", "SE o viva-voz está ligado ENTÃO ...", STATE),

        // ───────────── CONEXÕES ─────────────
        A("usb_tether", "Conexões", "Compartilhar internet por USB", "USB tethering set state",
            "Liga ou desliga o compartilhamento de internet pelo cabo USB.", "Escolha Ligar ou Desligar. O cabo precisa estar conectado.", "Conectei no PC → ligar tethering USB.",
            ch("state", "Estado", ONOFF, "on")),
        A("restrict_bg_data", "Conexões", "Restringir dados em segundo plano", "Restrict background data set state",
            "Liga ou desliga a economia de dados (apps só usam internet quando abertos).", "Escolha Ligar ou Desligar.", "Em roaming, ligar a economia de dados.",
            ch("state", "Estado", ONOFF, "on")),
        A("wifi_connect", "Conexões", "Conectar a uma rede Wi-Fi", "Wi-Fi network connect",
            "Conecta a uma rede Wi-Fi pelo nome e senha.", "Digite nome e senha. A rede precisa estar ao alcance.", "Cheguei no escritório → conectar à rede 'Escritorio'.",
            txt("ssid", "Nome da rede"), ch("sec", "Segurança", listOf("wpa2" to "WPA2", "wpa3" to "WPA3", "open" to "Aberta"), "wpa2"), txt("pass", "Senha")),
        A("http_request", "Conexões", "Requisição HTTP", "HTTP request",
            "Acessa um endereço na internet (GET ou POST) e guarda a resposta numa variável. Serve para avisar outros sistemas ou buscar dados.",
            "Preencha o endereço e o método. Para POST, escreva o corpo.", "Chegou em casa → chamar um webhook do Home Assistant.",
            txt("url", "Endereço", "https://"), ch("method", "Método", listOf("GET" to "GET", "POST" to "POST"), "GET"), P("body", "Corpo (POST)", PT.MULTILINE), SAVE, shz = false),
        A("wol_send", "Conexões", "Acordar PC (Wake-on-LAN)", "Wake-on-LAN send",
            "Envia o sinal que liga um computador pela rede.", "Digite o endereço MAC do computador (ex.: AA:BB:CC:DD:EE:FF).", "Cheguei em casa → ligar o PC.",
            txt("mac", "MAC"), txt("host", "Endereço de envio", "255.255.255.255"), num("port", "Porta", "9"), shz = false),
        C("bt_enabled", "Conexões", "Bluetooth ligado?", "Bluetooth enabled?", "Verifica se o Bluetooth está ligado.", "Escolha a resposta esperada.", "SE o Bluetooth está desligado ENTÃO ligar.", STATE),
        C("wifi_enabled", "Conexões", "Wi-Fi ligado?", "Wi-Fi enabled?", "Verifica se o Wi-Fi está ligado (mesmo sem rede).", "Escolha a resposta esperada.", "SE o Wi-Fi está desligado ENTÃO ligar.", STATE),
        C("mobile_data_enabled", "Conexões", "Dados móveis ligados?", "Mobile data enabled?", "Verifica se os dados móveis estão ligados.", "Escolha a resposta esperada.", "SE os dados estão ligados ENTÃO ...", STATE),
        C("nfc_enabled", "Conexões", "NFC ligado?", "NFC enabled?", "Verifica se o NFC está ligado.", "Escolha a resposta esperada.", "SE o NFC está desligado ENTÃO ligar.", STATE),
        C("wifi_signal", "Conexões", "Sinal do Wi-Fi", "Wi-Fi signal strength?",
            "Compara a força do sinal Wi-Fi em dBm (perto de -30 é ótimo, -80 é fraco).", "Escolha a comparação e o valor.", "SE o sinal está abaixo de -75 ENTÃO ligar dados móveis.",
            ch("op", "É", CMP, "below"), num("value", "Valor (dBm)", "-75")),
        C("ping", "Conexões", "Servidor responde (ping)?", "Ping", "Verifica se um endereço da internet ou da rede local responde.", "Digite o endereço ou IP.", "SE o roteador de casa responde ENTÃO estou em casa.",
            txt("host", "Endereço", "8.8.8.8"), STATE),

        // ───────────── CONTEÚDO ─────────────
        A("alarm_add", "Conteúdo", "Criar alarme", "Alarm add", "Cria um alarme no app Relógio.", "Escolha a hora e uma mensagem.", "Sexta à noite → criar alarme de 07:00 para sábado.",
            P("time", "Hora", PT.TIME, def = "07:00"), txt("msg", "Mensagem")),
        A("timer_add", "Conteúdo", "Criar timer", "Timer add", "Cria um timer no app Relógio.", "Digite os segundos.", "Ligou o forno → timer de 20 minutos (1200).",
            num("seconds", "Segundos", "600")),
        A("calendar_add", "Conteúdo", "Novo evento no calendário", "Calendar event add", "Abre o calendário com um evento novo já preenchido.", "Preencha título e descrição. Você confirma no calendário.", "Terminei a reunião → criar evento de acompanhamento.",
            txt("title", "Título"), txt("desc", "Descrição")),
        A("content_query", "Conteúdo", "Consultar provedor de conteúdo", "Content query",
            "Lê dados de um 'content provider' do Android (contatos, configurações etc.). Avançado.", "Digite os argumentos do comando 'content query', ex.: --uri content://settings/system --projection name:value.",
            "Ler uma configuração do sistema.", txt("args", "Argumentos"), SAVE),
        A("content_insert", "Conteúdo", "Inserir em provedor de conteúdo", "Content insert", "Insere um dado num 'content provider'. Avançado.", "Digite os argumentos, ex.: --uri content://... --bind chave:s:valor.",
            "Inserir um registro de teste.", txt("args", "Argumentos")),
        A("content_delete", "Conteúdo", "Apagar de provedor de conteúdo", "Content delete", "Apaga dados de um 'content provider'. Avançado e perigoso.", "Digite os argumentos, ex.: --uri content://... --where \"_id=1\".",
            "Apagar um registro de teste.", txt("args", "Argumentos")),
        A("content_update", "Conteúdo", "Atualizar provedor de conteúdo", "Content update", "Altera dados de um 'content provider'. Avançado.", "Digite os argumentos, ex.: --uri content://... --bind chave:s:valor --where \"_id=1\".",
            "Mudar um valor de teste.", txt("args", "Argumentos")),

        // ───────────── DATA E HORA ─────────────
        A("tz_get", "Data e hora", "Ler fuso horário", "Time zone get", "Descobre o fuso horário atual e guarda numa variável.", "Dê um nome para a variável.", "Guardar o fuso em {fuso}.", SAVE, shz = false),
        A("tz_set", "Data e hora", "Mudar fuso horário", "Time zone set", "Muda o fuso horário do aparelho.", "Digite o nome do fuso (ex.: Europe/Lisbon, America/Sao_Paulo).", "Em viagem ao Brasil, trocar para America/Sao_Paulo.",
            txt("tz", "Fuso", hint = "Europe/Lisbon")),

        // ───────────── ARQUIVOS ─────────────
        A("file_copy", "Arquivos", "Copiar arquivo ou pasta", "File copy", "Copia um arquivo ou pasta.", "Digite origem e destino.", "Copiar fotos de /sdcard/DCIM para /sdcard/Backup.", txt("src", "Origem"), txt("dst", "Destino")),
        A("file_move", "Arquivos", "Mover arquivo ou pasta", "File move", "Move (ou renomeia) um arquivo ou pasta.", "Digite origem e destino.", "Mover downloads antigos para /sdcard/Arquivo.", txt("src", "Origem"), txt("dst", "Destino")),
        A("file_delete", "Arquivos", "Apagar arquivo ou pasta", "File delete", "Apaga um arquivo ou pasta (sem lixeira!).", "Digite o caminho completo.", "Apagar /sdcard/Download/temp toda semana.", txt("path", "Caminho")),
        A("file_mkdir", "Arquivos", "Criar pasta", "File make directory", "Cria uma pasta (e as pastas acima dela, se faltarem).", "Digite o caminho.", "Criar /sdcard/Backup/hoje.", txt("path", "Caminho")),
        A("file_write", "Arquivos", "Escrever em arquivo", "File write", "Escreve (ou acrescenta) um texto num arquivo.", "Digite o caminho, o texto e se apaga ou acrescenta.", "Guardar a hora de cada vez que cheguei em casa.",
            txt("path", "Arquivo"), P("text", "Texto", PT.MULTILINE), ch("mode", "Modo", listOf("w" to "Substituir", "a" to "Acrescentar no fim"), "a")),
        A("file_read", "Arquivos", "Ler arquivo de texto", "File read text", "Lê um arquivo de texto e guarda o conteúdo numa variável.", "Digite o caminho e a variável.", "Ler /sdcard/config.txt em {config}.", txt("path", "Arquivo"), SAVE),
        A("file_list", "Arquivos", "Listar pasta", "File list", "Lista os arquivos de uma pasta e guarda numa variável (separados por vírgula).", "Digite a pasta e a variável.", "Listar /sdcard/Download.", txt("path", "Pasta"), SAVE),
        A("file_unzip", "Arquivos", "Extrair zip", "Zip extract", "Extrai um arquivo .zip numa pasta.", "Digite o zip e a pasta destino.", "Extrair backup.zip em /sdcard/Backup.", txt("zip", "Arquivo .zip"), txt("dst", "Pasta destino")),
        C("file_exists", "Arquivos", "Arquivo existe?", "File exists?", "Verifica se um arquivo ou pasta existe.", "Digite o caminho.", "SE /sdcard/ok.txt existe ENTÃO ...", txt("path", "Caminho"), STATE, shz = true),
        C("storage_space", "Arquivos", "Espaço livre no armazenamento", "Storage space?", "Compara o espaço livre (em GB).", "Escolha a comparação e o valor.", "SE o espaço livre é menor que 5 GB ENTÃO limpar o cache.",
            ch("op", "É", CMP, "below"), num("value", "Valor (GB)", "5")),

        // ───────────── FLUXO ─────────────
        A("run_routine", "Fluxo", "Rodar outra rotina", "Flow start", "Dispara outra rotina pelo nome. Serve para dividir tarefas grandes em partes.", "Digite o nome exato da outra rotina.", "A rotina 'Chegar em casa' chama a rotina 'Luzes'.",
            txt("name", "Nome da rotina"), shz = false),

        // ───────────── GERAL ─────────────
        C("android_version", "Geral", "Versão do Android", "Android version?", "Compara a versão do Android (número principal, ex.: 15).", "Escolha a comparação e o número.", "SE a versão é maior que 14 ENTÃO ...",
            ch("op", "É", CMP, "above"), num("value", "Versão", "14")),

        // ───────────── INTERFACE ─────────────
        A("clipboard_set", "Interface", "Copiar texto", "Clipboard set", "Coloca um texto na área de transferência (como 'copiar').", "Digite o texto. Pode usar variáveis.", "Copiar o Wi-Fi atual {wifi}.", txt("text", "Texto"), shz = false),
        A("toast", "Interface", "Mostrar aviso rápido (toast)", "Toast show", "Mostra uma mensagem curta na tela que some sozinha.", "Digite a mensagem.", "Ao conectar no carro: 'Bom trajeto!'.", txt("text", "Mensagem"), shz = false),
        A("key_send", "Interface", "Apertar uma tecla", "Key send", "Simula um botão do aparelho (voltar, home, volume...).", "Escolha a tecla.", "Apertar HOME depois de abrir um app.",
            ch("key", "Tecla", listOf("3" to "Home", "4" to "Voltar", "187" to "Recentes", "26" to "Liga/desliga", "24" to "Volume +", "25" to "Volume -", "66" to "Enter", "82" to "Menu"), "3")),
        A("text_send", "Interface", "Digitar texto", "Key send characters", "Digita um texto no campo que estiver selecionado.", "Digite o texto (sem acentos funciona melhor).", "Preencher um campo de busca automaticamente.", txt("text", "Texto")),
        A("tap", "Interface", "Tocar na tela", "Interact touch", "Toca numa posição da tela (x, y em pixels).", "Digite x e y. Dica: o Android tem 'Mostrar toques' nas opções de desenvolvedor para descobrir coordenadas.", "Tocar no botão 'Aceitar' de um app.", num("x", "x"), num("y", "y")),
        A("swipe", "Interface", "Deslizar na tela", "Interact touch", "Desliza de um ponto a outro.", "Digite início (x1,y1), fim (x2,y2) e duração.", "Deslizar para rolar a página para baixo.", num("x1", "x inicial"), num("y1", "y inicial"), num("x2", "x final"), num("y2", "y final"), num("ms", "Duração (ms)", "300")),
        A("car_mode", "Interface", "Modo carro", "Car mode set state", "Liga ou desliga a interface de modo carro.", "Escolha Ligar ou Desligar.", "Bluetooth do carro conectou → modo carro.", ch("state", "Estado", ONOFF, "on")),
        A("orientation_set", "Interface", "Orientação da tela", "Screen orientation set", "Trava a tela em retrato/paisagem ou volta ao automático.", "Escolha a orientação.", "Abri o app de leitura → travar em retrato.",
            ch("mode", "Orientação", listOf("auto" to "Automática", "portrait" to "Retrato", "landscape" to "Paisagem"), "auto")),
        A("ime_set", "Interface", "Trocar teclado", "Input method set", "Troca o teclado em uso.", "Digite o ID do teclado (use 'ime list -s' num bloco de comando para ver).", "Trocar para o teclado em português ao abrir o app de notas.", txt("id", "ID do teclado", hint = "pacote/classe")),
        C("device_locked", "Interface", "Aparelho bloqueado?", "Device locked?", "Verifica se a tela de bloqueio está ativa.", "Escolha a resposta esperada.", "SE o aparelho está bloqueado ENTÃO não mostrar notificações sensíveis.", STATE),
        C("device_secure", "Interface", "Aparelho tem senha/biometria?", "Device secure?", "Verifica se existe bloqueio por senha, PIN, padrão ou biometria.", "Escolha a resposta esperada.", "SE o aparelho não tem senha ENTÃO avisar.", STATE),
        C("orientation_is", "Interface", "Orientação atual", "Device orientation?", "Verifica se a tela está em retrato ou paisagem.", "Escolha a orientação.", "SE está em paisagem ENTÃO ...", ch("mode", "Orientação", listOf("portrait" to "Retrato", "landscape" to "Paisagem"), "portrait")),

        // ───────────── LOCAL ─────────────
        A("location_set", "Local", "Ligar/desligar localização (GPS)", "Location provider set state", "Liga ou desliga a localização do aparelho.", "Escolha Ligar ou Desligar.", "Sai de casa → ligar a localização.", ch("state", "Estado", ONOFF, "on")),
        C("location_enabled", "Local", "Localização ligada?", "Location provider enabled?", "Verifica se a localização está ligada.", "Escolha a resposta esperada.", "SE a localização está desligada ENTÃO ligar.", STATE),

        // ───────────── MENSAGENS ─────────────
        A("compose_sms", "Mensagens", "Escrever SMS (abre o app)", "Compose SMS", "Abre o app de mensagens com um SMS pronto. Você só toca em enviar.", "Digite número e texto.", "Cheguei → preparar SMS 'Cheguei bem'.", txt("number", "Número"), txt("text", "Texto")),
        A("compose_email", "Mensagens", "Escrever e-mail (abre o app)", "Compose e-mail", "Abre o app de e-mail com uma mensagem pronta.", "Preencha destinatário, assunto e texto.", "Fim do dia → preparar relatório por e-mail.", txt("to", "Para"), txt("subject", "Assunto"), P("text", "Texto", PT.MULTILINE)),
        A("sms_send", "Mensagens", "Enviar SMS (automático)", "SMS send", "Envia um SMS sem você tocar em nada. Pode gerar custo! Permita 'SMS' na tela Configuração.", "Digite número e texto.", "Bateria < 5% → avisar alguém por SMS.", txt("number", "Número"), txt("text", "Texto"), shz = false),

        // ───────────── SENSORES ─────────────
        A("sensor_read", "Sensores", "Ler sensor", "Ambient light? / Proximity distance? …", "Lê um sensor e guarda o valor numa variável.", "Escolha o sensor e a variável.", "Guardar a luz ambiente em {lux}.",
            ch("sensor", "Sensor", SENSORS, "light"), SAVE, shz = false),
        C("sensor_value", "Sensores", "Valor de sensor", "Ambient light? / Atmospheric pressure? / Relative humidity? …", "Compara a leitura de um sensor (luz, proximidade, pressão, temperatura, umidade, campo magnético).", "Escolha o sensor, a comparação e o valor.",
            "SE a luz ambiente é menor que 20 lux ENTÃO ligar modo escuro.", ch("sensor", "Sensor", SENSORS, "light"), ch("op", "É", CMP, "below"), num("value", "Valor", "20")),

        // ───────────── AJUSTES ─────────────
        A("settings_get", "Ajustes", "Ler ajuste do sistema", "System setting get", "Lê o valor de um ajuste do Android e guarda numa variável.", "Escolha a tabela, a chave e a variável.", "Ler 'screen_off_timeout' em {tempo}.",
            ch("ns", "Tabela", listOf("system" to "system", "secure" to "secure", "global" to "global"), "system"), txt("key", "Chave"), SAVE),
        A("sysprop_get", "Ajustes", "Ler propriedade do sistema", "System property get", "Lê uma propriedade do sistema (getprop).", "Digite o nome e a variável.", "Ler ro.product.model.", txt("key", "Propriedade", hint = "ro.product.model"), SAVE),
        C("ringer_is", "Ajustes", "Modo de som atual", "Ringer mode?", "Verifica se o aparelho está em normal, vibrar ou silencioso.", "Escolha o modo.", "SE está em silencioso ENTÃO ...", ch("mode", "Modo", listOf("normal" to "Normal", "vibrate" to "Vibrar", "silent" to "Silencioso"), "silent")),
        C("brightness_level", "Ajustes", "Brilho da tela", "Screen brightness?", "Compara o brilho atual (0 a 100%).", "Escolha a comparação e o valor.", "SE o brilho está acima de 80% ENTÃO baixar.", ch("op", "É", CMP, "above"), num("value", "Valor (%)", "50")),
        C("dnd_is", "Ajustes", "Não incomodar ligado?", "Interruptions?", "Verifica se o Não incomodar está ativo.", "Escolha a resposta esperada.", "SE o Não incomodar está ligado ENTÃO ...", STATE),

        // ───────────── TELEFONIA ─────────────
        A("call_answer", "Telefonia", "Atender chamada", "Call answer", "Atende uma chamada que está tocando.", "Não precisa configurar. Em alguns aparelhos pode não funcionar.", "Conectou no carro e é minha esposa ligando → atender.", shz = true),
        A("call_end", "Telefonia", "Encerrar chamada", "Call end", "Desliga a chamada atual.", "Não precisa configurar.", "Chamada de número desconhecido → desligar.", shz = true),
        A("call_number", "Telefonia", "Ligar para um número", "Call number", "Faz uma ligação na hora, sem confirmar.", "Digite o número.", "Botão ▶ 'Ligar para casa'.", txt("number", "Número")),
        A("dial_number", "Telefonia", "Discar número (sem ligar)", "Dial number", "Abre o discador com o número pronto.", "Digite o número.", "Abrir o discador com o número do suporte.", txt("number", "Número")),
        C("call_state_is", "Telefonia", "Estado da chamada", "Call state?", "Verifica se o telefone está livre, tocando ou em chamada.", "Escolha o estado.", "SE está em chamada ENTÃO não mexer no volume.",
            ch("mode", "Estado", listOf("idle" to "Livre", "ringing" to "Tocando", "offhook" to "Em chamada"), "offhook")),
        C("operator_is", "Telefonia", "Operadora", "Mobile operator?", "Verifica o nome da operadora.", "Digite parte do nome.", "SE a operadora contém 'Vodafone' ENTÃO ...", txt("name", "Nome da operadora")),
        C("roaming", "Telefonia", "Em roaming?", "Roaming?", "Verifica se você está em roaming (fora da rede de origem).", "Escolha a resposta esperada.", "SE está em roaming ENTÃO desligar dados móveis.", STATE),
    )

    private val SENSORS get() = listOf(
        "light" to "Luz ambiente (lux)", "proximity" to "Proximidade (cm)", "pressure" to "Pressão (hPa)",
        "temperature" to "Temperatura ambiente (°C)", "humidity" to "Umidade (%)", "magnetic" to "Campo magnético (µT)",
    )
}
