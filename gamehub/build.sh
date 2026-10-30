#!/usr/bin/env bash
# Build do Onix GameHub com as mesmas ferramentas do Onix (javac, dx, aapt, zipalign, apksigner)
# mais o Android NDK para o frontend nativo (jni/retro.c).
#   bash gamehub/build.sh          # gera onix-gamehub.apk na raiz do projeto
# Os núcleos vêm de gamehub/build/cores (bash gamehub/cores.sh compila a partir do código-fonte).
# Só compila neste PC: precisa do NDK e da mesma chave das releases do Onix.
# Sem NDK (no celular), quando só o Java/HTML mudou: NATIVE_FROM=<apk anterior> reaproveita as
# bibliotecas nativas (frontend e núcleos) daquela APK em vez de compilar o jni/.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd .. && pwd)
SDK_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
NDK=${NDK:-$HOME/sdk/android-ndk-r27c}
OUT=build/apk
KEYSTORE="$HOME/.android/debug.keystore"
APK="$ROOT/onix-gamehub.apk"
ABIS="arm64-v8a armeabi-v7a"
CORES="fceumm snes9x mgba gpgx"

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/assets" "$OUT/native"

echo "[1/8] Versão"
CODE=$(sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' AndroidManifest.xml)
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
IFS=. read -r MA MI PA <<< "$VERSION"
[ "$CODE" = "$((MA * 10000 + MI * 100 + PA))" ] || { echo "versionCode $CODE não bate com $VERSION (maior*10000+menor*100+correção)" >&2; exit 1; }
echo "    $VERSION ($CODE)"

echo "[2/8] Arte (ícone e banner)"
[ -f res/drawable-xxxhdpi/icon.png ] || { echo "Falta o ícone: rode python3 gamehub/tools/art.py gamehub/res" >&2; exit 1; }

echo "[3/8] Assets"
cp -r assets/. "$OUT/assets/"

echo "[4/8] Frontend nativo (NDK)"
if [ -n "${NATIVE_FROM:-}" ]; then
  echo "    reaproveitando as bibliotecas de $NATIVE_FROM"
  unzip -q "$NATIVE_FROM" 'lib/*' -d "$OUT/native"
  for abi in $ABIS; do for c in onixretro $CORES; do
    [ -f "$OUT/native/lib/$abi/lib$c.so" ] || { echo "Falta lib/$abi/lib$c.so em $NATIVE_FROM" >&2; exit 1; }
  done; done
else
[ -x "$NDK/ndk-build" ] || { echo "NDK não encontrado em $NDK" >&2; exit 1; }
"$NDK/ndk-build" -s -j6 NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=jni/Android.mk NDK_APPLICATION_MK=jni/Application.mk \
  NDK_OUT=build/ndk-obj NDK_LIBS_OUT=build/ndk-libs
for abi in $ABIS; do
  mkdir -p "$OUT/native/lib/$abi"
  cp build/ndk-libs/$abi/libonixretro.so "$OUT/native/lib/$abi/"
  for c in $CORES; do
    f=build/cores/$abi/lib$c.so
    [ -f "$f" ] || { echo "Falta $f: rode bash gamehub/cores.sh" >&2; exit 1; }
    cp "$f" "$OUT/native/lib/$abi/"
  done
done
fi

echo "[5/8] javac"
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath "$SDK_JAR" \
  -d "$OUT/classes" $(find src -name '*.java')

echo "[6/8] dx (dalvik-exchange)"
dalvik-exchange --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes"

echo "[7/8] aapt + bibliotecas"
aapt package -f -M AndroidManifest.xml -S res -A "$OUT/assets" -I "$SDK_JAR" -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add -f unsigned.apk classes.dex >/dev/null)
(cd "$OUT/native" && aapt add -f ../unsigned.apk $(find lib -name '*.so' | sort) >/dev/null)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[8/8] apksigner (a mesma chave do Onix)"
[ -f "$KEYSTORE" ] || { echo "Sem $KEYSTORE: use a mesma chave das releases do Onix" >&2; exit 1; }
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$APK" "$OUT/aligned.apk"
rm -f "$APK.idsig"
apksigner verify --verbose "$APK" | grep -E "Verified|v[123] scheme"
ls -l "$APK"
