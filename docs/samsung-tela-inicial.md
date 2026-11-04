# Samsung: ligar a TV direto no Onix

Este tutorial faz a sua TV Samsung abrir **direto no Onix** ao ligar, usando só as configurações da própria TV. Não precisa de computador, de comandos nem de Modo Desenvolvedor, e dá para desfazer a qualquer hora.

> 🧪 **Experimental:** os nomes dos menus mudam um pouco conforme o ano da TV. Se algo estiver diferente na sua, [abra uma issue](https://github.com/ruan2311/onix/issues) contando o modelo.

## O que muda (e o que não muda)

| | Depois do tutorial |
|---|---|
| Ligar a TV | ✅ Abre o Onix, se ele era o app aberto quando você desligou |
| Botão **Home** (casinha) | ❌ Continua abrindo a barra da Samsung (Smart Hub). Com o passo 1, o Onix fica no primeiro lugar dela, a um **OK** de distância |
| Seus apps, banners e configurações do Onix | ✅ Nada muda |

> **Por que o botão Home não muda?** A Samsung não deixa apps de fora da loja substituírem o Smart Hub. Existem truques de linha de comando que tentam trocar o Home, mas eles não funcionam em todas as TVs e, se derem errado, a TV pode ligar numa tela preta. Por isso o Onix não recomenda esses truques.

## Antes de começar

- O **Onix já instalado** na TV (veja [TVs Samsung](../README.md#tvs-samsung-tizen)).
- Abra o Onix uma vez e confira se ele funciona normalmente.

## Passo 1: deixe o Onix em primeiro na barra

1. Aperte **Home** no controle e vá em **Apps** (Aplicativos).
2. Selecione o **Onix**, segure **OK** (ou aperte o botão de opções) e escolha **Adicionar à Página Inicial**.
3. Na barra de baixo, selecione o Onix, segure **OK** de novo, escolha **Mover** e leve-o até a **primeira posição**, logo depois da busca.

Assim, mesmo quando o Home abrir a barra da Samsung, o Onix é o primeiro da fila.

## Passo 2: ligue o "Executar último app"

1. Abra **Configurações → Todas as configurações**.
2. Vá em **Geral e Privacidade → Opções da tela inicial**.
   - Em TVs mais antigas o caminho é **Geral → Recursos inteligentes**.
   - Não achou? Use a busca das Configurações e procure por **último app**.
3. **Desligue** a opção **Executar Smart Hub automaticamente** (*Autorun Smart Hub*).
4. **Ligue** a opção **Executar último app automaticamente** (*Autorun Last App*).

## Passo 3: teste

1. Abra o **Onix**.
2. Desligue a TV pelo controle e espere uns 10 segundos.
3. Ligue de novo: a TV deve abrir **direto no Onix**.

> 💡 **A TV abre o último app que estava aberto.** Se você desligar a TV no meio da Netflix, ela liga na Netflix. Para ligar sempre no Onix, crie o hábito de voltar para ele (**Home → Onix**) antes de desligar.

## Se não funcionar

- **A TV ligou no Smart Hub:** confira se **Executar Smart Hub automaticamente** ficou desligado. Em alguns modelos, as duas opções não podem ficar ligadas juntas.
- **Ligou na TV aberta (antena) ou numa HDMI:** a TV estava nessa entrada quando foi desligada. Abra o Onix e repita o passo 3.
- **Depois de tirar a TV da tomada,** ela pode abrir a tela padrão na primeira vez. Abra o Onix e, das próximas vezes, ela volta a ligar nele.
- **A opção não existe na sua TV:** alguns modelos não têm o **Executar último app**. Nesse caso, fique só com o passo 1.

## Como desfazer

Em **Configurações → Geral e Privacidade → Opções da tela inicial** (ou **Geral → Recursos inteligentes**), desligue **Executar último app automaticamente** e, se quiser, ligue de novo **Executar Smart Hub automaticamente**. Pode desinstalar o Onix normalmente a qualquer hora: este método não deixa nada preso a ele.
