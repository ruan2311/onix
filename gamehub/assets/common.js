'use strict';
/* Código comum do GameHub (hub.html e menu.html): traduções, elementos, navegação pelo
   controle remoto, camadas, avisos, consoles e o gerador de QR. */

/* ---------------- Idioma ----------------
   Os textos são escritos em português; t() devolve a tradução do ES quando o idioma é espanhol. */
let LANG = 'pt';
const ES = {
  'Início': 'Inicio', 'Adicionar jogos': 'Agregar juegos', 'Configurações': 'Configuración',
  'Continuar jogando': 'Seguir jugando', 'Continuar': 'Continuar', 'Jogar': 'Jugar', 'Jogar do começo': 'Jugar desde el inicio',
  'Consoles': 'Consolas', 'Favoritos': 'Favoritos', 'Adicionados recentemente': 'Agregados recientemente',
  'Jogados recentemente': 'Jugados recientemente', 'Nintendinho': 'Nintendo (NES)',
  'jogo': 'juego', 'jogos': 'juegos', 'Nenhum jogo': 'Ningún juego',
  'Sua biblioteca está vazia': 'Tu biblioteca está vacía',
  'Mande seus jogos pelo celular: abra Adicionar jogos e leia o QR code com a câmera.': 'Manda tus juegos desde el celular: abre Agregar juegos y lee el código QR con la cámara.',
  'Nenhum jogo liberado ainda': 'Todavía no hay juegos permitidos',
  'Peça para um adulto liberar jogos no GameHub (no jogo, Liberar no Modo Infantil).': 'Pide a un adulto que permita juegos en GameHub (en el juego, Permitir en Modo Infantil).',
  'Sem controle': 'Sin control', '{n} livres': '{n} libres', 'controle': 'control', 'controles': 'controles',
  'Conecte um controle': 'Conecta un control',
  'Para jogar de verdade, use um controle Bluetooth (Xbox, PlayStation, 8BitDo…). O controle remoto da TV também joga, mas só com poucos botões.': 'Para jugar de verdad, usa un control Bluetooth (Xbox, PlayStation, 8BitDo…). El control remoto de la TV también juega, pero con pocos botones.',
  'Conectar controle': 'Conectar control', 'Jogar com o controle remoto': 'Jugar con el control remoto',
  'Controle conectado': 'Control conectado', 'Nenhum controle conectado': 'Ningún control conectado',
  'Ordenar': 'Ordenar', 'A–Z': 'A–Z', 'Recentes': 'Recientes', 'Mais jogados': 'Más jugados',
  'Estados salvos': 'Estados guardados', 'Favorito': 'Favorito', 'Tirar dos favoritos': 'Quitar de favoritos',
  'Trapaças': 'Trucos', 'Configurações do jogo': 'Configuración del juego', 'Liberar no Modo Infantil': 'Permitir en Modo Infantil',
  'Renomear': 'Renombrar', 'Apagar jogo': 'Borrar juego', 'Liberado': 'Permitido', 'Bloqueado': 'Bloqueado',
  'Jogado por {t}': 'Jugado por {t}', 'Ainda não jogado': 'Todavía no jugado', 'Onde parou': 'Donde lo dejaste',
  'Espaço {n}': 'Espacio {n}', 'Vazio': 'Vacío', 'Carregar': 'Cargar', 'Apagar': 'Borrar', 'Salvar aqui': 'Guardar aquí',
  'Apagar {n}?': '¿Borrar {n}?', 'O jogo, os saves e os estados salvos dele saem da TV. Não dá para desfazer.': 'El juego, sus partidas y estados guardados salen de la TV. No se puede deshacer.',
  'Cancelar': 'Cancelar', 'Jogo apagado': 'Juego borrado', 'Nome do jogo': 'Nombre del juego', 'Salvar': 'Guardar',
  'agora': 'ahora', 'há {n} min': 'hace {n} min', 'há {n} h': 'hace {n} h', 'ontem': 'ayer', 'há {n} dias': 'hace {n} días',
  'Mande jogos pelo celular': 'Manda juegos desde el celular',
  'Aponte a câmera do celular para o QR code. A página abre no navegador, sem instalar nada; escolha os arquivos e eles vêm para a TV.': 'Apunta la cámara del celular al código QR. La página se abre en el navegador, sin instalar nada; elige los archivos y llegan a la TV.',
  'O celular precisa estar no mesmo Wi-Fi da TV.': 'El celular tiene que estar en el mismo Wi-Fi que la TV.',
  'Ou digite no navegador: {u}': 'O escribe en el navegador: {u}', 'Código para quem digitar o endereço:': 'Código para quien escriba la dirección:',
  'Sem rede: conecte a TV ao Wi-Fi para mandar jogos.': 'Sin red: conecta la TV al Wi-Fi para mandar juegos.',
  'Formatos: .nes, .sfc, .smc, .gba, .md, .gen, .bin e .zip': 'Formatos: .nes, .sfc, .smc, .gba, .md, .gen, .bin y .zip',
  'Use só jogos que você tem o direito de usar. Nenhum jogo vem com o GameHub.': 'Usa solo juegos que tengas derecho a usar. Ningún juego viene con GameHub.',
  'Recebendo': 'Recibiendo', 'Pronto': 'Listo', 'Já estava na biblioteca': 'Ya estaba en la biblioteca', 'Aguardando arquivos…': 'Esperando archivos…',
  'Armazenamento': 'Almacenamiento', 'Jogos': 'Juegos', 'Livre para jogos': 'Libre para juegos', 'Reservado para a TV': 'Reservado para la TV',
  'O GameHub sempre deixa livre para o sistema 10% da memória desta TV (no mínimo 768 MB e no máximo 2 GB).': 'GameHub siempre deja libre para el sistema el 10% de la memoria de esta TV (mínimo 768 MB y máximo 2 GB).',
  'Imagem': 'Imagen', 'Som': 'Sonido', 'Jogo': 'Juego', 'Controles': 'Controles', 'Sobre': 'Acerca de',
  'Filtro': 'Filtro', 'Nítido': 'Nítido', 'Pixel puro': 'Píxel puro', 'Suave': 'Suave', 'CRT (TV antiga)': 'CRT (TV antigua)', 'LCD (portátil)': 'LCD (portátil)',
  'Tamanho': 'Tamaño', 'Ajustar à tela': 'Ajustar a la pantalla', 'Pixel perfeito': 'Píxel perfecto', 'Esticar': 'Estirar',
  'Proporção': 'Proporción', 'Original': 'Original', 'Pixel quadrado': 'Píxel cuadrado',
  'Mostrar FPS': 'Mostrar FPS', 'Volume': 'Volumen', 'Atraso do som': 'Retraso del sonido',
  'Menos atraso pode estalar em TVs mais lentas.': 'Menos retraso puede chasquear en TVs más lentas.',
  'Salvar onde parou ao sair': 'Guardar donde lo dejaste al salir',
  'Ao sair do jogo, ele guarda o ponto exato. Continuar jogando volta dali.': 'Al salir del juego, guarda el punto exacto. Seguir jugando vuelve desde ahí.',
  'Rebobinar (segure L2)': 'Rebobinar (mantén L2)', 'Usa mais memória e processador.': 'Usa más memoria y procesador.',
  'Velocidade do avançar (segure R2)': 'Velocidad de avance (mantén R2)',
  'Run-ahead (menos atraso)': 'Run-ahead (menos retraso)', 'Desligado': 'Apagado', 'Ligado': 'Encendido', '1 quadro': '1 cuadro', '2 quadros': '2 cuadros',
  'Adianta o jogo por baixo dos panos para os botões responderem mais rápido. Pesa o dobro no processador.': 'Adelanta el juego por debajo para que los botones respondan más rápido. Pesa el doble en el procesador.',
  'Velocidade do turbo': 'Velocidad del turbo', 'Botões com turbo': 'Botones con turbo', 'Nenhum': 'Ninguno',
  'Analógico como direcional': 'Analógico como direccional', 'Zona morta do analógico': 'Zona muerta del analógico',
  'Escolha a pasta onde estão os seus jogos. O GameHub procura também nas subpastas e copia os de NES, Super Nintendo, Game Boy Advance e Mega Drive.': 'Elige la carpeta donde están tus juegos. GameHub también busca en las subcarpetas y copia los de NES, Super Nintendo, Game Boy Advance y Mega Drive.',
  'Reservado para o sistema': 'Reservado para el sistema', 'O GameHub sempre deixa livre para o sistema 10% da memória do aparelho (no mínimo 768 MB e no máximo 2 GB).': 'GameHub siempre deja libre para el sistema el 10% de la memoria del dispositivo (mínimo 768 MB y máximo 2 GB).',
  'Controles na tela': 'Controles en pantalla', 'Posição, tamanho e transparência dos botões de toque.': 'Posición, tamaño y transparencia de los botones táctiles.', 'Arraste os botões e mude o tamanho.': 'Arrastra los botones y cambia el tamaño.',
  'Abra Adicionar jogos e escolha os arquivos dos seus jogos.': 'Abre Agregar juegos y elige los archivos de tus juegos.',
  'Escolha os arquivos dos seus jogos no app Arquivos. O GameHub guarda uma cópia aqui e pega a capa e o nome sozinho.': 'Elige los archivos de tus juegos en la app Archivos. GameHub guarda una copia aquí y busca la portada y el nombre solo.',
  'O jogo, os saves e os estados salvos dele saem deste aparelho. Não dá para desfazer.': 'El juego, sus partidas y estados guardados salen de este aparato. No se puede deshacer.',
  'O espaço é o que o iPhone libera para o GameHub. Faça backup dos saves de vez em quando.': 'El espacio es el que el iPhone libera para GameHub. Haz copia de tus partidas de vez en cuando.',
  'Backup dos saves': 'Copia de las partidas', 'Exportar saves': 'Exportar partidas', 'Importar saves': 'Importar partidas',
  'Saves, estados salvos e configurações, sem os jogos.': 'Partidas, estados guardados y configuración, sin los juegos.',
  'Traz de volta um backup. Os jogos que faltarem voltam ao adicionar o arquivo deles.': 'Recupera una copia. Los juegos que falten vuelven al agregar su archivo.',
  'Preparando o backup…': 'Preparando la copia…', 'Backup pronto': 'Copia lista',
  'Guarde o arquivo ({s}) no app Arquivos ou no iCloud.': 'Guarda el archivo ({s}) en la app Archivos o en iCloud.',
  'Salvar arquivo': 'Guardar archivo', 'Backup restaurado': 'Copia restaurada',
  'Esse arquivo não é um backup do GameHub.': 'Ese archivo no es una copia de GameHub.', 'Não deu para fazer o backup.': 'No se pudo hacer la copia.',
  'Não deu para gravar: falta espaço no aparelho?': 'No se pudo guardar: ¿falta espacio en el aparato?',
  'No iPhone: Ajustes → Bluetooth. Depois, é só voltar para o GameHub.': 'En el iPhone: Ajustes → Bluetooth. Luego, solo vuelve a GameHub.',
  'Falta o arquivo deste jogo: adicione ele de novo em Adicionar jogos.': 'Falta el archivo de este juego: agrégalo de nuevo en Agregar juegos.',
  'Escolher pasta': 'Elegir carpeta', 'Escolher arquivos': 'Elegir archivos', '{n} arquivos de jogo encontrados': '{n} archivos de juego encontrados',
  'Nenhum jogo de NES, SNES, GBA ou Mega Drive nessa pasta.': 'No hay juegos de NES, SNES, GBA o Mega Drive en esa carpeta.', '{n} jogos novos na biblioteca': '{n} juegos nuevos en la biblioteca',
  'Atribuir botões': 'Asignar botones', 'OK num botão do console e aperte, no controle, o botão que vai fazer aquilo. Vale só para este jogo.': 'OK en un botón de la consola y presiona, en el control, el botón que hará eso. Vale solo para este juego.',
  'Trocar botões': 'Cambiar botones', 'Aperte o botão no controle…': 'Presiona el botón en el control…',
  'Padrão': 'Predeterminado', 'Restaurar padrão': 'Restaurar predeterminado', 'Desligar o botão': 'Desactivar el botón',
  'Igual ao global': 'Igual que el global', 'Igual ao console': 'Igual que la consola',
  'Opções do emulador': 'Opciones del emulador',
  'As opções deste emulador aparecem depois que você abre um jogo deste console.': 'Las opciones de este emulador aparecen después de abrir un juego de esta consola.', 'Carregando…': 'Cargando…', 'Este emulador não tem opções extras.': 'Este emulador no tiene opciones extra.',
  'Vale para todos os jogos deste console.': 'Vale para todos los juegos de esta consola.', 'Vale só para este jogo.': 'Vale solo para este juego.',
  'Vale para todos os consoles e jogos.': 'Vale para todas las consolas y juegos.',
  'Versão {v}': 'Versión {v}', 'Procurar atualizações': 'Buscar actualizaciones', 'Instalar atualização {v}': 'Instalar actualización {v}',
  'Em dia': 'Al día', 'Procurando…': 'Buscando…', 'Baixando…': 'Descargando…', 'Instalando…': 'Instalando…', 'Sem conexão com o GitHub': 'Sin conexión con GitHub',
  'Créditos e licenças': 'Créditos y licencias', 'Emuladores de código aberto do projeto libretro:': 'Emuladores de código abierto del proyecto libretro:',
  'Nomes dos jogos: libretro-database (No-Intro), CC BY-SA 4.0. Capas: libretro-thumbnails.': 'Nombres de los juegos: libretro-database (No-Intro), CC BY-SA 4.0. Portadas: libretro-thumbnails.',
  'O GameHub é gratuito e de código aberto (GPL-3.0). Nunca terá anúncios nem será vendido.': 'GameHub es gratuito y de código abierto (GPL-3.0). Nunca tendrá anuncios ni se venderá.',
  'Abrir licença': 'Abrir licencia', 'Fechar': 'Cerrar', 'Novo código': 'Nuevo código', 'Descrição (opcional)': 'Descripción (opcional)', 'Código': 'Código',
  'Os códigos (Game Genie, Action Replay, GameShark…) ligam e desligam na hora, pelo menu do jogo.': 'Los códigos (Game Genie, Action Replay, GameShark…) se activan y desactivan al instante, desde el menú del juego.',
  'Nenhum código ainda.': 'Todavía no hay códigos.', 'Adicionar código': 'Agregar código', 'Apagar código': 'Borrar código',
  'Nova versão do GameHub: {v}': 'Nueva versión de GameHub: {v}', 'Atualização pronta': 'Actualización lista',
  'A versão {v} do GameHub já foi baixada. Instalar agora? Seus jogos continuam.': 'La versión {v} de GameHub ya se descargó. ¿Instalar ahora? Tus juegos se mantienen.',
  'Instalar': 'Instalar', 'Depois': 'Después', 'Atualizar': 'Actualizar',
  'Perfil': 'Perfil', 'Modo Infantil': 'Modo Infantil', 'Sair do GameHub': 'Salir de GameHub',
  'Esse tipo de arquivo não é de NES, SNES, GBA ou Mega Drive.': 'Ese tipo de archivo no es de NES, SNES, GBA o Mega Drive.',
  'O .zip não tem nenhum jogo de NES, SNES, GBA ou Mega Drive.': 'El .zip no tiene ningún juego de NES, SNES, GBA o Mega Drive.',
  'O arquivo não parece um jogo desse console.': 'El archivo no parece un juego de esa consola.',
  'Sem espaço seguro no aparelho para esse jogo.': 'No hay espacio seguro en el dispositivo para ese juego.',
  'Não deu para guardar o jogo.': 'No se pudo guardar el juego.', 'Não deu para ler esse arquivo.': 'No se pudo leer ese archivo.',
  'Arquivo grande demais.': 'Archivo demasiado grande.', 'O envio parou no meio.': 'El envío se detuvo a la mitad.', 'Arquivo sem nome.': 'Archivo sin nombre.',
  // menu do jogo
  'Pausado': 'En pausa', 'Salvar estado': 'Guardar estado', 'Carregar estado': 'Cargar estado', 'Avançar rápido': 'Avance rápido',
  'Ajustes deste jogo': 'Ajustes de este juego', 'Reiniciar o jogo': 'Reiniciar el juego', 'Sair do jogo': 'Salir del juego',
  'O que não foi salvo no jogo desde o último save se perde.': 'Lo que no se guardó en el juego desde el último guardado se pierde.',
  'Reiniciar': 'Reiniciar', 'Estado salvo no espaço {n}': 'Estado guardado en el espacio {n}', 'Estado carregado': 'Estado cargado',
  'Não deu para salvar o estado.': 'No se pudo guardar el estado.', 'Não deu para carregar o estado.': 'No se pudo cargar el estado.',
  'O controle desconectou': 'El control se desconectó', 'Reconecte o controle ou continue com outro.': 'Vuelve a conectar el control o sigue con otro.',
  'Não deu para abrir este jogo': 'No se pudo abrir este juego', 'O arquivo pode estar corrompido ou ser de outra versão do console.': 'El archivo puede estar dañado o ser de otra versión de la consola.',
  'Atalhos': 'Atajos', 'Segure R2: acelera · Segure L2: rebobina · Select + Start: este menu': 'Mantén R2: acelera · Mantén L2: rebobina · Select + Start: este menú',
  'Ligar': 'Encender', 'Desligar': 'Apagar',
  'Interface': 'Interfaz', 'Som de navegação': 'Sonido de navegación', 'Os toques ao andar pelos menus.': 'Los toques al moverte por los menús.',
  'Animações': 'Animaciones', 'Transições e efeitos do foco. Desligadas, os menus respondem mais rápido em TVs lentas.': 'Transiciones y efectos del foco. Apagadas, los menús responden más rápido en TVs lentas.',
  'Igual ao Onix': 'Igual que Onix', 'Vale para as telas do GameHub e o menu do jogo.': 'Vale para las pantallas de GameHub y el menú del juego.',
  'Controle {n}': 'Control {n}', 'Definir': 'Definir', 'Ligados agora': 'Conectados ahora', 'Automático': 'Automático',
  'Automático: o primeiro controle que apertar um botão': 'Automático: el primer control que presione un botón',
  'Conectar controle novo': 'Conectar control nuevo', 'Voltar ao automático': 'Volver a automático', 'Definir o controle {n}': 'Definir el control {n}',
  'Aperte qualquer botão no controle que vai ser o controle {n}. Ele fica guardado, mesmo depois de desligar.': 'Presiona cualquier botón en el control que será el control {n}. Queda guardado, incluso después de apagarlo.',
  'Não deu para salvar': 'No se pudo guardar',
  // PC
  'Abra Adicionar jogos e escolha a pasta onde estão os seus jogos.': 'Abre Agregar juegos y elige la carpeta donde están tus juegos.',
  'O jogo sai da biblioteca, com os saves e os estados salvos dele. O arquivo continua na pasta do computador.': 'El juego sale de la biblioteca, con sus partidas y estados guardados. El archivo sigue en la carpeta de la computadora.',
  'Teclado: setas, X = A, Z = B, Enter = Start. Esc abre o menu.': 'Teclado: flechas, X = A, Z = B, Enter = Start. Esc abre el menú.',
  'Teclado': 'Teclado',
  'Tirar': 'Quitar',
  'A pasta saiu da lista. Os jogos dela continuam na biblioteca.': 'La carpeta salió de la lista. Sus juegos siguen en la biblioteca.',
  'Nenhuma pasta ainda.': 'Todavía no hay carpetas.',
  'Escolha a pasta onde estão os seus jogos. O GameHub procura também nas subpastas e, a cada abertura, traz os jogos novos que aparecerem nela.': 'Elige la carpeta donde están tus juegos. GameHub busca también en las subcarpetas y, cada vez que abre, trae los juegos nuevos que aparezcan en ella.',
  'Procurando jogos novos…': 'Buscando juegos nuevos…',
  'Procurar de novo': 'Buscar de nuevo',
  'Pastas de jogos': 'Carpetas de juegos',
  'O GameHub sempre deixa livre para o sistema 10% do disco (no mínimo 768 MB e no máximo 2 GB).': 'GameHub siempre deja libre para el sistema el 10% del disco (mínimo 768 MB y máximo 2 GB).',
  'Setas: direcional · X: A · Z: B · S: X · A: Y · Q/W: L/R · Enter: Start · Shift direito: Select · Tab: acelera · Backspace: rebobina · Esc: menu · F11: tela cheia': 'Flechas: cruceta · X: A · Z: B · S: X · A: Y · Q/W: L/R · Enter: Start · Shift derecho: Select · Tab: acelera · Retroceso: rebobina · Esc: menú · F11: pantalla completa',
  'CRT curvado': 'CRT curvo',
  'Linhas de varredura': 'Líneas de barrido',
  'Brilho': 'Brillo',
  'Cores (saturação)': 'Colores (saturación)',
  'Preto e branco': 'Blanco y negro',
  'Força das linhas (CRT)': 'Fuerza de las líneas (CRT)',
  'Curvatura da tela (CRT curvado)': 'Curvatura de la pantalla (CRT curvo)',
  'Reta': 'Plana',
  'Leve': 'Leve',
  'Média': 'Media',
  'Forte': 'Fuerte',
  'Bem forte': 'Muy fuerte',
  'Bordas escurecidas': 'Bordes oscurecidos',
  'Escurece de leve os cantos da imagem, como numa TV de tubo.': 'Oscurece un poco las esquinas de la imagen, como en una TV de tubo.',
  'Mistura de quadros': 'Mezcla de cuadros',
  'Junta cada quadro com o anterior: some a piscada de sombras e transparências (bom no Game Boy Advance).': 'Junta cada cuadro con el anterior: desaparece el parpadeo de sombras y transparencias (bueno en Game Boy Advance).',
  'Fundo ao redor do jogo': 'Fondo alrededor del juego',
  'Ambiente (a imagem do jogo, borrada)': 'Ambiente (la imagen del juego, desenfocada)',
  'Vidro do GameHub': 'Vidrio de GameHub',
  'Preto': 'Negro',
  'Tela cheia': 'Pantalla completa',
  'Também com F11 ou Alt + Enter.': 'También con F11 o Alt + Enter.',
  'Transições e efeitos do foco.': 'Transiciones y efectos del foco.',
  'Menos atraso pode estalar em computadores mais lentos.': 'Menos retraso puede chasquear en computadoras más lentas.',
  'Tela cheia ao jogar': 'Pantalla completa al jugar',
  'A janela ocupa a tela toda enquanto o jogo está aberto e volta ao normal quando você sai dele.': 'La ventana ocupa toda la pantalla mientras el juego está abierto y vuelve a la normalidad cuando sales.',
  'Atualizar capas': 'Actualizar portadas',
  'Baixa de novo a capa de todos os jogos da biblioteca, inclusive as que faltam.': 'Descarga de nuevo la portada de todos los juegos de la biblioteca, incluso las que faltan.',
  'Atualizando capas…': 'Actualizando portadas…', 'As capas já estão sendo atualizadas.': 'Las portadas ya se están actualizando.',
  '{n} de {t} capas atualizadas': '{n} de {t} portadas actualizadas',
  'Nenhuma capa baixada. Confira a internet.': 'No se descargó ninguna portada. Revisa la conexión a internet.',
};
function t(s, vars) {
  let r = LANG === 'es' && ES[s] ? ES[s] : s;
  if (vars) for (const k in vars) r = r.split('{' + k + '}').join(vars[k]);
  return r;
}

