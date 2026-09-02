@echo off
REM Wave-3.2 自检编译脚本（Windows 侧调用 WSL bash）
REM 用法: wave32-build.bat [compile|test|full]
setlocal

set JAVA_HOME=%USERPROFILE%\.local\jdk\jdk-17.0.19+10
set MAVEN_HOME=%USERPROFILE%\.local\apache-maven-3.9.11

REM 切换模式
if "%1"=="compile" goto :compile
if "%1"=="test"    goto :test
if "%1"=="full"    goto :full
if "%1"==""        goto :compile

:compile
wsl.exe -d Ubuntu -- bash -lc "cd /home/guorongxiao/ECOS/ecos_backend && mvn install -pl engine/cognitive-engine/cognitive-engine-api,engine/cognitive-engine/cognitive-engine-impl -am -DskipTests -Dmaven.test.skip=true -q -B 2>&1 | tail -120"
exit /b %ERRORLEVEL%

:test
wsl.exe -d Ubuntu -- bash -lc "cd /home/guorongxiao/ECOS/ecos_backend && mvn test -pl engine/cognitive-engine/cognitive-engine-impl -q -B 2>&1 | tail -120"
exit /b %ERRORLEVEL%

:full
wsl.exe -d Ubuntu -- bash -lc "cd /home/guorongxiao/ECOS/ecos_backend && mvn install -P enterprise -DskipTests -q -B 2>&1 | tail -120 && mvn test -pl engine/cognitive-engine/cognitive-engine-impl -q -B 2>&1 | tail -120"
exit /b %ERRORLEVEL%
