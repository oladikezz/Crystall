# 💎 Crystall Core & CMPS (Crystall Modular Plugin System)

<p align="center">
  <img src="https://img.shields.io/badge/Java-25%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25"/>
  <img src="https://img.shields.io/badge/TPS-20.0%20Rock%20Solid-4CAF50?style=for-the-badge" alt="20 TPS"/>
  <img src="https://img.shields.io/badge/Engine-Minestom%20%7C%20DoAPI-00BCD4?style=for-the-badge" alt="Minestom + DoAPI"/>
  <img src="https://img.shields.io/badge/Modules-35%20Active%20Modules-FF5722?style=for-the-badge" alt="35 Modules"/>
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="MIT License"/>
</p>

<p align="center">
  <b>🌐 [English](README.md) | [Русский](README.ru.md) | [中文](README.zh-CN.md)</b>
</p>

---

## 📌 О проекте (About The Project)

**Crystall** — это революционная модульная экосистема для высоконагруженных серверов Minecraft нового поколения, объединяющая два мощных стека:

1. **💎 Crystall Core (`:core`)** — Сверхпроизводительное, чистое автономное ядро на базе **Minestom (2026)** с пространственным $O(1)$ хэшированием, 32×32 регионным хранилищем, адаптивным LOD-тикингом и Zero-GC структурами (удерживает **20.0 TPS при 2000+ мобов** при **<100 МБ ОЗУ**).
2. **🧩 DoAPI & CMPS (`:DoAPI` + 35 Модулей)** — Модульная фреймворк-система горячей загрузки и изолированного управления плагинами/модулями на лету без перезагрузки сервера.

---

## ⚡ Технологический стек Crystall Core

- 🌐 **SpatialGrid Engine ($O(1)$ Entity Index):** Пространственная сетка чанков для мгновенного поиска сущностей в радиусе без глобального перебора мира $O(N)$.
- ⏱️ **AdaptiveTickEngine (LOD Zones 0..3):** 4-уровневые зоны детализации тиков, снижающие холостой ход CPU на 60–80%.
- 🗄️ **32×32 Region Chunk Storage:** Регионный формат сжатия с пропуском пустых секций и Thread-Local Zero-GC буферами сжатия (до 256 КБ).
- 🧮 **FastMath LUT:** 16,384-точечная таблица предрасчета тригонометрии (до 15x быстрее `java.lang.Math`).
- 🧱 **Zero-Box Collections:** Примитивные кэширующие хэш-таблицы `Long2ObjectOpenHashMap` и `LongOpenHashSet`.
- ⚡ **Alternate-Current BFS Redstone:** Очередь распространения редстоун-сигналов в ширину без рекурсии.
- 📡 **Встроенный мониторинг:** REST API на виртуальных потоках (`:25566`), Prometheus Metrics (`/metrics`) и живая веб-карта WebMap (`:8080`).

---

## 🧩 Список модулей CMPS (`build_all`)

Репозиторий включает **35 готовых модулей**, готовых к компиляции и использованию:

| Модуль | Описание |
| :--- | :--- |
| **`CM_Accounts`** | Интеграция аккаунтов, привязка БД и Discord-бот через JDA |
| **`CM_AdminList`** | Список администрации, отслеживание статуса и вебхуки |
| **`CM_Alert`** | Система глобальных всплывающих оповещений на экране |
| **`CM_Announces`** | Автоматические циклические объявления с поддержкой MiniMessage |
| **`CM_AutoReplenish`** | Автопополнение предметов и инструментов в инвентаре |
| **`CM_Checker`** | Проверка игроков на запрещенный софт и модерация |
| **`CM_Clans`** | Полнофункциональная клановая система с PlaceholderAPI |
| **`CM_Cosmetics`** | Система кастомизации: питомцы, шарики, частицы, эмодзи, музыка и гардероб |
| **`CM_Crowns`** | Короны и визуальные титулы игроков |
| **`CM_DebugStick`** | Кастомный оптимизированный инструмент отладки свойств блоков |
| **`CM_Essentials`** | Базовые серверные утилиты, телепортации и команды |
| **`CM_FastLeaves`** | Мгновенное и физически реалистичное опадание листвы |
| **`CM_Flags`** | Флаги территорий, регионов и игровых событий |
| **`CM_Hat`** | Команда надевания любого блока или предмета на голову |
| **`CM_Help`** | Интерактивное меню помощи и навигации для новичков |
| **`CM_Invsee`** | Просмотр, мониторинг и синхронное редактирование инвентарей игроков |
| **`CM_ItemDespawn`** | Оптимизированный деспавн выброшенных предметов |
| **`CM_ItemMeta`** | Расширенное управление метаданными и атрибутами предметов |
| **`CM_KeepInventory`** | Умное сохранение инвентаря на основе прав и пермишенов |
| **`CM_Lightcraft`** | Оптимизированный переносной динамический источник света |
| **`CM_Marry`** | Свадьбы, подарки и социальные взаимодействия игроков |
| **`CM_PhaseGuard`** | Защита от фазирования, VClip и прохождения сквозь стены |
| **`CM_PlayerHeads`** | Выпадение голов игроков при PvP-смерти с сохранением скинов |
| **`CM_QuietBan`** | Теневые (shadow) и тихие блокировки нарушителей правил |
| **`CM_Scale`** | Плавное изменение масштаба и размеров сущностей |
| **`CM_Spit`** | Забавные социальные механики и анимации |
| **`CM_Stats`** | Сбор, хранение и вывод расширенной игровой статистики |
| **`CM_StonecutterAdditions`** | Расширенные рецепты крафта для камнереза |
| **`CM_StreamerMode`** | Режим стримера: скрытие никнеймов, чата и координат |
| **`CM_TrafficOptimizer`** | Netty-фильтрация избыточных пакетов частиц и звуков |
| **`CM_TrollItems`** | Предметы для ивентов, развлечений и троллинга |
| **`CM_UserInfo`** | Подробное досье и информация об аккаунте игрока |
| **`CM_Vanish`** | Полная невидимость для администрации с поддержкой TAB API |
| **`CM_Voodoos`** | Куклы вуду и дистанционные магические механики |
| **`CM_Watcher`** | Отслеживание и аудит сетевых пакетов (PacketEvents) |

---

## 🛠️ Сборка и запуск проекта (Building & Running)

### Требования:
- **Java 25+** (OpenJDK / Eclipse Temurin / GraalVM)
- **Gradle 9.7+** (включен в проект через `./gradlew`)

### Команды Gradle:

```bash
# 1. Сборка ядра Crystall Core (Fat JAR -> core/build/libs/crystall-core-1.0.0.jar)
./gradlew :core:jar

# 2. Сборка DoAPI и всех 35 модулей CMPS (JARs -> build/dist/modules/)
./gradlew build_all

# 3. Локальный запуск ядра Crystall Core
./gradlew :core:run

# 4. Сборка конкретного модуля (например, CM_Clans)
./gradlew :CM_Clans:jar
```

---

## 🐳 Запуск через Docker Compose

В проекте настроен полноценный продакшн-стек:

```bash
docker-compose up -d --build
```

- **Minecraft Server:** `localhost:25565`
- **REST API & Prometheus:** `http://localhost:25566/metrics`
- **WebMap Live Map:** `http://localhost:8080`
- **Grafana Dashboard:** `http://localhost:3000` (admin / admin)
- **PostgreSQL Database:** `localhost:5432`

---

## 📖 Документация API

Подробное руководство по созданию собственных модулей для CMPS доступно в файле:  
📄 **[`CMPS_API_DOCUMENTATION.md`](CMPS_API_DOCUMENTATION.md)**

---

## 📜 Лицензия
Открытая лицензия **MIT**. Проект на 100% готов к продакшн-использованию.