/* ---------------- Elementos ---------------- */
const $ = (s, root) => (root || document).querySelector(s);
const $$ = (s, root) => Array.from((root || document).querySelectorAll(s));
function h(tag, attrs, ...kids) {
  const e = document.createElement(tag);
  if (attrs) for (const k in attrs) {
    const v = attrs[k];
    if (v == null || v === false) continue;
    if (k === 'text') e.textContent = v;
    else if (k === 'html') e.innerHTML = v;
    else if (k === 'act') e._act = v;
    else if (k === 'left') e._left = v;
    else if (k === 'right') e._right = v;
    else if (k === 'style' && typeof v === 'object') Object.assign(e.style, v);
    else e.setAttribute(k, v === true ? '' : v);
  }
  for (const k of kids.flat()) if (k != null && k !== false) e.append(k.nodeType ? k : document.createTextNode(String(k)));
  return e;
}
function svg(markup) {
  const d = document.createElement('div');
  d.innerHTML = markup.trim();
  return d.firstChild;
}
const ICON = {
  play: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5.5v13l11-6.5z"/></svg>',
  pad: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 8h10a5 5 0 0 1 4.6 7l-.6 1.4a2.5 2.5 0 0 1-4.2.6L15 15H9l-1.8 2a2.5 2.5 0 0 1-4.2-.6L2.4 15A5 5 0 0 1 7 8z"/><path d="M7 11v3M5.5 12.5h3"/><circle cx="15.5" cy="11.5" r=".6" fill="currentColor"/><circle cx="17.5" cy="13.5" r=".6" fill="currentColor"/></svg>',
  bt: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 7l10 10-5 5V2l5 5L7 17"/></svg>',
  plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
  gear: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>',
  star: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z"/></svg>',
  starOff: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z"/></svg>',
  save: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 3h11l3 3v15H5z"/><path d="M8 3v5h7V3M8 21v-7h8v7"/></svg>',
  load: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12M7 10l5 5 5-5M5 21h14"/></svg>',
  ff: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 6v12l8.5-6zM12.5 6v12L21 12z"/></svg>',
  code: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 7l-5 5 5 5M16 7l5 5-5 5"/></svg>',
  img: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="14" rx="2"/><path d="M8 21h8"/></svg>',
  sliders: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/></svg>',
  reset: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/></svg>',
  exit: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10"/></svg>',
  trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/></svg>',
  edit: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 20h4L19 9l-4-4L4 16z"/></svg>',
  kid: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M5 21a7 7 0 0 1 14 0"/></svg>',
  info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.5"/></svg>',
  disk: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 15h.01M11 15h6"/></svg>',
  sound: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9v6h4l5 4V5L8 9z"/><path d="M16.5 8.5a5 5 0 0 1 0 7"/></svg>',
  chev: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6"/></svg>',
  full: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5"/></svg>',
  lr: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 7l-5 5 5 5M15 7l5 5-5 5"/></svg>',
};
const icon = name => svg(ICON[name]);
// A marca do GameHub, no traço do Onix: o controle em linha branca fina (grade 48x48, como o Saturno).
const MARK = '<svg viewBox="0 0 32 32" shape-rendering="crispEdges"><path fill="#0a051a" d="M12 7h7v1h-7zM11 8h1v1h-1zM19 8h1v1h-1zM9 9h2v1h-2zM20 9h9v1h-9zM8 10h1v1h-1zM29 10h1v1h-1zM8 11h1v1h-1zM30 11h1v1h-1zM7 12h1v1h-1zM23 12h2v1h-2zM30 12h1v1h-1zM6 13h1v1h-1zM24 13h2v1h-2zM30 13h1v1h-1zM4 14h2v1h-2zM24 14h2v1h-2zM30 14h1v1h-1zM3 15h1v1h-1zM24 15h1v1h-1zM29 15h1v1h-1zM2 16h1v1h-1zM28 16h1v1h-1zM1 17h1v1h-1zM6 17h1v1h-1zM27 17h1v1h-1zM0 18h1v1h-1zM5 18h2v1h-2zM25 18h2v1h-2zM0 19h1v1h-1zM5 19h2v1h-2zM24 19h1v1h-1zM0 20h1v1h-1zM6 20h2v1h-2zM23 20h1v1h-1zM0 21h1v1h-1zM22 21h1v1h-1zM1 22h1v1h-1zM22 22h1v1h-1zM2 23h9v1h-9zM20 23h2v1h-2zM11 24h1v1h-1zM19 24h1v1h-1zM12 25h7v1h-7z"/><path fill="#d6c8ff" d="M12 8h1v1h-1zM14 8h1v1h-1zM11 9h3v1h-3zM10 10h5v1h-5zM16 10h1v1h-1zM9 11h3v1h-3zM13 11h3v1h-3zM8 12h7v1h-7zM16 12h1v1h-1zM7 13h7v1h-7zM15 13h1v1h-1zM7 14h6v1h-6zM7 15h1v1h-1zM9 15h1v1h-1zM8 16h1v1h-1z"/><path fill="#8b6cf0" d="M13 8h1v1h-1zM15 8h2v1h-2zM14 9h4v1h-4zM9 10h1v1h-1zM15 10h1v1h-1zM17 10h2v1h-2zM12 11h1v1h-1zM16 11h2v1h-2zM15 12h1v1h-1zM17 12h2v1h-2zM14 13h1v1h-1zM16 13h2v1h-2zM13 14h4v1h-4zM8 15h1v1h-1zM10 15h3v1h-3zM7 16h1v1h-1zM9 16h2v1h-2zM16 16h1v1h-1zM13 17h1v1h-1zM15 17h1v1h-1zM10 18h3v1h-3zM14 18h1v1h-1zM9 19h1v1h-1zM11 19h1v1h-1z"/><path fill="#5438c4" d="M17 8h2v1h-2zM18 9h2v1h-2zM19 10h2v1h-2zM18 11h2v1h-2zM19 12h2v1h-2zM18 13h2v1h-2zM13 15h1v1h-1zM15 15h1v1h-1zM11 16h5v1h-5zM17 16h2v1h-2zM7 17h3v1h-3zM11 17h2v1h-2zM14 17h1v1h-1zM16 17h2v1h-2zM8 18h2v1h-2zM13 18h1v1h-1zM15 18h2v1h-2zM18 18h1v1h-1zM7 19h2v1h-2zM10 19h1v1h-1zM12 19h4v1h-4zM8 20h7v1h-7z"/><path fill="#2d1a78" d="M21 10h1v1h-1zM20 11h2v1h-2zM21 12h1v1h-1zM17 14h2v1h-2zM14 15h1v1h-1zM16 15h2v1h-2zM19 15h3v1h-3zM23 15h1v1h-1zM19 16h2v1h-2zM22 16h1v1h-1zM10 17h1v1h-1zM18 17h2v1h-2zM21 17h1v1h-1zM7 18h1v1h-1zM17 18h1v1h-1zM19 18h2v1h-2zM16 19h2v1h-2zM15 20h1v1h-1zM21 21h1v1h-1zM18 22h1v1h-1zM20 22h1v1h-1zM15 23h1v1h-1zM19 23h1v1h-1zM12 24h1v1h-1zM14 24h1v1h-1zM16 24h1v1h-1zM18 24h1v1h-1z"/><path fill="#3f6e96" d="M22 10h5v1h-5zM22 11h5v1h-5zM25 12h2v1h-2z"/><path fill="#2a80a1" d="M27 10h2v1h-2zM27 11h3v1h-3zM27 12h2v1h-2z"/><path fill="#160c38" d="M22 12h1v1h-1zM20 13h4v1h-4zM19 14h5v1h-5zM18 15h1v1h-1zM22 15h1v1h-1zM21 16h1v1h-1zM23 16h1v1h-1zM20 17h1v1h-1zM22 17h1v1h-1zM18 19h1v1h-1zM23 19h1v1h-1zM21 20h2v1h-2zM18 21h3v1h-3zM15 22h3v1h-3zM19 22h1v1h-1zM21 22h1v1h-1zM11 23h4v1h-4zM16 23h3v1h-3zM13 24h1v1h-1zM15 24h1v1h-1zM17 24h1v1h-1z"/><path fill="#3de1ff" d="M29 12h1v1h-1zM27 13h3v1h-3zM28 14h2v1h-2zM28 15h1v1h-1z"/><path fill="#64c0eb" d="M26 13h1v1h-1zM26 14h2v1h-2zM25 15h3v1h-3zM24 16h4v1h-4zM23 17h4v1h-4zM22 18h3v1h-3z"/><path fill="#7f3874" d="M6 14h1v1h-1zM4 15h3v1h-3zM3 16h4v1h-4zM3 17h3v1h-3zM3 18h2v1h-2zM4 19h1v1h-1z"/><path fill="#942669" d="M2 17h1v1h-1zM1 18h2v1h-2zM1 19h3v1h-3zM1 20h1v1h-1z"/><path fill="#8b9fd7" d="M21 18h1v1h-1zM19 19h4v1h-4zM17 20h4v1h-4zM17 21h1v1h-1z"/><path fill="#3da0c8" d="M27 19h1v1h-1zM26 20h1v1h-1zM29 21h1v1h-1zM28 22h1v1h-1z"/><path fill="#ff3d9a" d="M2 20h2v1h-2zM1 21h3v1h-3zM2 22h2v1h-2z"/><path fill="#d85eae" d="M4 20h2v1h-2zM4 21h7v1h-7zM4 22h7v1h-7z"/><path fill="#b17fc2" d="M16 20h1v1h-1zM11 21h6v1h-6zM11 22h4v1h-4z"/><path fill="#ffffff" d="M27 20h2v1h-2zM27 21h1v1h-1z"/><path fill="#d2f5ff" d="M28 21h1v1h-1z"/></svg>';

