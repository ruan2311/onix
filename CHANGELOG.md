# Novidades do Onix

As mesmas notas aparecem dentro do app, em **Configurações → Novidades**.

## 14.0.0 beta (outubro de 2026)

- **Perfis:** a tela **Quem está assistindo?** tem um avatar para cada pessoa da casa (oito desenhos espaciais ou uma foto mandada pelo celular). Cada perfil tem os seus apps, recentes, recordes dos jogos, idioma, indicações e cidade do clima. Com um perfil só, o Onix entra direto, sem perguntar. Quem atualiza vira o primeiro perfil (**Meu perfil**, dá para trocar o nome) sem perder nada. Trocar de perfil fica no topo do menu e em **Configurações → Perfil**.
- **Modo Infantil:** um perfil para crianças mostra só os apps liberados, sem destaque, indicações, Apps, Entradas, Configurações e Desligar TV. Sair pede um PIN de 4 números, e dá para pôr um limite por dia (30 min a 3 horas); quando o tempo acaba, a tela avisa e só o PIN troca de perfil.
- **Controle pelo celular (Android TV e Google TV):** o celular vira controle (setas, OK, Voltar e Início), teclado (o do próprio celular, com as setas para escolher o app encontrado) e organizador de **Seus apps**, sem instalar nada. Para conectar sem usar o controle da TV, aponte a câmera para o QR code do botão **Celular**, no menu, ou da tela de descanso, que mostra o QR (em tom escuro) quando nenhum celular está conectado. Depois da primeira vez, o celular conecta sozinho; dá para salvar o controle na tela inicial dele. Funciona com o Onix na tela e no mesmo Wi-Fi.
- **Foto de perfil pelo celular:** escolha da galeria ou tire na hora, recorte no celular e ela vai para a TV, onde fica.
- **Clima na tela inicial (opcional):** a temperatura ao lado do relógio e, com OK, a previsão das próximas horas e dos próximos 5 dias, do Open-Meteo. O Onix não detecta onde você está: você liga e escolhe a cidade em **Configurações → Clima**, numa lista com estado e país.
- **Boas-vindas:** na primeira vez, o Onix ajuda a escolher o idioma, criar o perfil e acertar as indicações, o relógio e o clima. Tudo dá para pular, menos o nome.
- **Teclado na tela** para o nome do perfil e a cidade, que também aceita o texto digitado no celular.
- Gatinho do cartão **Sobre**: os corações passam por trás dele, e as orelhas e o rabo não se soltam mais ao mexer.

## 13.7.5 beta (outubro de 2026)

- **Jogos de cara nova:** Pong, Pinball, Tijolos e Cobrinha agora seguem o visual do Onix: painel de vidro, traços coloridos com brilho leve e nada de blocos chapados. Nos Tijolos, cada fileira é só um contorno na sua cor; os de aço têm um contorno branco a mais, que some na primeira batida. O aviso de começo, pausa e fim virou um cartão de vidro.
- **Fases em todos os jogos:** Pong com 5 fases (a máquina e a bola aceleram a cada uma), Pinball com 5 metas de 10.000 a 100.000 pontos (cada meta dá uma bola extra e a gravidade aumenta), Tijolos com 8 fases de desenhos diferentes (pirâmide, xadrez, losango, colunas, moldura…) e Cobrinha com 5 fases de 10 maçãs e muros novos.

## 13.7.4 beta (outubro de 2026)

- **Novo idioma: espanhol.** Em **Configurações → Idioma**, escolha **Português**, **Español** ou **Automático** (segue o idioma da TV, também na Samsung e na LG). Troca na hora, sem reiniciar o Onix. As notícias e as sinopses das indicações continuam em português, porque vêm de sites brasileiros.
- README: seção **🇪🇸 En español** com o passo a passo do Downloader.

## 13.7.3 beta (outubro de 2026)

- Botão **Limpar memória** ao lado de **Atualizar**: fecha os apps parados em segundo plano com `killBackgroundProcesses` (nunca o próprio Onix nem o teclado; o Android também não fecha o que está tocando) e mostra quanto liberou. No Android 14 ou mais novo, o sistema só deixa cada app fechar a si mesmo, então lá o botão só mostra a memória livre.
- **Android novo:** em aparelhos com Android recente, o Play Protect bloqueava a instalação dizendo que o app "foi criado para uma versão mais antiga do Android". O Onix agora declara o Android 15 (`targetSdkVersion` 35); antes declarava o 13 (33), e o Play Protect avisa quando a diferença para o Android do aparelho passa de 2 versões.
- README: o que fazer quando o Play Protect avisar que o app é desconhecido.

## 13.7.2 beta (outubro de 2026)

- **TV boxes com navegador interno antigo:** os ícones dos apps não apareciam e a tela ficava grande demais, cortada na direita. O Onix agora também funciona com o Android System WebView antigo (testado simulando o Chrome 70): sem `aspect-ratio`, `clamp()`, `max()` com `env()` nem `gap` no flexbox, ele usa equivalentes.
- O cartão **Sobre** mostra a versão do navegador interno da TV, para ajudar a achar problemas.

## 13.7.1 beta (outubro de 2026)

- Fechar o gatinho do cartão **Sobre** com **Voltar** não leva mais o foco para o menu.
- Por dentro, o código do Onix agora fica em `com.onix.launcher`. O pacote do app continua `com.aurora.launcher`, para a atualização instalar por cima e manter tudo (apps organizados, banners, recordes, tela inicial e o botão Desligar TV).

