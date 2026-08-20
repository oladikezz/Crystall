#!/bin/sh
# Modern ZGC Flags optimized for Minestom ultra-low latency (<1ms GC pauses)
JAVA_FLAGS="-Xms4G -Xmx4G -XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -Dfile.encoding=UTF-8"

echo "=============================================="
echo " Starting Crystall Server (Java 21 Generational ZGC) "
echo "=============================================="
exec /app/bin/core $JAVA_FLAGS