/* ---------------- Consoles ---------------- */
const CONSOLES = [
  { id: 'nes', name: 'Nintendinho', short: 'NES', color: '#ff5a6e' },
  { id: 'snes', name: 'Super Nintendo', short: 'SNES', color: '#a78bfa' },
  { id: 'gba', name: 'Game Boy Advance', short: 'GBA', color: '#38bdf8' },
  { id: 'md', name: 'Mega Drive', short: 'MD', color: '#fbbf24' },
];
const consoleOf = id => CONSOLES.find(c => c.id === id) || CONSOLES[0];
// Botões de cada console, na ordem da tela de trocar botões: [botão do RetroPad, nome no console].
const BUTTONS = {
  nes: [[8, 'A'], [0, 'B'], [2, 'Select'], [3, 'Start']],
  snes: [[8, 'A'], [0, 'B'], [9, 'X'], [1, 'Y'], [10, 'L'], [11, 'R'], [2, 'Select'], [3, 'Start']],
  gba: [[8, 'A'], [0, 'B'], [10, 'L'], [11, 'R'], [2, 'Select'], [3, 'Start']],
  md: [[1, 'A'], [0, 'B'], [8, 'C'], [10, 'X'], [9, 'Y'], [11, 'Z'], [3, 'Start'], [2, 'Mode']],
};
// Desenho simples de cada console (traço), para os cartões.
const CONSOLE_ART = {
  nes: '<svg viewBox="0 0 64 40" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><rect x="4" y="8" width="56" height="26" rx="3"/><path d="M4 18h56M10 26h12M40 24h6M50 24h4"/><rect x="12" y="11" width="22" height="4" rx="1"/></svg>',
  snes: '<svg viewBox="0 0 64 40" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M8 12h48a6 6 0 0 1 6 6v6a6 6 0 0 1-6 6H8a6 6 0 0 1-6-6v-6a6 6 0 0 1 6-6z"/><rect x="18" y="5" width="28" height="9" rx="2"/><circle cx="48" cy="21" r="2.2"/><circle cx="54" cy="21" r="2.2"/><path d="M10 21h8M14 17v8"/></svg>',
  gba: '<svg viewBox="0 0 64 40" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M12 6h40a10 10 0 0 1 10 10v8a10 10 0 0 1-10 10H12A10 10 0 0 1 2 24v-8A10 10 0 0 1 12 6z"/><rect x="20" y="11" width="24" height="18" rx="2"/><path d="M7 20h7M10.5 16.5v7"/><circle cx="52" cy="18" r="2"/><circle cx="56" cy="22" r="2"/></svg>',
  md: '<svg viewBox="0 0 64 40" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M4 14l6-6h44l6 6v18H4z"/><circle cx="24" cy="21" r="7"/><path d="M38 18h16M38 24h16M10 28h6"/></svg>',
};

