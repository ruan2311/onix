#!/usr/bin/env bash
# Compila os 4 primeiros núcleos libretro do GameHub a partir do código-fonte, nas versões travadas
# abaixo, para arm64-v8a e armeabi-v7a (muitas Google TVs rodam Android de 32 bits).
#   bash gamehub/cores.sh        # gera gamehub/build/cores/<abi>/lib<núcleo>.so
#   bash gamehub/cores.sh prontos   # só baixa os núcleos prontos (PS1, Atari e fliperama), sem NDK
# PS1 (PCSX ReARMed), Atari 2600 (Stella 2014), Atari 7800 (ProSystem) e fliperama (FinalBurn Neo)
# vêm prontos do buildbot do libretro, conferidos pelo SHA-256 abaixo (baixados em 03/11/2026).
# Precisa do Android NDK (NDK=~/sdk/android-ndk-r27c) e, para o mGBA, de cmake e ninja no PATH.
# Só é preciso rodar de novo para atualizar um emulador; o build.sh usa o que já estiver pronto.
set -euo pipefail
cd "$(dirname "$0")"
HERE=$(pwd)
NDK=${NDK:-$HOME/sdk/android-ndk-r27c}
SRC=${CORES_SRC:-$HOME/sdk/cores-src}
OUT=$HERE/build/cores
ABIS="arm64-v8a armeabi-v7a"
STRIP=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip

# núcleo  repositório                                         commit
CORES="
snes9x  https://github.com/libretro/snes9x.git              fae2fea08f74180759ef540ee94259213f503480
fceumm  https://github.com/libretro/libretro-fceumm.git      ac0d00a7fc01166f84c7bac01d1084ebfd2b865e
gpgx    https://github.com/libretro/Genesis-Plus-GX.git      60bee4c724859710ce05672239e5b601ad01382e
mgba    https://github.com/libretro/mgba.git                 7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6
"

# núcleo  abi  sha256 do .so
PRONTOS="
pcsx_rearmed arm64-v8a   fc817b195e76e67bfa8f0fb6c2191a45dadcc86b7306aeb44ff95225fe9dd4ea
pcsx_rearmed armeabi-v7a d7bac961d27e9e820ba114f340f8bd417fd259dd472492f62d40c0ee39fc85c6
stella2014   arm64-v8a   6f561690772a8fc15d0a3c37d77f6d5fcd407a14969e68e29cd4c6f586bf21b2
stella2014   armeabi-v7a 7d1f0a5ad37c634ed5bcb270f1620e891e8576ff56b2a5272fb3e00604b1716d
prosystem    arm64-v8a   87335c02fdfb0c45d2a386b5f8d2ee6c220115f69ba9a0b7a9949aa7fb85bf55
prosystem    armeabi-v7a 8b5eeecad6ab2f5c84d7a4908cc3ada5e907bcfddd93aab6e196f067716a6364
fbneo        arm64-v8a   816f6be8c5580332f641032dd163048ba114ce8391115bec201fbb8d68af304a
fbneo        armeabi-v7a 1696f66f57482384a22abb0942977452cad8f66135ff03072320859dfe5f3aba
"
prontos() {
  local tmp; tmp=$(mktemp -d)
  echo "$PRONTOS" | while read -r name abi sum; do
    [ -n "$name" ] || continue
    mkdir -p "$OUT/$abi"
    if ! echo "$sum  $OUT/$abi/lib$name.so" | sha256sum -c --status 2>/dev/null; then
      echo "== $name ($abi, pronto)"
      curl -sfL "https://buildbot.libretro.com/nightly/android/latest/$abi/${name}_libretro_android.so.zip" -o "$tmp/c.zip"
      rm -f "$tmp"/*.so; unzip -q -o "$tmp/c.zip" -d "$tmp"
      cp "$tmp/${name}_libretro_android.so" "$OUT/$abi/lib$name.so"
      # O buildbot só guarda a versão mais nova: se mudou, avisa (confira e troque o SHA-256 acima).
      echo "$sum  $OUT/$abi/lib$name.so" | sha256sum -c --status || echo "   aviso: $name ($abi) mudou no buildbot; confira antes de lançar" >&2
    fi
  done
  rm -rf "$tmp"
}
mkdir -p "$OUT"
prontos
[ "${1:-}" = prontos ] && { ls -l "$OUT"/*/; exit 0; }

[ -x "$NDK/ndk-build" ] || { echo "NDK não encontrado em $NDK" >&2; exit 1; }
mkdir -p "$SRC" "$OUT"

checkout() { # nome repo commit -> pasta
  local dir=$SRC/$1
  [ -d "$dir/.git" ] || git clone -q "$2" "$dir"
  git -C "$dir" fetch -q --depth 1 origin "$3" 2>/dev/null || true
  git -C "$dir" checkout -q "$3"
  echo "$dir"
}

ndk() { # nome pasta-do-jni
  (cd "$2" && "$NDK/ndk-build" -j6 APP_ABI="$ABIS" APP_PLATFORM=android-21 > "$OUT/$1.log" 2>&1) || { tail -20 "$OUT/$1.log"; exit 1; }
  for abi in $ABIS; do
    mkdir -p "$OUT/$abi"
    cp "$(ls "$2"/../libs/$abi/*.so "$2"/libs/$abi/*.so 2>/dev/null | head -1)" "$OUT/$abi/lib$1.so"
  done
}

echo "$CORES" | while read -r name repo commit; do
  [ -n "$name" ] || continue
  echo "== $name ($commit)"
  dir=$(checkout "$name" "$repo" "$commit")
  case $name in
    snes9x) ndk snes9x "$dir/libretro/jni" ;;
    fceumm) ndk fceumm "$dir/jni" ;;
    gpgx)   ndk gpgx "$dir/libretro/jni" ;;
    mgba)
      for abi in $ABIS; do
        b=$dir/build-$abi
        rm -rf "$b"
        cmake -S "$dir" -B "$b" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
          -DANDROID_ABI=$abi -DANDROID_PLATFORM=android-21 -DCMAKE_BUILD_TYPE=Release \
          -DBUILD_LIBRETRO=ON -DBUILD_QT=OFF -DBUILD_SDL=OFF -DBUILD_GL=OFF -DBUILD_GLES2=OFF -DBUILD_GLES3=OFF \
          -DSKIP_LIBRARY=ON -DBUILD_SHARED=OFF -DBUILD_STATIC=OFF -DUSE_FFMPEG=OFF -DUSE_ZLIB=OFF -DUSE_PNG=OFF \
          -DUSE_LIBZIP=OFF -DUSE_MINIZIP=OFF -DUSE_SQLITE3=OFF -DUSE_ELF=OFF -DUSE_LZMA=OFF -DUSE_EDITLINE=OFF \
          -DUSE_DISCORD_RPC=OFF -DUSE_LUA=OFF -DUSE_JSON_C=OFF -DENABLE_SCRIPTING=OFF -DUSE_EPOXY=OFF \
          > "$OUT/mgba-$abi.log" 2>&1
        cmake --build "$b" -j6 >> "$OUT/mgba-$abi.log" 2>&1 || { tail -20 "$OUT/mgba-$abi.log"; exit 1; }
        mkdir -p "$OUT/$abi"
        cp "$(find "$b" -name 'mgba_libretro*.so' | head -1)" "$OUT/$abi/libmgba.so"
      done ;;
  esac
done

for f in "$OUT"/*/*.so; do "$STRIP" --strip-unneeded "$f"; done
ls -l "$OUT"/*/
