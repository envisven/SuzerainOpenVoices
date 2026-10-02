#!/bin/sh
set -eu

has_javafx() {
  [ -x "$1/java" ] && [ -x "$1/javac" ] && "$1/java" --list-modules 2>/dev/null | grep -q '^javafx.controls@'
}

JDK_BIN=""

if [ -n "${JAVA_HOME:-}" ] && has_javafx "$JAVA_HOME/bin"; then
  JDK_BIN="$JAVA_HOME/bin"
fi

if [ -z "$JDK_BIN" ] && command -v javac >/dev/null 2>&1; then
  PATH_JDK_BIN=$(dirname "$(command -v javac)")
  if has_javafx "$PATH_JDK_BIN"; then
    JDK_BIN="$PATH_JDK_BIN"
  fi
fi

if [ -z "$JDK_BIN" ] && [ -x /usr/libexec/java_home ]; then
  MAC_JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || true)
  if [ -n "$MAC_JAVA_HOME" ] && has_javafx "$MAC_JAVA_HOME/bin"; then
    JDK_BIN="$MAC_JAVA_HOME/bin"
  fi
fi

if [ -z "$JDK_BIN" ]; then
  echo 'A JDK 25 distribution containing JavaFX is required. Set JAVA_HOME to BellSoft Liberica JDK 25 FULL or put its bin directory on PATH.' >&2
  exit 1
fi

export JDK_BIN