/* Rótulo de um valor das opções do núcleo: alguns vêm só como "enabled"/"disabled". */
function optLabel(value, label) {
  const raw = { enabled: 'Ligado', disabled: 'Desligado', true: 'Ligado', false: 'Desligado', on: 'Ligado', off: 'Desligado' };
  if ((label == null || label === value) && raw[String(value).toLowerCase()]) return t(raw[String(value).toLowerCase()]);
  return label == null ? String(value) : label;
}

/* ---------------- Formatos ---------------- */
function fmtTime(sec) {
  const m = Math.round((sec || 0) / 60);
  if (m < 60) return m + ' min';
  const hrs = Math.floor(m / 60), r = m % 60;
  return r ? hrs + ' h ' + r + ' min' : hrs + ' h';
}
function ago(ms) {
  const d = (Date.now() - ms) / 1000;
  if (d < 90) return t('agora');
  if (d < 3600) return t('há {n} min', { n: Math.round(d / 60) });
  if (d < 86400) return t('há {n} h', { n: Math.round(d / 3600) });
  if (d < 172800) return t('ontem');
  return t('há {n} dias', { n: Math.round(d / 86400) });
}
function fmtBytes(b) {
  if (b >= 1e9) return (b / 1073741824).toFixed(1).replace('.', LANG === 'es' ? ',' : ',') + ' GB';
  if (b >= 1e6) return Math.round(b / 1048576) + ' MB';
  if (b <= 0) return '0 MB';
  return Math.max(1, Math.round(b / 1024)) + ' KB';
}

