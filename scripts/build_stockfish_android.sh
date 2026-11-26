#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
INPUT_SOURCE="${1:-$PROJECT_ROOT/stockfish-src/src}"
TMP_EXTRACT_DIR=""

cleanup() {
    if [ -n "$TMP_EXTRACT_DIR" ] && [ -d "$TMP_EXTRACT_DIR" ]; then
        rm -rf "$TMP_EXTRACT_DIR"
    fi
}
trap cleanup EXIT

resolve_source_dir() {
    local target="$1"
    if [ -d "$target" ]; then
        if [ -f "$target/Makefile" ]; then
            (cd "$target" && pwd)
            return 0
        fi
        if [ -f "$target/src/Makefile" ]; then
            (cd "$target/src" && pwd)
            return 0
        fi
        echo "Error: Makefile not found in directory $target" >&2
        return 1
    fi

    if [ ! -f "$target" ]; then
        echo "Error: Source path or archive not found: $target" >&2
        return 1
    fi

    TMP_EXTRACT_DIR="$(mktemp -d)"
    case "${target,,}" in
        *.tar.gz|*.tgz)
            tar -xzf "$target" -C "$TMP_EXTRACT_DIR"
            ;;
        *.zip)
            unzip -q "$target" -d "$TMP_EXTRACT_DIR"
            ;;
        *.rar)
            if command -v unrar >/dev/null 2>&1; then
                unrar x -inul "$target" "$TMP_EXTRACT_DIR/"
            elif command -v 7z >/dev/null 2>&1; then
                7z x -y -o"$TMP_EXTRACT_DIR" "$target" >/dev/null
            else
                echo "Error: Neither unrar nor 7z is installed to extract .rar archive." >&2
                return 1
            fi
            ;;
        *)
            echo "Error: Unsupported archive format: $target (use .tar.gz, .zip, or .rar)" >&2
            return 1
            ;;
    esac

    local makefile_path
    makefile_path="$(find "$TMP_EXTRACT_DIR" -maxdepth 4 -type f -name Makefile | head -n 1 || true)"
    if [ -z "$makefile_path" ]; then
        echo "Error: Extracted archive does not contain a Makefile." >&2
        return 1
    fi
    dirname "$makefile_path"
}

SF_SRC_DIR="$(resolve_source_dir "$INPUT_SOURCE")"
echo "==> Bot source directory: $SF_SRC_DIR"

find_ndk() {
    local candidates=(
        "${2:-}"
        "${ANDROID_NDK_LATEST_HOME:-}"
        "${ANDROID_NDK_HOME:-}"
        "${ANDROID_NDK_ROOT:-}"
    )
    for dir in "${candidates[@]}"; do
        if [ -n "$dir" ] && [ -d "$dir/toolchains/llvm/prebuilt" ]; then
            echo "$dir"
            return 0
        fi
    done
    for sdk_base in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "/usr/local/lib/android/sdk"; do
        if [ -n "$sdk_base" ] && [ -d "$sdk_base/ndk" ]; then
            local newest
            newest="$(ls -1d "$sdk_base/ndk/"* 2>/dev/null | sort -V | tail -n 1 || true)"
            if [ -n "$newest" ] && [ -d "$newest/toolchains/llvm/prebuilt" ]; then
                echo "$newest"
                return 0
            fi
        fi
    done
    return 1
}

NDK_ROOT="$(find_ndk "$@" || true)"
if [ -z "$NDK_ROOT" ]; then
    echo "Error: Android NDK not found. Set ANDROID_NDK_HOME or pass NDK path as arg 2."
    exit 1
fi
echo "==> Using Android NDK: $NDK_ROOT"

case "$(uname -s)" in
    Linux*)  HOST_TAG="linux-x86_64" ;;
    Darwin*) HOST_TAG="darwin-x86_64" ;;
    *)       echo "Unsupported host OS: $(uname -s)"; exit 1 ;;
esac

TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG/bin"
if [ ! -d "$TOOLCHAIN" ]; then
    echo "Error: LLVM toolchain directory not found at $TOOLCHAIN"
    exit 1
fi

export PATH="$TOOLCHAIN:$PATH"

API_LEVEL=24
JOBS="$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"

pushd "$SF_SRC_DIR" >/dev/null
chmod +x ../scripts/net.sh 2>/dev/null || true
echo "==> Downloading/verifying NNUE network..."
make net || true

build_abi() {
    local ABI="$1"
    local SF_ARCH="$2"
    local CXX_BIN="$3"
    local OUT_DIR="$PROJECT_ROOT/app/src/main/jniLibs/$ABI"

    echo "==> Building bot engine for $ABI ($SF_ARCH, $CXX_BIN)..."
    make clean >/dev/null 2>&1 || true

    make -j"$JOBS" build \
        ARCH="$SF_ARCH" \
        COMP=ndk \
        COMPCXX="$CXX_BIN" \
        EXTRALDFLAGS="-latomic"

    mkdir -p "$OUT_DIR"
    cp stockfish "$OUT_DIR/libstockfish.so"
    "$TOOLCHAIN/llvm-strip" "$OUT_DIR/libstockfish.so"
    test -s "$OUT_DIR/libstockfish.so"
    echo "==> Wrote $OUT_DIR/libstockfish.so ($(du -h "$OUT_DIR/libstockfish.so" | cut -f1))"
}

build_abi "arm64-v8a" "armv8" "aarch64-linux-android${API_LEVEL}-clang++"
build_abi "armeabi-v7a" "armv7-neon" "armv7a-linux-androideabi${API_LEVEL}-clang++"

make clean >/dev/null 2>&1 || true
popd >/dev/null

echo "Done. Both arm64-v8a and armeabi-v7a libstockfish.so binaries are bundled."
