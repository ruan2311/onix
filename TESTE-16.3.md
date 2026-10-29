# Teste do Onix 16.3.0 (posição do menu e esconder o menu)

Roteiro para o Claude do celular (Termux) quando o usuário disser **"comece o teste do menu"**
(ou "teste da 16.3"). O Ruan vai testar na TV dele, em casa. Fale sempre em **português simples**,
sem jargão, e passe um item de cada vez.

Nada aqui publica release, muda versão ou mexe no `main`. Os resultados ficam em
`RESULTADOS-TESTE-16.3.md`, neste branch (`teste-16.3`).

## O que está sendo testado

A 16.3.0 traz duas novidades, em **Configurações → Menu** (grupo novo, entre "Animações" e "Início"):

1. **Posição do menu** (OK passa para a próxima):
   - **Lateral:** o de sempre.
   - **Em cima:** uma barra de abas de ponta a ponta. À esquerda a marca "ONIX By Ruan", o perfil e
     Início, Apps e Jogos; na ponta direita Entradas, Configurações e Desligar TV. A aba aberta fica
     numa pílula clara, com um traço de luz embaixo.
   - **Embaixo:** um dock flutuante no centro, com ícones grandes e o rótulo embaixo. O item aberto
     tem um ponto de luz, e o item focado sobe um pouco. Um separador fino divide os itens do Onix
     dos do sistema (Entradas, Configurações, Desligar TV).
2. **Esconder o menu ao abrir uma seção** (desligado por padrão): ao abrir Início, Jogos, Apps…, o
   menu se recolhe para a borda (esquerda, cima ou baixo, conforme a posição) com uma animação, e o
   conteúdo ocupa a tela toda. Ele volta ao apertar a seta na direção dele, ao apertar Voltar, ou
   tocando na abinha que aparece na borda (para mouse e toque; na TV ela é só uma pista visual).

Não mudam: o **Modo Infantil** (continua com o menu lateral) e o **celular em pé** (continua com a
barra de baixo dele).

## O que está neste branch

- `teste/onix-launcher-16.3.apk`: Onix **16.2.9 teste**, a variante **Launcher** que a TV usa. A
  versão é 16.2.9 de propósito: o código-fonte é o da 16.3.0, mas a release de verdade (16.3.0)
  continua instalando por cima pelo atualizador. Pacote `com.aurora.launcher`, assinado no PC com a
  mesma chave do Onix, então instala por cima **sem perder dados** (perfis, apps organizados).
- **Não compile APK no celular:** sem a chave do PC, a APK sai com outra assinatura, e a TV teria
  que desinstalar o Onix para instalar, perdendo os dados.
- Os arquivos antigos `teste/onix-launcher.apk` e `teste/onix-gamehub.apk` são do teste do GameHub:
  **não instale** (a `onix-launcher.apk` é a 15.9.0, mais antiga).

## Antes de começar

1. Pegue o branch: `git fetch origin teste-16.3 && git checkout teste-16.3 && git pull --ff-only`.
2. Ferramentas no Termux (só na primeira vez): `pkg install android-tools`.
3. A TV é uma Philips Google TV em `192.168.1.75:5555`. O celular precisa estar no mesmo Wi-Fi.
   - Rode `adb connect 192.168.1.75:5555`.
   - Se a TV mostrar "Permitir depuração?", peça para o usuário marcar
     "Sempre permitir deste computador" e confirmar.
   - Se recusar a conexão, peça para ele conferir a depuração USB/rede nas opções do desenvolvedor.
4. O Ruan vai estar na frente da TV, mas **pergunte antes** de mandar teclas pelo adb ou abrir algo.
   Veja o que está na tela: `adb -s 192.168.1.75:5555 shell dumpsys activity activities | grep -m1 topResumedActivity`.
5. Anote a tela da TV: `adb -s 192.168.1.75:5555 shell wm size` e `... shell wm density`.

## Instalar

```
adb -s 192.168.1.75:5555 install -r teste/onix-launcher-16.3.apk
adb -s 192.168.1.75:5555 shell dumpsys package com.aurora.launcher | grep -m1 versionName
```

O segundo comando tem que mostrar `versionName=16.2.9 teste`. `-r` instala por cima e mantém os dados.
Se aparecer "downgrade" ou "assinatura diferente", **pare** e avise o usuário (não desinstale o Onix).

**Sem adb (plano B):** no app Downloader da TV, use o link direto
`https://raw.githubusercontent.com/ruan2311/onix/teste-16.3/teste/onix-launcher-16.3.apk`.

Para abrir o Onix: o usuário aperta o botão Início do controle (o Onix é a tela inicial da TV).

## Ver a tela da TV (para conferir e guardar provas)

```
adb -s 192.168.1.75:5555 exec-out screencap -p > ~/tela-N.png
```

Abra o PNG para ver o que a TV mostra. Guarde no máximo uns 10 prints importantes em
`teste/prints/` neste branch (com nomes tipo `3-dock-inicio.png`). Nunca prints com dados pessoais.

Teclas do controle pelo adb, se o usuário pedir para você apertar (`adb shell input keyevent N`):
◀ 21, ▲ 19, ▶ 22, ▼ 20, OK 23, Voltar 4, Início 3. Normalmente deixe **o Ruan** apertar o controle e
use os prints para conferir.

## Roteiro com o usuário

Passe os itens um por um e anote o que ele disser. Marque ✅ (ok), ⚠️ (funciona, mas com um defeito)
ou ❌ (não funciona).

1. **Nada mudou no começo.** Ao abrir, aparece o aviso "Onix atualizado para a 16.2.9 teste!" e o
   menu lateral é o de sempre? Início, Apps e Jogos abrem como antes?
