# Onix

Este projeto é editado em duas estações: o celular (Termux) e o PC (WSL + VS Code).

- Ao abrir a sessão, um hook em `.claude/settings.json` roda `git pull --ff-only` sozinho. Se ele falhar (cópias divergiram), avise o usuário em português simples antes de mexer em qualquer arquivo.
- Ao terminar uma tarefa que gerou commits, rode `git push` na hora, sem perguntar.
- Quando o usuário disser "comece o teste do onix gamehub" (ou parecido), siga o roteiro `gamehub/TESTE.md` do branch `gamehub` (`git fetch origin gamehub && git checkout gamehub`). Os APKs de teste já estão em `teste/` nesse branch; não compile nem publique nada pelo celular.
