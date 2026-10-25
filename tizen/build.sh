#!/usr/bin/env bash
# Build do Onix para Samsung (Tizen). Gera tizen/build/onix/ (o app pronto para assinar) e,
# se o Tizen CLI estiver instalado e houver um perfil de certificado, o Onix.wgt assinado.
#   bash tizen/build.sh                 # só a pasta (e o onix-tizen.zip para a página de Releases)
#   bash tizen/build.sh MeuPerfil       # pasta + Onix.wgt assinado com o perfil do Certificate Manager
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd .. && pwd)
OUT=build/onix
PROFILE=${1:-}

rm -rf build; mkdir -p "$OUT"

# Versão única: a mesma do APK (android:versionName). O config.xml só aceita números (13.4.0).
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$ROOT/android/AndroidManifest.xml")
NUM=$(printf '%s' "$VERSION" | grep -oE '^[0-9]+\.[0-9]+\.[0-9]+')
echo "[1/3] Onix $VERSION (config.xml $NUM)"

echo "[2/3] index.html com a ponte do Tizen"
# Ponte comum (tv/bridge.js) + adaptador do Tizen (tizen/platform.js).
python3 "$ROOT/tv/page.py" "$ROOT" "$OUT" "$VERSION" platform.js
grep -q "const APP_VERSION = '$VERSION';" "$OUT/index.html" || { echo "APP_VERSION não encontrado no index.html" >&2; exit 1; }
sed "s/@VERSION@/$NUM/" config.xml > "$OUT/config.xml"
cp "$ROOT/android/res/drawable-xxxhdpi/icon.png" "$OUT/icon.png"
(cd build && rm -f "$ROOT/onix-tizen.zip" && python3 -c "import shutil; shutil.make_archive('$ROOT/onix-tizen', 'zip', '.', 'onix')")

echo "[3/3] Pacote .wgt"
if [ -z "$PROFILE" ]; then
  echo "Sem perfil de certificado: pasta pronta em tizen/$OUT e onix-tizen.zip na raiz."
  echo "Para gerar o .wgt assinado: bash tizen/build.sh NOME_DO_PERFIL (veja o README)."
  exit 0
fi
TIZEN=$(command -v tizen || echo "$HOME/tizen-studio/tools/ide/bin/tizen")
[ -x "$TIZEN" ] || { echo "Tizen CLI não encontrado (instale o Tizen Studio)." >&2; exit 1; }
"$TIZEN" package -t wgt -s "$PROFILE" -- "$OUT"
mv "$OUT/Onix.wgt" "$ROOT/Onix.wgt"
ls -l "$ROOT/Onix.wgt"
