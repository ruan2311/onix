#!/usr/bin/env bash
# Build do Onix Anúncio (app de celular) só com ferramentas do Ubuntu, como o android/build.sh.
#   bash anuncio/build.sh      # gera onix-anuncio.apk na raiz do projeto
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd .. && pwd)
SDK_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
OUT=build
KEYSTORE="$HOME/.android/debug.keystore"
APK="$ROOT/onix-anuncio.apk"

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/assets"

echo "[1/7] Ícone"
[ -f res/mipmap-xxxhdpi/icon.png ] || java -Djava.awt.headless=true ../android/tools/GenArt.java res anuncio

echo "[2/7] Assets"
cp assets/* "$OUT/assets/"
echo "[3/7] javac"
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath "$SDK_JAR" \
  -d "$OUT/classes" $(find src -name '*.java')

echo "[4/7] dx (dalvik-exchange)"
dalvik-exchange --dex --min-sdk-version=23 --output="$OUT/classes.dex" "$OUT/classes"

echo "[5/7] aapt"
aapt package -f -M AndroidManifest.xml -S res -A "$OUT/assets" -I "$SDK_JAR" -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add -f unsigned.apk classes.dex >/dev/null)

echo "[6/7] zipalign"
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[7/7] apksigner (a mesma chave do Onix)"
[ -f "$KEYSTORE" ] || { echo "Sem $KEYSTORE: use a mesma chave das releases do Onix" >&2; exit 1; }
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$APK" "$OUT/aligned.apk"
rm -f "$APK.idsig"
apksigner verify --verbose "$APK" | grep -E "Verified|v[123] scheme"
ls -l "$APK"
