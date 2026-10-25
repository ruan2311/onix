#!/usr/bin/env bash
# Build do launcher-philips.apk só com ferramentas do Ubuntu (javac, dx, aapt, zipalign, apksigner).
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd .. && pwd)
SDK_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
OUT=build
KEYSTORE="$HOME/.android/debug.keystore"
APK="$ROOT/launcher-philips.apk"

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/assets"

echo "[1/7] Arte (ícone e banner)"
[ -f res/drawable-xhdpi/banner.png ] || java -Djava.awt.headless=true tools/GenArt.java res

echo "[2/7] Assets"
cp "$ROOT/index.html" "$OUT/assets/index.html"
# Versão única: o android:versionCode manda. O número inicial do versionName acompanha ele
# (13 -> "13.0.0 beta") e o app (menu e Sobre) mostra esse versionName.
CODE=$(sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' AndroidManifest.xml)
sed -i "s|android:versionName=\"[0-9]*|android:versionName=\"$CODE|" AndroidManifest.xml
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
sed -i "s|^const APP_VERSION = '[^']*';|const APP_VERSION = '$VERSION';|" "$ROOT/index.html"
sed -i "s|^const APP_VERSION = '[^']*';|const APP_VERSION = '$VERSION';|" "$OUT/assets/index.html"
grep -q "^const APP_VERSION = '$VERSION';" "$OUT/assets/index.html" || { echo "APP_VERSION não encontrado no index.html" >&2; exit 1; }
# Indicações embutidas para abrir sem internet (geradas por tools/gen_seed.py).
[ -f assets/picks-seed.json ] && cp assets/picks-seed.json "$OUT/assets/"

echo "[3/7] javac"
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath "$SDK_JAR" \
  -d "$OUT/classes" $(find src -name '*.java')

echo "[4/7] dx (dalvik-exchange)"
dalvik-exchange --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes"

echo "[5/7] aapt"
aapt package -f -M AndroidManifest.xml -S res -A "$OUT/assets" -I "$SDK_JAR" -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add -f unsigned.apk classes.dex >/dev/null)

echo "[6/7] zipalign"
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[7/7] apksigner (chave de debug)"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$APK" "$OUT/aligned.apk"
rm -f "$APK.idsig"
apksigner verify --verbose "$APK" | grep -E "Verified|v[123] scheme"
ls -l "$APK"
