<div align="center">

# 🪐 Onix

**Um launcher para Android TV, Google TV e TVs Samsung (Tizen), feito para ser rápido no controle remoto.**

Visual escuro de vidro, apps organizados do seu jeito, indicações da semana da Netflix,
notícias de games, jogos embutidos, troca de entradas HDMI e uma tela de descanso espacial.

![Versão](https://img.shields.io/badge/vers%C3%A3o-13.4.0%20beta-white?style=flat-square)
![Android](https://img.shields.io/badge/Android-5.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white)
![Plataforma](https://img.shields.io/badge/Android%20TV%20%7C%20Google%20TV-black?style=flat-square)
![Samsung](https://img.shields.io/badge/Samsung%20Tizen-2022%2B%20(beta)-1428a0?style=flat-square&logo=samsung&logoColor=white)
![Licença](https://img.shields.io/badge/licen%C3%A7a-MIT-blue?style=flat-square)

<img src="docs/screenshots/inicio.png" alt="Tela inicial do Onix" width="900">

</div>

---

## Sumário

- [Recursos](#recursos)
- [Capturas de tela](#capturas-de-tela)
- [Download: Onix App ou Onix Launcher?](#download-onix-app-ou-onix-launcher)
- [Instalação](#instalação)
- [TVs Samsung (Tizen)](#tvs-samsung-tizen)
- [Usar como tela inicial (Onix Launcher)](#usar-como-tela-inicial-onix-launcher)
- [Ativar o botão "Desligar TV"](#ativar-o-botão-desligar-tv)
- [Como desfazer tudo](#como-desfazer-tudo)
- [Permissões e privacidade](#permissões-e-privacidade)
- [Compilar a partir do código](#compilar-a-partir-do-código)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Personalização](#personalização)
- [Limitações conhecidas](#limitações-conhecidas)
- [Créditos](#créditos)

---

## Recursos

### Início
- **Destaque rotativo** com as indicações da semana. O botão **Assistir na Netflix** abre o título direto na página dele no app da Netflix.
- **Seus apps**: uma fileira com os seus apps favoritos. Dá para adicionar, remover e reordenar na tela **Organizar seus apps**.
- **Indicações da semana**: títulos populares da Netflix Brasil (via JustWatch), complementados pelos filmes mais vistos da iTunes Store. A lista é renovada toda segunda-feira.
- **Botão Atualizar**: baixa notícias, indicações e a lista de apps na hora, sem esperar o próximo ciclo automático.

### Apps
- Grade com todos os apps instalados, com o banner oficial de TV de cada um quando existir.

### Jogos
Quatro jogos feitos para o controle remoto, com música chiptune e recorde salvo:

| Jogo | Como ganhar | Poderes e malefícios |
|---|---|---|
| **Pong** | Quem fizer 5 pontos primeiro vence | `+` raquete maior · `−` raquete menor · `»` turbo · `\|\|` congela o adversário |
| **Pinball** | Fazer 10.000 pontos antes de perder as 3 bolas | Faixas que sobem o multiplicador, alvos e bumpers |
| **Tijolos** | Quebrar todos os tijolos de cada fase | Bons: **W** larga, **♥** vida, **S** lenta, **F** fogo · Ruins: **−** curta, **R** rápida |
| **Cobrinha** | Crescer o máximo possível | Pedras surgem a cada 3 maçãs; a maçã dourada vale 50 |

### Notícias dos games
- As últimas notícias da **Adrenaline** e da **Eurogamer.pt**, atualizadas a cada 3 horas.
- **OK** abre um resumo dentro do próprio Onix. No fim, um **QR code** leva à matéria completa no celular, já que a maioria das TVs não tem navegador.

### Entradas (HDMI)
- Troca de entrada HDMI direto pelo launcher.
- **Banners editáveis**: a canetinha embaixo de cada porta alterna entre PlayStation 5, Xbox, Nintendo Switch, Videogame, Computador ou HDMI genérico.
- Atalho para os **ajustes de imagem** da TV.

### Tela de descanso
- Depois de 10 minutos sem usar o controle, planetas e asteroides passam devagar sobre um céu preto, com relógio e estrelas cadentes.
- O relógio muda de posição a cada minuto, para não marcar telas OLED.

### Outros
- **Desligar TV** no menu: pede confirmação com um segundo OK e coloca a TV em standby.
- **Configurações do Onix**: desliga efeitos visuais para TVs mais fracas e controla relógio de 24 h, sons do menu e tela de descanso.

---

## Capturas de tela

| Jogos e notícias | Leitor de notícias |
|---|---|
| <img src="docs/screenshots/jogos.png" alt="Jogos e notícias"> | <img src="docs/screenshots/noticia.png" alt="Leitor de notícias"> |

| Entradas HDMI com banners |
|---|
| <img src="docs/screenshots/entradas.png" alt="Entradas HDMI" width="640"> |

---

## Download: Onix App ou Onix Launcher?

Existem duas versões. O conteúdo é o mesmo; muda só a forma como o Android trata o Onix.

| | **Onix App** | **Onix Launcher** |
|---|---|---|
| Download | [onix-app.apk](https://github.com/ruan2311/onix/releases/latest/download/onix-app.apk) | [onix-launcher.apk](https://github.com/ruan2311/onix/releases/latest/download/onix-launcher.apk) |
| Aparece na grade de apps | ✅ | ✅ |
| Pode virar a tela inicial | ❌ | ✅ |
| Recomendado para | Experimentar sem mexer no sistema | Quem quer o Onix como tela principal |

Tem uma **TV Samsung**? Ela não roda Android: use o [Onix para Samsung (Tizen)](#tvs-samsung-tizen), que tem instalação própria.

> **Dica:** comece pelo **Onix App**. Se gostar, instale o **Onix Launcher** por cima. Os dois usam o mesmo pacote (`com.aurora.launcher`), então a troca é uma atualização normal: apps organizados, banners e recordes são mantidos.

---

## Instalação

### Opção 1: direto na TV (sem computador)
1. Na TV, instale o app **Downloader** (by AFTVnews) pela Play Store.
2. Em **Configurações → Apps → Segurança e restrições → Fontes desconhecidas**, permita o Downloader instalar apps. O caminho exato muda um pouco de marca para marca.
3. Abra o Downloader, digite o link do APK da [página de Releases](https://github.com/ruan2311/onix/releases/latest) e confirme a instalação.

### Opção 2: pelo computador, com adb
1. Na TV, ative as **Opções do desenvolvedor**: em **Configurações → Sistema → Sobre**, clique 7 vezes em **Build do sistema operacional**.
2. Em **Opções do desenvolvedor**, ative **Depuração USB** e/ou **Depuração sem fio**.
3. No computador, com o [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) instalado:

```bash
adb connect IP_DA_TV:5555        # ou o IP:porta mostrado em "Depuração sem fio"
adb install -r onix-app.apk      # ou onix-launcher.apk
```

Na primeira conexão, a TV pede para autorizar o computador: aceite.

---

## TVs Samsung (Tizen)

As TVs Samsung usam o **Tizen**, não o Android, então APKs não instalam nelas. O Onix para Samsung é a mesma interface empacotada como app do Tizen (`.wgt`), com uma ponte própria no lugar do código Java.

> 🧪 **Beta.** Esta versão é nova e ainda tem pouco teste em TVs de verdade. Se algo não funcionar na sua, [abra uma issue](https://github.com/ruan2311/onix/issues) dizendo o modelo e o ano da TV.

### O que muda na Samsung

| Recurso | Na Samsung |
|---|---|
| Apps, Organizar seus apps, Jogos, Notícias, Tela de descanso | ✅ Igual ao Android |
| Indicações da semana + **Assistir na Netflix** | ✅ Abre a Netflix no título. Se a sua TV não aceitar o link direto, abre a Netflix e você procura o título |
| Indicações que não são da Netflix | ⚠️ A Samsung não tem a busca "onde assistir" do Google |
| Entradas HDMI | ⚠️ A HDMI aparece **em tela cheia dentro do Onix**. **Voltar** ou **OK** retorna ao Onix. Recursos como Modo Jogo automático podem não ligar nessa janela: para jogar, prefira o botão de fontes do controle |
| Virar a tela inicial | ❌ O Tizen não aceita launchers de terceiros. O Onix abre como um app; dá para fixá-lo na barra da tela inicial |
| Desligar TV, ajustes de imagem, configurações da TV | ❌ O Tizen não deixa apps comuns fazerem isso, então esses botões não aparecem |

**TVs compatíveis:** Samsung de **2022 em diante** (Tizen 6.5 ou mais novo). Em modelos mais antigos, o navegador interno não tem os recursos que a interface usa.

### Por que não tem um `.wgt` pronto para baixar?

A Samsung só instala apps de fora da loja se eles forem **assinados com um certificado gerado para a sua TV** (ele leva o número de série, o DUID). Por isso a página de Releases traz o app pronto, mas sem assinatura (`onix-tizen.zip`). A assinatura você faz uma vez no computador, seguindo os passos abaixo. É de graça e só precisa de uma conta Samsung.

### Passo 1: instale o Tizen Studio no computador

1. Baixe e instale o [Tizen Studio](https://developer.tizen.org/development/tizen-studio/download) (Windows, macOS ou Linux). A versão "CLI", sem a IDE, já basta.
2. Abra o **Package Manager** do Tizen Studio e, na aba **Extension SDK**, instale:
   - **Samsung Certificate Extension**
   - **TV Extensions** (a versão mais nova)
3. Os comandos `tizen` e `sdb` ficam em `tizen-studio/tools/ide/bin` e `tizen-studio/tools`. No Windows, são `C:\tizen-studio\tools\ide\bin\tizen.bat` e `C:\tizen-studio\tools\sdb.exe`. Coloque essas pastas no `PATH` ou use o caminho completo.

### Passo 2: ative o Modo Desenvolvedor na TV

1. Descubra o IP do computador (no Windows, `ipconfig`; no macOS e no Linux, `ip addr` ou Ajustes de Rede). A TV e o computador precisam estar **na mesma rede**.
2. Na TV, abra **Apps** (Aplicativos) na tela inicial.
3. Com a tela de Apps aberta, digite **1 2 3 4 5** no controle. Se o controle não tiver números, use o botão **123** ou o teclado virtual da tela.
4. Na janela que abrir, ligue o **Developer mode** (**On**), digite o **IP do computador** e confirme.
5. **Reinicie a TV**: desligue e ligue de novo pelo controle. Se o modo não ativar, tire da tomada por 30 segundos.

### Passo 3: conecte o computador à TV

Descubra o IP da TV em **Configurações → Geral → Rede → Status da rede → Configurações de IP**. Depois:

```bash
sdb connect IP_DA_TV
sdb devices          # anote o nome do aparelho (ex.: UN55AU7700) para o passo 5
```

> Se aparecer `failed to connect`, confira se o Modo Desenvolvedor está ligado com o IP certo do computador e se a TV foi reiniciada depois disso.

### Passo 4: crie o certificado da Samsung (uma vez só)

1. Com a TV conectada, abra o **Certificate Manager** do Tizen Studio (**Tools → Certificate Manager**, ou `tizen-studio/tools/certificate-manager`).
2. Clique em **+**, escolha **Samsung** e depois **TV**.
3. Dê um nome ao perfil, por exemplo **Onix**. Esse nome é usado no passo 5.
4. **Author certificate:** escolha *Create a new author certificate*, preencha um nome e uma senha (guarde a senha) e entre com a sua **conta Samsung** quando pedir.
5. **Distributor certificate:** deixe o nível de privilégio em **Public**. O **DUID** da TV aparece sozinho porque ela está conectada; se não aparecer, clique em *Add* e escolha a TV. Entre com a conta Samsung de novo e conclua.

### Passo 5: assine e instale o Onix

1. Baixe o [onix-tizen.zip](https://github.com/ruan2311/onix/releases/latest/download/onix-tizen.zip) e extraia. Vai aparecer a pasta `onix`.
2. No terminal, dentro da pasta onde está a `onix`:

```bash
tizen package -t wgt -s Onix -- onix                 # "Onix" = nome do perfil do passo 4; gera onix/Onix.wgt
tizen install -n Onix.wgt -t NOME_DA_TV -- onix      # NOME_DA_TV = o nome que apareceu no "sdb devices"
```

3. Pronto: o **Onix** aparece em **Apps** na TV. Para deixá-lo sempre à mão, selecione o Onix na lista de Apps, aperte e segure **OK** (ou o botão de opções) e escolha **Adicionar à Página Inicial**.

> Para **atualizar**, baixe o zip novo e repita o passo 5: a instalação substitui a anterior e mantém seus apps organizados, banners e recordes. Para **desinstalar**, vá em **Apps → ⚙️ Configurações**, selecione o Onix e escolha **Excluir**.

Depois de instalado, você pode desligar o Modo Desenvolvedor (repita o passo 2 e deixe em **Off**). Ele só é necessário para instalar e atualizar.

### Compilar a versão Samsung a partir do código

```bash
bash tizen/build.sh            # gera tizen/build/onix/ e o onix-tizen.zip
bash tizen/build.sh Onix       # também gera o Onix.wgt assinado (precisa do Tizen CLI e do perfil "Onix")
```

O `tizen/build.sh` pega o mesmo `index.html` do Android e faz quatro coisas: coloca a ponte do Tizen (`tizen/bridge.js`) antes do script da página, embute as indicações iniciais, libera na Content-Security-Policy só os hosts de indicações e notícias, e troca `inset` e `aspect-ratio` por equivalentes que o navegador das TVs de 2022 entende.

---

## Usar como tela inicial (Onix Launcher)

### Android TV "clássico"
Depois de instalar o **Onix Launcher**, aperte **Home** no controle. Se aparecer a pergunta de qual tela inicial usar, escolha **Onix Launcher → Sempre**.

### Google TV
Na Google TV, o botão **Home** do controle quase sempre volta para o launcher do Google, mesmo com outro launcher instalado. A solução mais comum é **desativar** o launcher do Google pelo adb. Ele não é apagado e pode ser reativado a qualquer momento:

```bash
# Google TV (Chromecast com Google TV, TVs Philips, TCL, Sony etc.)
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

```bash
# Android TV "clássico" (launcher antigo do Google), se for o caso
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Depois disso, aperte **Home**: o Onix assume como tela inicial.

> ⚠️ **Antes de desativar o launcher do Google, confirme que o Onix Launcher abre normalmente.** Sem nenhum launcher ativo, a TV fica sem tela inicial. Para voltar ao normal, veja [Como desfazer tudo](#como-desfazer-tudo).

---

## Ativar o botão "Desligar TV"

Apps comuns não podem desligar a TV. O Onix usa a única via oficial do Android para isso: registrar-se como **administrador do dispositivo** com uma só política, *bloquear a tela*. Na TV, bloquear a tela coloca o aparelho em standby, como o botão de energia.

**Pelo adb (recomendado):**

```bash
adb shell dpm set-active-admin com.aurora.launcher/.PowerAdmin
```

**Sem adb:** aperte **Desligar TV** duas vezes. Se o aparelho tiver a tela de administradores, o Android pede a autorização; aceite e aperte de novo.

O Onix não usa nenhuma outra função de administrador: não apaga dados, não troca senhas e não bloqueia nada.

---

## Como desfazer tudo

```bash
# 1) Reativar o launcher do Google (se você desativou)
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
adb shell pm enable com.google.android.tvlauncher

# 2) Desinstalar o Onix
adb uninstall com.aurora.launcher
```

> Se a permissão de **Desligar TV** estiver ativa, o Android não deixa desinstalar o Onix. Desative antes em **Configurações → Privacidade/Segurança → Apps de administrador do dispositivo → Onix**. O nome do menu varia conforme a marca; procure por "administrador".

---

## Permissões e privacidade

| Permissão | Para quê |
|---|---|
| `INTERNET` | Baixar indicações, notícias e miniaturas (só pelo código nativo) |
| `ACCESS_NETWORK_STATE` | Mostrar "Conectado / Sem internet" e tentar de novo quando a rede volta |
| `RECEIVE_BOOT_COMPLETED` | Manter o agendamento semanal das indicações depois de reiniciar a TV |
| Administrador do dispositivo (opcional) | Apenas `force-lock`, usado pelo botão **Desligar TV** |

- **Sem rastreamento, sem anúncios, sem conta.** O Onix não envia nenhum dado sobre você ou sobre o que você assiste.
- As preferências (apps favoritos, banners, recordes, configurações) ficam **só na TV**.
- A interface roda num WebView **totalmente offline**: o HTML vem empacotado no APK, com uma Content-Security-Policy que bloqueia qualquer acesso à rede. Os downloads são feitos pelo Java, apenas por HTTPS e apenas destes serviços:
  - `apis.justwatch.com` e `images.justwatch.com`: indicações da Netflix Brasil
  - `itunes.apple.com`: filmes mais vistos
  - `adrenaline.com.br` e `eurogamer.pt`: notícias de games
- Tudo o que vem da página para o código nativo é validado (nomes de pacote, IDs de entrada, títulos da lista atual).

---

## Compilar a partir do código

O build não usa Gradle nem Android Studio: só ferramentas que estão nos repositórios do Ubuntu/Debian.

```bash
sudo apt install openjdk-17-jdk-headless libandroid-23-java \
                 dalvik-exchange aapt zipalign apksigner
```

```bash
git clone https://github.com/ruan2311/onix.git
cd onix
bash android/build.sh
```

O APK sai em `launcher-philips.apk`, na raiz do projeto, assinado com a chave de debug do seu computador (`~/.android/debug.keystore`, criada automaticamente se não existir).

**Versão:** o `android:versionName` do `AndroidManifest.xml` manda. O `build.sh` copia esse valor para o `index.html`, e ele aparece no menu e em "Sobre".

**Onix App × Onix Launcher:** os dois saem do mesmo código. O **Onix App** é o mesmo manifesto sem as categorias `HOME` e `DEFAULT` no `intent-filter` da `MainActivity` e com `android:label="Onix App"`.

> **Assinatura:** um APK só instala por cima de outro com a mesma chave. Se você compilar com a sua chave, não vai conseguir atualizar um Onix instalado a partir dos APKs deste repositório (e vice-versa): desinstale antes.

### Testar a interface no navegador
O `index.html` também roda direto no navegador do computador, em "modo navegador": sem a ponte nativa, ele mostra dados de exemplo e simula as ações. Use as setas e o Enter como se fossem o controle.

```bash
python3 -m http.server 8000
# abra http://localhost:8000/index.html
```

---

## Estrutura do projeto

```
.
├── index.html                  # Toda a interface: HTML, CSS e JS num arquivo só
├── tizen/                      # Versão Samsung (Tizen)
│   ├── bridge.js               # Ponte JS com as APIs do Tizen (apps, HDMI, Netflix, indicações, notícias)
│   ├── config.xml              # Manifesto do app (.wgt): privilégios e hosts liberados
│   └── build.sh                # Monta o app e, com um perfil de certificado, o .wgt assinado
├── android/
│   ├── AndroidManifest.xml
│   ├── build.sh                # Build sem Gradle (javac → dx → aapt → zipalign → apksigner)
│   ├── src/com/aurora/launcher/
│   │   ├── MainActivity.java   # WebView, tela inicial, rede, Voltar
│   │   ├── AndroidBridge.java  # Ponte JS ↔ Android (apps, entradas, Netflix, desligar…)
│   │   ├── WeeklyPicks.java    # Indicações da semana (JustWatch + iTunes)
│   │   ├── GameNews.java       # Notícias (RSS da Adrenaline e da Eurogamer.pt)
│   │   ├── PicksJob.java       # Agendamento semanal das indicações
│   │   └── PowerAdmin.java     # Administrador do dispositivo (só force-lock)
│   ├── res/                    # Ícone, banner e política de administrador
│   ├── assets/picks-seed.json  # Indicações embutidas para o primeiro uso sem internet
│   └── tools/                  # Geradores do ícone/banner e da lista embutida
└── docs/screenshots/
```

**Na Samsung**, o `tizen/bridge.js` cria um `AndroidBridge` com os mesmos métodos, feito com as APIs web do Tizen (`tizen.application`, `tizen.systeminfo`, `tizen.tvwindow`), e baixa indicações e notícias com o mesmo código, portado para JavaScript.

**Como funciona:** a `MainActivity` abre um WebView com o `index.html` dos assets. A página conversa com o Android pelo objeto `AndroidBridge` (por exemplo, `getInstalledApps()`, `launchApp()`, `getTvInputs()`, `openPick()`, `powerOff()`), e o Android avisa a página das novidades com `window.Launcher.onAppsChanged()`, `onPicksChanged()`, `onNewsChanged()` e outros.

---

## Personalização

- **Novos banners de entrada:** adicione um item em `INPUT_BANNERS`, no `index.html`, com `id`, `name` e `art` (um SVG de 160×100).
- **Fontes de notícias:** edite `FEEDS` em `GameNews.java`.
- **País e idioma das indicações:** `vars.put("c", "BR")` e `vars.put("l", "pt-BR")` em `WeeklyPicks.java`.
- **Tempo da tela de descanso:** `SAVER_IDLE` no `index.html` (padrão: 10 minutos).

---

## Limitações conhecidas

- **"Continuar assistindo" da Google TV:** não é possível mostrar. Ler o progresso dos outros apps exige permissões que o Android reserva a apps de sistema (`ACCESS_WATCHED_PROGRAMS`, `ACCESS_ALL_EPG_DATA`).
- **Botão Home na Google TV:** só leva ao Onix depois de desativar o launcher do Google (veja [acima](#google-tv)).
- **Entradas HDMI** só aparecem em TVs. Em Chromecast e TV boxes, a aba avisa que o aparelho não tem entradas.
- **Desligar TV** depende do recurso de administrador do dispositivo, que alguns aparelhos não têm.
- **Indicações e notícias** são focadas no Brasil e em português.
- **Samsung (Tizen):** não vira tela inicial, não desliga a TV e mostra a HDMI numa janela do próprio Onix. Veja [O que muda na Samsung](#o-que-muda-na-samsung).

---

## Créditos

Criado por **Ruan Amaral** · Instagram [@ruan_amarl](https://instagram.com/ruan_amarl)

Distribuído sob a [licença MIT](LICENSE).

PlayStation, Xbox, Nintendo Switch, Netflix e os demais nomes e logotipos citados são marcas de seus respectivos donos. Eles aparecem apenas para identificar aparelhos e serviços; o Onix não tem nenhuma ligação com essas empresas. Indicações via [JustWatch](https://www.justwatch.com) e iTunes; notícias da [Adrenaline](https://www.adrenaline.com.br) e da [Eurogamer.pt](https://www.eurogamer.pt).
