package app.fluxo

/**
 * Blocos inspirados nos Atalhos da Apple (texto, números, datas, listas, variáveis, repetição)
 * e novos gatilhos (QUANDO). Aparecem sem precisar de "avançado", exceto os marcados.
 */
object ShortcutsCatalog {
    private fun txt(k: String, l: String, def: String = "", hint: String = "") = P(k, l, PT.TEXT, def = def, hint = hint)
    private fun num(k: String, l: String, def: String = "", hint: String = "") = P(k, l, PT.NUMBER, def = def, hint = hint)
    private fun ch(k: String, l: String, o: List<Pair<String, String>>, def: String) = P(k, l, PT.CHOICE, o, def)
    private val SAVE = txt("save", "Guardar resultado na variável", hint = "ex.: resultado (depois use {resultado})")
    private val ONOFF = listOf("on" to "Ligado", "off" to "Desligado")
    private val YESNO = listOf("yes" to "Sim", "no" to "Não")
    private val APPOPT = P("pkg", "App (vazio = qualquer)", PT.APP)

    private fun T(id: String, cat: String, title: String, summary: String, howTo: String, example: String, vararg p: P) =
        Spec(Kind.TRIGGER, id, cat, title, summary, howTo, example, p.toList())

    private fun A(id: String, cat: String, title: String, summary: String, howTo: String, example: String, vararg p: P) =
        Spec(Kind.ACTION, id, cat, title, summary, howTo, example, p.toList(), advanced = false, shizuku = false)

    private fun C(id: String, cat: String, title: String, summary: String, howTo: String, example: String, vararg p: P) =
        Spec(Kind.CONDITION, id, cat, title, summary, howTo, example, p.toList())