## 13.7.0 beta (outubro de 2026)

- **TVs LG (webOS):** o Onix agora tem uma versão para LG (TVs de 2022 em diante). 🧪 Experimental: pode apresentar falhas. O passo a passo está no README.
- O aviso **"O Onix já está na versão mais recente"** só aparece quando você mesmo procura atualizações, e não mais ao abrir.
- Por dentro: a ponte da Samsung e a da LG agora dividem o mesmo código (`tv/bridge.js`), com um adaptador pequeno para cada TV.

## 13.6.4 beta (outubro de 2026)

- **Etiquetas nas Novidades:** cada mudança mostra se é **Novo**, **Correção** ou **Bug** corrigido.
- Tem um segredo escondido no cartão **Sobre**, no fim das Configurações. A dica está lá. :)

## 13.6.3 beta (outubro de 2026)

- **Voltar ao Onix sem sumir nada:** às vezes, ao voltar de outro app, a tela abria vazia (sem os apps, as entradas, as indicações e as notícias baixadas) e com os destaques de demonstração da primeira versão. Acontecia quando a TV fechava o Onix em segundo plano: a página reabria antes de a ponte com o Android ser liberada. Corrigido, e a página ainda relê tudo assim que termina de abrir.
- **Sem internet:** no lugar dos destaques de fachada, o Onix agora avisa **"Parece que você está sem conexão"**, com atalho para as configurações da TV. As indicações e as notícias também avisam.
- **Configurações da TV** saiu das configurações do Onix e agora fica ao lado do botão **Atualizar**, na tela inicial.
- **Mais animações:** em **Configurações → Animações**, escolha o estilo do foco (suave, elástica, rápida, flutuar ou pulsar), como o destaque troca (esmaecer, deslizar ou zoom) e ligue a transição entre abas.
- **Apps organizados:** a aba **Apps** separa os **Recentes** (abertos pelo Onix nos últimos 30 dias, o último primeiro) dos **Não usados**, que ficam lá embaixo.

## 13.6.2 beta (outubro de 2026)

- As indicações do **Disney+**, da **HBO Max** e do **Globoplay** agora mostram o ícone oficial de cada serviço no selo da capa.
- Em **Entradas**, a TV e os banners de PlayStation 5, Xbox e Nintendo Switch ganharam o mesmo visual das HDMI: vidro escuro com o símbolo em branco.

## 13.6.1 beta (outubro de 2026)

- **TV em Entradas:** a primeira entrada agora é a **TV** (antena e cabo), para voltar aos canais abertos sem sair do Onix, mesmo se a TV estava numa HDMI. Só aparece em TVs com sintonizador; os "canais" de apps como Pluto TV e Globoplay ficam de fora.
- Os pontinhos do destaque da tela inicial agora ficam centralizados embaixo da capa, sem encostar nela.

## 13.6.0 beta (outubro de 2026)

- **Atualizações pelo próprio Onix:** ele procura versões novas no GitHub, avisa na tela (com um ponto verde em Configurações), baixa sozinho e abre a confirmação de instalação. O download é conferido pelo SHA-256 do GitHub. Tudo fica em **Configurações → Atualizações**, com a opção **Atualizar automaticamente**.
- **Escolha suas indicações:** em **Configurações → Indicações**, desligue **Pelo que você assiste** e marque você mesmo Netflix, Disney+, HBO Max ou Globoplay.
- **Seu aparelho no topo:** o chip mostra o que está rodando o Onix, como *Google TV · Philips*, *Android TV*, *Fire TV* ou *Celular Android*.
- **"Conectado" de verdade:** o Onix testa a internet a cada 30 segundos e mostra **Sem internet** quando o Wi-Fi está ligado mas a internet caiu.
- Samsung: aviso de versão nova (a instalação continua pelo computador).

## 13.5.0 beta (outubro de 2026)

- **Indicações do que você assiste:** abra o **Disney+**, a **HBO Max** ou o **Globoplay** mais de 3 vezes pelo Onix e as indicações da semana passam a trazer títulos deles, junto com os da Netflix. As 8 vagas são divididas entre os serviços, e os mais abertos vêm primeiro.
- Ficou uma semana sem abrir um desses apps? Ele sai das indicações sozinho, e elas voltam a ser só da Netflix.
- **Assistir no Disney+, na HBO Max e no Globoplay** abre direto na página do título, como já acontecia com a Netflix. Cada indicação mostra o selo do serviço.
- **Novidades:** nova tela em Configurações com o que mudou em cada versão. Depois de uma atualização, o Onix avisa na tela inicial.
- **TVs Samsung (Tizen):** versão beta para TVs de 2022 em diante. Veja o [tutorial](README.md#tvs-samsung-tizen).
- A contagem de aberturas fica só na TV: nada sai do aparelho.

## 13.4.0 beta (outubro de 2026)

- Primeira versão pública do Onix.
- Indicações da semana da Netflix Brasil, que abrem direto no título.
- Notícias de games (Adrenaline e Eurogamer.pt) com leitor e QR code.
- Quatro jogos: Pong, Pinball, Tijolos e Cobrinha.
- Entradas HDMI com banners editáveis, tela de descanso espacial, botão Atualizar e botão Desligar TV.
