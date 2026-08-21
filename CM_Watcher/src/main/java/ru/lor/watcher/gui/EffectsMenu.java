package ru.lor.watcher.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.model.WatcherSpawnSettings;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.utils.ItemBuilder;

public class EffectsMenu {

    public static void open(WatcherPlugin plugin, Player admin, String targetPlayerName, WatcherSpawnSettings settings) {
        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.EFFECTS, targetPlayerName, settings);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtil.parse("<#ec4899><b>Эффекты при появлении</b></#ec4899>"));
        holder.setInventory(inv);

        GuiHolder.fillBorder(inv);

        // Slot 10: Freezing Aura
        boolean freezeOn = settings.isFreezingEnabled();
        inv.setItem(10, new ItemBuilder(freezeOn ? Material.POWDER_SNOW_BUCKET : Material.BUCKET)
                .name("<#38bdf8><b>Эффект Заморозки (Freezing Aura)</b>")
                .loreStrings(
                        "<gray>Статус: " + (freezeOn ? "<green>ВКЛ (Снег и Иней на экране)</green>" : "<red>ВЫКЛ</red>"),
                        "",
                        "<light_purple>▶ Нажмите для включения / выключения</light_purple>"
                )
                .glow(freezeOn)
                .build());

        // Slot 12: Jumpscare Effect Toggle
        boolean jumpOn = settings.isJumpscareEnabled();
        inv.setItem(12, new ItemBuilder(jumpOn ? Material.WITHER_SKELETON_SKULL : Material.SKELETON_SKULL)
                .name("<red><b>Скример при контакте (Jumpscare)</b></red>")
                .loreStrings(
                        "<gray>Статус: " + (jumpOn ? "<green>ВКЛ (Звук крика + разряд тьмы)</green>" : "<red>ВЫКЛ</red>"),
                        "",
                        "<light_purple>▶ Нажмите для включения / выключения</light_purple>"
                )
                .glow(jumpOn)
                .build());

        // Slot 14: Sound Scheme Presets
        String preset = settings.getSoundPreset();
        String presetName = switch (preset != null ? preset : "ANCIENT_HORROR") {
            case "SPECTRAL_WHISPER" -> "<purple>Призрачный шёпот (Elder Guardian + Vex)</purple>";
            case "SHADOW_DISCHARGE" -> "<dark_red>Разряд тьмы (Wither + Sculk Sensor)</dark_red>";
            case "NONE" -> "<red>Выключен (Без звуков)</red>";
            default -> "<dark_purple>Древний ужас (Warden Heartbeat + Soul Sand)</dark_purple>";
        };

        inv.setItem(14, new ItemBuilder(Material.JUKEBOX)
                .name("<#eab308><b>Звуковая атмосфера</b>")
                .loreStrings(
                        "<gray>Текущий пресет: " + presetName,
                        "",
                        "<light_purple>▶ ЛКМ: Переключить звуковой пресет</light_purple>"
                )
                .glow(!preset.equals("NONE"))
                .build());

        // Slot 15: Message Visibility (Everyone vs Nearby Only)
        boolean allMode = settings.isBroadcastToAll();
        inv.setItem(15, new ItemBuilder(allMode ? Material.BEACON : Material.SPYGLASS)
                .name("<#38bdf8><b>Видимость сообщений</b>")
                .loreStrings(
                        "<gray>Кому видно: " + (allMode ? "<light_purple>🌐 ВСЕ ИГРОКИ НА СЕРВЕРЕ</light_purple>" : "<green>👥 Только ИГРОКИ РЯДОМ (до 32 бл.)</green>"),
                        "",
                        "<light_purple>▶ Нажмите для смены видимости</light_purple>"
                )
                .glow(allMode)
                .build());

        // Slot 16: AI Message Toggle
        boolean aiMsgOn = settings.isAiMessageEnabled();
        inv.setItem(16, new ItemBuilder(aiMsgOn ? Material.WRITABLE_BOOK : Material.BOOK)
                .name("<#a855f7><b>ИИ Сообщения в чат</b>")
                .loreStrings(
                        "<gray>Статус: " + (aiMsgOn ? "<green>ВКЛ (Смотрящий пишет сообщения)</green>" : "<red>ВЫКЛ (Без сообщений в чат)</red>"),
                        "",
                        "<light_purple>▶ Нажмите для включения / выключения</light_purple>"
                )
                .glow(aiMsgOn)
                .build());

        // Back button
        inv.setItem(22, new ItemBuilder(Material.ARROW).name("<yellow>← Готово (В главное меню)</yellow>").build());

        admin.openInventory(inv);
    }

    public static void handleClick(WatcherPlugin plugin, Player admin, int slot, GuiHolder holder, ClickType click) {
        String targetName = holder.getTargetPlayerName();
        WatcherSpawnSettings settings = holder.getSettings();

        if (slot == 22) {
            MainMenu.open(plugin, admin, targetName, settings);
            return;
        }

        // Slot 10: Freezing aura
        if (slot == 10) {
            settings.setFreezingEnabled(!settings.isFreezingEnabled());
            open(plugin, admin, targetName, settings);
            return;
        }

        // Slot 12: Jumpscare toggle
        if (slot == 12) {
            settings.setJumpscareEnabled(!settings.isJumpscareEnabled());
            open(plugin, admin, targetName, settings);
            return;
        }

        // Slot 14: Sound scheme toggle
        if (slot == 14) {
            String current = settings.getSoundPreset();
            String next = switch (current != null ? current : "ANCIENT_HORROR") {
                case "ANCIENT_HORROR" -> "SPECTRAL_WHISPER";
                case "SPECTRAL_WHISPER" -> "SHADOW_DISCHARGE";
                case "SHADOW_DISCHARGE" -> "NONE";
                default -> "ANCIENT_HORROR";
            };
            settings.setSoundPreset(next);
            open(plugin, admin, targetName, settings);
            return;
        }

        // Slot 15: Message visibility toggle
        if (slot == 15) {
            settings.setBroadcastToAll(!settings.isBroadcastToAll());
            open(plugin, admin, targetName, settings);
            return;
        }

        // Slot 16: AI Message toggle
        if (slot == 16) {
            settings.setAiMessageEnabled(!settings.isAiMessageEnabled());
            open(plugin, admin, targetName, settings);
        }
    }
}
