#!/bin/bash
# Packages the current dev build as a Shandalar Ascendant release zip and publishes it on GitHub.
# Usage: tools/make-release.sh <tag> "<one-line summary>"     e.g. tools/make-release.sh v2026.10.10 "Take back, seasons prep"
# Build first (mvn -o install -DskipTests -pl forge-gui-mobile,forge-gui-mobile-dev -am) with Forge closed.
set -euo pipefail
cd "$(dirname "$0")/.."
TAG=${1:?tag}; SUMMARY=${2:-}
REPO=sjones321/forge_adventure
JAR=forge-gui-mobile-dev/target/forge-gui-mobile-dev-2.0.16-SNAPSHOT-jar-with-dependencies.jar

if ! git diff --quiet HEAD -- forge-core forge-game forge-ai forge-gui forge-gui-mobile forge-gui-mobile-dev; then
    echo "Code differs from HEAD; commit or stash first so the release matches its tag." >&2; exit 1
fi
COMMIT=$(git rev-parse HEAD)
git merge-base --is-ancestor "$COMMIT" "origin/$(git rev-parse --abbrev-ref HEAD)" \
    || { echo "Push HEAD first." >&2; exit 1; }
[ -f "$JAR" ] || { echo "No jar; build first." >&2; exit 1; }
LAST_CODE=$(git log -1 --format=%ct -- forge-core forge-game forge-ai forge-gui/src forge-gui-mobile/src forge-gui-mobile-dev)
[ "$(stat -c %Y "$JAR")" -ge "$LAST_CODE" ] || { echo "Jar is older than the last code commit; rebuild." >&2; exit 1; }

STAGE=$(mktemp -d)/ShandalarAscendant
mkdir -p "$STAGE"
git archive "$COMMIT" forge-gui/res | tar -x -C "$STAGE"
mv "$STAGE/forge-gui/res" "$STAGE/res" && rmdir "$STAGE/forge-gui"
cp "$JAR" "$STAGE/forge-adventure.jar"
cp CREDITS.md "$STAGE/"
[ -f LICENSE ] && cp LICENSE "$STAGE/"

OPENS="--add-opens java.desktop/java.beans=ALL-UNNAMED --add-opens java.desktop/javax.swing.border=ALL-UNNAMED --add-opens java.desktop/javax.swing.event=ALL-UNNAMED --add-opens java.desktop/sun.swing=ALL-UNNAMED --add-opens java.desktop/java.awt.image=ALL-UNNAMED --add-opens java.desktop/java.awt.color=ALL-UNNAMED --add-opens java.desktop/sun.awt.image=ALL-UNNAMED --add-opens java.desktop/javax.swing=ALL-UNNAMED --add-opens java.desktop/java.awt=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.desktop/java.awt.font=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED --add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED -Dio.netty.tryReflectionSetAccessible=true -Dfile.encoding=UTF-8"
printf '@echo off\r\nrem Shandalar Ascendant %s (%s). Needs Java 17 or newer.\r\npushd "%%~dp0"\r\nstart "" javaw -Xmx4096m %s -jar "%%~dp0forge-adventure.jar"\r\npopd\r\n' \
    "$TAG" "${COMMIT:0:10}" "$OPENS" > "$STAGE/Play Shandalar Ascendant.cmd"
printf 'Shandalar Ascendant %s\r\nCommit %s on feature/set-start\r\n\r\nUnzip anywhere and double-click "Play Shandalar Ascendant.cmd". Needs Java 17+ (https://adoptium.net).\r\nUpdating: delete the old folder and unzip the new one. Saves and settings live in %%APPDATA%%\\Forge and are kept.\r\nBuilt on Forge (https://github.com/Card-Forge/forge), GPL-3.0. See CREDITS.md.\r\n' \
    "$TAG" "$COMMIT" > "$STAGE/README.txt"

OUT="$(pwd)/target-release"; mkdir -p "$OUT"
ZIP="$OUT/ShandalarAscendant-$TAG.zip"; rm -f "$ZIP"
(cd "$(dirname "$STAGE")" && 7z a -tzip -mx=5 "$(cygpath -w "$ZIP")" ShandalarAscendant > /dev/null)
ls -la "$ZIP"

NOTES="Shandalar Ascendant $TAG — commit ${COMMIT:0:10} on feature/set-start.

${SUMMARY}

Install: unzip, run \"Play Shandalar Ascendant.cmd\" (Java 17+). To update, replace the folder; saves in %APPDATA%\\Forge are kept."
gh release create "$TAG" "$ZIP" --repo "$REPO" --target "$COMMIT" --title "Shandalar Ascendant $TAG" --notes "$NOTES"
rm -rf "$(dirname "$STAGE")"
