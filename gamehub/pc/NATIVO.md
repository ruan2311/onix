# GameHub para PC: o jogo rodando nativo (roteiro para o Claude do PC)

> Para quem pegar este projeto: leia tudo antes de começar. Fale com o Ruan sempre em
> **português do Brasil**, em linguagem simples, sem jargão. Escrito em 29/10/2026, no celular,
> depois do GameHub para PC 1.1.1 (Electron + WebAssembly) ser publicado.

## O que o Ruan quer
Que **o processamento do jogo rode nativo no Windows**, em C. Só isso: **as telas continuam as
mesmas** (Electron, `gamehub/assets` + `gamehub/pc`). Ele escolheu esse caminho (o "cenário 1")
em vez de refazer o app inteiro em C.

## Onde as coisas estão hoje
- `gamehub/jni/retro.c` — o motor da TV (Android): carrega o núcleo libretro (.so) com `dlopen`,
  roda os quadros, desenha com OpenGL ES 2, toca som pelo OpenSL ES, run-ahead, rebobinar,
  estados, memória do cartucho, trapaças, opções do núcleo. **É a base do programa nativo.**
- `gamehub/pc/core/web.c` — o mesmo motor adaptado para WebAssembly (o que roda hoje no PC).
  Já tem a imagem em RGBA8 e a API `ox_*` que o `game.js` usa.
- `gamehub/pc/src/main.js` — processo principal do Electron. `openGame()` abre a camada do jogo
  (`web/game.html`); `GAME` e `GAME_ASYNC` são os arquivos (ROM, estados, memória do cartucho).
- `gamehub/pc/web/game.js` — a tela do jogo atual: WebGL 2 com todos os filtros (o shader `FS`),
  teclado e controles (`PAD_DEFAULT`, `KEY_DEFAULT`), som (AudioWorklet), e `window.OnixMenu`, a
  ponte do menu do jogo (`menu.html` num iframe por cima).
- `gamehub/pc/src/preload.js` — os códigos de tecla (`PAD_CODES`, `KEY_CODES`): "Trocar botões"
  guarda `remap.<código>` com os **códigos de tecla do Android**, os mesmos da TV.
- `gamehub/pc/src/store.js` — os arquivos de dados (biblioteca, configurações, saves) em
  `%APPDATA%\Onix GameHub`. O programa nativo tem que usar **os mesmos arquivos**.
- `gamehub/cores.sh` — os commits travados dos 4 núcleos (FCEUmm, Snes9x, mGBA, Genesis Plus GX).
  Os núcleos nativos têm que sair **dessas mesmas versões** (os estados salvos dependem disso).

## O plano
1. **`gamehub/pc/native/onix-game.c`**, a partir do `retro.c`:
   - `dlopen`/`dlsym` → `LoadLibraryW`/`GetProcAddress` (núcleos como `.dll`).
   - Janela, OpenGL, som e controles pelo **SDL2** (`SDL_GameController` para Xbox, PlayStation,
     8BitDo; `SDL_AudioStream` ou fila de áudio no lugar do OpenSL; contexto OpenGL 3.3).
   - Desenho: portar o shader `FS` do `game.js` (GLSL ES 3.00 → `#version 330 core`), com os
     mesmos filtros e ajustes (`filter`, `brightness`, `saturation`, `scanStrength`, `curvature`,
     `vignette`, `frameBlend`, `backdrop`) e o fundo ambiente (as 3 reduções em framebuffer).
   - Botões: os mesmos padrões do `game.js` (`PAD_DEFAULT`, `KEY_DEFAULT`), a mesma troca de
     botões (`remap` com códigos do Android: use as tabelas `PAD_CODES`/`KEY_CODES` do
     `preload.js`), R2 segurado acelera, L2 rebobina, Select + Start ou Esc pedem o menu.
   - Controle 1 e 2: `players.json` guarda o nome do aparelho (`Gamepad.id` do Chromium). No SDL o
     nome é outro; dá para guardar os dois (ou usar o GUID do SDL e deixar o Electron só para o hub).
   - Ritmo, run-ahead, rebobinar, controle dinâmico do som: igual ao `retro.c`.
