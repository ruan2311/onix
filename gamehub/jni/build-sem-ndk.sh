#!/usr/bin/env bash
# Compila só o frontend (jni/retro.c) sem o ndk-build: para o celular (Termux, aarch64), onde o NDK
# do Google não roda. Usa o clang do Termux com os arquivos de sistema (sysroot) do NDK r27c, os
# mesmos do ndk-build no PC, e gera build/ndk-libs/<abi>/libonixretro.so.
#   NDK=~/sdk/ndk-dl/android-ndk-r27c bash gamehub/jni/build-sem-ndk.sh
# Para baixar só o necessário: unzip do android-ndk-r27c-linux.zip com
#   toolchains/llvm/prebuilt/linux-x86_64/sysroot/* e .../lib/clang/*/lib/linux/*
set -euo pipefail
cd "$(dirname "$0")/.."
NDK=${NDK:-$HOME/sdk/ndk-dl/android-ndk-r27c}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64
CLANG=${CLANG:-$(command -v clang || echo /data/data/com.termux/files/usr/bin/clang)}
RT=$(ls -d "$TC"/lib/clang/*/lib/linux | head -1)
[ -d "$TC/sysroot" ] || { echo "Sem o sysroot do NDK em $TC" >&2; exit 1; }
for abi in arm64-v8a armeabi-v7a; do
  case $abi in
    arm64-v8a)   target=aarch64-linux-android21; rt=$RT/libclang_rt.builtins-aarch64-android.a; extra="" ;;
    armeabi-v7a) target=armv7a-linux-androideabi21; rt="$RT/libclang_rt.builtins-arm-android.a $RT/arm/libunwind.a"; extra="-mthumb -mfloat-abi=softfp -mfpu=vfpv3-d16" ;;
  esac
  mkdir -p build/ndk-libs/$abi
  "$CLANG" --target=$target --sysroot="$TC/sysroot" $extra -fPIC -shared -O2 -std=c11 -Wall -Wno-unused-variable -Wno-unused-function \
    -DANDROID -ffunction-sections -fdata-sections -fstack-protector-strong -D_FORTIFY_SOURCE=2 \
    -nodefaultlibs -fuse-ld=lld -Wl,--build-id=sha1 -Wl,--no-undefined -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now \
    -Wl,-z,max-page-size=16384 -Wl,--gc-sections -Wl,-soname,libonixretro.so \
    -o build/ndk-libs/$abi/libonixretro.so jni/retro.c -lGLESv2 -lEGL -lOpenSLES -llog -ldl -lz -lm -lc $rt
  "$(dirname "$CLANG")/llvm-strip" --strip-unneeded build/ndk-libs/$abi/libonixretro.so 2>/dev/null || true
  echo "$abi: $(stat -c %s build/ndk-libs/$abi/libonixretro.so) bytes"
done
