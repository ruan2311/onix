# Onix

Este projeto é editado em duas estações: o celular (Termux) e o PC (WSL + VS Code).

- Ao abrir a sessão, um hook em `.claude/settings.json` roda `git pull --ff-only` sozinho. Se ele falhar (cópias divergiram), avise o usuário em português simples antes de mexer em qualquer arquivo.
- Ao terminar uma tarefa que gerou commits, rode `git push` na hora, sem perguntar.
