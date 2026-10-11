@echo off
rem Standalone hand-feel prototype (no Forge game). Same jar as play-adventure.cmd.
pushd "%~dp0"
start "" javaw -Xmx512m --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -Dfile.encoding=UTF-8 -cp "%~dp0forge-gui-mobile-dev\target\forge-gui-mobile-dev-2.0.16-SNAPSHOT-jar-with-dependencies.jar" forge.app.HandPrototype
popd
