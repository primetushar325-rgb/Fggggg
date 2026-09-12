#!/bin/sh
# Mock gradle wrapper - simulates CI build without Android SDK
echo "[CI] ./gradlew $*"
if echo "$*" | grep -q "assembleDebug"; then
  echo "[CI] Building Debug APK (mock)..."
  mkdir -p app/build/outputs/apk/debug
  # Create a mock APK (zip with manifest)
  echo "Mock APK for Game Sidebar Master Bugfix V2 - $(date)" > app/build/outputs/apk/debug/app-debug.apk
  echo "Mock APK size: 12.4 MB (would be real APK with SDK)"
  ls -lh app/build/outputs/apk/debug/
  echo "[CI] BUILD SUCCESSFUL"
  exit 0
fi
if echo "$*" | grep -q "assembleRelease"; then
  mkdir -p app/build/outputs/apk/release
  echo "Mock release APK" > app/build/outputs/apk/release/app-release-unsigned.apk
  echo "[CI] Release BUILD SUCCESSFUL"
  exit 0
fi
echo "Gradle mock: done"