/* ---------------- Navegação ----------------
   Cada elemento focável tem a classe "f". As setas andam para o vizinho mais próximo naquela
   direção (só dentro da camada de cima); OK chama el._act; ◀ ▶ num item com _left/_right
   mudam o valor em vez de andar. */
let focused = null;
const layers = [];
function scope() {
  return layers.length ? layers[layers.length - 1].el : document.body;
}
function focusables() {
  return $$('.f', scope()).filter(e => e.offsetParent !== null && !e.closest('[hidden]'));
}
function focus(el, quiet) {
  if (!el) return;
  if (focused) focused.classList.remove('focus');
  focused = el;
  el.classList.add('focus');
  // O destaque "Continuar jogando" aparece inteiro, não só o botão. No celular, com o dedo, a
  // própria pessoa rola: a tela só acompanha o foco quando ela usa setas (controle ou teclado).
  if (!isMobile() || usingKeys) (el.closest('.hero') || el).scrollIntoView({ block: 'nearest', inline: 'nearest' });
  if (!quiet && el._focus) el._focus();
}
function ensureFocus(preferred) {
  const list = focusables();
  if (preferred && list.includes(preferred)) return focus(preferred);
  if (focused && list.includes(focused)) return focus(focused, true);
  focus(list[0]);
}
function move(dir) {
  const list = focusables();
  if (!focused || !list.includes(focused)) return ensureFocus();
  const a = focused.getBoundingClientRect();
  const ax = a.left + a.width / 2, ay = a.top + a.height / 2;
  // Primeiro dentro da mesma área (a página rolável, o topo…): com a página rolada, o que ficou
  // acima da tela está "atrás" das abas e perdia para elas. Só sai da área se nada servir lá.
  const area = e => e.closest('main, nav, header, .layer') || document.body;
  const here = area(focused);
  return pick(list.filter(e => area(e) === here)) || pick(list);
  function pick(cands) {
  let best = null, bestScore = Infinity;
  for (const e of cands) {
    if (e === focused) continue;
    const b = e.getBoundingClientRect();
    const bx = b.left + b.width / 2, by = b.top + b.height / 2;
    const dx = bx - ax, dy = by - ay;
    let main, side;
    if (dir === 'left') { if (b.right > a.left + 2 && dx >= -2) continue; main = -dx; side = Math.abs(dy); }
    else if (dir === 'right') { if (b.left < a.right - 2 && dx <= 2) continue; main = dx; side = Math.abs(dy); }
    else if (dir === 'up') { if (b.bottom > a.top + 2 && dy >= -2) continue; main = -dy; side = Math.abs(dx); }
    else { if (b.top < a.bottom - 2 && dy <= 2) continue; main = dy; side = Math.abs(dx); }
    if (main <= 0) continue;
    // mesma linha/coluna conta muito mais que a distância
    const overlap = dir === 'left' || dir === 'right' ? (b.top < a.bottom && b.bottom > a.top) : (b.left < a.right && b.right > a.left);
    const score = main + side * (overlap ? 0.5 : 3);
    if (score < bestScore) { bestScore = score; best = e; }
  }
  if (cands !== list && !best) return false;
  if (best) { focus(best); sfx('move'); } else sfx('edge');
  return true;
  }
}
/* ---------------- Celular ----------------
   Tocar num item é o mesmo que focar e apertar OK. O contorno do foco só aparece depois de usar
   setas (controle Bluetooth ou teclado). */
