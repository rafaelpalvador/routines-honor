package app.fluxo

/** Última leva de blocos: janelas na tela, NFC, atalhos, calendário, contatos, imagem/OCR, clima, scan etc. */
object MoreCatalog {
    private fun txt(k: String, l: String, def: String = "", hint: String = "") = P(k, l, PT.TEXT, def = def, hint = hint)
    private fun num(k: String, l: String, def: String = "", hint: String = "") = P(k, l, PT.NUMBER, def = def, hint = hint)
    private fun ch(k: String, l: String, o: List<Pair<String, String>>, def: String) = P(k, l, PT.CHOICE, o, def)
    private val SAVE = txt("save", "Guardar resultado na variável", hint = "ex.: resultado (depois use {resultado})")
    private val YESNO = listOf("yes" to "Sim", "no" to "Não")
    private val ONOFF = listOf("on" to "Ligar", "off" to "Desligar")
    private val ONFAIL = ch("onCancel", "Se você cancelar", listOf("continue" to "Continuar a rotina", "stop" to "Parar a rotina"), "continue")

    private fun T(id: String, cat: String, title: String, summary: String, howTo: String, example: String, vararg p: P) =
        Spec(Kind.TRIGGER, id, cat, title, summary, howTo, example, p.toList())
    private fun A(id: String, cat: String, title: String, summary: String, howTo: String, example: String, vararg p: P, shz: Boolean = false, adv: Boolean = false) =
        Spec(Kind.ACTION, id, cat, title, summary, howTo, example, p.toList(), advanced = adv, shizuku = shz)
    private fun C(id: String, cat: String, title: String, summary: String, howTo: String, example: String, vararg p: P) =
        Spec(Kind.CONDITION, id, cat, title, summary, howTo, example, p.toList())

