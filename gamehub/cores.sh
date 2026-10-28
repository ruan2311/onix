#!/usr/bin/env bash
# Compila os 4 núcleos libretro do GameHub a partir do código-fonte, nas versões travadas abaixo,
# para arm64-v8a e armeabi-v7a (muitas Google TVs rodam Android de 32 bits).
#   bash gamehub/cores.sh        # gera gamehub/build/cores/<abi>/lib<núcleo>.so
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
