# SM_Voodoos — Resource Pack

Ресурспак для кастомных текстур вуду-кукол. **Без ресурспака** игроки видят
обычный тотем бессмертия. **С ресурспаком** — кастомную текстуру для каждого
конкретного игрока (или обычный тотем, если ID не совпал).

## Как это работает

Плагин ставит на тотем `custom_model_data` (целое число), например `7770001`.
Маппинг `ник → число` задаётся в `config.yml` → секция `models`.

- **Без ресурспака** — клиент игнорирует неизвестный `custom_model_data` и рисует обычный тотем.
- **С ресурспаком** — клиент читает `totem_of_undying.json`, находит case по числу и подставляет кастомную модель/текстуру.

## Структура

```
resourcepack/
├── pack.mcmeta
└── assets/
    ├── minecraft/items/
    │   └── totem_of_undying.json          # Оверрайд ванильного тотема (select по custom_model_data)
    └── smps/
        ├── models/item/
        │   ├── woodoo_default.json        # Фоллбэк (можно не использовать)
        │   └── woodoo_<ник>.json          # Модель для конкретного игрока
        └── textures/item/
            └── woodoo_<ник>.png           # 16×16 или 32×32 текстура
```

## Текущие маппинги (config.yml → models)

| Ник             | custom_model_data |
|-----------------|-------------------|
| ComboGames_GG   | 7770001           |
| lalawar         | 7770002           |
| __force         | 7770003           |
| MeXaNoBoP       | 7770004           |
| kwertyk         | 7770005           |
| carlistish      | 7770006           |
| doit2           | 7770007           |
| 5api            | 7770008           |
| _Jun10r_        | 7770009           |
| seimantt        | 7770010           |

## Как добавить нового игрока

### 1. Назначьте ID в config.yml

```yaml
models:
  новыйник: 7770011
```

### 2. Добавьте текстуру

`assets/smps/textures/item/woodoo_новыйник.png` (16×16 или 32×32)

### 3. Создайте модель

`assets/smps/models/item/woodoo_новыйник.json`:
```json
{
  "parent": "minecraft:item/totem_of_undying",
  "textures": {
    "layer0": "smps:item/woodoo_новыйник"
  }
}
```

### 4. Добавьте case в totem_of_undying.json

В массив `cases` добавьте:
```json
{
  "when": "7770011",
  "model": {
    "type": "minecraft:model",
    "model": "smps:item/woodoo_новыйник"
  }
}
```

### 5. Перепакуйте .zip и обновите серверный ресурспак.
