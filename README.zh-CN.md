<div align="center">
  
# 💎 Crystall 核心

**基于 [Minestom](https://minestom.net/) 的新一代高性能 Minecraft 服务器核心**

[🇺🇸 English](README.md) | [🇷🇺 Русский](README.ru.md) | [🇨🇳 中文](README.zh-CN.md)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=java)](https://adoptium.net/)
[![Minestom](https://img.shields.io/badge/Minestom-Core-blue.svg?style=for-the-badge)](https://minestom.net/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-数据库-336791.svg?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![Grafana](https://img.shields.io/badge/Grafana-指标监控-F46800.svg?style=for-the-badge&logo=grafana)](https://grafana.com/)

</div>

---

## ✨ 功能特性

Crystall 是一个从零开始构建的定制化 Minecraft 服务器实现，旨在处理海量玩家的同时不牺牲现代生存机制。

*   🌍 **自定义世界生成：** 基于 FastNoiseLite 的地形生成，多维度支持（主世界与下界），1:8 坐标缩放。
*   ⚔️ **战斗与物理系统：** 原版风格的 PvP 机制，自定义方块物理（沙子/砾石掉落），以及流体（水/岩浆）流动。
*   🛡️ **高级反作弊：** 内置加速、飞行与数据包刷屏检测。
*   💰 **经济与社交：** 游戏内货币、领地保护、公会系统及私聊功能。
*   🔌 **插件系统：** 通过将 `.jar` 插件放入 `plugins/` 目录来动态扩展核心。
*   📊 **生产环境就绪：** 使用 PostgreSQL 存储玩家数据，Prometheus 指标导出 (`/metrics`)，并预配了 Docker Compose 栈。
*   🌐 **国际化 (I18n)：** 客户端感知的翻译系统（消息会自动适应玩家客户端的语言设置）。

## 🚀 快速开始

### 运行环境
*   **Java 21** 或更高版本
*   **Docker**（可选，用于部署完整生产环境栈）

### 本地启动 (开发环境)
使用 Gradle 在本地运行应用程序：
```bash
./gradlew :core:run
```

### 生产环境部署 (Docker Compose)
我们提供了一个开箱即用的 Docker Compose 栈，可以一键启动服务器、PostgreSQL 数据库、Prometheus 以及 Grafana。

```bash
docker-compose up -d
```
*   Minecraft 服务器：`localhost:25565`
*   Grafana 仪表盘：`http://localhost:3000` (账号/密码: admin/admin)
*   监控指标 API：`http://localhost:25566/metrics`

## 🧩 编写插件

Crystall 拥有一套隔离的插件加载系统。只需创建一个 Java 项目并实现 `CrystallPlugin` 接口：

```java
import net.myserver.plugin.CrystallPlugin;
import net.myserver.plugin.PluginContext;

public class MyPlugin implements CrystallPlugin {
    @Override
    public void onEnable(PluginContext context) {
        System.out.println("插件已启用!");
    }

    @Override
    public void onDisable() {
        System.out.println("插件已禁用!");
    }
}
```
添加 `META-INF/crystall-plugin.json` 描述文件，构建 `.jar` 包，然后将其放入 `plugins/` 文件夹即可。

## 🧪 压力测试
Crystall 附带了基于无头客户端（Headless bot）的集群测试工具，用于对 TPS 和各项指标进行压力测试。
```bash
cd stress_test
npm install
npm start
```
