#!/usr/bin/env sh
set -eu
GV=9.6.0
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CACHE="${TMPDIR:-/tmp}/sono-gradle-$GV"
ZIP="$CACHE/gradle-$GV-bin.zip"
GRADLE="$CACHE/gradle-$GV/bin/gradle"

if [ -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "Gradle Wrapper da ton tai."
  exit 0
fi
mkdir -p "$CACHE"
if [ ! -x "$GRADLE" ]; then
  echo "[So No] Dang tai Gradle $GV..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "https://services.gradle.org/distributions/gradle-$GV-bin.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$GV-bin.zip"
  else
    echo "Can curl hoac wget" >&2; exit 1
  fi
  unzip -q -o "$ZIP" -d "$CACHE"
fi
"$GRADLE" -p "$ROOT" wrapper --gradle-version "$GV" --distribution-type bin
echo "Xong. Mo thu muc nay bang Android Studio va Sync Gradle."