    val all: List<Spec> = listOf(
        // ───────── gatilhos ─────────
        T("nfc", "Conexões", "NFC lida", "Dispara quando você encosta uma tag NFC no aparelho. O código da tag fica em {nfc_id} e o texto (se tiver) em {nfc_texto}.",
            "Encoste a tag uma vez com o Fluxo aberto: o código aparece na tela Registro. Depois cole o código aqui (ou deixe vazio para qualquer tag). No Honor o NFC precisa estar ligado.",
            "Tag na mesa de cabeceira → ligar Não incomodar e alarme.", txt("tag", "Código da tag (vazio = qualquer)")),
        T("tile", "Dispositivo", "Botão na barra rápida", "Dispara quando você toca num botão do Fluxo na barra de configurações rápidas (puxe a barra duas vezes).",
            "Edite a barra rápida do Android, adicione 'Fluxo 1', 'Fluxo 2' ou 'Fluxo 3' e escolha qual número esta rotina usa.", "Botão 'Fluxo 1' → modo cinema.",
            ch("n", "Botão", listOf("1" to "Fluxo 1", "2" to "Fluxo 2", "3" to "Fluxo 3"), "1")),
        T("cal_event", "Tempo", "Evento do calendário", "Dispara quando um evento do seu calendário começa (ou X minutos antes). O título fica em {evento_titulo}.",
            "Escolha quantos minutos antes e, se quiser, um texto que o título precisa conter. Permita 'Calendário' na tela Configuração.", "10 minutos antes de uma reunião → ligar Não incomodar.",
            num("before", "Minutos antes", "0"), txt("contains", "Título contém (opcional)")),

        // ───────── janelas na tela (Atalhos / Automate) ─────────
        A("dialog_message", "Janelas", "Mostrar alerta", "Abre uma janela com uma mensagem e o botão OK. A rotina espera você tocar.", "Digite título e mensagem. Precisa do Shizuku para abrir com o app em segundo plano.", "Terminou o backup → alerta 'Backup pronto'.",
            txt("title", "Título"), txt("msg", "Mensagem"), shz = true),
        A("dialog_confirm", "Janelas", "Pedir confirmação (Sim/Não)", "Pergunta Sim ou Não. O resultado fica na variável (yes ou no).", "Digite a pergunta e a variável. Depois use SE para decidir.", "Perguntar 'Ligar o modo avião?' antes de ligar.",
            txt("title", "Pergunta"), SAVE, ONFAIL, shz = true),
        A("dialog_input", "Janelas", "Pedir texto", "Abre uma janela para você digitar um texto. O texto fica na variável.", "Digite a pergunta, um valor padrão (opcional) e a variável.", "Perguntar o nome da nota e guardar em {nome}.",
            txt("title", "Pergunta"), txt("def", "Valor padrão"), SAVE, ONFAIL, shz = true),
        A("dialog_choice", "Janelas", "Escolher de uma lista", "Mostra opções para você escolher uma. O escolhido fica na variável.", "Digite as opções separadas por vírgula.", "Opções 'Trabalho,Casa,Viagem' → guardar em {modo}.",
            txt("title", "Pergunta"), txt("opts", "Opções (separe por vírgula)"), SAVE, ONFAIL, shz = true),
        A("time_pick", "Janelas", "Escolher uma hora", "Abre o seletor de hora. O resultado fica como HH:mm.", "Dê um nome para a variável.", "Perguntar a que horas acordar.", txt("title", "Título"), SAVE, ONFAIL, shz = true),
        A("date_pick", "Janelas", "Escolher uma data", "Abre o seletor de data. O resultado fica como aaaa-mm-dd.", "Dê um nome para a variável.", "Perguntar a data do evento.", txt("title", "Título"), SAVE, ONFAIL, shz = true),

        // ───────── lógica extra ─────────
        A("try", "Lógica", "Tentar / Se falhar", "Roda um grupo de passos. Se algum falhar, roda o outro grupo (e a mensagem do erro fica em {erro}).", "Adicione ações em 'TENTAR' e, se quiser, em 'SE FALHAR'.", "Tentar ligar o Wi-Fi; se falhar, mostrar um aviso."),
        A("until", "Lógica", "Esperar até", "Pausa a rotina até uma condição ficar verdadeira (máximo 5 minutos).", "Toque no cartão para escolher a condição.", "Esperar até conectar no Wi-Fi 'Casa' e então sincronizar."),
        A("dict_put", "Variáveis", "Dicionário: guardar", "Guarda um valor com uma chave (como uma ficha). Use depois como {nome.chave}.", "Digite o nome do dicionário, a chave e o valor.", "casa.temperatura = 22.", txt("name", "Dicionário"), txt("key", "Chave"), txt("value", "Valor")),
        A("dict_get", "Variáveis", "Dicionário: ler", "Lê um valor pela chave.", "Digite o dicionário e a chave.", "Ler casa.temperatura.", txt("name", "Dicionário"), txt("key", "Chave"), SAVE),
        A("dict_remove", "Variáveis", "Dicionário: remover", "Remove uma chave.", "Digite o dicionário e a chave.", "Apagar casa.temperatura.", txt("name", "Dicionário"), txt("key", "Chave")),
        A("list_set", "Listas", "Trocar item da lista", "Troca o item de uma posição.", "Posição 1 = primeiro.", "Trocar o 2º item da lista.", txt("list", "Nome da lista"), num("index", "Posição", "1"), txt("item", "Novo item")),

        // ───────── notificações ─────────
        A("notif_dismiss_all", "Apps", "Limpar todas as notificações", "Apaga todas as notificações da barra.", "Precisa do 'Acesso às notificações' (tela Configuração).", "Ao dormir → limpar notificações."),
        A("notif_dismiss_app", "Apps", "Limpar notificações de um app", "Apaga as notificações de um app.", "Escolha o app. Precisa do 'Acesso às notificações'.", "Limpar as do app de promoções.", P("pkg", "App", PT.APP)),
        A("notif_count", "Apps", "Contar notificações", "Conta quantas notificações existem (de um app ou de todos).", "Escolha o app (opcional) e a variável.", "Quantas notificações tenho?", P("pkg", "App (vazio = todos)", PT.APP), SAVE),

        // ───────── calendário e contatos ─────────
        A("calendar_insert", "Conteúdo", "Criar evento no calendário (direto)", "Cria um evento no calendário sem abrir nada.", "Digite título, em quantos minutos começa e a duração. Permita 'Calendário'.", "Chegou em casa → evento 'Jantar' daqui a 30 min.",
            txt("title", "Título"), num("in", "Começa em (minutos)", "30"), num("dur", "Duração (minutos)", "60")),
        A("calendar_next", "Conteúdo", "Próximo evento do calendário", "Pega o título do próximo evento nas próximas 24 horas.", "Dê um nome para a variável do título e, se quiser, outra para a hora.", "Falar o próximo compromisso.",
            SAVE, txt("save_time", "Variável da hora (opcional)")),
        A("contact_query", "Conteúdo", "Buscar contato", "Procura um contato pelo nome e guarda o número.", "Digite parte do nome. Permita 'Contatos'.", "Ligar para 'Mãe' (buscar e usar em Ligar).", txt("name", "Nome"), SAVE),
        A("sync_set", "Conteúdo", "Sincronização automática", "Liga ou desliga a sincronização automática de contas.", "Escolha Ligar ou Desligar.", "Bateria baixa → desligar a sincronização.", ch("state", "Estado", ONOFF, "off")),
        C("sync_on", "Conteúdo", "Sincronização ligada?", "Verifica se a sincronização automática está ligada.", "Escolha a resposta esperada.", "SE a sincronização está ligada ENTÃO ...", ch("state", "Resposta esperada", YESNO, "yes")),

        // ───────── imagem, OCR, QR ─────────
        A("screen_ocr", "Imagem", "Ler texto da tela (OCR)", "Tira uma captura da tela e lê o texto que aparece nela.", "Dê um nome para a variável. Precisa do Shizuku.", "Ler o código na tela e copiar.", SAVE, shz = true),
        A("ocr_image", "Imagem", "Ler texto de uma imagem (OCR)", "Lê o texto de uma foto ou imagem.", "Digite o caminho da imagem. Permita 'Fotos' na tela Configuração.", "Ler o texto de /sdcard/Pictures/nota.jpg.", txt("path", "Imagem"), SAVE),
        A("barcode_scan", "Imagem", "Ler QR code / código de barras", "Lê um QR code ou código de barras de uma imagem.", "Digite o caminho da imagem.", "Ler o QR de uma captura.", txt("path", "Imagem"), SAVE),
        A("image_edit", "Imagem", "Editar imagem", "Gira, espelha, redimensiona ou corta uma imagem e salva o resultado.", "Escolha a operação. Girar: graus. Redimensionar: porcentagem. Cortar: x,y,largura,altura.", "Reduzir uma foto para 50%.",
            txt("path", "Imagem"), ch("op", "Operação", listOf("rotate" to "Girar", "flip_h" to "Espelhar na horizontal", "flip_v" to "Espelhar na vertical", "scale" to "Redimensionar (%)", "crop" to "Cortar"), "rotate"),
            txt("value", "Valor", hint = "90 / 50 / 0,0,500,500"), txt("out", "Salvar em (vazio = ao lado)")),
        A("image_color", "Imagem", "Cor de um pixel", "Lê a cor de um ponto da imagem (#RRGGBB).", "Digite a imagem e x, y.", "Ver a cor do pixel 10,10.", txt("path", "Imagem"), num("x", "x", "0"), num("y", "y", "0"), SAVE),
        A("wallpaper_set", "Imagem", "Trocar papel de parede", "Troca o papel de parede por uma imagem.", "Digite o caminho e onde aplicar.", "Todo dia → um papel de parede da pasta.", txt("path", "Imagem"),
            ch("where", "Onde", listOf("both" to "Início e bloqueio", "home" to "Início", "lock" to "Bloqueio"), "both")),

        // ───────── rede, clima ─────────
        A("weather_get", "Local", "Clima (temperatura)", "Busca o clima atual de um lugar (serviço gratuito Open-Meteo). Guarda a temperatura em °C e, opcionalmente, o código do tempo e o vento.",
            "Defina o lugar com o botão de localização e dê nomes às variáveis.", "Se a temperatura é menor que 10 → avisar para levar casaco.",
            P("loc", "Lugar (latitude,longitude)", PT.LOCATION), SAVE, txt("save_code", "Variável do código do tempo (opcional)"), txt("save_wind", "Variável do vento km/h (opcional)")),
        A("ip_local", "Conexões", "Meu endereço IP", "Lê o endereço IP do aparelho na rede.", "Dê um nome para a variável.", "Guardar em {ip}.", SAVE),
        A("wifi_scan", "Conexões", "Procurar redes Wi-Fi", "Procura redes Wi-Fi por perto e guarda a lista de nomes. Precisa de Localização.", "Dê um nome para a variável.", "Listar redes ao chegar.", SAVE),
        A("bt_scan", "Conexões", "Procurar aparelhos Bluetooth", "Procura aparelhos Bluetooth por perto (leva uns 8 segundos).", "Dê um nome para a variável. Permita 'Bluetooth (procurar)'.", "Ver se o relógio está por perto.", SAVE),
        C("wifi_nearby", "Conexões", "Rede Wi-Fi por perto?", "Verifica se uma rede aparece na última busca de Wi-Fi.", "Digite o nome da rede.", "SE a rede 'Casa' está por perto ENTÃO ...", txt("ssid", "Nome da rede"), ch("state", "Resposta esperada", YESNO, "yes")),
        C("bt_nearby", "Conexões", "Aparelho Bluetooth por perto?", "Procura por ~8 segundos e verifica se o aparelho foi encontrado.", "Digite o nome do aparelho.", "SE o fone está por perto ENTÃO ...", txt("name", "Nome do aparelho"), ch("state", "Resposta esperada", YESNO, "yes")),
    )
}
