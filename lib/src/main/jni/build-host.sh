#!/bin/sh
# 容器为 aarch64，NDK 自带的 ndk-build/clang 为 x86_64 二进制无法运行，
# 故用宿主机 clang + NDK sysroot 交叉编译 libva++.so 到 jniLibs/<abi>/。
set -e
NDK=${NDK:-/root/android/sdk/ndk/26.1.10909125}
SYS=$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot
CLANG_LIB=$NDK/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/17
JNI=$(cd "$(dirname "$0")" && pwd)
ABI=${1:-armeabi-v7a}
OUT=$(cd "$JNI/.." && pwd)/jniLibs
BUILD=/tmp/va-native/$ABI

case "$ABI" in
  armeabi-v7a) TRIPLE=armv7a-linux-androideabi21; LIBDIR=$SYS/usr/lib/arm-linux-androideabi ;;
  arm64-v8a)   TRIPLE=aarch64-linux-android21;  LIBDIR=$SYS/usr/lib/aarch64-linux-android ;;
  x86)         TRIPLE=i686-linux-android21;      LIBDIR=$SYS/usr/lib/i686-linux-android ;;
  *) echo "unsupported abi $ABI"; exit 1 ;;
esac

CC="clang --target=$TRIPLE --sysroot=$SYS"
CFLAGS_COMMON="-fPIC -O2 -D__ANDROID_API__=21 -DANDROID -stdlib=libc++"
INC="-I$JNI -I$JNI/fb/include"

rm -rf "$BUILD" && mkdir -p "$BUILD"
cd "$BUILD"

FB_SRC="assert.cpp jni/ByteBuffer.cpp jni/Countable.cpp jni/Environment.cpp jni/Exceptions.cpp jni/fbjni.cpp jni/Hybrid.cpp jni/jni_helpers.cpp jni/LocalString.cpp jni/OnLoad.cpp jni/References.cpp jni/WeakReference.cpp log.cpp lyra/lyra.cpp"
for f in $FB_SRC; do
  obj=$(echo "$f" | tr '/' '_').o
  $CC $CFLAGS_COMMON $INC -DLOG_TAG='"libfb"' -DDISABLE_CPUCAP -DDISABLE_XPLAT \
      -fexceptions -frtti -std=gnu++11 -w -c "$JNI/fb/$f" -o "$obj"
done

VA_CPP="Jni/VAJni.cpp Foundation/IOUniformer.cpp Foundation/VMPatch.cpp Foundation/SymbolFinder.cpp Foundation/Path.cpp Foundation/SandboxFs.cpp Substrate/SubstrateDebug.cpp Substrate/SubstrateHook.cpp Substrate/SubstratePosixMemory.cpp"
for f in $VA_CPP; do
  obj=$(echo "$f" | tr '/' '_').o
  $CC $CFLAGS_COMMON $INC -DLOG_TAG='"VA++"' -w \
      -fexceptions -frtti -std=gnu++11 -c "$JNI/$f" -o "$obj"
done
$CC $CFLAGS_COMMON -c "$JNI/Substrate/hde64.c" -o Substrate_hde64.c.o -w

$CC -shared -fuse-ld=lld -rtlib=compiler-rt -resource-dir "$CLANG_LIB" \
    -o libva++.so *.o -llog -ldl -landroid \
    "$LIBDIR/libc++_static.a" "$LIBDIR/libc++abi.a"

mkdir -p "$OUT/$ABI" && cp libva++.so "$OUT/$ABI/"
echo "built $OUT/$ABI/libva++.so"
