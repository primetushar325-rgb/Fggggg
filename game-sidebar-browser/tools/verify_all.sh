#!/usr/bin/env bash
# One entry point for everything that can be verified without an Android SDK:
#   1. the pure-JVM core engine and its 164 tests
#   2. the whole Android app module compiled against the API mirror in tools/android-stubs
#
# The shipping build is still ./gradlew assembleDebug (needs the Android SDK + network for AGP).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "=============================================="
echo " Game SideBar - offline verification"
echo "=============================================="

bash "$REPO_ROOT/tools/run_core_tests.sh"
echo ""
bash "$REPO_ROOT/tools/check_app.sh"
echo ""
echo "=============================================="
echo " All offline checks passed"
echo "=============================================="
echo ""
echo "Not covered here (needs the Android SDK, a Gradle download and a device):"
echo "  - ./gradlew assembleDebug (AGP, AAPT2, KSP/Room codegen, R8)"
echo "  - the Compose compiler plugin (composable call-site rules)"
echo "  - instrumentation / on-device behaviour of the overlay windows"
