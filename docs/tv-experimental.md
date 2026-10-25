# Anotações não testadas: Samsung, LG e Google TV

> ⚠️ **Nada nesta página foi testado de ponta a ponta.** São os caminhos que, pela documentação de cada fabricante e de cada ferramenta, *deveriam* funcionar, mas ninguém confirmou ainda que dá para instalar o Onix seguindo estes passos. **Pode não funcionar.**
>
> O passo a passo **testado** está no [README](../README.md#instalação). Se você seguir algum destes e der certo (ou errado), [abra uma issue](https://github.com/ruan2311/onix/issues) contando o modelo da TV e onde travou: o que for confirmado vai para o README.

- [TVs Samsung (Tizen)](#tvs-samsung-tizen)
- [TVs LG (webOS)](#tvs-lg-webos)
- [Google TV: desativar a tela inicial do Google](#google-tv-desativar-a-tela-inicial-do-google)

---

## TVs Samsung (Tizen)

As TVs Samsung usam o **Tizen**, não o Android, então os arquivos `.apk` não servem nelas. A versão Samsung é a mesma interface, empacotada como app do Tizen (`.wgt`).

> 🧪 **Beta.** Esta versão ainda teve pouco teste em TVs de verdade. Se algo não funcionar na sua, [abra uma issue](https://github.com/ruan2311/onix/issues) dizendo o modelo e o ano da TV.

**TVs compatíveis:** Samsung de **2022 em diante**. Em modelos mais antigos, o navegador interno da TV não tem os recursos que a interface usa.

> **Por que é mais trabalhoso que no Android?** A Samsung só deixa instalar um app de fora da loja se ele for **assinado para a sua TV** (com o número de série dela) usando uma **conta Samsung**. O app de celular abaixo faz essa assinatura por você.

### Jeito 1: pelo celular, com o app Apps2Samsung (mais fácil)

Você vai precisar de: um **celular Android**, a sua **conta Samsung** e o celular e a TV **no mesmo Wi-Fi**.

> O Apps2Samsung é um app gratuito de outro desenvolvedor, não do Onix. Ele já instala outros apps (como o Jellyfin) em TVs Samsung, mas ainda não foi testado com o Onix.

**1. Descubra o IP do celular.** No celular, vá em **Configurações → Sobre o telefone → Status** (ou **Configurações → Wi-Fi →** toque na rede conectada) e anote o **Endereço IP**, algo como `192.168.0.25`.

**2. Ligue o Modo Desenvolvedor da TV.**
1. Na TV, abra **Apps** (Aplicativos) na tela inicial.
2. Com a tela de Apps aberta, digite **1 2 3 4 5** no controle. Se o controle não tiver números, use o botão **123** para abrir o teclado numérico da tela.
3. Na janela que abrir, ligue o **Developer mode** (**On**), digite o **IP do celular** do passo 1 e confirme.
4. **Desligue e ligue a TV** pelo controle. Se não funcionar, tire a TV da tomada por 30 segundos.

**3. Instale o Apps2Samsung no celular.** Baixe o arquivo que termina em **`-android.apk`** na [página de downloads do Apps2Samsung](https://github.com/Apps2Samsung/Apps2Samsung/releases/latest) e abra. Se o celular pedir, permita instalar apps do navegador.

**4. Baixe o Onix no celular:** [onix-tizen.wgt](https://github.com/ruan2311/onix/releases/latest/download/onix-tizen.wgt).

**5. Instale.** Abra o Apps2Samsung, escolha a sua TV na lista, escolha **instalar um arquivo .wgt próprio** (custom .wgt) e selecione o **onix-tizen.wgt** (fica em **Downloads**). Na primeira vez, ele pede para entrar na **conta Samsung**: é assim que ele assina o app para a sua TV.

**6. Pronto!** O **Onix** aparece em **Apps** na TV. Para deixá-lo à mão, selecione o Onix, segure **OK** (ou aperte o botão de opções) e escolha **Adicionar à Página Inicial**.

Depois de instalado, você pode desligar o Modo Desenvolvedor (repita o passo 2 e deixe em **Off**). Ele só é necessário para instalar e atualizar.

> Para **atualizar**, baixe o `onix-tizen.wgt` novo e repita o passo 5: seus apps organizados, banners e recordes são mantidos. Para **desinstalar**, vá em **Apps → ⚙️ Configurações**, selecione o Onix e escolha **Excluir**.

<details>
<summary><b>Jeito 2: pelo computador, com o Tizen Studio</b> (oficial da Samsung)</summary>

#### Passo 1: instale o Tizen Studio no computador

1. Baixe e instale o [Tizen Studio](https://developer.tizen.org/development/tizen-studio/download) (Windows, macOS ou Linux). A versão "CLI", sem a IDE, já basta.
2. Abra o **Package Manager** do Tizen Studio e, na aba **Extension SDK**, instale:
   - **Samsung Certificate Extension**
   - **TV Extensions** (a versão mais nova)
3. Os comandos `tizen` e `sdb` ficam em `tizen-studio/tools/ide/bin` e `tizen-studio/tools`. No Windows, são `C:\tizen-studio\tools\ide\bin\tizen.bat` e `C:\tizen-studio\tools\sdb.exe`. Coloque essas pastas no `PATH` ou use o caminho completo.

#### Passo 2: ative o Modo Desenvolvedor na TV

Igual ao passo 2 do jeito pelo celular, mas digitando o **IP do computador** (no Windows, `ipconfig`; no macOS e no Linux, `ip addr`). A TV e o computador precisam estar na mesma rede.

#### Passo 3: conecte o computador à TV

Descubra o IP da TV em **Configurações → Geral → Rede → Status da rede → Configurações de IP**. Depois:

```bash
sdb connect IP_DA_TV
sdb devices          # anote o nome do aparelho (ex.: UN55AU7700) para o passo 5
```

> Se aparecer `failed to connect`, confira se o Modo Desenvolvedor está ligado com o IP certo do computador e se a TV foi reiniciada depois disso.

#### Passo 4: crie o certificado da Samsung (uma vez só)

1. Com a TV conectada, abra o **Certificate Manager** do Tizen Studio (**Tools → Certificate Manager**, ou `tizen-studio/tools/certificate-manager`).
2. Clique em **+**, escolha **Samsung** e depois **TV**.
3. Dê um nome ao perfil, por exemplo **Onix**. Esse nome é usado no passo 5.
4. **Author certificate:** escolha *Create a new author certificate*, preencha um nome e uma senha (guarde a senha) e entre com a sua **conta Samsung** quando pedir.
5. **Distributor certificate:** deixe o nível de privilégio em **Public**. O **DUID** da TV aparece sozinho porque ela está conectada; se não aparecer, clique em *Add* e escolha a TV. Entre com a conta Samsung de novo e conclua.

#### Passo 5: assine e instale o Onix

1. Baixe o [onix-tizen.zip](https://github.com/ruan2311/onix/releases/latest/download/onix-tizen.zip) e extraia. Vai aparecer a pasta `onix`.
2. No terminal, dentro da pasta onde está a `onix`:

```bash
tizen package -t wgt -s Onix -- onix                 # "Onix" = nome do perfil do passo 4; gera onix/Onix.wgt
tizen install -n Onix.wgt -t NOME_DA_TV -- onix      # NOME_DA_TV = o nome que apareceu no "sdb devices"
```

3. Pronto: o **Onix** aparece em **Apps** na TV. Para atualizar, baixe o zip novo e repita este passo.

</details>

### O que muda na Samsung

| Recurso | Na Samsung |
|---|---|
| Apps, Organizar seus apps, Jogos, Notícias, Tela de descanso | ✅ Igual ao Android |
| Indicações da semana + **Assistir na Netflix** | ✅ Abre a Netflix no título. Se a sua TV não aceitar o link direto, abre a Netflix e você procura o título |
| Atualizações | ⚠️ O Onix avisa quando sai uma versão nova, mas você instala do mesmo jeito que a primeira vez |
| Indicações do Disney+, HBO Max e Globoplay | ✅ Mesma regra do Android, inclusive a escolha manual em Configurações (mais de 3 aberturas, sai depois de 7 dias). O app abre, mas nem sempre direto no título: o Tizen não documenta links diretos para esses apps |
| Indicações que não são da Netflix | ⚠️ A Samsung não tem a busca "onde assistir" do Google |
| Entradas TV e HDMI | ⚠️ A TV (antena) e a HDMI aparecem **em tela cheia dentro do Onix**. **Voltar** ou **OK** retorna ao Onix. Recursos como Modo Jogo automático podem não ligar nessa janela: para jogar, prefira o botão de fontes do controle |
| Virar a tela inicial | ❌ O Tizen não aceita launchers de terceiros. O Onix abre como um app; dá para fixá-lo na barra da tela inicial |
| Desligar TV, ajustes de imagem, configurações da TV | ❌ O Tizen não deixa apps comuns fazerem isso, então esses botões não aparecem |

### Compilar a versão Samsung a partir do código

```bash
bash tizen/build.sh            # gera tizen/build/onix/, o onix-tizen.zip e o onix-tizen.wgt (sem assinatura)
bash tizen/build.sh Onix       # também gera o Onix.wgt assinado (precisa do Tizen CLI e do perfil "Onix")
```

O `tizen/build.sh` pega o mesmo `index.html` do Android e, pelo `tv/page.py`, faz quatro coisas: coloca a ponte (`tv/bridge.js` com o adaptador `tizen/platform.js`) antes do script da página, embute as indicações iniciais, libera na Content-Security-Policy só os hosts de indicações e notícias, e troca `inset` e `aspect-ratio` por equivalentes que o navegador das TVs de 2022 entende.

---

## TVs LG (webOS)

As TVs LG usam o **webOS**, então os arquivos `.apk` também não servem nelas. A versão LG é a mesma interface, empacotada como app do webOS (`onix-webos.ipk`).

> ⚠️ **Beta: pode não funcionar.** Esta versão ainda não foi testada numa LG de verdade, só com as APIs da LG simuladas no computador. A lista de apps, as entradas, o link direto da Netflix e as indicações podem falhar dependendo do modelo. Se algo não funcionar na sua TV, [abra uma issue](https://github.com/ruan2311/onix/issues) dizendo o modelo e o ano.

**TVs compatíveis:** LG de **2022 em diante** (webOS 22 ou mais novo).

> **Dá para instalar só com o celular?** Infelizmente, ainda não: a LG só deixa instalar apps de fora da loja usando um programa no **computador** (Windows, macOS ou Linux). Você precisa dele **só para instalar**; depois o Onix funciona sozinho na TV.

### Instalar pelo computador (sem terminal)

Você vai precisar de: um **computador** e a TV **no mesmo Wi-Fi**, e uns 15 minutos.

**1. Crie uma conta de desenvolvedor da LG (grátis).** Entre em [webostv.developer.lge.com](https://webostv.developer.lge.com/), clique em **Sign In → Create Account** e siga os passos. É diferente da conta LG que você usa na TV.

**2. Ligue o Modo Desenvolvedor na TV.**
1. Na TV, abra a **LG Content Store** (ou **Apps**), procure **Developer Mode** e instale.
2. Abra o **Developer Mode** e entre com a conta do passo 1.
3. Ligue **Dev Mode Status**. A TV reinicia sozinha.
4. Abra o **Developer Mode** de novo e ligue **Key Server**. Deixe essa tela aberta: ela mostra o **IP** e a **Passphrase** da TV.

**3. Instale o webOS Dev Manager no computador.** Baixe na [página de downloads dele](https://github.com/webosbrew/dev-manager-desktop/releases/latest) o arquivo do seu sistema (no Windows, o que termina em **`.msi`**) e instale.

**4. Conecte o computador à TV.** Abra o Dev Manager, clique em **Add Device** (adicionar aparelho), escolha a sua TV ou digite o **IP** que aparece no Developer Mode, e cole a **Passphrase** quando ele pedir.

**5. Instale o Onix.** Baixe o [onix-webos.ipk](https://github.com/ruan2311/onix/releases/latest/download/onix-webos.ipk). No Dev Manager, vá em **Apps**, clique em **Install** (ícone de instalar) e escolha o arquivo baixado.

**6. Pronto!** O **Onix** aparece na barra de apps da TV.

> ⏳ **Importante: o Modo Desenvolvedor vence em 50 horas.** Quando vence, a LG **apaga** os apps instalados por ele, inclusive o Onix. Para não perder: de vez em quando, abra o app **Developer Mode** na TV e aperte **Extend** (o contador volta para 50 horas). O Dev Manager também mostra quanto tempo falta.

> Para **atualizar**, baixe o `.ipk` novo e repita o passo 5: seus apps organizados, banners e recordes são mantidos. Para **desinstalar**, segure **OK** no Onix na barra de apps e escolha **Excluir**.

<details>
<summary><b>Pelo terminal, com o webOS CLI</b> (para quem já conhece)</summary>

Com o [webOS CLI](https://webostv.developer.lge.com/develop/tools/cli-installation) (`npm install -g @webos-tools/cli`), depois dos passos 1 e 2:

```bash
ares-setup-device                          # "add": nome tv, IP da TV, porta 9922, usuário prisoner
ares-novacom --device tv --getkey          # pede a Passphrase do app Developer Mode
ares-install --device tv onix-webos.ipk
ares-launch --device tv com.ruan2311.onix
```

</details>

<details>
<summary><b>TV com root (Homebrew Channel)</b></summary>

Se a sua TV já tem o [Homebrew Channel](https://github.com/webosbrew/webos-homebrew-channel), instale o `onix-webos.ipk` pelo webOS Dev Manager do mesmo jeito. Assim o Onix não expira.

</details>

### O que muda na LG

| Recurso | Na LG |
|---|---|
| Apps, Organizar seus apps, Jogos, Notícias, Tela de descanso | ✅ Igual ao Android. Os apps vêm da barra da LG; se a sua TV não deixar o Onix ler essa lista, ele mostra os apps mais comuns (Netflix, YouTube, Prime Video, Disney+, Spotify, Apple TV, Navegador e Configurações) |
| Indicações da semana + **Assistir na Netflix** | ✅ Abre a Netflix no título. Se a TV não aceitar o link direto, abre a Netflix e você procura o título |
| Indicações do Disney+, HBO Max e Globoplay | ⚠️ Mesma regra do Android. O app abre, mas nem sempre direto no título |
| Entradas TV e HDMI | ✅ Troca a TV inteira para a entrada, como o botão de entradas do controle. Se a TV não informar as portas, aparecem TV e HDMI 1 a 4 |
| Configurações da TV e ajustes de imagem | ✅ Abrem as Configurações da LG |
| Atualizações | ⚠️ O Onix avisa quando sai uma versão nova; instale o `.ipk` novo do mesmo jeito que o primeiro |
| Virar a tela inicial | ❌ O webOS não aceita launchers de terceiros. O Onix aparece na barra de apps da LG, e dá para trazê-lo para o começo dela |
| Desligar TV | ❌ O webOS não deixa apps comuns desligarem a TV |

### Compilar a versão LG a partir do código

```bash
bash webos/build.sh            # gera webos/build/onix/ e o onix-webos.ipk
```

O `webos/build.sh` monta o `index.html` com o mesmo `tv/page.py` da Samsung (trocando o adaptador por `webos/platform.js`), gera os ícones de 80 e 130 px e empacota o `.ipk` com o `webos/ipk.py`, sem precisar do `ares-package`.

---

## Google TV: desativar a tela inicial do Google
Na Google TV, o botão **Home** do controle quase sempre volta para a tela do Google, mesmo com o Onix Launcher instalado. Para o Onix assumir, é preciso **desativar** a tela inicial do Google. Ela **não é apagada** e pode ser reativada quando quiser.

> ⚠️ **Antes, abra o Onix Launcher pela lista de apps e confirme que ele funciona.** Sem nenhuma tela inicial ativa, a TV fica sem tela inicial. Para voltar ao normal, veja [Desfazer](#desfazer-trazer-de-volta-a-tela-inicial-do-google).

**Pelo celular (Android), com o app Bugjaeger:**

1. Na TV, ative as **Opções do desenvolvedor**: em **Configurações → Sistema → Sobre**, clique 7 vezes em **Build do sistema operacional** (aparece "Você é um desenvolvedor").
2. Em **Configurações → Sistema → Opções do desenvolvedor**, ligue **Depuração USB** (e, se existir, **Depuração sem fio** ou **ADB pela rede**).
3. Descubra o IP da TV em **Configurações → Rede e Internet →** sua rede (algo como `192.168.0.40`).
4. No celular, instale o **Bugjaeger Mobile ADB** pela Play Store. Celular e TV no **mesmo Wi-Fi**.
5. No Bugjaeger, use a opção de **conectar por Wi-Fi** (ícone de rede), digite o **IP da TV** com a porta **5555** (ex.: `192.168.0.40:5555`) e conecte. Na TV, aparece um pedido de autorização: marque **Sempre permitir** e aperte **OK**.
6. Abra a aba de **comandos** (terminal/shell) do Bugjaeger e rode, um de cada vez:

   ```
   pm disable-user --user 0 com.google.android.apps.tv.launcherx
   pm disable-user --user 0 com.google.android.tungsten.setupwraith
   ```

7. Aperte **Home** no controle: o Onix assume como tela inicial. Se a TV perguntar qual usar, escolha **Onix Launcher → Sempre**.

<details>
<summary><b>Pelo computador, com adb</b></summary>

```bash
# Google TV (Chromecast com Google TV, TVs Philips, TCL, Sony etc.)
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

```bash
# Android TV "clássico" (launcher antigo do Google), se for o caso
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

</details>

## Desfazer: trazer de volta a tela inicial do Google

Conecte de novo pelo Bugjaeger (ou pelo adb) e rode:

```
pm enable com.google.android.apps.tv.launcherx
pm enable com.google.android.tungsten.setupwraith
pm enable com.google.android.tvlauncher
```

Pelo computador, é o mesmo com `adb shell` na frente (ex.: `adb shell pm enable com.google.android.apps.tv.launcherx`), e `adb uninstall com.aurora.launcher` desinstala o Onix.