let usingKeys = false;
const isMobile = () => document.documentElement.classList.contains('mobile');
function setMobile(on) { document.documentElement.classList.toggle('mobile', !!on); }
/* Mouse: clicar fora da janela aberta (no fundo escuro) fecha ela, como Voltar. Só vale se o clique
   começou e terminou no fundo: arrastar de dentro para fora não fecha. O botão "voltar" do mouse
   (o lateral) também volta. */
let downOnBackdrop = null;
document.addEventListener('pointerdown', e => {
  downOnBackdrop = layers.length && e.target === scope() ? e.target : null;
}, true);
document.addEventListener('mouseup', e => {
  if (e.button === 3) { e.preventDefault(); back(); }
});
document.addEventListener('click', e => {
  if (downOnBackdrop && e.target === downOnBackdrop && e.target === scope()) { downOnBackdrop = null; back(); return; }
  const el = e.target.closest ? e.target.closest('.f') : null;
  if (!el || e.target.tagName === 'INPUT' || !scope().contains(el)) return;
  e.preventDefault();
  if (usingKeys) { usingKeys = false; document.documentElement.classList.remove('keys'); }
  focus(el, true);
  if (el._act) { sfx('ok'); el._act(); }
});
document.addEventListener('keydown', e => {
  if (!usingKeys && /^Arrow/.test(e.key)) { usingKeys = true; document.documentElement.classList.add('keys'); }
}, true);

let onBack = null;   // cada página define o que Voltar faz quando não há camada aberta
document.addEventListener('keydown', e => {
  const k = e.key;
  if (document.activeElement && document.activeElement.tagName === 'INPUT') {
    if (k === 'Enter' || k === 'ArrowDown' || k === 'ArrowUp') { document.activeElement.blur(); e.preventDefault(); }
    return;
  }
  if (k === 'ArrowLeft' && focused && focused._left) { e.preventDefault(); focused._left(); sfx('move'); return; }
  if (k === 'ArrowRight' && focused && focused._right) { e.preventDefault(); focused._right(); sfx('move'); return; }
  const dirs = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' };
  if (dirs[k]) { e.preventDefault(); move(dirs[k]); return; }
  if (k === 'Enter' || k === ' ' || k === 'NumpadEnter') {
    e.preventDefault();
    if (e.repeat) return;
    if (focused && focused._act) { sfx('ok'); focused._act(); }
    return;
  }
  if (k === 'Escape' || k === 'Backspace' || k === 'GoBack' || k === 'BrowserBack') { e.preventDefault(); back(); }
  if (k === 'PageUp' || k === 'PageDown') { e.preventDefault(); move(k === 'PageUp' ? 'left' : 'right'); }
});
function back() {
  sfx('back');
  if (layers.length) { closeLayer(); return; }
  if (onBack) onBack();
}

