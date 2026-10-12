@echo off
rem Standalone battlefield prototype. Copy of the fat jar, not the live SNAPSHOT.
pushd "%~dp0"
start "" javaw -Xmx512m --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -Dfile.encoding=UTF-8 -cp "%~dp0forge-gui-mobile-dev\target\battlefield-prototype-jar-with-dependencies.jar" forge.app.BattlefieldPrototype
popd
