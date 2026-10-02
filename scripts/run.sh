#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_DIR"
. "$PROJECT_DIR/scripts/jdk.sh"
"$PROJECT_DIR/scripts/build.sh"
exec "$JDK_BIN/java" --add-modules javafx.controls --enable-native-access=javafx.graphics -Xmx4g -cp build/classes sordland.Launcher "$@"
