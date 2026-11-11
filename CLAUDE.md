# Onix

Este projeto é editado em duas estações: o celular (Termux) e o PC (WSL + VS Code).

- Ao abrir a sessão, um hook em `.claude/settings.json` roda `git pull --ff-only` sozinho. Se ele falhar (cópias divergiram), avise o usuário em português simples antes de mexer em qualquer arquivo.
- Ao terminar uma tarefa que gerou commits, rode `git push` na hora, sem perguntar.
- Quando o usuário disser "comece o teste do onix gamehub" (ou parecido), siga o roteiro `gamehub/TESTE.md` do branch `gamehub` (`git fetch origin gamehub && git checkout gamehub`). Os APKs de teste já estão em `teste/` nesse branch; não compile nem publique nada pelo celular.
- Quando o usuário disser "continue o GameHub nativo" (ou falar da versão em C do GameHub para PC), siga o roteiro `gamehub/pc/NATIVO.md`. No PC dá para rodar o `.exe` direto do WSL para testar.

<!-- Seção movida do ~/AGENTS.md global em 10/11/2026, para não ser lida em toda sessão. -->
## Onix (launcher de TV)

- **Pasta:** `/root/onix` (celular) e `~/onix` (PC). **GitHub:** `ruan2311/onix` (público).
- **O que é:** tela inicial para Google TV / Android TV, com versões para Samsung (Tizen) e LG
  (webOS). A interface é o `index.html`. Versão atual: **17.5.0** (fora da fase beta desde a 16.3.2). Onix Controle: **1.0.8**.
- **Pacote Android:** fica **`com.aurora.launcher`** para sempre (o código está em
  `com.onix.launcher`). Nunca renomeie o pacote, o alias `com.aurora.launcher.MainActivity` nem a
  classe `PowerAdmin`. Isso quebraria as atualizações e as configurações dos usuários.

### Como lançar uma versão
1. Mude `android:versionName` (versão `X.Y.Z`, sem "beta"). O `versionCode` é o número maior (16).
2. Escreva a novidade no `CHANGELOG` do `index.html` (Configurações → Novidades) **e** no
   `CHANGELOG.md`. Todo texto novo passa por `t()` e ganha a tradução no dicionário `ES`.
3. Gere: `bash android/build.sh launcher`, `bash android/build.sh app`, `bash tizen/build.sh`,
   `bash webos/build.sh`.
4. Release **nova** `vX.Y.Z` (título "Onix X.Y.Z", `--latest`) com `onix-app.apk`,
   `onix-launcher.apk`, `onix-tizen.zip`, `onix-tizen.wgt` e `onix-webos.ipk`. Os nomes dos
   arquivos não mudam: o atualizador do app procura por eles.
5. Samsung e LG continuam chamados de beta.

### Pedidos com roteiro pronto (detalhes no `CLAUDE.md` do repositório)
- "comece o teste do onix gamehub" → `gamehub/TESTE.md` no branch `gamehub`.
- "comece o teste do menu" / "teste da 16.3" → `TESTE-16.3.md` no branch `teste-16.3`.
- "continue o GameHub nativo" → `gamehub/pc/NATIVO.md`.
- Nos testes pelo celular, não compile nem publique: as APKs de teste já estão em `teste/`.

### Onix Controle (app de controle remoto para celular)
- Fica em `controle/` no mesmo repositório. Gere com `bash controle/build.sh`.
- Release `controle-vX.Y.Z` com `onix-controle.apk` e **sempre `--latest=false`** (senão o
  atualizador do Onix na TV quebra). Atualize o link com a versão no README (parte em português
  e em espanhol).

### Anúncio do Project Gems (desde a 17.2.0)
- Primeiro banner do destaque (só Google TV), com QR code. Conteúdo em `projectgems.github.io/onix/anuncio/`
  (`anuncio.json` + imagens), lido pelo `Anuncio.java` a cada 1 h. Opção para desligar em Configurações → Indicações.
- O Ruan edita pelo app **Onix Anúncio** (`anuncio/`, pacote `com.onix.anuncio`, `bash anuncio/build.sh` →
  `onix-anuncio.apk`, sem release), que publica no site pela API do GitHub com uma chave só do site.

### Plano guardado: Onix 14.0.0
- Perfis (entra sozinho se só houver um), Modo Infantil (PIN e limite de tempo) e controle pelo
  celular. **Só começar quando eu pedir.**

## Cobalt GameHub (jogos clássicos; antes Onix GameHub)

- **Código:** pasta `gamehub/` dentro do repositório do Onix (é a fonte oficial).
- **GitHub próprio:** `ruan2311/cobalt-gamehub` (público, GPL; era `onix-gamehub`), alimentado assim:
  `git subtree split --prefix=gamehub` e push desse resultado para o `main` do cobalt-gamehub.
  Faça isso depois de todo commit no GameHub.
- **Cuidado com a troca de nome:** o GameHub 1.2.0 ou mais velho e o Onix 16.4.0 ou mais velho só
  aceitam baixar de `github.com/ruan2311/onix-gamehub/...` com o arquivo `onix-gamehub.apk`. Por
  isso as releases levam os arquivos com os dois nomes. Para eles, existe o repositório ponte
  `ruan2311/onix-gamehub` (só um aviso e a release `gamehub-v1.2.2` com os arquivos de nome
  antigo): não apague. Quem atualiza por ele passa a usar o `cobalt-gamehub` sozinho. Nunca mude o pacote `com.onix.gamehub` nem
  a pasta de dados do PC (`%APPDATA%\Onix GameHub`): isso apagaria os jogos e saves das pessoas.
