@echo off
REM Reusable Jakarta EE 10 build helper for Phase H.
REM Usage: mvnbuild.cmd <pom-relative-path> <logfile> [extra maven args...]
REM Kills stray JVMs (release file locks), sets JDK 21, and runs a clean install
REM skipping tests and gpg.
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot"
set "POM=%~1"
set "LOG=%~2"
shift
shift
taskkill /F /IM java.exe /T > nul 2>&1
call mvn.cmd -B -f "%POM%" -Dmaven.test.skip=true -Dgpg.skip=true %1 %2 %3 %4 %5 %6 clean install 1> "%LOG%" 2>&1
echo DONE_EXIT_%ERRORLEVEL%>> "%LOG%"
endlocal
