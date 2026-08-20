package net.myserver.mechanics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockInteractEvent;

public class SleepSystem {
    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerBlockInteractEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.getInstance() == null) return;
            
            // Проверяем, кликнул ли игрок по кровати
            if (event.getBlock().name().contains("bed")) {
                long totalTime = player.getInstance().getTime();
                long dayTime = totalTime % 24000L;
                
                // Ночь с 12500 до 23500 тиков каждого дня
                if (dayTime >= 12500 && dayTime <= 23500) {
                    long nextMorning = ((totalTime / 24000L) + 1) * 24000L;
                    player.getInstance().setTime(nextMorning); // Наступление утра
                    
                    Component message = Component.text("🌙 " + player.getUsername() + " лег спать. Ночь пропущена!", NamedTextColor.YELLOW);
                    for (Player p : player.getInstance().getPlayers()) {
                        p.sendMessage(message);
                    }
                } else {
                    player.sendMessage(Component.text("Вы можете спать только ночью!", NamedTextColor.RED));
                }
            }
        });
    }
}
