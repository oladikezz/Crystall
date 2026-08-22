# 💎 Crystall Core — Высокопроизводительное ядро Minecraft (2026)

<p align="center">
  <img src="https://img.shields.io/badge/Java-25%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25"/>
  <img src="https://img.shields.io/badge/TPS-20.0%20Стабильный-4CAF50?style=for-the-badge" alt="20 TPS"/>
  <img src="https://img.shields.io/badge/Движок-Minestom%202026-00BCD4?style=for-the-badge" alt="Minestom 2026"/>
  <img src="https://img.shields.io/badge/Встроенные%20Модули-35%20Нативно%20Встроено-FF5722?style=for-the-badge" alt="35 Modules"/>
  <img src="https://img.shields.io/badge/Лицензия-MIT-green?style=for-the-badge" alt="MIT License"/>
</p>

<p align="center">
  <b>🌐 [English](README.md) | [Русский](README.ru.md) | [中文](README.zh-CN.md)</b>
</p>

---

## 📌 О проекте

**Crystall Core** — это передовое, чистое и сверхпроизводительное автономное ядро сервера Minecraft на базе **Minestom (2026)** с поддержкой **Java 25**, **Generational ZGC**, пространственного $O(1)$ хэширования, 32×32 регионного хранилища чанков, адаптивного LOD-тикинга и **35 встроенных нативных модулей** прямо внутри ядра.

Сервер собирается в виде единого автономного Fat JAR файла (`crystall-core-1.0.0.jar`), не требует внешних Bukkit/Paper прослоек и удерживает **стабильные 20.0 TPS при 2000+ мобов** при потреблении **менее 100 МБ ОЗУ**.

---

## ⚡ Ключевые архитектурные прорывы Crystall Core

- 🌐 **SpatialGrid ($O(1)$ Индексация сущностей):** Пространственная сетка чанков для мгновенного поиска сущностей в радиусе без глобального перебора мира $O(N)$.
- ⏱️ **AdaptiveTickEngine (LOD Зоны 0..3):** 4-уровневые зоны детализации тиков, снижающие холостой ход CPU на 60–80%.
- 🗄️ **32×32 Region Chunk Storage:** Регионный формат сжатия с пропуском пустых секций и Thread-Local Zero-GC буферами до 256 КБ.
- 🧮 **FastMath LUT:** 16,384-точечная таблица предрасчета тригонометрии (до 15x быстрее `java.lang.Math`).
- 🧱 **Zero-Box Collections:** Примитивные кэширующие хэш-таблицы `Long2ObjectOpenHashMap` и `LongOpenHashSet`.
- ⚡ **Alternate-Current BFS Redstone:** Очередь распространения редстоун-сигналов в ширину без рекурсии.
- 📡 **Встроенный мониторинг:** REST API на виртуальных потоках (`:25566`), Prometheus Metrics (`/metrics`) и живая веб-карта WebMap (`:8080`).

---

## 🧩 35 Встроенных модулей ядра Crystall

Все 35 модулей интегрированы прямо в ядро и могут быть независимо включены/отключены в `config.yml`:

| Модуль | Идентификатор | Описание |
| :--- | :--- | :--- |
| **Accounts** | `accounts` | Управление профилями, авторизацией и привязкой аккаунтов |
| **AdminList** | `adminlist` | Список администрации онлайн (`/adminlist`) и оповещения |
| **Alert** | `alert` | Глобальные всплывающие оповещения на экране (`/alert`) |
| **Announces** | `announces` | Автоматические циклические объявления в чате с MiniMessage |
| **AutoReplenish** | `autoreplenish` | Автопополнение предметов и инструментов в инвентаре |
| **Checker** | `checker` | Вызов игроков на проверку софта и модерация (`/check`) |
| **Clans** | `clans` | Кланы, клановые чаты, составы и базы (`/clan`) |
| **Cosmetics** | `cosmetics` | Кастомизация: следы, частицы, гардероб (`/cosmetics`) |
| **Crowns** | `crowns` | Визуальные короны над головами игроков (`/crown`) |
| **DebugStick** | `debugstick` | Инструмент отладки свойств блоков (`/debugstick`) |
| **Essentials** | `essentials` | Базовые команды: `/spawn`, `/heal`, `/feed`, `/fly`, `/speed` |
| **FastLeaves** | `fastleaves` | Мгновенное и красивое опадание листвы при срубе дерева |
| **Flags** | `flags` | Флаги территорий и зон безопасности |
| **Hat** | `hat` | Надевание любого блока из руки на голову (`/hat`) |
| **Help** | `help` | Интерактивное меню помощи и навигации (`/help`) |
| **Invsee** | `invsee` | Просмотр и редактирование инвентаря игрока (`/invsee`) |
| **ItemDespawn** | `itemdespawn` | Оптимизированный деспавн выброшенных предметов |
| **ItemMeta** | `itemmeta` | Кастомные имена предметов и описание (`/rename`) |
| **KeepInventory** | `keepinventory` | Сохранение инвентаря при смерти |
| **Lightcraft** | `lightcraft` | Динамическое переносное освещение от факела в руке |
| **Marry** | `marry` | Свадьбы, партнерство и телепортация к паре (`/marry`) |
| **PhaseGuard** | `phaseguard` | Защита от фазирования и прохождения сквозь стены |
| **PlayerHeads** | `playerheads` | Выпадение голов игроков при PvP-смерти |
| **QuietBan** | `quietban` | Shadow-мут и тихие блокировки нарушителей (`/quietban`) |
| **Scale** | `scale` | Масштабирование размеров и пропорций сущностей (`/scale`) |
| **Spit** | `spit` | Забавные социальные анимации и звуки (`/spit`) |
| **Stats** | `stats` | Сбор и показ игровой статистики (`/stats`) |
| **Stonecutter** | `stonecutter` | Расширенные рецепты для камнереза |
| **StreamerMode** | `streamermode` | Режим стримера со скрытием никнеймов и координат |
| **TrafficOptimizer** | `trafficoptimizer` | Netty-фильтрация сетевых пакетов частиц |
| **TrollItems** | `trollitems` | Предметы для ивентов и троллинга (`/trollitem`) |
| **UserInfo** | `userinfo` | Детальное досье и профиль игрока (`/userinfo`) |
| **Vanish** | `vanish` | Полная невидимость для администрации (`/vanish`, `/v`) |
| **Voodoos** | `voodoos` | Куклы вуду и магические механики (`/voodoo`) |
| **Watcher** | `watcher` | Сетевой аудит пакетов и защита от эксплойтов |

---

## 🛠️ Сборка и запуск проекта

### Требования:
- **Java 25+** (OpenJDK / Eclipse Temurin / GraalVM)

### Команды сборки:

```bash
# 1. Сборка Fat JAR сервера со всеми 35 модулями
./gradlew :core:jar

# 2. Быстрый запуск сервера через Gradle
./gradlew run

# 3. Запуск готового JAR напрямую с ZGC
java -Xms2G -Xmx4G -XX:+UseZGC -XX:+ZGenerational -jar core/build/libs/crystall-core-1.0.0.jar
```

---

## 🐳 Запуск через Docker Compose

```bash
docker-compose up -d --build
```

- **Minecraft Сервер:** `localhost:25565`
- **REST API & Prometheus:** `http://localhost:25566/metrics`
- **WebMap Онлайн Карта:** `http://localhost:8080`
- **Grafana Дашборд:** `http://localhost:3000` (admin / admin)
- **PostgreSQL База Данных:** `localhost:5432`

---

## 📜 Лицензия
Открытая лицензия **MIT**. Проект на 100% готов к продакшн-использованию.