/* ---------------- Camadas ---------------- */
function openLayer(el, opts) {
  opts = opts || {};
  document.body.append(el);
  layers.push({ el, prev: focused, onClose: opts.onClose });
  ensureFocus(opts.focus);
  return el;
}
function closeLayer() {
  const l = layers.pop();
  if (!l) return;
  l.el.remove();
  if (l.onClose) l.onClose();
  if (l.prev && document.body.contains(l.prev)) focus(l.prev, true);
  else ensureFocus();
}
function closeAllLayers() {
  while (layers.length) closeLayer();
}
/* Pergunta com botões: [[texto, ação, classe]] */
function ask(title, text, buttons) {
  const row = h('div', { style: { display: 'flex', gap: '1rem', marginTop: '.6rem' } });
  const sheet = h('div', { class: 'sheet' }, h('h2', { text: title }), text ? h('p', { text }) : null, row);
  for (const [label, act, cls] of buttons) {
    row.append(h('button', { class: 'f btn ' + (cls || ''), text: label, act: () => { closeLayer(); if (act) act(); } }));
  }
  openLayer(h('div', { class: 'layer' }, sheet));
}
/* Lista de escolhas: [{label, value, sub}], chama pick(value). */
function picker(title, choices, current, pick) {
  const list = h('div', { class: 'list' });
  let cur = null;
  for (const c of choices) {
    const it = h('button', { class: 'f item' + (c.value === current ? ' on' : '') },
      h('span', { class: 'grow' }, c.label, c.sub ? h('span', { class: 'sub', text: c.sub }) : null),
      c.value === current ? h('span', { class: 'val', text: '●' }) : null);
    it._act = () => { closeLayer(); pick(c.value); };
    if (c.value === current) cur = it;
    list.append(it);
  }
  const sheet = h('div', { class: 'sheet' }, h('h2', { text: title }), h('div', { class: 'scroll', style: { maxHeight: '64vh', overflowY: 'auto' } }, list));
  openLayer(h('div', { class: 'layer' }, sheet), { focus: cur });
}

/* ---------------- Avisos ---------------- */
let toastTimer = 0;
function toast(text) {
  $$('.toast').forEach(e => e.remove());
  const el = h('div', { class: 'toast', text });
  document.body.append(el);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.remove(), 2800);
}

/* ---------------- Sons do menu ----------------
   Toques curtos sintetizados (sem arquivos), como os do Onix, mas com timbre de console. */
let sctx = null, soundsOn = true;
/* Som de navegação e animações: "on", "off" ou "onix" (segue o Onix; ligado se ele nunca disse). */
function uiOn(v, onix) {
  return v === 'on' ? true : v === 'off' ? false : onix !== false;
}
function applyUi(sounds, motion) {
  soundsOn = !!sounds;
  document.documentElement.classList.toggle('no-motion', !motion);
  if (!soundsOn && sctx && sctx.state === 'running') sctx.suspend();
  else if (soundsOn && sctx && sctx.state === 'suspended') sctx.resume();
}
function sfx(kind) {
  if (!soundsOn) return;
  try {
    if (!sctx) sctx = new (window.AudioContext || window.webkitAudioContext)({ latencyHint: 'interactive' });
    const notes = { move: [660, .035], ok: [880, .07], back: [440, .06], edge: [220, .05] }[kind];
    if (!notes) return;
    const o = sctx.createOscillator(), g = sctx.createGain(), now = sctx.currentTime;
    o.type = 'square';
    o.frequency.setValueAtTime(notes[0], now);
    if (kind === 'ok') o.frequency.setValueAtTime(notes[0] * 1.5, now + .035);
    g.gain.setValueAtTime(.0001, now);
    g.gain.exponentialRampToValueAtTime(.035, now + .005);
    g.gain.exponentialRampToValueAtTime(.0001, now + notes[1]);
    o.connect(g); g.connect(sctx.destination);
    o.start(now); o.stop(now + notes[1] + .02);
  } catch (_) { }
}

/* ---------------- Eventos do Java ---------------- */
const handlers = {};
window.onHub = (type, data) => { if (handlers[type]) handlers[type](data); };

/* ---------------- Capa ---------------- */
function coverEl(g, opts) {
  opts = opts || {};
  const c = consoleOf(g.console);
  const el = h('div', { class: 'cover' });
  el.append(h('div', { class: 'ph', style: { background: 'linear-gradient(160deg,' + c.color + '55,#140c2c 70%)', color: c.color } },
    svg(CONSOLE_ART[c.id]), opts.title ? h('span', { style: { color: 'var(--ink)' }, text: g.name }) : null));
  if (g.cover) {
    const img = h('img', { src: 'cover/' + g.id + '.jpg?v=' + (g.coverTried || 0), alt: '' });
    img.onerror = () => img.remove();
    el.append(img);
  }
  if (opts.tag !== false) el.append(h('span', { class: 'tag', style: { color: c.color }, text: c.short }));
  if (g.fav) el.append(h('span', { class: 'fav' }, icon('star')));
  return el;
}

/* ---------------- QR code ----------------
   Gerador mínimo (modo byte, correção M, versões 1 a 40), sem bibliotecas: a CSP não
   carrega scripts de fora. Devolve a matriz de módulos (true = escuro). */
