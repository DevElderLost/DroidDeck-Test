#!/usr/bin/env bash
set -euo pipefail
: "${NDK:?set NDK to the Android NDK root}"
here=$(cd "$(dirname "$0")" && pwd)
output=$1
case "$(uname -s):$(uname -m)" in
  Darwin:*) host=darwin-x86_64 ;;
  Linux:x86_64) host=linux-x86_64 ;;
  Linux:aarch64|Linux:arm64) host=linux-aarch64 ;;
  *) echo "Unsupported Android NDK host" >&2; exit 1 ;;
esac
toolchain="$NDK/toolchains/llvm/prebuilt/$host/bin"
mkdir -p "$(dirname "$output")"
# API 26 matches DroidDeck's floor; 16 KB alignment also supports newer kernels.
"$toolchain/aarch64-linux-android26-clang" -O2 -Wall -Wextra -Wno-unused-parameter \
  -fPIE -pie -Wl,-z,max-page-size=16384 -Wl,-z,now \
  "$here/directaudio-relay.c" -o "$output" -laaudio -llog -ldl -pthread
"$toolchain/llvm-strip" --strip-all "$output"

# Android assigns executable permissions when extracting native libraries.
chmod 644 "$output"
