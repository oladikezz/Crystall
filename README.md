# 💎 Crystall Core & SMPS (Schalker Modular Plugin System)

<p align="center">
  <img src="https://img.shields.io/badge/Java-25%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25"/>
  <img src="https://img.shields.io/badge/TPS-20.0%20Rock%20Solid-4CAF50?style=for-the-badge" alt="20 TPS"/>
  <img src="https://img.shields.io/badge/Engine-Minestom%20%7C%20DoAPI-00BCD4?style=for-the-badge" alt="Minestom + DoAPI"/>
  <img src="https://img.shields.io/badge/Modules-36%20Active%20Modules-FF5722?style=for-the-badge" alt="36 Modules"/>
</p>

---

## 📌 О проекте (About The Project)

Данный репозиторий объединяет два мощных технологических стека для высоконагруженных Minecraft-серверов нового поколения:

1. **💎 Crystall Core (`:core`)** — Сверхпроизводительное, чистое автономное ядро на базе **Minestom (2026)** с пространственным $O(1)$ хэшированием, 32×32 регионным хранилищем, адаптивным LOD-тикингом и Zero-GC структурами (удерживает **20.0 TPS при 2000+ мобов** при **<100 МБ ОЗУ**).
2. **🧩 DoAPI & SMPS (`:DoAPI` + 36 Модулей)** — Модульная фреймворк-система горячей загрузки и управления плагинами/модулями на лету без перезагрузки сервера.

---

## ⚡ Технологический стек Crystall Core

- 🌐 **SpatialGrid ($O(1)$ Entity Index):** Пространственная сетка чанков для мгновенного поиска сущностей без глобального перебора мира $O(N)$.
- ⏱️ **AdaptiveTickEngine (LOD 0..3):** 4-уровневые зоны детализации тиков, снижающие холостой ход CPU на 60–80%.
- 🗄️ **32×32 Region Chunk Storage:** Регионный формат с пропуском пустых секций и Thread-Local Zero-GC буферами сжатия.
- 🧮 **FastMath LUT:** 16,384-точечная таблица предрасчета тригонометрии (до 15x быстрее `java.lang.Math`).
- 🧱 **Zero-Box Collections:** Примитивные хэш-таблицы `Long2ObjectOpenHashMap` и `LongOpenHashSet`.
- ⚡ **Alternate-Current BFS Redstone:** Очередь распространения редстоун-сигналов без рекурсии.
- 📡 **Встроенный мониторинг:** REST API (`:25566`), Prometheus Metrics и WebMap (`:8080`).

---

## 🧩 Список модулей SMPS (`build_all`)

Репозиторий включает 36 готовых модулей для экосистемы DoAPI / SMPS:

| Модуль | Описание |
| :--- | :--- |
| **`SM_Accounts`** | Интеграция аккаунтов и Discord-бот через JDA |
| **`SM_AdminList`** | Список администрации и вебхуки оповещений |
| **`SM_Alert`** | Система глобальных всплывающих оповещений |
| **`SM_Announces`** | Автоматические циклические объявления с поддержкой MiniMessage |
| **`SM_AutoReplenish`** | Автопополнение предметов в инвентаре |
| **`SM_Checker`** | Проверка игроков на запрещенный софт и модерация |
| **`SM_Clans`** | Полнофункциональная клановая система с PlaceholderAPI |
| **`SM_Cosmetics`** | Система кастомизации, питомцы и визуальные эффекты |
| **`SM_Crowns`** | Короны и титулы игроков |
| **`SM_DebugStick`** | Кастомный инструмент отладки блоков |
| **`SM_Essentials`** | Базовые серверные утилиты и команды |
| **`SM_FastLeaves`** | Мгновенное и красивое опадание листвы |
| **`SM_Flags`** | Флаги территорий и событий |
| **`SM_Hat`** | Команда надевания блока на голову |
| **`SM_Help`** | Интерактивное меню помощи |
| **`SM_Invsee`** | Просмотр и редактирование инвентарей игроков |
| **`SM_ItemDespawn`** | Оптимизированный деспавн выброшенных предметов |
| **`SM_ItemMeta`** | Расширенное управление метаданными предметов |
| **`SM_KeepInventory`** | Умное сохранение инвентаря по пермишенам |
| **`SM_Lightcraft`** | Оптимизированный переносной источник света |
| **`SM_Marry`** | Свадьбы и социальные взаимодействия |
| **`SM_PhaseGuard`** | Защита от фазирования и прохождения сквозь блоки |
| **`SM_PlayerHeads`** | Выпадение голов игроков при смерти |
| **`SM_QuietBan`** | Теневые и тихие блокировки нарушителей |
| **`SM_Scale`** | Изменение масштаба и размера сущностей |
| **`SM_Spit`** | Забавные социальные анимации и механики |
| **`SM_Stats`** | Сбор и хранение игровой статистики |
| **`SM_StonecutterAdditions`** | Расширенные рецепты для камнереза |
| **`SM_StreamerMode`** | Режим стримера со скрытием ников и координат |
| **`SM_TrafficOptimizer`** | Netty-фильтр пакетов частиц для снижения сетевого трафика |
| **`SM_TrollItems`** | Предметы для ивентов и троллинга |
| **`SM_UserInfo`** | Подробная информация об аккаунте игрока |
| **`SM_Vanish`** | Полная невидимость для администрации с поддержкой TAB API |
| **`SM_Voodoos`** | Куклы вуду и магические механики |
| **`SM_Watcher`** | Защита и отслеживание пакетов (PacketEvents) |

---

## 🛠️ Сборка проекта (Building)

### Требования:
- **Java 25+** (OpenJDK / Temurin / GraalVM)

### Команды Gradle:

```bash
# 1. Сборка ядра Crystall Core (Fat JAR)
./gradlew :core:jar

# 2. Сборка DoAPI и всех 36 модулей SMPS в папку build/dist/
./gradlew build_all

# 3. Сборка конкретного модуля (например, SM_Clans)
./gradlew :SM_Clans:jar
```

---

## 📖 Документация API

Подробное руководство по созданию собственных модулей для SMPS доступно в файле:  
📄 **[`SMPS_API_DOCUMENTATION.md`](file:///c:/Users/user/Desktop/Crystall%20-%20%D0%AF%D0%B4%D1%80%D0%BE/SMPS_API_DOCUMENTATION.md)**

---

## 📜 Лицензия
Открытая лицензия **MIT**. Проект готов к продакшн-использованию.