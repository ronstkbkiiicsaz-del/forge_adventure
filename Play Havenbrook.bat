@echo off
cd /d "%~dp0"
start "" javaw -jar "%~dp0forge-ascendant\target\forge-ascendant-2.0.16-SNAPSHOT-jar-with-dependencies.jar" --res "%~dp0forge-gui\res\adventure\common\maps"
if errorlevel 1 (
  echo Could not start Havenbrook. Java may be missing.
  pause
)
