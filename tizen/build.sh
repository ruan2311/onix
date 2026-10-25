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
python3 - "$ROOT" "$OUT" "$VERSION" <<'PY'
import json, re, sys
root, out, version = sys.argv[1:]
page = open(f'{root}/index.html', encoding='utf-8').read()
bridge = open(f'{root}/tizen/bridge.js', encoding='utf-8').read()
seed = json.load(open(f'{root}/android/assets/picks-seed.json', encoding='utf-8'))

def sub(pattern, repl, text, count=1):
    new, n = re.subn(pattern, repl, text, count=count)
    if n == 0: sys.exit(f'build: não achei {pattern!r} no index.html')
    return new

page = re.sub(r"^const APP_VERSION = '[^']*';", lambda m: f"const APP_VERSION = '{version}';", page, count=1, flags=re.M)
# CSP: a página continua sem rede própria; liberam-se só os hosts que a ponte usa e os ícones (file:) dos apps da TV.
hosts = ' '.join(['https://apis.justwatch.com', 'https://images.justwatch.com', 'https://itunes.apple.com',
                  'https://*.mzstatic.com', 'https://www.adrenaline.com.br', 'https://www.eurogamer.pt',
                  'https://assetsio.gnwcdn.com'])
page = sub(r"img-src data:;", "img-src data: file:;", page)
page = sub(r"connect-src 'none';", f"connect-src {hosts};", page)
# Chromium das TVs de 2022 (M85) não conhece "inset" nem "aspect-ratio": troca por equivalentes.
def inset(m):
    v = m.group(1).strip()
    return f'top:{v};right:{v};bottom:{v};left:{v}'
page = re.sub(r'(?<![-\w])inset:([^;}"]+)', inset, page)
page = sub(r'</style>', '@supports not (aspect-ratio:1/1){.tile-art{height:0;padding-top:62.5%}'
           '.tile.pick .tile-art{padding-top:150%}}\n</style>', page)
bridge = bridge.replace('/*ONIX_SEED*/null', json.dumps(seed, ensure_ascii=False, separators=(',', ':')), 1)
# A ponte entra antes do script da página (que lê window.AndroidBridge ao abrir).
page = sub(r'<script>', lambda m: '<script>\n' + bridge.replace('</script', '<\\/script') + '\n</script>\n<script>', page)
open(f'{out}/index.html', 'w', encoding='utf-8').write(page)
PY
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
