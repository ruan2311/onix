# Novidades do Onix

As mesmas notas aparecem dentro do app, em **Configurações → Novidades**.

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