    val all: List<Spec> = listOf(
        // ───────────── NOVOS GATILHOS (QUANDO) ─────────────
        T("app_open", "Apps", "App aberto ou fechado",
            "Dispara quando você abre ou fecha um app específico. O app aberto fica na variável {app}.",
            "Escolha o app e se é ao abrir ou ao fechar. Na tela Configuração, dê o 'Acesso ao uso de apps' ao Fluxo (botão com Shizuku, 1 toque).",
            "Abriu o Netflix → aumentar o brilho e ligar Não incomodar. Fechou → voltar ao normal.",
            P("pkg", "App", PT.APP), ch("when", "Quando", listOf("open" to "Abrir", "close" to "Fechar"), "open")),
        T("notification", "Apps", "Notificação recebida",
            "Dispara quando chega uma notificação. Você pode filtrar por app e por texto. Ficam disponíveis {notif_app}, {notif_titulo} e {notif_texto}.",
            "Escolha o app (ou deixe vazio para qualquer) e um texto que a notificação precisa conter (opcional). Na tela Configuração, dê o 'Acesso às notificações'.",
            "Notificação do banco contendo 'compra' → falar em voz alta o texto.",
            APPOPT, txt("contains", "Contém o texto (opcional)")),
        T("sms", "Mensagens", "SMS recebido",
            "Dispara quando chega um SMS. Ficam disponíveis {sms_de} e {sms_texto}.",
            "Preencha parte do número e/ou um texto que o SMS precisa conter (ambos opcionais). Permita 'Receber SMS' na tela Configuração e reabra o app.",
            "SMS com 'código' → copiar o texto para a área de transferência.",
            txt("from", "Número contém (opcional)"), txt("contains", "Texto contém (opcional)")),
        T("interval", "Tempo", "A cada X minutos",
            "Dispara repetidamente, de X em X minutos, enquanto o Fluxo está ativo.",
            "Digite os minutos (mínimo 1). Conta a partir do momento em que o Fluxo inicia.",
            "A cada 30 minutos → ler a temperatura da bateria e avisar se passar de 40.",
            num("minutes", "Minutos", "30")),
        T("boot", "Dispositivo", "Aparelho ligou",
            "Dispara quando o aparelho termina de ligar (depois de reiniciar).", "Não precisa configurar.", "Aparelho ligou → ligar o Bluetooth e a localização."),
        T("shake", "Sensores", "Chacoalhar o aparelho",
            "Dispara quando você balança o aparelho com força.", "Não precisa configurar. Balance duas vezes rapidamente.", "Chacoalhou → ligar/desligar a lanterna."),
        T("dnd", "Dispositivo", "Não incomodar ligou/desligou",
            "Dispara quando o Não incomodar muda.", "Escolha ligado ou desligado.", "Não incomodar desligou → voltar o volume de mídia.",
            ch("when", "Quando", ONOFF, "on")),
        T("ringer", "Dispositivo", "Modo de som mudou",
            "Dispara quando o modo de som muda para normal, vibrar ou silencioso.", "Escolha o modo.", "Foi para silencioso → apagar o brilho.",
            ch("when", "Mudou para", listOf("normal" to "Normal", "vibrate" to "Vibrar", "silent" to "Silencioso"), "silent")),
        T("volume", "Dispositivo", "Volume mudou",
            "Dispara quando você mexe em algum volume.", "Não precisa configurar.", "Mexeu no volume → mostrar aviso com o nível."),
        T("bt_state", "Conexões", "Bluetooth ligou/desligou",
            "Dispara quando o Bluetooth é ligado ou desligado (não quando um aparelho conecta).", "Escolha ligado ou desligado.", "Bluetooth desligou → avisar.",
            ch("when", "Quando", ONOFF, "on")),
        T("wifi_state", "Conexões", "Wi-Fi ligou/desligou",
            "Dispara quando o Wi-Fi é ligado ou desligado.", "Escolha ligado ou desligado.", "Wi-Fi desligou → ligar os dados móveis.",
            ch("when", "Quando", ONOFF, "off")),
        T("app_install", "Apps", "App instalado ou removido",
            "Dispara quando um app é instalado ou desinstalado.", "Escolha instalar/remover e, se quiser, um app específico.", "Instalou um app novo → avisar.",
            ch("when", "Quando", listOf("install" to "Instalado", "remove" to "Removido"), "install"), APPOPT),
        T("timezone", "Tempo", "Fuso horário mudou", "Dispara quando o fuso horário do aparelho muda (viagem).", "Não precisa configurar.", "Mudou o fuso → ajustar o alarme."),
        T("broadcast", "Apps", "Broadcast recebido",
            "Dispara quando outro app (ou o sistema) envia uma mensagem 'broadcast'. Serve para integrar com Tasker, Automate etc.", "Digite o nome da ação do broadcast.", "Outro app envia com.exemplo.AVISO → tocar um som.",
            txt("action", "Ação do broadcast")),
        T("place", "Local", "Chegar ou sair de um lugar",
            "Dispara quando você entra ou sai de um lugar (círculo no mapa).",
            "Toque em 'Usar minha localização atual' estando no lugar, defina o raio e escolha chegar/sair. Dê 'Localização em segundo plano' na tela Configuração.",
            "Cheguei em casa → ligar o Wi-Fi e abrir o app de casa.",
            P("loc", "Lugar (latitude,longitude)", PT.LOCATION), num("radius", "Raio (metros)", "150"),
            ch("when", "Quando", listOf("arrive" to "Chegar", "leave" to "Sair"), "arrive")),
        T("sun", "Local", "Nascer ou pôr do sol",
            "Dispara no nascer ou no pôr do sol do seu lugar, com ajuste opcional em minutos.",
            "Defina o lugar (use o botão de localização), escolha nascer/pôr e o ajuste (negativo = antes).",
            "30 minutos antes do pôr do sol → ligar o modo escuro.",
            P("loc", "Lugar (latitude,longitude)", PT.LOCATION), ch("when", "Quando", listOf("sunrise" to "Nascer do sol", "sunset" to "Pôr do sol"), "sunset"),
            num("offset", "Ajuste (minutos)", "0")),

        // ───────────── TEXTO (Atalhos) ─────────────
        A("text_replace", "Texto", "Substituir texto", "Troca uma parte do texto por outra e guarda o resultado.", "Digite o texto, o trecho a achar e o novo trecho. Pode usar regex.", "Trocar vírgula por ponto num número.",
            txt("text", "Texto"), txt("find", "Achar"), txt("repl", "Trocar por"), ch("regex", "É regex?", YESNO, "no"), SAVE),
        A("text_split", "Texto", "Dividir texto e pegar um item", "Divide um texto por um separador e pega um pedaço.", "Digite o texto, o separador e o número do item (1 = primeiro, -1 = último).", "Pegar o nome do Wi-Fi antes do '-'.",
            txt("text", "Texto"), txt("sep", "Separador", ","), num("index", "Item (1 = primeiro)", "1"), SAVE),
        A("text_case", "Texto", "Maiúsculas/minúsculas", "Muda o texto para MAIÚSCULAS, minúsculas ou Primeira Maiúscula.", "Escolha o formato.", "Falar o nome da rede com a primeira maiúscula.",
            txt("text", "Texto"), ch("mode", "Formato", listOf("upper" to "MAIÚSCULAS", "lower" to "minúsculas", "title" to "Primeira Maiúscula"), "upper"), SAVE),
        A("text_trim", "Texto", "Aparar espaços", "Tira os espaços do começo e do fim.", "Digite o texto.", "Limpar o texto de um arquivo.", txt("text", "Texto"), SAVE),
        A("text_substring", "Texto", "Pegar parte do texto", "Pega um pedaço do texto (a partir de uma posição, com um tamanho).", "Posição 1 é o primeiro caractere.", "Pegar os 4 primeiros caracteres.",
            txt("text", "Texto"), num("start", "Começar em", "1"), num("len", "Tamanho", "5"), SAVE),
        A("text_length", "Texto", "Contar caracteres", "Conta quantos caracteres tem o texto.", "Digite o texto.", "Saber se a mensagem passa de 160.", txt("text", "Texto"), SAVE),
        A("text_match", "Texto", "Extrair com regex", "Procura um padrão (regex) no texto e guarda o primeiro trecho encontrado.", "Digite o texto e a regex (ex.: \\d{6} acha 6 números).", "Pegar o código de 6 dígitos de um SMS.",
            txt("text", "Texto"), txt("regex", "Regex"), SAVE),
        A("text_base64", "Texto", "Base64", "Codifica ou decodifica em Base64.", "Escolha codificar ou decodificar.", "Montar um cabeçalho de autenticação.",
            txt("text", "Texto"), ch("mode", "Ação", listOf("enc" to "Codificar", "dec" to "Decodificar"), "enc"), SAVE),
        A("text_urlencode", "Texto", "Codificar para URL", "Troca espaços e símbolos para poder usar num link.", "Digite o texto.", "Montar um link de busca.", txt("text", "Texto"), SAVE),
        A("text_hash", "Texto", "Hash (MD5, SHA)", "Calcula o hash de um texto.", "Escolha o algoritmo.", "Gerar uma assinatura.",
            txt("text", "Texto"), ch("algo", "Algoritmo", listOf("MD5" to "MD5", "SHA-1" to "SHA-1", "SHA-256" to "SHA-256"), "SHA-256"), SAVE),

        // ───────────── NÚMEROS E DATAS ─────────────
        A("calc", "Números", "Calcular", "Faz uma conta e guarda o resultado. Aceita + - * / % e parênteses, e variáveis como {bateria}.", "Digite a expressão.", "{bateria} * 2 / 100.",
            txt("expr", "Expressão", hint = "{bateria} / 2 + 10"), SAVE),
        A("round", "Números", "Arredondar", "Arredonda um número para um número de casas decimais.", "Digite o número e as casas.", "Arredondar 3.14159 para 2 casas.",
            txt("value", "Número"), num("digits", "Casas decimais", "0"), SAVE),
        A("random_number", "Números", "Número aleatório", "Sorteia um número inteiro entre dois valores.", "Digite o mínimo e o máximo.", "Sortear de 1 a 6.", num("min", "Mínimo", "1"), num("max", "Máximo", "10"), SAVE),
        A("date_now", "Data e hora", "Data e hora (formatada)", "Pega a data e hora atuais (ou somando/subtraindo um tempo) e formata do jeito que você quiser.",
            "Formato: dd/MM/yyyy HH:mm. Em 'Somar' use números negativos para voltar no tempo.", "Guardar a data de amanhã em {amanha}.",
            txt("format", "Formato", "dd/MM/yyyy HH:mm"), num("add", "Somar", "0"), ch("unit", "Unidade", listOf("m" to "Minutos", "h" to "Horas", "d" to "Dias"), "d"), SAVE),
        A("date_diff", "Data e hora", "Tempo até uma hora", "Calcula quantos minutos faltam para uma hora do dia (hoje, ou amanhã se já passou).", "Escolha a hora.", "Quantos minutos faltam para 23:00?",
            P("time", "Hora", PT.TIME, def = "23:00"), SAVE),

        // ───────────── LISTAS E VARIÁVEIS ─────────────
        A("list_add", "Listas", "Adicionar item à lista", "Acrescenta um item no fim de uma lista (guardada numa variável, separada por vírgula).", "Digite o nome da lista e o item.", "Guardar cada Wi-Fi visitado na lista {wifis}.",
            txt("list", "Nome da lista"), txt("item", "Item")),
        A("list_remove", "Listas", "Remover item da lista", "Tira um item da lista.", "Digite o nome da lista e o item.", "Remover um Wi-Fi da lista.", txt("list", "Nome da lista"), txt("item", "Item")),
        A("list_get", "Listas", "Pegar item da lista", "Pega um item pela posição ou sorteia um.", "Posição 1 = primeiro; vazio = sorteia.", "Sortear uma música da lista.",
            txt("list", "Nome da lista"), num("index", "Posição (vazio = sorteio)"), SAVE),
        A("list_count", "Listas", "Contar itens da lista", "Conta quantos itens a lista tem.", "Digite o nome da lista.", "Saber quantos Wi-Fi visitei.", txt("list", "Nome da lista"), SAVE),
        A("var_increment", "Variáveis", "Somar na variável", "Soma (ou subtrai) um valor numa variável. Se não existir, começa em 0.", "Digite o nome e o valor.", "Contar quantas vezes abri o app.", txt("name", "Variável"), num("by", "Somar", "1")),
        A("var_clear", "Variáveis", "Apagar variável", "Apaga uma variável.", "Digite o nome.", "Zerar o contador.", txt("name", "Variável")),

        // ───────────── LÓGICA ─────────────
        A("loop", "Lógica", "Repetir",
            "Repete um grupo de passos. Pode repetir um número de vezes ou uma vez para cada item de uma lista.",
            "Escolha 'N vezes' ou 'Para cada item'. Em 'para cada', o item atual fica numa variável. Dentro do bloco, adicione as ações.",
            "Para cada Wi-Fi da lista {wifis} → falar o nome.",
            ch("mode", "Repetir", listOf("times" to "N vezes", "each" to "Para cada item da lista"), "times"),
            num("count", "Quantas vezes", "3"), txt("list", "Lista (para cada)"), txt("item", "Variável do item", "item")),

        C("compare", "Lógica", "Comparar valores",
            "Compara dois valores (texto ou número). Aceita variáveis como {resultado}.",
            "Escreva o valor da esquerda, escolha a comparação e o da direita.", "SE {notif_texto} contém 'urgente' ENTÃO tocar um alarme.",
            txt("left", "Valor"), ch("op", "É", listOf("==" to "igual a", "!=" to "diferente de", ">" to "maior que", "<" to "menor que", ">=" to "maior ou igual", "<=" to "menor ou igual",
                "contains" to "contém", "starts" to "começa com", "ends" to "termina com", "regex" to "bate com regex", "empty" to "está vazio", "notempty" to "não está vazio"), "contains"),
            txt("right", "Outro valor")),
        C("expression", "Lógica", "Expressão verdadeira",
            "Verdadeiro se uma expressão é verdadeira. Aceita números, && (e), || (ou), ! (não), comparações e variáveis.", "Escreva a expressão.", "SE {bateria} < 20 && {hora} > 22 ENTÃO ...",
            txt("expr", "Expressão", hint = "{bateria} < 20 && {x} == 1")),
        C("daylight", "Local", "É dia?", "Verdadeiro se agora é dia (entre nascer e pôr do sol) no lugar escolhido.", "Defina o lugar com o botão de localização.", "SE é noite ENTÃO ligar modo escuro.",
            P("loc", "Lugar (latitude,longitude)", PT.LOCATION), ch("state", "Resposta esperada", YESNO, "yes")),
        C("at_place", "Local", "Estou num lugar?", "Verdadeiro se a última localização conhecida está dentro do círculo.", "Defina o lugar e o raio. Precisa de permissão de localização.", "SE estou em casa ENTÃO ...",
            P("loc", "Lugar (latitude,longitude)", PT.LOCATION), num("radius", "Raio (metros)", "150"), ch("state", "Resposta esperada", YESNO, "yes")),
        A("location_get", "Local", "Ler minha localização", "Lê a última localização conhecida e guarda como 'latitude,longitude'.", "Dê um nome para a variável. Precisa de permissão de localização.", "Guardar em {pos}.", SAVE),
    )
}
