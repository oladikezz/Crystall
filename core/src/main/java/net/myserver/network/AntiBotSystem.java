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

/**
 * Высокопроизводительная система защиты от бот-атак с фиксированными кольцевыми буферами.
 */
public class AntiBotSystem {
    private static final Logger log = LoggerFactory.getLogger(AntiBotSystem.class);

    private static final int MAX_ACCOUNTS_PER_IP = 3;
    private static final int MAX_JOINS_PER_IP_5SEC = 3;
    private static final int ATTACK_MODE_THRESHOLD = 8;

    private static class IpJoinHistory {
        long[] timestamps = new long[8];
        int count = 0;
        int head = 0;

        synchronized boolean addAndCheckRateLimit(long now, int maxJoins) {
            // Очищаем старые таймстемпы (> 5000 мс)
            int active = 0;
            for (int i = 0; i < count; i++) {
                int idx = (head - 1 - i + 8) % 8;
                if (now - timestamps[idx] <= 5000) {
                    active++;
                }
            }

            timestamps[head] = now;
            head = (head + 1) % 8;
            if (count < 8) count++;

            return active >= maxJoins;
        }

        synchronized long getLastJoin() {
            if (count == 0) return 0;
            int lastIdx = (head - 1 + 8) % 8;
            return timestamps[lastIdx];
        }
    }

    private static final Map<String, IpJoinHistory> joinHistoryByIp = new ConcurrentHashMap<>();
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

            IpJoinHistory history = joinHistoryByIp.computeIfAbsent(ip, k -> new IpJoinHistory());

            // Если включен режим атаки, блокируем быстро повторяющиеся входы
            if (attackMode) {
                long lastJoin = history.getLastJoin();
                if (lastJoin > 0 && now - lastJoin < 4000) {
                    player.kick(Component.text("Сервер находится под защитой от ботов. Повторите попытку через пару секунд.", NamedTextColor.RED));
                    return;
                }
            }

            // 2. Лимит частоты подключений с одного IP (Rate Limit через кольцевой буфер)
            boolean rateLimited = history.addAndCheckRateLimit(now, MAX_JOINS_PER_IP_5SEC);
            if (rateLimited) {
                player.kick(Component.text("Слишком частые подключения с вашего IP. Подождите 5 секунд.", NamedTextColor.RED));
                return;
            }

            // 3. Лимит одновременных аккаунтов на один IP (не применяется к localhost / proxy)
            int currentOnline = onlineByIp.getOrDefault(ip, 0);
            if (!ip.equals("127.0.0.1") && !ip.equals("localhost") && currentOnline >= MAX_ACCOUNTS_PER_IP) {
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
