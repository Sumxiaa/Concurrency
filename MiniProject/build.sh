#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSES_DIR="$ROOT_DIR/target/classes"
JAR_FILE="$ROOT_DIR/target/MiniProject-1.0-SNAPSHOT.jar"

mkdir -p "$CLASSES_DIR"
find "$CLASSES_DIR" -type f -name "*.class" -delete
find "$ROOT_DIR/src/main/java" -name "*.java" -print0 | xargs -0 javac --release 21 -d "$CLASSES_DIR"
jar --create --file "$JAR_FILE" --main-class main.MandelbrotExplorer -C "$CLASSES_DIR" .

printf 'Built %s\n' "$JAR_FILE"
