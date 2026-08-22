package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.myserver.modules.CrystallModule;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AnnouncesModule implements CrystallModule {
    private Task broadcastTask;
    private static final List<String> ANNOUNCEMENTS = List.of(
            "💎 Сервер Crystall Core работает на ультрабыстром движке Minestom 2026!",
            "📢 Вступайте в кланы через команду /clan create <название>!",
            "🛡️ Заметили читера? Используйте команду /check или позовите модератора.",
            "🎮 Посмотреть список администрации онлайн: /adminlist"
    );

    @Override
    public String getId() {
        return "announces";
    }

    @Override
    public String getName() {
        return "Announces";
    }

    @Override
    public String getDescription() {
        return "Автоматические циклические объявления в чате";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        AtomicInteger index = new AtomicInteger(0);
        broadcastTask = MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (MinecraftServer.getConnectionManager().getOnlinePlayers().isEmpty()) return;

            int i = index.getAndUpdate(cur -> (cur + 1) % ANNOUNCEMENTS.size());
            String msg = ANNOUNCEMENTS.get(i);

            Component text = Component.text("[ИНФО] ", NamedTextColor.AQUA)
                    .append(Component.text(msg, NamedTextColor.WHITE));

            for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                player.sendMessage(text);
            }
        }).repeat(TaskSchedule.minutes(3)).schedule();
    }

    @Override
    public void onDisable() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
        }
    }
}
