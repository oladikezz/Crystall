@echo off
title Crystall Server 1.0.0 (High Performance Minestom Core)
chcp 65001 > nul
cls

echo ======================================================================
echo             💎 Crystall Pure Core Server v1.0.0
echo   Ultra-Low Latency Minecraft Engine with Generational ZGC (Java 25)
echo ======================================================================
echo.

set JAVA_FLAGS=-Xms2G -Xmx4G -XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -Dfile.encoding=UTF-8

:LOOP
echo [%time%] Запуск сервера Crystall Core...
java %JAVA_FLAGS% -jar core\build\libs\crystall-core-1.0.0.jar

echo.
echo [%time%] Сервер остановлен. Перезапуск через 5 секунд (Нажмите Ctrl+C для выхода)...
timeout /t 5 > nul
goto LOOP
