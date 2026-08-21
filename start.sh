#!/bin/bash
# ======================================================================
#            💎 Crystall Pure Core Server v1.0.0
#  Ultra-Low Latency Minecraft Engine with Generational ZGC (Java 25)
# ======================================================================

JAVA_FLAGS="-Xms2G -Xmx4G -XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -Dfile.encoding=UTF-8"

echo "=============================================="
echo " Starting Crystall Server (Java 25 Generational ZGC) "
echo "=============================================="

while true; do
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Запуск сервера Crystall Core..."
    java $JAVA_FLAGS -jar core/build/libs/crystall-core-1.0.0.jar
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Сервер остановлен. Перезапуск через 5 секунд (Ctrl+C для выхода)..."
    sleep 5
done