2. **Núcleos como `.dll`** com MinGW, das mesmas versões: `make -f Makefile.libretro platform=win`
   (FCEUmm, Genesis Plus GX), `make -C libretro platform=win` (Snes9x) e CMake com toolchain
   MinGW para o mGBA (as opções estão no `gamehub/cores.sh`). Script novo:
   `gamehub/pc/native/build.sh` (MinGW: `sudo apt install gcc-mingw-w64-x86-64 g++-mingw-w64-x86-64`;
   SDL2: o `SDL2-devel-2.x-mingw.tar.gz` oficial do GitHub do SDL).
3. **Conversa com o Electron:** o `main.js` abre o `onix-game.exe` (`child_process.spawn`) com
   os caminhos (núcleo, ROM, pasta dos estados, arquivo da memória do cartucho, configurações
   efetivas em JSON, modo `resume`/`new`/`1`–`4`) e conversa por **linhas JSON no stdin/stdout**:
   - do jogo para o app: `ready`, `menu` (pediram o menu), `toast`, `fps`, `options` (JSON das
     opções do núcleo, para `options-<console>-<lang>.json`), `saved`, `loaded`, `quit`;
   - do app para o jogo: `pause`, `resume`, `saveState`, `loadState`, `reset`, `fastForward`,
     `settings` (configurações novas), `cheats`, `coreOptions`, `quit`.
4. **Menu do jogo:** o mesmo `menu.html`. Sugestão: a janela do SDL como **filha** da janela do
   Electron (`SetParent` com o `getNativeWindowHandle()`), acompanhando o tamanho e a tela cheia,
   e o menu numa `BrowserWindow` **transparente** por cima (uma janela nativa filha fica sempre por
   cima do conteúdo web da mesma janela, por isso o menu precisa ser outra janela). O
   `window.OnixMenu` do `game.js` mostra tudo o que o menu precisa; vire uma ponte que repassa os
   comandos ao processo nativo.
5. **Plano B:** se o `onix-game.exe` não existir ou fechar sozinho ao abrir, o `openGame()` usa a
   versão WebAssembly de hoje (`game.html`). Nada da versão atual pode ser apagado.
6. **Empacotar:** o `build-win.js` copia `onix-game.exe`, `SDL2.dll` e `cores/*.dll` para dentro
   do app. Licenças: o SDL2 é zlib; os núcleos, as mesmas da TV (já em `assets/licenses`).

## Testar
- No PC dá para **rodar o .exe direto do WSL** (`./onix-game.exe ...`): o Windows abre a janela.
  O Claude do celular não podia fazer isso; aqui dá para testar de verdade.
- Use os jogos do próprio Ruan. Para um teste rápido de NES sem jogo comercial, dá para montar uma
  ROM mínima (o Claude do celular fez uma: tela azul que fica vermelha com o botão A).
- Grave um log (`%APPDATA%\Onix GameHub\game-native.log`) com o que acontecer, para as rodadas de
  teste renderem.
- Confira: imagem e filtros, som sem estalos (60 FPS com "Mostrar FPS"), controle e teclado,
  estados salvos que abrem nos dois motores (nativo e WebAssembly), "Continuar jogando".

## Regras do projeto (valem aqui também)
- Mudança visual: **print na pasta de imagens e esperar o OK do Ruan** antes de commit/release.
- Depois de commits, `git push` na hora. O código do GameHub é espelhado no repositório público
  **ruan2311/onix-gamehub**: `git subtree split --prefix=gamehub -b gh-split`, push para `main`
  de lá e apague o branch local.
- **Releases:** uma por versão, `gamehub-vX.Y.Z` no onix-gamehub, com os **3 arquivos juntos** e
  rótulos: `onix-gamehub.apk` → "Onix GameHub para Google TV" (o nome do arquivo não pode mudar:
  o atualizador do Android, o instalador do Onix e o link `releases/latest/download` usam ele),
  `onix-gamehub-android.apk` → "Onix GameHub para Android" (o mesmo arquivo) e
  `Onix-GameHub-win64.zip` → "Onix GameHub para PC (Windows)". Upload com
  `gh release upload <tag> "arquivo#rótulo"`. A versão do PC (`pc/package.json`) é a mesma do
  GameHub (`android:versionName` em `gamehub/AndroidManifest.xml`). Nunca apague releases antigas.
- Até a 1.1.1 o GameHub também saía no `ruan2311/onix`; daqui para frente, só no onix-gamehub.
- Tudo que o Ruan vê no app tem tradução para o espanhol (`ES` em `gamehub/assets/common.js`).
