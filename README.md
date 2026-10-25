<div align="center">

# 🪐 Onix

**Um launcher para Android TV, Google TV, TVs Samsung (Tizen) e TVs LG (webOS), feito para ser rápido no controle remoto.**

Visual escuro de vidro, apps organizados do seu jeito, indicações da semana da Netflix,
notícias de games, jogos embutidos, troca de entradas HDMI e uma tela de descanso espacial.

![Versão](https://img.shields.io/badge/vers%C3%A3o-13.7.0%20beta-white?style=flat-square)
![Android](https://img.shields.io/badge/Android-5.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white)
![Plataforma](https://img.shields.io/badge/Android%20TV%20%7C%20Google%20TV-black?style=flat-square)
![Samsung](https://img.shields.io/badge/Samsung%20Tizen-2022%2B%20(beta)-1428a0?style=flat-square&logo=samsung&logoColor=white)
![LG](https://img.shields.io/badge/LG%20webOS-2022%2B%20(beta)-a50034?style=flat-square&logo=lg&logoColor=white)
![Licença](https://img.shields.io/badge/licen%C3%A7a-MIT-blue?style=flat-square)

<img src="docs/screenshots/inicio.png" alt="Tela inicial do Onix" width="900">

</div>

---

## Sumário

- [Novidades da 13.6.0](#novidades-da-1360)
- [Recursos](#recursos)
- [Capturas de tela](#capturas-de-tela)
- [Instalação: qual é a sua TV?](#instalação)
- [Android TV e Google TV](#android-tv-e-google-tv)
- [TVs Samsung (Tizen) · experimental](#tvs-samsung-tizen)
- [TVs LG (webOS) · experimental](#tvs-lg-webos)
- [Usar como tela inicial (Onix Launcher)](#usar-como-tela-inicial-onix-launcher)
- [Atualizações automáticas](#atualizações-automáticas)
- [Ativar o botão "Desligar TV"](#ativar-o-botão-desligar-tv)
- [Como desfazer tudo](#como-desfazer-tudo)
- [Permissões e privacidade](#permissões-e-privacidade)
- [Compilar a partir do código](#compilar-a-partir-do-código)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Personalização](#personalização)
- [Limitações conhecidas](#limitações-conhecidas)
- [Créditos](#créditos)

---

## Novidades da 13.6.0

- **Atualizações pelo próprio Onix.** Ele procura versões novas no GitHub, avisa na tela, baixa sozinho e abre a confirmação de instalação. Veja [Atualizações automáticas](#atualizações-automáticas).
- **Escolha suas indicações.** Em **Configurações → Indicações**, desligue *Pelo que você assiste* e marque você mesmo Netflix, Disney+, HBO Max ou Globoplay.
- **Seu aparelho no topo.** O chip mostra o que está rodando o Onix: *Google TV · Philips*, *Android TV*, *Fire TV*, *Celular Android*…
- **"Conectado" de verdade.** O Onix testa a internet a cada 30 segundos e mostra **Sem internet** quando o Wi-Fi está ligado mas a internet caiu.

Na 13.7.0: versão para **TVs LG (webOS)**, em beta e ainda sem teste numa LG de verdade. Veja [TVs LG (webOS)](#tvs-lg-webos).

Na 13.6.4: etiquetas **Novo**, **Correção** e **Bug** nas Novidades, e um segredo no cartão **Sobre**.

Na 13.6.3: o Onix não some mais com os apps, as entradas e as indicações ao voltar de outro app; aviso "Parece que você está sem conexão" no lugar dos destaques de fachada; **Configurações da TV** ao lado do **Atualizar**; novos estilos de animação; e a aba **Apps** separada em **Recentes** e **Não usados**.

Na 13.6.2: ícones oficiais do Disney+, da HBO Max e do Globoplay nos selos das indicações, e TV, PlayStation 5, Xbox e Nintendo Switch com o mesmo visual das HDMI em **Entradas**.

Na 13.6.1: a entrada **TV** (antena e cabo) em **Entradas** e os pontinhos do destaque centralizados embaixo da capa.

Na 13.5.0: indicações do Disney+, HBO Max e Globoplay pelo uso, a tela **Novidades** e a versão para TVs Samsung.

O histórico completo está no [CHANGELOG.md](CHANGELOG.md) e na [página de Releases](https://github.com/ruan2311/onix/releases).

---

## Recursos

### Início
- **Destaque rotativo** com as indicações da semana. O botão **Assistir na Netflix** abre o título direto na página dele no app da Netflix.
- **Seus apps**: uma fileira com os seus apps favoritos. Dá para adicionar, remover e reordenar na tela **Organizar seus apps**.
- **Indicações da semana**: títulos populares no Brasil (via JustWatch) dos serviços que você usa, complementados pelos filmes mais vistos da iTunes Store. A lista é renovada toda segunda-feira.
  - A **Netflix** é o padrão e está sempre presente.
  - **Disney+**, **HBO Max** e **Globoplay** entram quando você abre o app **mais de 3 vezes pelo Onix** (pela grade ou pelas indicações) e o app está instalado. As 8 vagas são divididas entre os serviços, e os mais abertos vêm primeiro.
  - Uma **semana sem abrir** o app zera a contagem e ele sai das indicações.
  - Prefere escolher? Em **Configurações → Indicações**, desligue **Pelo que você assiste** e marque os serviços que quiser (pelo menos um).
  - Cada indicação tem o selo do serviço, e **Assistir no…** abre o título direto no app.
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

### Entradas (TV e HDMI)
- **TV** (antena e cabo) como primeira entrada: abre os canais abertos no app de TV do aparelho, mesmo se ele estava numa HDMI. Só aparece em TVs com sintonizador.
- Troca de entrada HDMI direto pelo launcher.
- **Banners editáveis**: a canetinha embaixo de cada porta alterna entre PlayStation 5, Xbox, Nintendo Switch, Videogame, Computador ou HDMI genérico.
- Atalho para os **ajustes de imagem** da TV.

### Tela de descanso
- Depois de 10 minutos sem usar o controle, planetas e asteroides passam devagar sobre um céu preto, com relógio e estrelas cadentes.
- O relógio muda de posição a cada minuto, para não marcar telas OLED.

### Outros
- **Desligar TV** no menu: pede confirmação com um segundo OK e coloca a TV em standby.
- **Aparelho e conexão no topo**: o chip da esquerda mostra o aparelho (*Google TV · Philips*, *Android TV*, *Celular Android*…) e o da direita fica vermelho com **Sem internet** quando a internet cai, mesmo com o Wi-Fi ligado (teste a cada 30 s).
- **Atualizações**: o Onix avisa quando sai uma versão nova e se atualiza pelo próprio app. Veja [abaixo](#atualizações-automáticas).
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

## Instalação

### Qual é a sua TV?

| Sua TV | Situação |
|---|---|
| **Android TV ou Google TV** (Philips, Sony, TCL, Xiaomi, AOC, Chromecast, TV boxes…) | ✅ **Testado.** [Passo a passo abaixo](#android-tv-e-google-tv), só com o controle remoto, uns 5 minutos |
| **Samsung** (2022 ou mais nova) | 🧪 **Experimental.** Ainda sem um jeito de instalar testado. [Veja aqui](#tvs-samsung-tizen) |
| **LG** (2022 ou mais nova) | 🧪 **Experimental.** Ainda sem um jeito de instalar testado. [Veja aqui](#tvs-lg-webos) |

> **Não sabe qual é a sua?** Se a tela inicial da TV tem a **Play Store** (ou diz "Google TV"), ela é Android TV/Google TV. TVs Samsung e LG têm sistema próprio e **não** têm a Play Store.

---

## Android TV e Google TV

### Qual baixar: Onix App ou Onix Launcher?

O conteúdo é o mesmo; muda só se ele pode virar a tela inicial.

| | **Onix App** | **Onix Launcher** |
|---|---|---|
| Arquivo | [onix-app.apk](https://github.com/ruan2311/onix/releases/latest/download/onix-app.apk) | [onix-launcher.apk](https://github.com/ruan2311/onix/releases/latest/download/onix-launcher.apk) |
| Aparece na lista de apps | ✅ | ✅ |
| Pode virar a tela inicial | ❌ | ✅ |
| Para quem | Quer experimentar sem mexer em nada | Quer o Onix como tela principal |

> **Na dúvida, comece pelo Onix App.** Gostou? Instale o Onix Launcher por cima depois: nada se perde (apps organizados, banners e recordes ficam).

### Instalar pela própria TV, com o app Downloader

✅ *Testado numa Philips Google TV (outubro de 2026), do começo ao fim, só com o controle.*

1. Na TV, abra a **Play Store**, procure **Downloader** (o ícone laranja, de *AFTVnews*) e instale.
2. Abra o **Downloader**. Se ele pedir permissão para acessar arquivos, aceite.
3. Clique na barra laranja do topo, onde está escrito **"Click here to enter a URL…"**.
4. No quadro que abre, digite exatamente o link abaixo e clique em **Load**:
   - para o Onix App: **`github.com/ruan2311/onix/releases/latest/download/onix-app.apk`**
   - para o Onix Launcher: **`github.com/ruan2311/onix/releases/latest/download/onix-launcher.apk`**
5. O download começa sozinho. Se a TV avisar que o Downloader não pode instalar apps, clique em **Configurações**, ligue a chave do **Downloader** e aperte **Voltar** no controle.
6. Na tela do Android, clique em **Instalar** (se o Onix já estiver na TV, o botão se chama **Atualizar**).
7. Apareceu **"App instalado"**? Clique em **Abrir**. Pronto! O Onix também fica na sua lista de apps.

> Se o Downloader perguntar depois se quer apagar o arquivo baixado, pode apagar: ele não é mais necessário.

<details>
<summary><b>Pelo computador, com adb</b> (para quem já conhece)</summary>

✅ *Testado numa Philips Google TV.*

1. Na TV, ative as **Opções do desenvolvedor**: em **Configurações → Sistema → Sobre**, clique 7 vezes em **Build do sistema operacional**.
2. Em **Opções do desenvolvedor**, ative **Depuração USB** e/ou **Depuração sem fio**.
3. No computador, com o [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools) instalado:

```bash
adb connect IP_DA_TV:5555        # ou o IP:porta mostrado em "Depuração sem fio"
adb install -r onix-app.apk      # ou onix-launcher.apk
```

Na primeira conexão, a TV pede para autorizar o computador: aceite.

</details>

Depois de instalado, o próprio Onix avisa e instala as versões novas: veja [Atualizações automáticas](#atualizações-automáticas).

---

## TVs Samsung (Tizen)

🧪 **Experimental: ainda não existe um jeito de instalar testado.** A versão Samsung (`onix-tizen.wgt` e `onix-tizen.zip` na [página de Releases](https://github.com/ruan2311/onix/releases/latest)) nunca foi instalada numa Samsung de verdade, e a Samsung só aceita apps de fora da loja assinados para cada TV, com uma conta Samsung.

Quer se arriscar? Os caminhos que *deveriam* funcionar (pelo celular, com o app Apps2Samsung, ou pelo computador, com o Tizen Studio) estão nas [anotações não testadas](docs/tv-experimental.md#tvs-samsung-tizen). Se conseguir (ou não), [conte numa issue](https://github.com/ruan2311/onix/issues) com o modelo da TV.

---

## TVs LG (webOS)

🧪 **Experimental: ainda não existe um jeito de instalar testado, e o app pode não funcionar.** A versão LG (`onix-webos.ipk` na [página de Releases](https://github.com/ruan2311/onix/releases/latest)) nunca foi instalada numa LG de verdade, só testada com as APIs da LG simuladas no computador. Para instalar, a LG exige um computador e o Modo Desenvolvedor, que apaga o app se não for renovado a cada 50 horas.

Quer se arriscar? O caminho que *deveria* funcionar (pelo computador, com o webOS Dev Manager) está nas [anotações não testadas](docs/tv-experimental.md#tvs-lg-webos). Se conseguir (ou não), [conte numa issue](https://github.com/ruan2311/onix/issues) com o modelo da TV.

---

## Usar como tela inicial (Onix Launcher)

### Android TV "clássico"
Depois de instalar o **Onix Launcher**, aperte **Home** no controle. Se aparecer a pergunta de qual tela inicial usar, escolha **Onix Launcher → Sempre**.

### Google TV
✅ *Testado numa Philips Google TV:* depois de instalar o **Onix Launcher**, aperte **Home** no controle e, quando a TV perguntar qual tela inicial usar, escolha **Onix Launcher → Sempre**.

Em algumas Google TV (como o Chromecast), a TV não pergunta e o **Home** sempre volta para a tela do Google. Nesse caso, dá para abrir o Onix pela lista de apps normalmente. Fazer ele virar a tela inicial nesses aparelhos exige desativar a tela do Google, um caminho que ainda não foi testado: está nas [anotações não testadas](docs/tv-experimental.md#google-tv-desativar-a-tela-inicial-do-google).

---

## Atualizações automáticas

A partir da **13.6.0**, o Onix se atualiza sozinho pela [página de Releases](https://github.com/ruan2311/onix/releases):

1. Com o Onix aberto (e a cada 6 horas em segundo plano), ele confere se saiu uma versão nova.
2. Saiu? Aparece um aviso na tela e um **ponto verde** em **Configurações**. Em **Configurações → Atualizações**, a linha mostra a versão nova.
3. Com **Atualizar automaticamente** ligado (padrão), o Onix já baixa a APK e, quando você volta para ele, abre a tela de instalação do Android. É só apertar **Instalar**. Com a opção desligada, ele só avisa, e você aperta **Instalar a versão…** quando quiser.

Na **primeira atualização**, o Android pede para **permitir que o Onix instale apps**. O Onix abre essa tela sozinho: ligue a opção do Onix, volte e aperte **Instalar a versão…** de novo.

> **Por que precisa confirmar?** O Android não deixa um app comum se instalar sem a pessoa apertar **Instalar**. O Onix baixa da página de Releases, confere o **SHA-256** publicado pelo GitHub e se o arquivo é mesmo uma versão nova do Onix. O Android ainda confere se a APK foi assinada com a mesma chave. A atualização mantém apps organizados, banners, recordes e configurações. Se recusar, ele pergunta de novo no dia seguinte, ou quando você mandar.

**TVs Samsung e LG (experimentais):** o Onix avisa quando sai uma versão nova, mas não consegue se instalar sozinho; a atualização é feita do mesmo jeito que a instalação (veja as [anotações não testadas](docs/tv-experimental.md)).

---

## Ativar o botão "Desligar TV"

Apps comuns não podem desligar a TV. O Onix usa a única via oficial do Android para isso: registrar-se como **administrador do dispositivo** com uma só política, *bloquear a tela*. Na TV, bloquear a tela coloca o aparelho em standby, como o botão de energia.

**Jeito mais fácil:** aperte **Desligar TV** duas vezes. Se o aparelho tiver a tela de administradores, o Android pede a autorização; aceite e aperte de novo.

**Se a sua TV não mostrar o pedido** (algumas não têm essa tela), dá para liberar pelo computador, com adb:

```bash
adb shell dpm set-active-admin com.aurora.launcher/.PowerAdmin
```

O Onix não usa nenhuma outra função de administrador: não apaga dados, não troca senhas e não bloqueia nada.

---

## Como desfazer tudo

**Tirar o Onix (Android TV e Google TV):** em **Configurações → Apps →** **Onix** (ou **Onix Launcher**) **→ Desinstalar**.

> Se a permissão de **Desligar TV** estiver ativa, o Android não deixa desinstalar o Onix. Desative antes em **Configurações → Privacidade/Segurança → Apps de administrador do dispositivo → Onix**. O nome do menu varia conforme a marca; procure por "administrador".

**Pelo computador:** `adb uninstall com.aurora.launcher` desinstala o Onix.

Desativou a tela inicial do Google seguindo as [anotações não testadas](docs/tv-experimental.md#desfazer-trazer-de-volta-a-tela-inicial-do-google)? Lá também está como trazer de volta.

---

## Permissões e privacidade

| Permissão | Para quê |
|---|---|
| `INTERNET` | Baixar indicações, notícias e miniaturas (só pelo código nativo) |
| `ACCESS_NETWORK_STATE` | Mostrar "Conectado / Sem internet" e tentar de novo quando a rede volta |
| `RECEIVE_BOOT_COMPLETED` | Manter o agendamento semanal das indicações depois de reiniciar a TV |
| `REQUEST_INSTALL_PACKAGES` | Instalar as atualizações do Onix (o Android sempre pede confirmação na tela) |
| Administrador do dispositivo (opcional) | Apenas `force-lock`, usado pelo botão **Desligar TV** |

- **Sem rastreamento, sem anúncios, sem conta.** O Onix não envia nenhum dado sobre você ou sobre o que você assiste.
- A contagem de aberturas dos apps de streaming (para as indicações) também fica **só na TV**: o Onix guarda apenas quantas vezes cada serviço foi aberto e quando foi a última vez.
- As preferências (apps favoritos, banners, recordes, configurações) ficam **só na TV**.
- A interface roda num WebView **totalmente offline**: o HTML vem empacotado no APK, com uma Content-Security-Policy que bloqueia qualquer acesso à rede. Os downloads são feitos pelo Java, apenas por HTTPS e apenas destes serviços:
  - `apis.justwatch.com` e `images.justwatch.com`: indicações da Netflix, do Disney+, da HBO Max e do Globoplay
  - `itunes.apple.com`: filmes mais vistos
  - `adrenaline.com.br` e `eurogamer.pt`: notícias de games
  - `api.github.com`, `github.com` e `release-assets.githubusercontent.com`: procurar e baixar versões novas do Onix
  - `connectivitycheck.gstatic.com`: teste de internet do chip **Conectado** (uma resposta vazia, a mesma que o Android usa)
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
bash android/build.sh            # launcher-philips.apk
bash android/build.sh launcher   # onix-launcher.apk (o da página de Releases)
bash android/build.sh app        # onix-app.apk (o da página de Releases)
```

O APK sai em `launcher-philips.apk` (ou `onix-launcher.apk` / `onix-app.apk`), na raiz do projeto, assinado com a chave de debug do seu computador (`~/.android/debug.keystore`, criada automaticamente se não existir).

**Versão:** o `android:versionName` do `AndroidManifest.xml` manda. O `build.sh` copia esse valor para o `index.html`, e ele aparece no menu e em "Sobre".

**Onix App × Onix Launcher:** os dois saem do mesmo código. O **Onix App** é o mesmo manifesto sem as categorias `HOME` e `DEFAULT` no `intent-filter` da `MainActivity` e com `android:label="Onix App"`; o `build.sh app` gera essa versão sem mexer no manifesto.

**Samsung e LG:** `bash tizen/build.sh` e `bash webos/build.sh` (detalhes nas [anotações](docs/tv-experimental.md#compilar-a-versão-samsung-a-partir-do-código)).

**Novidades:** a lista `CHANGELOG` no `index.html` alimenta a tela **Configurações → Novidades**. Ao lançar uma versão, atualize ela junto com o `CHANGELOG.md`.

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
├── CHANGELOG.md                # O que mudou em cada versão
├── tv/                         # Comum às TVs sem Android (Samsung e LG)
│   ├── bridge.js               # Ponte JS: apps, indicações, notícias, internet, atualizações
│   └── page.py                 # Monta o index.html da TV (ponte, CSP, CSS das TVs de 2022)
├── tizen/                      # Versão Samsung (Tizen)
│   ├── platform.js             # Adaptador com as APIs do Tizen (apps, HDMI, Netflix)
│   ├── config.xml              # Manifesto do app (.wgt): privilégios e hosts liberados
│   └── build.sh                # Monta o app e, com um perfil de certificado, o .wgt assinado
├── webos/                      # Versão LG (webOS)
│   ├── platform.js             # Adaptador com os serviços Luna da LG (apps, entradas, Netflix)
│   ├── appinfo.json            # Manifesto do app
│   ├── ipk.py                  # Empacota o .ipk (sem o ares-package)
│   ├── Resize.java             # Ícones de 80 e 130 px a partir do ícone do Android
│   └── build.sh                # Monta o app e o onix-webos.ipk
├── android/
│   ├── AndroidManifest.xml
│   ├── build.sh                # Build sem Gradle (javac → dx → aapt → zipalign → apksigner)
│   ├── src/com/aurora/launcher/
│   │   ├── MainActivity.java   # WebView, tela inicial, rede, Voltar
│   │   ├── AndroidBridge.java  # Ponte JS ↔ Android (apps, entradas, Netflix, desligar…)
│   │   ├── WeeklyPicks.java    # Indicações da semana (JustWatch + iTunes), por serviço
│   │   ├── AppUsage.java       # Aberturas de cada serviço (quem entra nas indicações)
│   │   ├── Updater.java        # Atualizações pelo GitHub (procura, baixa, confere e instala)
│   │   ├── UpdateReceiver.java # Resposta do instalador do sistema
│   │   ├── GameNews.java       # Notícias (RSS da Adrenaline e da Eurogamer.pt)
│   │   ├── PicksJob.java       # Agendamento semanal das indicações
│   │   └── PowerAdmin.java     # Administrador do dispositivo (só force-lock)
│   ├── res/                    # Ícone, banner e política de administrador
│   ├── assets/picks-seed.json  # Indicações embutidas para o primeiro uso sem internet
│   └── tools/                  # Geradores do ícone/banner e da lista embutida
└── docs/screenshots/
```

**Na Samsung e na LG**, o `tv/bridge.js` cria um `AndroidBridge` com os mesmos métodos e baixa indicações e notícias com o mesmo código, portado para JavaScript. O que muda de uma TV para a outra fica num adaptador pequeno: `tizen/platform.js` (APIs web do Tizen: `tizen.application`, `tizen.systeminfo`, `tizen.tvwindow`) e `webos/platform.js` (serviços Luna da LG pelo `PalmServiceBridge`: `applicationManager` e o serviço de entradas).

**Como funciona:** a `MainActivity` abre um WebView com o `index.html` dos assets. A página conversa com o Android pelo objeto `AndroidBridge` (por exemplo, `getInstalledApps()`, `launchApp()`, `getTvInputs()`, `openPick()`, `powerOff()`), e o Android avisa a página das novidades com `window.Launcher.onAppsChanged()`, `onPicksChanged()`, `onNewsChanged()` e outros.

---

## Personalização

- **Novos banners de entrada:** adicione um item em `INPUT_BANNERS`, no `index.html`, com `id`, `name` e `art` (um SVG de 160×100).
- **Fontes de notícias:** edite `FEEDS` em `GameNews.java`.
- **Serviços das indicações:** a tabela `PROVIDERS` em `WeeklyPicks.java` (e em `tv/bridge.js`), com o código do serviço no JustWatch, o formato do link do título e os pacotes do app. A regra de uso (4 aberturas, 7 dias) fica em `AppUsage.java`.
- **País e idioma das indicações:** `vars.put("c", "BR")` e `vars.put("l", "pt-BR")` em `WeeklyPicks.java`.
- **Tempo da tela de descanso:** `SAVER_IDLE` no `index.html` (padrão: 10 minutos).

---

## Limitações conhecidas

- **"Continuar assistindo" da Google TV:** não é possível mostrar. Ler o progresso dos outros apps exige permissões que o Android reserva a apps de sistema (`ACCESS_WATCHED_PROGRAMS`, `ACCESS_ALL_EPG_DATA`).
- **Botão Home na Google TV:** só leva ao Onix depois de desativar o launcher do Google (veja [acima](#google-tv)).
- **Entradas HDMI** só aparecem em TVs. Em Chromecast e TV boxes, a aba avisa que o aparelho não tem entradas.
- **Desligar TV** depende do recurso de administrador do dispositivo, que alguns aparelhos não têm.
- **Indicações e notícias** são focadas no Brasil e em português.
- **Samsung (Tizen):** não vira tela inicial, não desliga a TV e mostra a HDMI numa janela do próprio Onix. Veja [O que muda na Samsung](docs/tv-experimental.md#o-que-muda-na-samsung).
- **LG (webOS):** não vira tela inicial, não desliga a TV e, pelo Modo Desenvolvedor, precisa renovar a sessão a cada 50 horas. Veja [O que muda na LG](docs/tv-experimental.md#o-que-muda-na-lg).

---

## Créditos

Criado por **Ruan Amaral** · Instagram [@ruan_amarl](https://instagram.com/ruan_amarl)

Distribuído sob a [licença MIT](LICENSE).

PlayStation, Xbox, Nintendo Switch, Netflix e os demais nomes e logotipos citados são marcas de seus respectivos donos. Eles aparecem apenas para identificar aparelhos e serviços; o Onix não tem nenhuma ligação com essas empresas. Indicações via [JustWatch](https://www.justwatch.com) e iTunes; notícias da [Adrenaline](https://www.adrenaline.com.br) e da [Eurogamer.pt](https://www.eurogamer.pt).
