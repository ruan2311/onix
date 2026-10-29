# Resultados do teste do Onix GameHub (28/10/2026)

Teste feito pelo celular (Termux) na Philips Google TV (192.168.1.75), com o usuário e por adb.
Instalados: Onix **15.9.0 teste** e GameHub **0.9.0**, por cima, sem perder dados (mesma chave).

> As APKs de `teste/` **não** foram trocadas. As correções abaixo estão no código. No celular, o
> GameHub de teste foi montado só com o Java e os assets novos, por cima dos emuladores da APK do
> PC; o `retro.c` não foi compilado (sem NDK). **Compile as duas no PC** antes do próximo teste.

## Roteiro

| Passo | Resultado | Observação |
|---|---|---|
| 1. Aba Jogos do Onix | ✅ | GameHub com os 4 consoles e "Abrir o GameHub"; não aparece na lista de apps. A aba foi reorganizada (veja abaixo). |
| 2. Abertura | ✅ | A abertura original do GameHub fica. Uma transição extra no Onix foi testada e **removida a pedido** (pesava). A abertura agora toca mesmo com as animações desligadas. |
| 3. Controle | ✅ | DualSense pareado. "virtual-search" (aparelho interno da Google TV) contava como controle: corrigido. O nome "DualSense Wireless Controller Touchpad" agora aparece sem "Touchpad". |
| 4. Mandar jogos | ✅ | Ultimate Mortal Kombat 3 (SNES) enviado; capa baixada. |
| 5. Desempenho SNES | ✅ | **60 FPS** com "Mostrar FPS". O usuário disse que o jogo funcionou. |
| 6. Menu do jogo / Continuar | ✅ | "Sair do jogo" grava a miniatura certa (a preta de antes foi salva com o próprio jogo na tela preta). |
| 7–13 | — | Não chegaram a ser testados nesta sessão. |

## Defeitos achados e corrigidos (no código deste branch)

1. **Jogo fechava ao abrir.** Log:
   `Using WebView from more than one process at once with the same data directory is not supported … Current process com.onix.gamehub:game, lock owner com.onix.gamehub`.
   O menu do jogo (WebView no processo `:game`) agora usa outra pasta de dados (`setDataDirectorySuffix("game")`, por reflexão).
2. **Armazenamento mostrava "1 KB" livre.** A reserva era 2 GB fixos; a TV tem 7,9 GB com 1,8 GB livres.
   Agora: 10% do total, entre 768 MB e 2 GB. Nesta TV: 812 MB reservados e 1,0 GB para jogos. Sem espaço, mostra "0 MB".
3. **Filtro LCD:** shader não compilava (`No matching overload for function 'step'`). Corrigido em `jni/retro.c` (**precisa compilar no PC**).
4. **Contorno do foco cortado nas Configurações:** a lista de seções rolava a página inteira. Agora cada coluna rola sozinha, com folga para o contorno.
5. **Navegação lenta:** medido pela depuração do WebView. Animar sombra e fundo no foco travava quadros ao segurar a seta; agora só o zoom anima, em 0,1 s (cerca de metade do travamento). A lista de seções não redesenha o painel a cada passo.

## Pedidos do usuário feitos nesta sessão

- **Configurações → Interface:** "Som de navegação" e "Animações" (Igual ao Onix / Ligado / Desligado). O Onix manda as preferências dele ao abrir o GameHub (extras `sound` e `motion`), e o menu do jogo também segue.
- **Controle 1 e Controle 2:** em Configurações → Controles, "Definir" e aperta um botão no controle escolhido (guarda o descriptor em `players.json`). No jogo, o controle definido fica sempre na sua vaga. **Falta testar com o controle de verdade** (pelo adb não dá).
- **Capas em 3:4:** caixa deitada (SNES americano) fica inteira no centro, sobre ela mesma desfocada. As capas antigas são baixadas e montadas de novo uma vez.
- **Aba Jogos do Onix:** Continuar jogando (cartões baixos) → cartão do Onix GameHub (abrir, 4 consoles, atualizar/desinstalar) → Minijogos em fileira → Notícias em uma fileira (8 mais recentes). Topo da aba com o desenho do controle em órbita.
