package net.myserver.network;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.*;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AntiBotSystem {
    private static final Logger log = LoggerFactory.getLogger(AntiBotSystem.class);

    private static final int MAX_ACCOUNTS_PER_IP = 3;
    private static final int MAX_JOINS_PER_IP_5SEC = 3;
    private static final int ATTACK_MODE_THRESHOLD = 8;

    private static final Map<String, List<Long>> joinHistoryByIp = new ConcurrentHashMap<>();
    private static final Map<String, Integer> onlineByIp = new ConcurrentHashMap<>();
    private static final Set<UUID> unverifiedPlayers = ConcurrentHashMap.newKeySet();

    private static final AtomicInteger globalJoinsThisSecond = new AtomicInteger(0);
    private static volatile boolean attackMode = false;

    public static void register(GlobalEventHandler handler) {
        // Сброс счетчика глобальных входов раз в секунду
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            int joins = globalJoinsThisSecond.getAndSet(0);
            if (joins >= ATTACK_MODE_THRESHOLD) {
                if (!attackMode) {
                    attackMode = true;
                    log.warn("[AntiBot] ОБНАРУЖЕНА БОТ-АТАКА! Включен защитный режим фильтрации (Attack Mode).");
                }
            } else if (attackMode && joins < 2) {
                attackMode = false;
                log.info("[AntiBot] Бот-атака завершилась. Защитный режим выключен.");
            }
        }).repeat(TaskSchedule.seconds(1)).schedule();

        // 1. Проверка на этапе конфигурации подключения
        handler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            String ip = extractIp(player.getPlayerConnection().getRemoteAddress());
            long now = System.currentTimeMillis();

            globalJoinsThisSecond.incrementAndGet();

            // Если включен режим атаки, блокируем быстро повторяющиеся входы
            if (attackMode) {
                List<Long> history = joinHistoryByIp.getOrDefault(ip, Collections.emptyList());
                if (!history.isEmpty() && now - history.get(history.size() - 1) < 4000) {
                    player.kick(Component.text("Сервер находится под защитой от ботов. Повторите попытку через пару секунд.", NamedTextColor.RED));
                    return;
                }
            }

            // 2. Лимит частоты подключений с одного IP (Rate Limit)
            joinHistoryByIp.compute(ip, (k, list) -> {
                if (list == null) list = new ArrayList<>();
                list.removeIf(time -> now - time > 5000);
                list.add(now);
                return list;
            });

            List<Long> recentJoins = joinHistoryByIp.get(ip);
            if (recentJoins != null && recentJoins.size() > MAX_JOINS_PER_IP_5SEC) {
                player.kick(Component.text("Слишком частые подключения с вашего IP. Подождите 5 секунд.", NamedTextColor.RED));
                return;
            }

            // 3. Лимит одновременных аккаунтов на один IP
            int currentOnline = onlineByIp.getOrDefault(ip, 0);
            if (currentOnline >= MAX_ACCOUNTS_PER_IP) {
                player.kick(Component.text("Превышен лимит одновременных аккаунтов на один IP (макс. " + MAX_ACCOUNTS_PER_IP + ").", NamedTextColor.RED));
                return;
            }

            onlineByIp.put(ip, currentOnline + 1);
            unverifiedPlayers.add(player.getUuid());
        });

        // 4. Проверка реального движения игрока
        handler.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            if (unverifiedPlayers.contains(player.getUuid())) {
                if (event.getNewPosition().distanceSquared(player.getPosition()) > 0.001 
                        || Math.abs(event.getNewPosition().yaw() - player.getPosition().yaw()) > 1.0f 
                        || Math.abs(event.getNewPosition().pitch() - player.getPosition().pitch()) > 1.0f) {
                    unverifiedPlayers.remove(player.getUuid());
                }
            }
        });

        // 5. Блокировка команд/сообщений/дропов от ботов без движения
        handler.addListener(PlayerChatEvent.class, event -> {
            if (unverifiedPlayers.contains(event.getPlayer().getUuid())) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(Component.text("Сделайте шаг для завершения проверки на бота!", NamedTextColor.YELLOW));
            }
        });

        handler.addListener(PlayerCommandEvent.class, event -> {
            if (unverifiedPlayers.contains(event.getPlayer().getUuid())) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(Component.text("Сделайте шаг для доступа к командам!", NamedTextColor.YELLOW));
            }
        });

        handler.addListener(ItemDropEvent.class, event -> {
            if (event.getEntity() instanceof Player player && unverifiedPlayers.contains(player.getUuid())) {
                event.setCancelled(true);
            }
        });

        // 6. Очистка при отключении игрока
        handler.addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();
            unverifiedPlayers.remove(player.getUuid());

            String ip = extractIp(player.getPlayerConnection().getRemoteAddress());
            onlineByIp.computeIfPresent(ip, (k, count) -> count > 1 ? count - 1 : null);
        });
    }

    private static String extractIp(SocketAddress address) {
        if (address == null) return "127.0.0.1";
        String str = address.toString();
        if (str.startsWith("/")) str = str.substring(1);
        int colon = str.indexOf(':');
        if (colon != -1) str = str.substring(0, colon);
        return str;
    }
}
