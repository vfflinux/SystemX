#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$ROOT_DIR/build"

mkdir -p "$BUILD_DIR"
find "$ROOT_DIR/src" -name "*.java" > "$BUILD_DIR/sources.txt"

echo "[SystemX] Compilando backend Java..."
javac -encoding UTF-8 -d "$BUILD_DIR" @"$BUILD_DIR/sources.txt"

PORT="${1:-8080}"
echo "[SystemX] Iniciando servidor web na porta $PORT..."
exec java -cp "$BUILD_DIR" br.com.systemx.biblioteca.Main "$PORT"
