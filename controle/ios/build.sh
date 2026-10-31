#!/usr/bin/env bash
# Monta o Onix Controle para iPhone / iPad (site instalável PWA)
#   bash controle/ios/build.sh                  # gera em ~/gems/onix/controle (o site Project Gems)
#   bash controle/ios/build.sh /outra/pasta     # gera em outra pasta
set -euo pipefail
cd "$(dirname "$0")"
OUT=${1:-$HOME/gems/onix/controle}

rm -rf "$OUT"
mkdir -p "$OUT"

cp index.html controle.css controle.js touch.js manifest.webmanifest icon-180.png icon-512.png "$OUT/"

# Gera o Service Worker com hash dos arquivos para cache e atualização automática
HASH=$(cd "$OUT" && find . -type f | LC_ALL=C sort | xargs sha1sum | sha1sum | cut -c1-12)
FILES=$(cd "$OUT" && { echo ./; find . -type f | LC_ALL=C sort; } | sed 's/.*/"&"/' | paste -sd,)
sed -e "s/__HASH__/$HASH/" -e "s#__FILES__#[$FILES]#" sw.js > "$OUT/sw.js"

echo "Onix Controle para iPhone ($HASH) gerado em $OUT"
du -sh "$OUT"
