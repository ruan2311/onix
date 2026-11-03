#!/usr/bin/env bash
# Compila os núcleos libretro do GameHub para WebAssembly, já ligados ao frontend (core/web.c):
# um módulo por console em pc/build/cores/<núcleo>.js + .wasm. Os 4 da TV têm as mesmas versões
# (a lista CORES do ../cores.sh); os que só existem no PC ficam em PC_CORES, logo abaixo. O
# código-fonte fica em CORES_SRC (padrão ~/sdk/cores-src).
#   bash gamehub/pc/cores.sh            # todos
#   bash gamehub/pc/cores.sh snes9x     # só um
#   LINK_ONLY=1 bash gamehub/pc/cores.sh # só liga de novo (mudou só o core/web.c)
# Precisa do Emscripten (EMSDK, padrão ~/emsdk) e, para o mGBA, de cmake.
set -euo pipefail
cd "$(dirname "$0")"
HERE=$(pwd)
EMSDK=${EMSDK:-$HOME/emsdk}
# shellcheck disable=SC1091
source "$EMSDK/emsdk_env.sh" > /dev/null 2>&1
SRC=${CORES_SRC:-$HOME/sdk/cores-src}
OUT=$HERE/build/cores
LIB=$HERE/build/lib
LOG=$HERE/build/logs
mkdir -p "$SRC" "$OUT" "$LIB" "$LOG"

CORES=$(sed -n '/^CORES="/,/^"$/p' ../cores.sh | sed '1d;$d')
# Só no PC (a TV não tem estes consoles).
PC_CORES="
pcsx_rearmed https://github.com/libretro/pcsx_rearmed.git 7b0e8f8fadfbce3d170d195b9d9eecaabfa08c8e
"
CORES=$(printf '%s\n%s' "$CORES" "$PC_CORES")
ONLY=${1:-}

checkout() { # nome repo commit -> pasta
  local dir=$SRC/$1
  [ -d "$dir/.git" ] || git clone -q "$2" "$dir"
  git -C "$dir" fetch -q --depth 1 origin "$3" 2>/dev/null || true
  git -C "$dir" checkout -q "$3"
  git -C "$dir" submodule update --init --depth 1 -q 2>/dev/null || true
  echo "$dir"
}

# Os Makefiles do libretro, com platform=emscripten, deixam de fora o libretro-common (o RetroArch
# já traz). Aqui o frontend é outro: compila com STATIC_LINKING=0 e junta todos os objetos num .a.
emmake_core() { # nome pasta-do-makefile makefile pasta-do-repositório (os .o podem ficar fora da do makefile)
  local name=$1 dir=$2 mk=$3 repo=$4
  find "$repo" -name '*.o' -delete
  (cd "$dir" && emmake make -f "$mk" platform=emscripten STATIC_LINKING=0 STATIC_LINKING_LINK=0 -j"$(nproc)") > "$LOG/$name.log" 2>&1 || true
  rm -f "$LIB/lib$name.a"
  find "$repo" -name '*.o' -print0 | xargs -0 emar rcs "$LIB/lib$name.a"
}

link_core() { # nome
  local extra=''
  case $1 in pcsx_rearmed) extra='-sUSE_ZLIB=1' ;; esac
  emcc -O3 -std=gnu11 -I../jni -c core/web.c -o "$LIB/web.o"
  # shellcheck disable=SC2086
  em++ -O3 "$LIB/web.o" "$LIB/lib$1.a" $extra -o "$OUT/$1.js" \
    -sMODULARIZE=1 -sEXPORT_NAME=OnixCore -sALLOW_MEMORY_GROWTH=1 -sINITIAL_MEMORY=67108864 -sSTACK_SIZE=1048576 \
    -sENVIRONMENT=web -sFORCE_FILESYSTEM=1 -sEXPORTED_FUNCTIONS=_malloc,_free \
    -sEXPORTED_RUNTIME_METHODS=FS,UTF8ToString,stringToNewUTF8,HEAPU8,HEAPU16,HEAP16 \
    > "$LOG/$1-link.log" 2>&1 || { tail -20 "$LOG/$1-link.log"; exit 1; }
  ls -l "$OUT/$1.wasm"
}

echo "$CORES" | while read -r name repo commit; do
  [ -n "$name" ] || continue
  [ -z "$ONLY" ] || [ "$ONLY" = "$name" ] || continue
  echo "== $name ($commit)"
  if [ -n "${LINK_ONLY:-}" ]; then link_core "$name"; continue; fi
  dir=$(checkout "$name" "$repo" "$commit")
  case $name in
    fceumm) emmake_core fceumm "$dir" Makefile.libretro "$dir" ;;
    snes9x) emmake_core snes9x "$dir/libretro" Makefile "$dir" ;;
    gpgx)   emmake_core gpgx "$dir" Makefile.libretro "$dir" ;;
    pcsx_rearmed) emmake_core pcsx_rearmed "$dir" Makefile.libretro "$dir" ;;
    mgba)
      b=$dir/build-web
      rm -rf "$b"
      emcmake cmake -S "$dir" -B "$b" -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_LIBRETRO=ON -DBUILD_QT=OFF -DBUILD_SDL=OFF -DBUILD_GL=OFF -DBUILD_GLES2=OFF -DBUILD_GLES3=OFF \
        -DSKIP_LIBRARY=ON -DBUILD_SHARED=OFF -DBUILD_STATIC=OFF -DUSE_FFMPEG=OFF -DUSE_ZLIB=OFF -DUSE_PNG=OFF \
        -DUSE_LIBZIP=OFF -DUSE_MINIZIP=OFF -DUSE_SQLITE3=OFF -DUSE_ELF=OFF -DUSE_LZMA=OFF -DUSE_EDITLINE=OFF \
        -DUSE_DISCORD_RPC=OFF -DUSE_LUA=OFF -DUSE_JSON_C=OFF -DENABLE_SCRIPTING=OFF -DUSE_EPOXY=OFF \
        > "$LOG/mgba.log" 2>&1
      cmake --build "$b" -j"$(nproc)" >> "$LOG/mgba.log" 2>&1 || true
      rm -f "$LIB/libmgba.a"
      find "$b" -name '*.o' -print0 | xargs -0 emar rcs "$LIB/libmgba.a" ;;
  esac
  link_core "$name"
done
