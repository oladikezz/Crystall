<div align="center">
  
# 💎 Crystall Core

**Next-Generation High-Performance Minecraft Server Core based on [Minestom](https://minestom.net/)**

[🇺🇸 English](README.md) | [🇷🇺 Русский](README.ru.md) | [🇨🇳 中文](README.zh-CN.md)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=java)](https://adoptium.net/)
[![Minestom](https://img.shields.io/badge/Minestom-Core-blue.svg?style=for-the-badge)](https://minestom.net/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-336791.svg?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![Grafana](https://img.shields.io/badge/Grafana-Metrics-F46800.svg?style=for-the-badge&logo=grafana)](https://grafana.com/)

</div>

---

## ✨ Features

Crystall is a custom Minecraft server implementation built from the ground up to handle massive player counts without sacrificing modern survival mechanics.

*   🌍 **Custom World Generation:** FastNoiseLite-based terrain generation, multi-dimensional support (Overworld & Nether) with 1:8 coordinate scaling.
*   ⚔️ **Combat & Physics:** Vanilla-like PvP mechanics, custom block physics (falling sand/gravel), and water/lava fluid flow.
*   🛡️ **Advanced Anti-Cheat:** Built-in speedhack, fly, and packet spam detection.
*   💰 **Economy & Social:** In-game currency, chunk claiming/protection, clans system, and private messaging.
*   🔌 **Plugin System:** Extend the core dynamically by dropping `.jar` plugins into the `plugins/` directory.
*   📊 **Production Ready:** PostgreSQL player data storage, Prometheus metrics exporter (`/metrics`), and pre-configured Docker Compose stack.
*   🌐 **I18n Localization:** Client-aware translations (messages adapt to the player's client language settings).

## 🚀 Getting Started

### Prerequisites
*   **Java 21** or newer
*   **Docker** (Optional, for full production stack)

### Quick Start (Development)
Use Gradle to run the application locally:
```bash
./gradlew :core:run
```

### Production Deployment (Docker Compose)
We provide a ready-to-use Docker Compose stack that spins up the Server, PostgreSQL Database, Prometheus, and Grafana.

```bash
docker-compose up -d
```
*   Minecraft Server: `localhost:25565`
*   Grafana Dashboard: `http://localhost:3000` (admin/admin)
*   Metrics API: `http://localhost:25566/metrics`

## 🧩 Building a Plugin

Crystall features an isolated plugin loading system. Create a Java project and implement the `CrystallPlugin` interface:

```java
import net.myserver.plugin.CrystallPlugin;
import net.myserver.plugin.PluginContext;

public class MyPlugin implements CrystallPlugin {
    @Override
    public void onEnable(PluginContext context) {
        System.out.println("Plugin enabled!");
    }

    @Override
    public void onDisable() {
        System.out.println("Plugin disabled!");
    }
}
```
Add a `META-INF/crystall-plugin.json` descriptor, build the `.jar`, and place it in the `plugins/` folder.

## 🧪 Load Testing
Crystall includes a headless bot swarm for stress-testing metrics and TPS.
```bash
cd stress_test
npm install
npm start
```
