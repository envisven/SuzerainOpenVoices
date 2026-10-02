#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_DIR"
. "$PROJECT_DIR/scripts/jdk.sh"
"$PROJECT_DIR/scripts/build.sh"
mkdir -p build/test-classes
"$JDK_BIN/javac" --release 25 --add-modules javafx.controls -encoding UTF-8 -cp build/classes -d build/test-classes tests/sordland/VariableVisualChecks.java
exec "$JDK_BIN/java" --add-modules javafx.controls --enable-native-access=javafx.graphics -Xmx4g -cp build/classes:build/test-classes sordland.VariableVisualChecks