2. **Configurações → Menu.** O grupo "Menu" aparece entre "Animações" e "Início", com "Posição do
   menu" (valor "Lateral") e "Esconder o menu ao abrir uma seção" (desligado)? Os textos estão certos?
3. **Em cima.** Em "Posição do menu", OK uma vez: avisa "Posição do menu: Em cima" e o menu vai para
   o topo?
   - A barra mostra a marca, o perfil, Início/Apps/Jogos e, na ponta direita, Entradas/Configurações/
     Desligar TV? Algum texto cortado ou um item em cima do outro?
   - O relógio e o destaque da tela inicial ficam **abaixo** da barra, sem ficar atrás dela?
   - ◀ ▶ andam entre os itens do menu e trocam a seção sem engasgar? ▼ entra no conteúdo e ▲ volta
     ao menu? Voltar leva ao menu e, apertando de novo, ao Início?
   - **Cuidado com "Desligar TV":** um OK só "arma" o botão (fica vermelho) e o segundo desliga a
     TV. Se armar sem querer, aperte Voltar.
4. **Embaixo.** OK mais uma vez: "Embaixo".
   - O dock aparece flutuando no centro, com ícones grandes? O item aberto tem um ponto de luz, e o
     item focado sobe sem ser cortado?
   - Na tela inicial, desça até o fim (última fileira): o conteúdo **não** fica escondido atrás do
     dock? Faça o mesmo em Apps e no fim de Configurações.
   - Um aviso (toast), como ao trocar uma opção, aparece **acima** do dock?
   - ▲ sai do dock para o conteúdo e ▼ volta ao dock?
5. **De volta à Lateral.** OK mais uma vez: volta ao de sempre, igual ao começo?
6. **Esconder o menu.** Ligue "Esconder o menu ao abrir uma seção" (com a posição Lateral).
   - Ao ligar, o menu se recolhe na hora e aparece uma abinha na borda: é o esperado.
   - Aperte Voltar (ou ◀ no primeiro item): o menu volta? Escolha **Jogos** e OK: o menu se recolhe
     com a animação e o conteúdo ocupa a tela toda?
   - Repita para **Em cima** (o menu sobe) e **Embaixo** (o menu desce).
   - A animação é **suave**? Anote: ✅ suave, ⚠️ engasga um pouco, ❌ trava muito.
   - Troque de seção depressa umas 10 vezes (abrir Jogos, Voltar, Apps, Voltar, Início…): o menu
     nunca fica preso escondido, nem pisca ou volta sozinho?
   - Com o menu escondido, as setas na direção dele (◀ na lateral, ▲ em cima, ▼ embaixo), quando o
     foco está na borda do conteúdo, o trazem de volta?
7. **Desempenho (o mais importante nesta TV).** Com "Esconder" ligado:
   ```
   adb -s 192.168.1.75:5555 shell dumpsys gfxinfo com.aurora.launcher reset
   ```
   Peça ao Ruan para abrir e fechar o menu umas 10 vezes (seção, Voltar, seção, Voltar…). Depois:
   ```
   adb -s 192.168.1.75:5555 shell dumpsys gfxinfo com.aurora.launcher | grep -E "Total frames|Janky|50th|90th|95th|99th"
   ```
   Anote os números. Se engasgar, desligue **"Transições suaves"** (Configurações → Desempenho): o
   menu deve passar a sumir e voltar na hora, sem animar. Funciona?
8. **Combinações.**
   - Em **Configurações → Início**, troque o estilo para **Clean Glass** e **Onix Cosmos**, com o
     menu em cima e embaixo: o relógio e a arte ficam bem posicionados, sem ficar atrás da barra
     nem do dock?
   - **Perfil:** o item do perfil na barra e no dock abre "Quem está assistindo?"?
   - **Modo Infantil:** num perfil infantil o menu continua lateral, mesmo com a posição "Em cima"
     escolhida? Ao sair do perfil infantil, volta a escolha do Ruan?
   - **Espanhol:** em Idioma → Español, o grupo vira "Menú", a opção vira "Ocultar el menú al abrir
     una sección", e a barra e o dock continuam sem texto cortado (os rótulos ficam maiores)?
   - **Descansar agora** (Configurações → Início): entra e sai da tela de descanso com o menu
     escondido sem bagunçar nada?
   - **Organizar apps** (Início → Organizar) com o menu escondido: pegar e mover um app funciona?
   - Abrir um app (Netflix) e voltar ao Onix pelo Início: o estado do menu está certo?
9. **Salvar.** Deixe Em cima + Esconder ligados, force o Onix a recarregar (pelo botão Início, abra
   outro app e volte) e depois **reinicie a TV**: a posição e a opção continuam salvas?
10. **Desligar de volta.** Termine deixando a posição **Lateral** e "Esconder" **desligado**, como o
    Ruan usa no dia a dia (a não ser que ele prefira outro).

## Se algo travar ou fechar sozinho

Pegue o log logo depois:

```
adb -s 192.168.1.75:5555 logcat -d -v time | grep -i -E "aurora|chromium|AndroidRuntime|onix" | tail -200
```

## Anotar os resultados

Preencha `RESULTADOS-TESTE-16.3.md` (já existe um modelo): um item por passo do roteiro com ✅, ⚠️
ou ❌, o que o usuário disse, os números do item 7 e os prints guardados. Descreva cada defeito com a
tela, a posição do menu, o que aconteceu e o que era esperado.

Depois faça commit **neste branch** e `git push origin teste-16.3`. Não faça merge no `main`, não
crie release, não mude versões: as correções e a release 16.3.0 saem do PC.
