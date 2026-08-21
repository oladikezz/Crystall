package ru.lor.watcher.model;

public enum WatcherPositionType {
    BEHIND("Позади игрока", "Смотрящий появится за спиной игрока"),
    LEFT("Слева", "Смотрящий появится с левой стороны"),
    RIGHT("Справа", "Смотрящий появится с правой стороны"),
    FRONT("Перед игроком", "Смотрящий появится прямо перед глазами игрока"),
    ROOF("На крыше", "Смотрящий появится на блоке над головой игрока"),
    RANDOM("Случайная точка", "Смотрящий появится в случайной точке вокруг игрока");

    private final String displayName;
    private final String description;

    WatcherPositionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
