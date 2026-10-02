#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_DIR"
. "$PROJECT_DIR/scripts/jdk.sh"
mkdir -p build/classes
find src -type f -name '*.java' | LC_ALL=C sort | sed 's/.*/"&"/' > build/sources.txt
"$JDK_BIN/javac" --release 25 --add-modules javafx.controls -encoding UTF-8 -d build/classes @build/sources.txt
echo 'Compiled Sordland Tree Viewer.'