const qrMatrix = (() => {
  // Por versão, no nível M: códigos de correção por bloco e número de blocos.
  const ECC_LEN = [0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28];
  const BLOCKS = [0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49];
  const rawModules = v => {
    let n = (16 * v + 128) * v + 64;
    if (v >= 2) { const a = Math.floor(v / 7) + 2; n -= (25 * a - 10) * a - 55; if (v >= 7) n -= 36; }
    return n;
  };
  const dataBytes = v => Math.floor(rawModules(v) / 8) - ECC_LEN[v] * BLOCKS[v];
  const gfMul = (x, y) => {
    let z = 0;
    for (let i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11d); z ^= ((y >>> i) & 1) * x; }
    return z;
  };
  function rsDivisor(deg) {
    const r = new Array(deg).fill(0);
    r[deg - 1] = 1;
    let root = 1;
    for (let i = 0; i < deg; i++) {
      for (let j = 0; j < deg; j++) { r[j] = gfMul(r[j], root); if (j + 1 < deg) r[j] ^= r[j + 1]; }
      root = gfMul(root, 2);
    }
    return r;
  }
  function rsRemainder(data, div) {
    const r = new Array(div.length).fill(0);
    for (const b of data) {
      const f = b ^ r.shift();
      r.push(0);
      div.forEach((c, i) => { r[i] ^= gfMul(c, f); });
    }
    return r;
  }

  return function (text) {
    const bytes = Array.from(new TextEncoder().encode(text));
    let v = 1;
    while (v <= 40 && 4 + (v <= 9 ? 8 : 16) + bytes.length * 8 > dataBytes(v) * 8) v++;
    if (v > 40) return null;

    // Bits: modo byte (0100), tamanho, dados, terminador, preenchimento.
    const bits = [];
    const put = (val, len) => { for (let i = len - 1; i >= 0; i--) bits.push((val >>> i) & 1); };
    put(4, 4); put(bytes.length, v <= 9 ? 8 : 16); bytes.forEach(b => put(b, 8));
    const cap = dataBytes(v) * 8;
    put(0, Math.min(4, cap - bits.length));
    put(0, (8 - bits.length % 8) % 8);
    for (let pad = 0xec; bits.length < cap; pad ^= 0xec ^ 0x11) put(pad, 8);
    const data = [];
    for (let i = 0; i < bits.length; i += 8) data.push(parseInt(bits.slice(i, i + 8).join(''), 2));

    // Divide em blocos, calcula a correção (Reed-Solomon) e intercala.
    const nb = BLOCKS[v], el = ECC_LEN[v], raw = Math.floor(rawModules(v) / 8);
    const nShort = nb - raw % nb, shortLen = Math.floor(raw / nb), div = rsDivisor(el);
    const blocks = [];
    for (let i = 0, k = 0; i < nb; i++) {
      const len = shortLen - el + (i < nShort ? 0 : 1);
      const dat = data.slice(k, k + len);
      k += len;
      const ecc = rsRemainder(dat, div);
      if (i < nShort) dat.push(0);
      blocks.push(dat.concat(ecc));
    }
    const words = [];
    for (let i = 0; i < blocks[0].length; i++) {
      blocks.forEach((b, j) => { if (i !== shortLen - el || j >= nShort) words.push(b[i]); });
    }

    // Padrões fixos: localizadores, sincronismo, alinhamento, formato e versão.
    const size = v * 4 + 17;
    const mod = Array.from({ length: size }, () => new Array(size).fill(false));
    const fixed = Array.from({ length: size }, () => new Array(size).fill(false));
    const set = (x, y, dark) => { mod[y][x] = dark; fixed[y][x] = true; };
    for (let i = 0; i < size; i++) { set(6, i, i % 2 === 0); set(i, 6, i % 2 === 0); }
    for (const [cx, cy] of [[3, 3], [size - 4, 3], [3, size - 4]]) {
      for (let dy = -4; dy <= 4; dy++) for (let dx = -4; dx <= 4; dx++) {
        const x = cx + dx, y = cy + dy, d = Math.max(Math.abs(dx), Math.abs(dy));
        if (x >= 0 && x < size && y >= 0 && y < size) set(x, y, d !== 2 && d !== 4);
      }
    }
    if (v > 1) {
      const n = Math.floor(v / 7) + 2, step = Math.floor((v * 8 + n * 3 + 5) / (n * 4 - 4)) * 2;
      const pos = [6];
      for (let p = size - 7; pos.length < n; p -= step) pos.splice(1, 0, p);
      for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
        if ((i === 0 && j === 0) || (i === 0 && j === n - 1) || (i === n - 1 && j === 0)) continue;
        for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) {
          set(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
        }
      }
    }
    const drawFormat = mask => {
      const d = mask;   // nível M = 00
      let r = d;
      for (let i = 0; i < 10; i++) r = (r << 1) ^ ((r >>> 9) * 0x537);
      const f = ((d << 10) | r) ^ 0x5412, bit = i => ((f >>> i) & 1) === 1;
      for (let i = 0; i <= 5; i++) set(8, i, bit(i));
      set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8));
      for (let i = 9; i < 15; i++) set(14 - i, 8, bit(i));
      for (let i = 0; i < 8; i++) set(size - 1 - i, 8, bit(i));
      for (let i = 8; i < 15; i++) set(8, size - 15 + i, bit(i));
      set(8, size - 8, true);
    };
    drawFormat(0);   // reserva a área; o valor certo vem depois da máscara
    if (v >= 7) {
      let r = v;
      for (let i = 0; i < 12; i++) r = (r << 1) ^ ((r >>> 11) * 0x1f25);
      const f = (v << 12) | r;
      for (let i = 0; i < 18; i++) {
        const dark = ((f >>> i) & 1) === 1, a = size - 11 + i % 3, b = Math.floor(i / 3);
        set(a, b, dark); set(b, a, dark);
      }
    }

    // Dados em zigue-zague, de baixo para cima, em colunas duplas.
    let i = 0;
    for (let right = size - 1; right >= 1; right -= 2) {
      if (right === 6) right = 5;
      for (let vert = 0; vert < size; vert++) {
        for (let j = 0; j < 2; j++) {
          const x = right - j, y = ((right + 1) & 2) === 0 ? size - 1 - vert : vert;
          if (!fixed[y][x] && i < words.length * 8) { mod[y][x] = ((words[i >>> 3] >>> (7 - (i & 7))) & 1) === 1; i++; }
        }
      }
    }

    // Escolhe a máscara com menos penalidade (blocos, faixas e falsos localizadores confundem o celular).
    const MASKS = [
      (x, y) => (x + y) % 2 === 0, (x, y) => y % 2 === 0, x => x % 3 === 0, (x, y) => (x + y) % 3 === 0,
      (x, y) => (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0, (x, y) => x * y % 2 + x * y % 3 === 0,
      (x, y) => (x * y % 2 + x * y % 3) % 2 === 0, (x, y) => ((x + y) % 2 + x * y % 3) % 2 === 0,
    ];
    const applyMask = m => {
      for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) if (!fixed[y][x] && MASKS[m](x, y)) mod[y][x] = !mod[y][x];
    };
    const FINDER = [1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0];
    const linePenalty = line => {
      let p = 0, run = 1;
      for (let k = 1; k <= line.length; k++) {
        if (k < line.length && line[k] === line[k - 1]) run++;
        else { if (run >= 5) p += run - 2; run = 1; }
      }
      for (let k = 0; k + 11 <= line.length; k++) {
        if (FINDER.every((b, t) => line[k + t] === !!b) || FINDER.every((b, t) => line[k + 10 - t] === !!b)) p += 40;
      }
      return p;
    };
    const penalty = () => {
      let p = 0, dark = 0;
      for (let y = 0; y < size; y++) {
        p += linePenalty(mod[y]) + linePenalty(mod.map(r => r[y]));
        for (let x = 0; x < size; x++) {
          if (mod[y][x]) dark++;
          if (x < size - 1 && y < size - 1) {
            const c = mod[y][x];
            if (c === mod[y][x + 1] && c === mod[y + 1][x] && c === mod[y + 1][x + 1]) p += 3;
          }
        }
      }
      return p + (Math.ceil(Math.abs(dark * 20 - size * size * 10) / (size * size)) - 1) * 10;
    };
    let bestMask = 0, bestScore = Infinity;
    for (let m = 0; m < 8; m++) {
      applyMask(m); drawFormat(m);
      const s = penalty();
      if (s < bestScore) { bestScore = s; bestMask = m; }
      applyMask(m);   // desfaz (XOR)
    }
    applyMask(bestMask); drawFormat(bestMask);
    return mod;
  };
})();
// Desenha a matriz como SVG (um único path), com 4 módulos de margem branca.
function qrSvg(text) {
  const m = qrMatrix(text);
  if (!m) return null;
  const n = m.length + 8, NS = 'http://www.w3.org/2000/svg';
  let d = '';
  m.forEach((row, y) => row.forEach((dark, x) => { if (dark) d += 'M' + (x + 4) + ' ' + (y + 4) + 'h1v1h-1z'; }));
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('viewBox', '0 0 ' + n + ' ' + n);
  const path = document.createElementNS(NS, 'path');
  path.setAttribute('d', d);
  path.setAttribute('fill', '#0a0b0e');
  svg.append(path);
  return svg;
}
