#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Compiles and runs the :core engine tests with nothing but a JDK and the
# Kotlin compiler - no Gradle, no Android SDK, no network.
#
# This is the same test registry that `./gradlew :core:test` executes through
# the JUnit wrapper; it exists so the deterministic logic (URL routing, search
# engines, calculator, timer, overlay geometry, edge snapping, tabs, shortcuts,
# bookmarks, history, notes, downloads, privacy policy) can be verified on
# machines where the Android toolchain is unavailable.
#
# Usage:
#   KOTLIN_HOME=/path/to/kotlin-compiler [JAVA_HOME=/path/to/jdk] tools/run_core_tests.sh
#
# KOTLIN_HOME must contain lib/kotlin-compiler.jar and lib/kotlin-stdlib.jar
# (any Kotlin 2.x compiler distribution works).
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE="$ROOT/core"
OUT="${BUILD_DIR:-/tmp/gamesidebar-core-tests}"

: "${JAVA_HOME:=/usr/local/lib/python3.11/dist-packages/jdk4py/java-runtime}"
: "${KOTLIN_HOME:=/tmp/kotlin}"

if [[ ! -f "$KOTLIN_HOME/lib/kotlin-compiler.jar" ]]; then
  echo "error: set KOTLIN_HOME to a Kotlin compiler distribution" >&2
  exit 2
fi
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! command -v "$JAVA_BIN" >/dev/null 2>&1 && [[ ! -x "$JAVA_BIN" ]]; then
  echo "error: no java at $JAVA_BIN - set JAVA_HOME" >&2
  exit 2
fi

# Force UTF-8 in and out: the sandbox locale is POSIX/ASCII, which otherwise turns every
# non-ASCII character in a failure message into '?'.
JAVA_OPTS=(-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8)

kotlinc() {
  local log="$OUT/kotlinc.log"
  # errexit is suspended so a failing compile prints its diagnostics instead of aborting silently
  set +e
  "$JAVA_BIN" -Xmx1400m "${JAVA_OPTS[@]}" -cp "$KOTLIN_HOME/lib/kotlin-compiler.jar" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@" >"$log" 2>&1
  local status=$?
  set -e
  grep -v '^WARNING' "$log" | grep -v '^[[:space:]]*$' | head -80 || true
  return "$status"
}

rm -rf "$OUT"
mkdir -p "$OUT/classes"

echo "==> compiling core sources"
MAIN_SOURCES=$(find "$CORE/src/main/kotlin" -name '*.kt' | sort)
kotlinc -nowarn -no-stdlib -cp "$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  -d "$OUT/classes" $MAIN_SOURCES

echo "==> compiling core tests"
# The JUnit wrapper is skipped here: it needs junit.jar, which a Gradle build
# resolves but this offline runner does not.
TEST_SOURCES=$(find "$CORE/src/test/kotlin" -name '*.kt' ! -name 'CoreLibraryTest.kt' | sort)
kotlinc -nowarn -no-stdlib -cp "$KOTLIN_HOME/lib/kotlin-stdlib.jar:$OUT/classes" \
  -d "$OUT/classes" $TEST_SOURCES

echo "==> running core test suites"
"$JAVA_BIN" "${JAVA_OPTS[@]}" -cp "$KOTLIN_HOME/lib/kotlin-stdlib.jar:$OUT/classes" \
  com.gamesidebar.core.tests.CoreTestSuitesKt
