#!/usr/bin/env bash
# Monta o GameHub para iPhone (site instalável) a partir das telas (assets/), do jogo (pc/web/) e
# dos núcleos em WebAssembly (pc/build/cores, feitos pelo pc/cores.sh).
#   bash gamehub/ios/build.sh                 # gera em ~/gems/gamehub/app (o site Project Gems)
#   bash gamehub/ios/build.sh /outra/pasta    # gera em outra pasta
set -euo pipefail
cd "$(dirname "$0")"
OUT=${1:-$HOME/gems/gamehub/app}
ASSETS=../assets WEB=../pc/web CORES=../pc/build/cores
for n in fceumm snes9x mgba gpgx; do
  [ -f "$CORES/$n.wasm" ] || { echo "Falta $CORES/$n.wasm: rode bash gamehub/pc/cores.sh" >&2; exit 1; }
done
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' ../AndroidManifest.xml)

rm -rf "$OUT"
mkdir -p "$OUT/cores" "$OUT/db" "$OUT/licenses"
cp index.html game.html edit.html ios.css touch.js shell.js manifest.webmanifest icon-180.png icon-512.png "$OUT/"
cp "$ASSETS"/hub.html "$ASSETS"/menu.html "$ASSETS"/common.js "$ASSETS"/common.css "$OUT/"
cp "$ASSETS"/db/*.tsv "$OUT/db/"
cp "$ASSETS"/licenses/*.txt "$OUT/licenses/"
cp "$WEB"/game.js "$WEB"/audio.js "$WEB"/pc.css "$OUT/"
cp "$CORES"/*.js "$CORES"/*.wasm "$OUT/cores/"
sed -i "s/__VERSION__/$VERSION/" "$OUT/shell.js"

# O nome do cache muda quando qualquer arquivo muda: é assim que o iPhone pega a versão nova.
HASH=$(cd "$OUT" && find . -type f | LC_ALL=C sort | xargs sha1sum | sha1sum | cut -c1-12)
FILES=$(cd "$OUT" && { echo ./; find . -type f | LC_ALL=C sort; } | sed 's/.*/"&"/' | paste -sd,)
sed -e "s/__HASH__/$HASH/" -e "s#__FILES__#[$FILES]#" sw.js > "$OUT/sw.js"
echo "GameHub para iPhone $VERSION ($HASH) em $OUT"
du -sh "$OUT"
