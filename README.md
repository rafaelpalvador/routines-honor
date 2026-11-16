# Fluxo — rotinas SE / ENTÃO / SENÃO para Android (Honor 400 Pro)

> ⚠️ Código escrito sem compilador: pode haver erros de compilação. Cole o erro do Android Studio no chat que eu corrijo.

## Como obter o APK
Cada push no GitHub gera um APK em Releases (Fluxo.apk). Baixe no celular e instale.

## Como compilar (opcional, Android Studio)
1. Instale o Android Studio (versão recente) e abra a pasta `fluxo`.
2. Aguarde o Gradle sincronizar (precisa de internet).
3. Clique em Run, ou use o APK das Releases.

## Primeiro uso
Abra o app → ícone de engrenagem (Configuração) e resolva cada item pendente:
Shizuku, permissões, alarmes, bateria e o passo manual do Honor:
Configurações → Bateria → Início de apps → Fluxo → Gerenciar manualmente → ligar as 3 opções.

## Como usar
- QUANDO: gatilhos (qualquer um dispara).
- FAZER: ações em ordem; "Se / Senão" cria ramos com condições E/OU/NÃO.
- Toque em ⓘ em qualquer opção para ver: para que serve, como usar e exemplo.
- Variáveis em textos: {bateria} {hora} {wifi} e as suas (bloco "Guardar variável").
- Opções avançadas: ligue "Mostrar opções avançadas" no seletor.

## Como adicionar opções do Automate
Tudo vem de `Catalog.kt`. Para cada bloco novo:
1. Adicione um `act(...)` / `trig(...)` / `cond(...)` com título, "para que serve", "como usar", exemplo e `advanced = true`.
2. Se for ação, adicione um ramo com o mesmo `id` em `Actions.kt`.
Me mande a lista do Automate e eu faço isso para você.
