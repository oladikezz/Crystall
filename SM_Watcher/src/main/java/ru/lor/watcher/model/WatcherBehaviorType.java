package ru.lor.watcher.model;

public enum WatcherBehaviorType {
    STATIC("Стоит", "Неподвижно стоит и поворачивает голову за игроком"),
    STALKER("Медленно идет за игроком", "Медленно сокращает дистанцию к игроку"),
    BLINKING("Исчезает и появляется", "Периодически исчезает и телепортируется на новую точку"),
    HEAD_TURN("Поворачивает голову", "Периодически отворачивает голову и снова смотрит на игрока"),
    MIRROR_STARE("Смотрит когда игрок оборачивается", "Смотрит на игрока только тогда, когда игрок поворачивается к нему");

    private final String displayName;
    private final String description;

    WatcherBehaviorType(String displayName, String description) {
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
