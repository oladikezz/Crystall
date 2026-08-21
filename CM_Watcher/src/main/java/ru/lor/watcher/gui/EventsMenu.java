package ru.lor.watcher.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.AutoEvent;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.utils.ItemBuilder;

import ru.lor.watcher.utils.PermissionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EventsMenu {

    private static final Pattern VALID_EVENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    public static void open(WatcherPlugin plugin, Player admin, String targetPlayerName, WatcherSpawnSettings settings) {
        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.EVENTS, targetPlayerName, settings);
        Inventory inv = Bukkit.createInventory(holder, 36, ColorUtil.parse("<dark_purple><b>Автоматические события</b></dark_purple>"));
        holder.setInventory(inv);

        GuiHolder.fillBorder(inv);

        List<AutoEvent> eventList = new ArrayList<>(plugin.getEventManager().getEvents().values());

        for (int i = 0; i < eventList.size() && i < 18; i++) {
            AutoEvent event = eventList.get(i);
            ItemStack item = new ItemBuilder(Material.CLOCK)
                    .name("<dark_purple><b>Событие: " + event.getId() + "</b></dark_purple>")
                    .loreStrings(
                            "<gray>Интервал: <yellow>Каждые " + event.getIntervalMinutes() + " мин</yellow>",
                            "<gray>Время появления: <yellow>" + event.getSettings().getDurationSeconds() + " сек</yellow>",
                            "<gray>Звук: <yellow>" + (event.getSettings().getSoundName() != null ? event.getSettings().getSoundName() : "Нет") + "</yellow>",
                            "<gray>Сообщение: <yellow>" + (event.getSettings().getMessageText() != null ? event.getSettings().getMessageText() : "Нет") + "</yellow>",
                            "",
                            "<green>▶ Левый клик: Запустить сейчас</green>",
                            "<red>▶ Правый клик: Удалить событие</red>"
                    ).build();
            inv.setItem(i, item);
        }

        // Create new event button
        inv.setItem(22, new ItemBuilder(Material.NETHER_STAR)
                .name("<green><b>+ Создать событие</b></green>")
                .loreStrings(
                        "<gray>Добавить авто-событие появления</gray>",
                        "",
                        "<light_purple>▶ Нажмите для создания</light_purple>"
                ).build());

        // Back button
        inv.setItem(31, new ItemBuilder(Material.ARROW).name("<yellow>← В главное меню</yellow>").build());

        admin.openInventory(inv);
    }

    public static void handleClick(WatcherPlugin plugin, Player admin, int slot, GuiHolder holder, ClickType click) {
        String targetName = holder.getTargetPlayerName();
        WatcherSpawnSettings settings = holder.getSettings();

        if (slot == 31) {
            MainMenu.open(plugin, admin, targetName, settings);
            return;
        }

        if (!PermissionUtil.require(plugin, admin, PermissionUtil.EVENTS)) {
            admin.closeInventory();
            return;
        }

        List<AutoEvent> eventList = new ArrayList<>(plugin.getEventManager().getEvents().values());

        if (slot >= 0 && slot < 18 && slot < eventList.size()) {
            AutoEvent event = eventList.get(slot);

            if (click.isRightClick()) {
                plugin.getEventManager().deleteEvent(event.getId());
                admin.sendMessage(ColorUtil.parse("<red>Событие <yellow>" + event.getId() + "</yellow> успешно удалено!</red>"));
            } else {
                plugin.getEventManager().triggerEvent(event);
                admin.sendMessage(ColorUtil.parse("<green>Автоматическое событие <yellow>" + event.getId() + "</yellow> запущено!</green>"));
            }

            open(plugin, admin, targetName, settings);
            return;
        }

        if (slot == 22) {
            plugin.getInputSessionManager().startSession(admin, "<dark_purple>Введите ID нового авто-события (англ. буквой/цифрой):</dark_purple>", id -> {
                if (!VALID_EVENT_ID.matcher(id).matches()) {
                    admin.sendMessage(ColorUtil.parse(
                            "<red>ID может содержать только латинские буквы, цифры, дефис и подчёркивание (до 32 символов).</red>"));
                    open(plugin, admin, targetName, settings);
                    return;
                }
                plugin.getInputSessionManager().startSession(admin, "<yellow>Введите интервал запуска в минутах (напр. 40):</yellow>", intervalStr -> {
                    try {
                        int interval = Integer.parseInt(intervalStr);
                        if (interval < 1 || interval > 10080) {
                            admin.sendMessage(ColorUtil.parse("<red>Интервал должен быть от 1 до 10080 минут.</red>"));
                            open(plugin, admin, targetName, settings);
                            return;
                        }
                        WatcherSpawnSettings evSettings = settings.clone();
                        AutoEvent newEv = new AutoEvent(id, interval, evSettings);
                        plugin.getEventManager().saveEvent(newEv);
                        admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("event-created").replace("{id}", id)));
                    } catch (NumberFormatException e) {
                        admin.sendMessage(ColorUtil.parse(plugin.getConfigManager().getMessage("chat-input-invalid-number")));
                    }
                    open(plugin, admin, targetName, settings);
                });
            });
        }
    }

    public static void handleClick(WatcherPlugin plugin, Player admin, int slot, GuiHolder holder) {
        handleClick(plugin, admin, slot, holder, ClickType.LEFT);
    }
}