- **O que é:** emuladores de NES, Super Nintendo, Game Boy Advance e Mega Drive em todos os ports.
  Pacote `com.onix.gamehub`. A mesma APK serve para TV e celular. Também tem versão para PC (Windows,
  Electron + WebAssembly) e para iPhone (site instalável). Versão atual: **2.0.1** (Android, PC e iPhone juntos). Na TV (desde a 1.6.0), os jogos também rodam direto do pendrive ou HD (`UsbDrives.java`, sem copiar).
  Na TV e no celular (desde a 1.5.0) também **Game Boy Color, PS1, Atari 2600/7800 e Fliperama** (FinalBurn
  Neo), com núcleos prontos do buildbot (SHA-256 no `gamehub/cores.sh`). O PC tem PS1 e GBC (lista `PC_CORES`
  no `gamehub/pc/cores.sh`) e N64, DS, PSP, GameCube, Wii e PS2 pelo RetroArch; o iPhone tem só os 4.
  Desde a 1.7.0, a TV e o celular também têm N64, DS e PSP e, só em 64 bits e "em teste", GameCube, Wii
  e PS2 (motor com 3D no `jni/retro.c`; `Hardware.java` esconde o que o aparelho não aguenta).
  Desde a 2.0.0: conquistas do Cobalt (`Conquistas.java`, `assets/conquistas.json`; TV, celular e PC) e
  RetroAchievements na TV e no celular (rcheevos em `jni/rcheevos`, ponte `jni/cheevos.c`, `RetroAchievements.java`;
  softcore até o RAdmin validar; textos traduzidos pelo MyMemory em `Traducao.java`). No PC desde a 2.0.1 (a mesma ponte no WebAssembly,
  `pc/src/retroachievements.js`; nos consoles do RetroArch, o dele com o token); o iPhone ainda não tem.
- **Compilar no celular sem NDK:** `SEM_NDK=1 NATIVE_FROM=<apk anterior> bash gamehub/build.sh` (o motor sai do
  `gamehub/jni/build-sem-ndk.sh`). O zip do PC precisa da pasta `gamehub/pc/build/motor-retroarch-1`
  (`gh release download motor-retroarch-1 -R ruan2311/cobalt-gamehub`), senão sai sem o RetroArch.
- `versionCode = maior×10000 + menor×100 + correção` (o `build.sh` confere).
- O `build.sh` usa no `aapt` a base do Android 10 (`~/sdk/platforms/android-29/android.jar`, baixada sozinha na primeira vez, 78 MB): sem ela, o Android 10 não lê o pendrive.

### Como lançar uma versão
1. Mude a versão no `gamehub/AndroidManifest.xml` e no `gamehub/pc/package.json` (as versões devem acompanhar sempre).
2. **Android:** `bash gamehub/build.sh`. No PC (com NDK) compila tudo. **No celular** (sem NDK),
   se a pasta `jni/` não mudou, reaproveite as bibliotecas da versão anterior:
   `NATIVE_FROM=<cobalt-gamehub.apk da release anterior> bash gamehub/build.sh` (gera também as
   cópias `cobalt-gamehub*.apk` e `onix-gamehub-android.apk`).
3. **PC:** `bash gamehub/pc/cores.sh` (precisa do `~/emsdk`), depois `cd gamehub/pc && npm
   install` e `node gamehub/pc/build-win.js` → `Cobalt-GameHub-win64.zip` e `Onix-GameHub-win64.zip`.
4. Release nova **`gamehub-vX.Y.Z`** no `ruan2311/cobalt-gamehub` com `--latest`, trazendo **obrigatoriamente todos os ports juntos** (mesmo se o PC não tiver mudanças de código, gere e anexe o zip para nunca quebrar links nem downloads):
   - `cobalt-gamehub.apk` → "Cobalt GameHub para Google TV"
   - `cobalt-gamehub-android.apk` → "Cobalt GameHub para Android" (o mesmo arquivo)
   - `Cobalt-GameHub-win64.zip` → "Cobalt GameHub para PC (Windows)"
   - e as cópias com o nome antigo, para os apps antigos: `onix-gamehub.apk`,
     `onix-gamehub-android.apk` e `Onix-GameHub-win64.zip` (rótulos com "(nome antigo)").
5. Atualize o `gamehub/README.md` quando o comportamento mudar.
6. Gere de novo o site do iPhone (`bash gamehub/ios/build.sh`) e atualize o card de novidades do site.

### iPhone e iPad (site instalável)
- **Código:** `gamehub/ios/`. **Endereço:** https://projectgems.github.io/gamehub/app/ (sem release, sem
  conta da Apple e sem contagem de downloads). Reaproveita as telas, o `pc/web/game.js` e os núcleos
  do PC. O que muda em `assets/` ou `pc/web/` só chega ao iPhone ao gerar de novo.
- **Publicar:** `bash gamehub/ios/build.sh` (gera em `~/gems/gamehub/app`, sobrescrevendo a pasta) e
  commit + push em `/root/gems`. O iPhone pega a versão nova sozinho ao abrir.

### Testar o PC sem um PC
- `xvfb-run` + Electron com `--use-angle=gl` (sem isso, o WebGL falha no Ubuntu do celular).
