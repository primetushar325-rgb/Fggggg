#!/usr/bin/env bash
# Compiles the entire Android app module (services, overlay, WebView, Room, DataStore, ViewModels,
# Compose screens) against the mirror stubs in tools/android-stubs, with no Android SDK, no Gradle
# and no network. The shipping build is ./gradlew :app:assembleDebug; this is the offline check.
#
# Note on Compose: the sources are compiled WITHOUT the Compose compiler plugin (it needs the real
# androidx.compose artifacts), so @Composable call-site rules are not enforced here. Every other
# category of error - unknown identifiers, wrong signatures, type mismatches, missing imports - is.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STUB_SRC="$REPO_ROOT/tools/android-stubs/src"
APP_SRC="$REPO_ROOT/app/src/main/kotlin"
CORE_SRC="$REPO_ROOT/core/src/main/kotlin"
BUILD_DIR="${BUILD_DIR:-/tmp/gamesidebar-app-check}"
JAVA_HOME="${JAVA_HOME:-/usr/local/lib/python3.11/dist-packages/jdk4py/java-runtime}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

KOTLIN_HOME="${KOTLIN_HOME:-/tmp/kotlin}"
if [ ! -x "$KOTLIN_HOME/bin/kotlinc" ]; then
  echo "kotlinc not found at $KOTLIN_HOME (set KOTLIN_HOME to a Kotlin distribution)" >&2
  exit 1
fi
KOTLIN_LIB="$KOTLIN_HOME/lib"
if ! ls "$KOTLIN_LIB"/kotlinx-coroutines-core-jvm*.jar >/dev/null 2>&1; then
  echo "kotlinx-coroutines-core-jvm.jar missing from $KOTLIN_LIB" >&2
  exit 1
fi
COROUTINES_CP="$(ls "$KOTLIN_LIB"/kotlinx-coroutines-core-jvm*.jar | head -1)"

kotlinc() {
  set +e
  JAVA_OPTS="-Dfile.encoding=UTF-8" "$KOTLIN_HOME/bin/kotlinc" "$@" 2>&1
  local code=$?
  set -e
  return $code
}

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/stubs" "$BUILD_DIR/core" "$BUILD_DIR/app"

echo "==> Compiling Android API mirror stubs"
kotlinc "$STUB_SRC"/*.kt -classpath "$COROUTINES_CP" -d "$BUILD_DIR/stubs" -nowarn

echo "==> Generating R from res/ (every R.* reference is therefore checked)"
python3 "$REPO_ROOT/tools/gen_r.py" "$REPO_ROOT/app/src/main/res" com.gamesidebar.browser "$BUILD_DIR/generated/R.kt"

echo "==> Compiling :core"
kotlinc "$CORE_SRC" -classpath "$COROUTINES_CP" -d "$BUILD_DIR/core" -nowarn

echo "==> Compiling :app against the stubs"
set +e
JAVA_OPTS="-Dfile.encoding=UTF-8" "$KOTLIN_HOME/bin/kotlinc" \
  "$APP_SRC" "$BUILD_DIR/generated" \
  -classpath "$BUILD_DIR/core:$BUILD_DIR/stubs:$COROUTINES_CP" \
  -d "$BUILD_DIR/app" 2>&1
CODE=$?
set -e
if [ "$CODE" -ne 0 ]; then
  echo ""
  echo "RESULT: FAIL"
  exit 1
fi
echo ""
echo "RESULT: PASS - core and app compile against the Android API mirror"
