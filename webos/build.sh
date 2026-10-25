#!/usr/bin/env bash
# Build do Onix para LG (webOS). Gera webos/build/onix/ (o app) e o onix-webos.ipk na raiz,
# pronto para instalar pelo modo desenvolvedor (ares-install ou webOS Dev Manager) ou pelo Homebrew Channel.
#   bash webos/build.sh
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd .. && pwd)
OUT=build/onix

rm -rf build; mkdir -p "$OUT"

# Versão única: a mesma do APK (android:versionName). O appinfo.json só aceita números (13.6.4).
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$ROOT/android/AndroidManifest.xml")
NUM=$(printf '%s' "$VERSION" | grep -oE '^[0-9]+\.[0-9]+\.[0-9]+')
echo "[1/3] Onix $VERSION (appinfo.json $NUM)"

echo "[2/3] index.html com a ponte do webOS"
# Ponte comum (tv/bridge.js) + adaptador da LG (webos/platform.js).
python3 "$ROOT/tv/page.py" "$ROOT" "$OUT" "$VERSION" platform.js
grep -q "const APP_VERSION = '$VERSION';" "$OUT/index.html" || { echo "APP_VERSION não encontrado no index.html" >&2; exit 1; }
sed "s/@VERSION@/$NUM/" appinfo.json > "$OUT/appinfo.json"
java -Djava.awt.headless=true Resize.java "$ROOT/android/res/drawable-xxxhdpi/icon.png" "$OUT/icon.png" 80
java -Djava.awt.headless=true Resize.java "$ROOT/android/res/drawable-xxxhdpi/icon.png" "$OUT/largeIcon.png" 130

echo "[3/3] Pacote .ipk"
python3 ipk.py "$OUT" "$ROOT/onix-webos.ipk"
ls -l "$ROOT/onix-webos.ipk"
