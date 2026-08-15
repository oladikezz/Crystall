<div align="center">
  
# 💎 Crystall Ядро

**Высокопроизводительное ядро Minecraft-сервера нового поколения на базе [Minestom](https://minestom.net/)**

[🇺🇸 English](README.md) | [🇷🇺 Русский](README.ru.md) | [🇨🇳 中文](README.zh-CN.md)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=java)](https://adoptium.net/)
[![Minestom](https://img.shields.io/badge/Minestom-Core-blue.svg?style=for-the-badge)](https://minestom.net/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-База_данных-336791.svg?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![Grafana](https://img.shields.io/badge/Grafana-Метрики-F46800.svg?style=for-the-badge&logo=grafana)](https://grafana.com/)

</div>

---

## ✨ Особенности

Crystall — это кастомная реализация сервера Minecraft, созданная с нуля для поддержки огромного онлайна без потери современных механик выживания.

*   🌍 **Кастомная генерация мира:** Генерация рельефа на базе FastNoiseLite, поддержка нескольких измерений (Обычный мир и Незер) с масштабированием координат 1:8.
*   ⚔️ **Бой и Физика:** Ванильные механики PvP, кастомная физика блоков (падающий песок/гравий) и растекание жидкостей (вода/лава).
*   🛡️ **Продвинутый Античит:** Встроенная защита от Speedhack, Fly и спама пакетами.
*   💰 **Экономика и Социум:** Внутриигровая валюта, приваты чанков, система кланов и личные сообщения.
*   🔌 **Система плагинов:** Динамическое расширение ядра путем загрузки `.jar` плагинов из папки `plugins/`.
*   📊 **Готовность к продакшену:** Хранение данных игроков в PostgreSQL, экспорт метрик Prometheus (`/metrics`) и преднастроенный Docker Compose стек.
*   🌐 **Локализация (I18n):** Клиенто-ориентированный перевод (сообщения адаптируются под настройки языка в клиенте игрока).

## 🚀 Запуск

### Требования
*   **Java 21** или новее
*   **Docker** (Опционально, для полного продакшен-стека)

### Быстрый старт (Разработка)
Используйте Gradle для локального запуска:
```bash
./gradlew :core:run
```

### Продакшен развертывание (Docker Compose)
В проекте есть готовый Docker Compose стек, который поднимает Сервер, БД PostgreSQL, Prometheus и Grafana.

```bash
docker-compose up -d
```
*   Minecraft Сервер: `localhost:25565`
*   Дашборд Grafana: `http://localhost:3000` (логин: admin/admin)
*   API метрик: `http://localhost:25566/metrics`

## 🧩 Создание плагина

В Crystall реализована изолированная система загрузки плагинов. Создайте Java-проект и реализуйте интерфейс `CrystallPlugin`:

```java
import net.myserver.plugin.CrystallPlugin;
import net.myserver.plugin.PluginContext;

public class MyPlugin implements CrystallPlugin {
    @Override
    public void onEnable(PluginContext context) {
        System.out.println("Плагин включен!");
    }

    @Override
    public void onDisable() {
        System.out.println("Плагин выключен!");
    }
}
```
Добавьте дескриптор `META-INF/crystall-plugin.json`, соберите `.jar` и поместите его в папку `plugins/`.

## 🧪 Стресс-тестирование
Crystall включает headless-ботов для стресс-тестирования TPS и метрик.
```bash
cd stress_test
npm install
npm start
```
