package net.myserver.network;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.myserver.permissions.RoleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class StressTestRunner {
    private static final Logger log = LoggerFactory.getLogger(StressTestRunner.class);
    private static final List<EntityCreature> simulatedBots = new CopyOnWriteArrayList<>();
    private static Task movementTask = null;

    public static int getActiveBotsCount() {
        return simulatedBots.size();
    }

    public static class StressTestCommand extends Command {
        public StressTestCommand() {
            super("stresstest");

            var actionArg = ArgumentType.Word("action"); // "start", "stop", "status"
            var countArg = ArgumentType.Integer("count"); // e.g. 100, 500

            addSyntax((sender, context) -> {
                if (sender instanceof Player player && !RoleManager.isAdmin(player)) {
                    player.sendMessage(Component.text("⛔ Только администраторы могут запускать стресс-тесты!", NamedTextColor.RED));
                    return;
                }

                String action = context.get(actionArg);

                if (action.equalsIgnoreCase("stop")) {
                    stopStressTest();
                    sender.sendMessage(Component.text("⚡ Стресс-тест остановлен. Все боты удалены.", NamedTextColor.YELLOW));
                } else if (action.equalsIgnoreCase("status")) {
                    sender.sendMessage(Component.text("📊 Активных виртуальных ботов: " + simulatedBots.size() + " | Игроков онлайн: " + MinecraftServer.getConnectionManager().getOnlinePlayers().size(), NamedTextColor.AQUA));
                }
            }, actionArg);

            addSyntax((sender, context) -> {
                if (sender instanceof Player player && !RoleManager.isAdmin(player)) {
                    player.sendMessage(Component.text("⛔ Только администраторы могут запускать стресс-тесты!", NamedTextColor.RED));
                    return;
                }

                String action = context.get(actionArg);
                int count = context.get(countArg);

                if (action.equalsIgnoreCase("start")) {
                    if (count > 2000) {
                        sender.sendMessage(Component.text("Лимит ботов для одного теста: 2000.", NamedTextColor.RED));
                        return;
                    }

                    Instance instance = MinecraftServer.getInstanceManager().getInstances().stream().findFirst().orElse(null);
                    if (instance == null) {
                        sender.sendMessage(Component.text("Мир не найден.", NamedTextColor.RED));
                        return;
                    }

                    sender.sendMessage(Component.text("⚡ Запуск стресс-теста: спавн " + count + " виртуальных сущностей...", NamedTextColor.GREEN));
                    startStressTest(instance, count);
                    sender.sendMessage(Component.text("✅ Все боты запущены! Наблюдайте за TPS и нагрузкой памяти.", NamedTextColor.GOLD));
                }
            }, actionArg, countArg);

            setDefaultExecutor((sender, context) -> {
                sender.sendMessage(Component.text("Использование: /stresstest <start|stop|status> [количество]", NamedTextColor.GRAY));
            });
        }
    }

    public static void startStressTest(Instance instance, int count) {
        stopStressTest();

        for (int i = 0; i < count; i++) {
            EntityCreature bot = new EntityCreature(EntityType.ZOMBIE);
            bot.setCustomName(Component.text("§c[Bot-" + (i + 1) + "]"));
            bot.setCustomNameVisible(false);

            ThreadLocalRandom rand = ThreadLocalRandom.current();
            double x = (rand.nextDouble() * 100) - 50;
            double z = (rand.nextDouble() * 100) - 50;
            bot.setInstance(instance, new Pos(x, 100, z));
            simulatedBots.add(bot);
        }

        // Симуляция активного движения ботов каждые 5 тиков
        movementTask = MinecraftServer.getSchedulerManager().buildTask(() -> {
            for (EntityCreature bot : simulatedBots) {
                if (bot.getInstance() != null) {
                    ThreadLocalRandom rand = ThreadLocalRandom.current();
                    double dx = (rand.nextDouble() * 2) - 1;
                    double dz = (rand.nextDouble() * 2) - 1;
                    bot.teleport(bot.getPosition().add(dx * 0.2, 0, dz * 0.2));
                }
            }
        }).repeat(TaskSchedule.tick(5)).schedule();

        log.info("[StressTest] Запущено {} активных ботов.", count);
    }

    public static void stopStressTest() {
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
        for (EntityCreature bot : simulatedBots) {
            bot.remove();
        }
        simulatedBots.clear();
        log.info("[StressTest] Все боты выгружены.");
    }
}
