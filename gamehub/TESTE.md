# Teste do Onix GameHub (antes da 16.0.0)

Roteiro para o Claude do celular (Termux) quando o usuário disser **"comece o teste do onix gamehub"**.
Nada aqui publica release, muda versão ou mexe no `main`. Os resultados ficam em
`gamehub/RESULTADOS-TESTE.md` no branch `gamehub`.

## O que está no branch `gamehub`

- `teste/onix-launcher.apk`: Onix **15.9.0 teste**, a variante Launcher que a TV usa, com a aba
  Jogos nova (atalhos do GameHub, instalar e desinstalar). A versão é 15.9.0 de propósito: a
  release 16.0.0 de verdade ainda instala por cima pelo atualizador, e o atualizador não troca
  este teste pela 15.0.2.
- `teste/onix-gamehub.apk`: Onix GameHub **0.9.0**. Pacote `com.onix.gamehub`, a mesma chave do Onix.
- Os dois foram compilados e assinados no PC. **Não compile APK no celular:** sem a chave do PC, a
  APK sai com outra assinatura, e a TV teria que desinstalar o Onix para instalar, perdendo os dados.

## Antes de começar

1. Pegue o branch: `git fetch origin gamehub && git checkout gamehub && git pull --ff-only`.
2. Ferramentas no Termux (só na primeira vez): `pkg install android-tools`.
3. A TV é uma Philips Google TV em `192.168.1.75:5555`. O celular precisa estar no mesmo Wi-Fi.
   - Rode `adb connect 192.168.1.75:5555`.
   - Na primeira vez, a TV mostra "Permitir depuração?". Peça para o usuário marcar
     "Sempre permitir deste computador" e confirmar.
4. Veja se alguém está assistindo:
   `adb -s 192.168.1.75:5555 shell dumpsys activity activities | grep -m1 topResumedActivity`.
   Se for outro app (TV ao vivo, Netflix…), pergunte ao usuário antes de instalar ou abrir qualquer coisa.

## Instalar

```
adb -s 192.168.1.75:5555 install -r teste/onix-launcher.apk
adb -s 192.168.1.75:5555 install -r teste/onix-gamehub.apk
```

`-r` instala por cima e mantém os dados (perfis, apps organizados).

**Sem adb (plano B):** no app Downloader da TV, use os links diretos:
- `https://raw.githubusercontent.com/ruan2311/onix/gamehub/teste/onix-launcher.apk`
- `https://raw.githubusercontent.com/ruan2311/onix/gamehub/teste/onix-gamehub.apk`

## Roteiro com o usuário

Passe os itens um por um, em português simples, e anote o que ele disser.

1. **Aba Jogos do Onix.**
   - Aparece "Onix GameHub" com os 4 consoles e o "Abrir o GameHub"?
   - O GameHub **não** aparece na lista de apps do Onix?
2. **Abrir e a abertura.**
   - Ao abrir pela aba Jogos, aparece a abertura inteira (linha colorida atravessando a tela, depois o logo "ONIX GAMEHUB By Ruan", e tudo some em cerca de 1,5 s)?
   - Ou a TV pula para a tela pronta? Fica preta por muito tempo?
   - Volte para o Onix com o botão Início e entre de novo pelo GameHub: a abertura toca de novo?
   - O perfil aparece no topo?
3. **Controle.** Sem controle conectado:
   - O topo mostra "Sem controle"?
   - OK nesse chip abre a tela de Bluetooth/acessórios da TV? Anote qual tela abriu.
   - Ao parear um controle Bluetooth, aparece "Controle conectado: <nome>"?
4. **Mandar jogos.** Em "Adicionar jogos" (o +):
   - Leia o QR com o celular e mande jogos do próprio usuário: um de SNES, um de GBA, um de NES,
     um de Mega Drive e um .zip.
   - A TV mostra o progresso?
   - A capa e o nome certo aparecem em alguns segundos (precisa de internet)?
   - Um arquivo errado (uma foto) é recusado com aviso?
5. **Desempenho no SNES (o mais importante).** Ligue "Mostrar FPS" (Configurações → Imagem) e
   jogue um jogo de SNES. Compare com o RetroArch na mesma TV:
   - Fica em 60 FPS?
   - Os botões têm atraso?
   - O som estala ou falha?
   - Se estalar, teste "Atraso do som" em 96 ms.
6. **Menu do jogo** (Voltar, ou Select + Start):
   - Salvar no espaço 1 e carregar.
   - "Sair do jogo".
   - O "Continuar jogando" aparece com a imagem do ponto onde parou, no GameHub e na aba Jogos do Onix?
   - Ao entrar por ali, ele volta exatamente daquele ponto?
7. **Atalhos:**
   - Segurar R2 acelera.
   - Ligue "Rebobinar" em Configurações → Jogo: segurar L2 volta no tempo.
8. **Imagem e opções:**
   - Teste os filtros Nítido, CRT e LCD.
   - Veja Configurações → Super Nintendo → Opções do emulador: elas vêm em português?
9. **Outros consoles:** GBA, NES e Mega Drive abrem e têm som?
10. **Desconectar o controle no meio do jogo:** o jogo pausa e avisa?
11. **Perfis e Modo Infantil:**
    - Outro perfil do Onix não vê os saves do primeiro?
    - Num perfil infantil, só aparecem os jogos marcados com "Liberar no Modo Infantil"?
12. **Atualizar sem perder nada:**
    - Reinstale `teste/onix-gamehub.apk` com `adb install -r`. Isso simula uma atualização.
    - Os jogos, as capas e os saves continuam lá?
13. **Desinstalar pelo Onix:**
    - "Desinstalar o GameHub" mostra a confirmação do Android?
    - Depois, a aba Jogos volta a mostrar "Instalar o GameHub"?
    - "Instalar" vai dizer que o GameHub ainda não foi publicado. É o esperado até a release.
    - Para voltar a testar, reinstale pelo adb.

## Se algo travar ou fechar sozinho

Pegue o log logo depois:

```
adb -s 192.168.1.75:5555 logcat -d -v time -s OnixRetro:* AndroidRuntime:E DEBUG:* libc:F ActivityManager:W | tail -200
```

Se um jogo específico não abrir, anote o console e o nome. O nome do arquivo ajuda, mas nunca
mande a ROM para o repositório.

## Anotar os resultados

Crie ou atualize `gamehub/RESULTADOS-TESTE.md`:
- um item por passo do roteiro: ✅, ❌ ou ⚠️, com o que o usuário disse;
- os trechos de log;
- os números de FPS;
- a tela que o botão de Bluetooth abriu.

Depois faça commit **no branch `gamehub`** e `git push origin gamehub`. Não faça merge no `main`,
não crie release e não mude versões: as correções e a 16.0.0 saem do PC.
