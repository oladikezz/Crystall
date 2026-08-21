package ru.lor.watcher.events;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.utils.ColorUtil;
import ru.lor.watcher.watcher.WatcherEntity;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class WatcherChatListener implements Listener {

    private static final long RESPONSE_COOLDOWN_MILLIS = 5000L;
    private static final double NEARBY_RADIUS_SQUARED = 256.0;
    private static final double BROADCAST_RADIUS_SQUARED = 1024.0;

    // Profanity and insult roots targeting the Watcher / general toxic rage
    private static final Pattern PROFANITY_PATTERN = Pattern.compile(
            "(?iu).*(?:\\b|\\d|_)(?:ху[йяеё]|пизд|еб[аеёиу]|бля[дт]|сук[аио]|муд[ао]|гондон|шлюх|даун|урод|дебил|лох|сдохни|соси|тварь|чмо|fuck|bitch|shit|asshole|cunt|dick|idiot|stfu|bastard)(?:\\b|\\d|_).*"
    );

    // Lore-related keywords and themes
    private static final Pattern LORE_PATTERN = Pattern.compile(
            "(?iu).*(?:смотрящ|наблюдател|междумир|пустот|око|эндер|глаз|врем[яен]|тайн|секрет|бог|боги|судьб|создател|мистик|ритуал|смерт|вечност|душ[аеи]|тьм|мрак|кто ты|зачем ты|почему ты|где мы|что это|куда ты|откуда ты|покажись|исчезни|проклят|знамени|знак|lore|watcher|void|in-between|god|time|fate|eye|secret).*"
    );

    // Off-topic / trivial questions that Watcher ignores
    private static final Pattern OFFTOPIC_PATTERN = Pattern.compile(
            "(?iu).*(?:дай алмаз|дай рес|как скрафти|где дом|тпни|телепорт|сетхоум|сколько стоит|почем|за скок|как сдела|сервер|донат|правил|админ|хелпер|модер|скинь координаты).*"
    );

    private final WatcherPlugin plugin;
    private final AtomicLong lastResponseTime = new AtomicLong(0L);

    public WatcherChatListener(WatcherPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        // 1. Check Stream / Media Link Trigger
        if (plugin.getAutonomousStalkerManager() != null
                && plugin.getAutonomousStalkerManager().checkMediaTrigger(player, plainMessage)) {
            return;
        }

        // Only react if Watcher is present near the player
        if (!isWatcherNearby(player)) {
            return;
        }

        String lowerMessage = plainMessage.toLowerCase(Locale.ROOT);

        // 2. PROFANITY & INSULTS -> INSTANT WRATH PUNISHMENT!
        if (isProfaneOrInsulting(lowerMessage)) {
            if (!tryClaimCooldown()) {
                return;
            }
            player.getScheduler().run(plugin.getBukkitPlugin(), task -> applyWrathPunishment(player, plainMessage), null);
            return;
        }

        // 3. OFFTOPIC / TRIVIAL -> Complete cold silence
        if (isOfftopic(lowerMessage)) {
            return;
        }

        // 4. LORE QUESTIONS vs GENERAL CHAT
        boolean isLore = isLoreRelated(lowerMessage);
        boolean isDirectlyMentioned = lowerMessage.contains("смотрящий")
                || lowerMessage.contains("наблюдатель")
                || lowerMessage.contains("ты")
                || lowerMessage.contains("?")
                || lowerMessage.contains("кто");

        // Lore questions have 95% chance to answer; general chat has 25% chance
        double chance = isLore ? 0.95 : (isDirectlyMentioned ? 0.25 : 0.10);

        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return; // Watcher chooses to remain silent
        }

        if (!tryClaimCooldown()) {
            return;
        }

        player.getScheduler().run(plugin.getBukkitPlugin(), task -> respond(player, plainMessage, isLore), null);
    }

    private boolean tryClaimCooldown() {
        long now = System.currentTimeMillis();
        while (true) {
            long previous = lastResponseTime.get();
            if (now - previous < RESPONSE_COOLDOWN_MILLIS) {
                return false;
            }
            if (lastResponseTime.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    private void applyWrathPunishment(Player player, String playerMessage) {
        if (!player.isOnline()) return;

        Location loc = player.getLocation();

        // 1. Debuff Curses: Freezing, Slowness IV, Darkness, Blindness, Nausea
        player.setFreezeTicks(160);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 140, 0, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 90, 0, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 3, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, true));

        // 2. Shocking Particles & Sounds
        loc.getWorld().spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 1.0, 0), 40, 0.6, 0.8, 0.6, 0.08);
        loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1.0, 0), 30, 0.4, 0.6, 0.4, 0.04);
        loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 1.2, 0), 25, 0.5, 0.7, 0.5, 0.05);

        try {
            player.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.5f);
            player.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.5f);
            player.playSound(loc, Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.4f);
        } catch (Throwable ignored) {}

        // 3. Wrath Message to Player
        String wrathText = "Смотрящий выражает гнев за твою дерзость... Твой язык скован мраком и льдом...";
        String formatted = "<dark_gray>[<dark_red><b>Гнев Смотрящего</b></dark_red>]</dark_gray> <white>" + wrathText + "</white>";
        String actionBar = "<dark_red><b>[СМОТРЯЩИЙ НАКАЗАЛ ТЕБЯ]</b></dark_red> <white>" + wrathText + "</white>";

        player.sendMessage(ColorUtil.parse(formatted));
        ColorUtil.sendActionBarPersistent(plugin.getBukkitPlugin(), player, actionBar, 6);

        // 4. Log to Telegram
        if (plugin.getTelegramBotManager() != null) {
            plugin.getTelegramBotManager().logWrathPunishment(player, "Оскорбление / нецензурная брань в чате", playerMessage);
        }
    }

    private void respond(Player player, String plainMessage, boolean isLoreQuestion) {
        if (!player.isOnline() || !isWatcherNearby(player)) {
            return;
        }

        WatcherEntity matchedWatcher = plugin.getWatcherManager().getWatcher(player);
        boolean broadcastToAll = matchedWatcher != null && matchedWatcher.getSettings().isBroadcastToAll();

        if (plugin.getAiBrainManager() == null) {
            return;
        }

        plugin.getAiBrainManager().generateChatResponse(player, plainMessage).thenAccept(reply -> {
            if (reply == null || reply.isBlank()) {
                return;
            }
            player.getScheduler().run(plugin.getBukkitPlugin(),
                    task -> deliver(player, plainMessage, reply, broadcastToAll), null);
        });
    }

    private void deliver(Player player, String plainMessage, String reply, boolean broadcastToAll) {
        if (!player.isOnline()) {
            return;
        }

        String safeReply = ColorUtil.escape(reply);
        String formatted = plugin.getConfigManager().getBroadcastFormat().replace("{message}", safeReply);
        String actionBarText = "<#a855f7><b>[Смотрящий]</b></#a855f7> <white>" + safeReply + "</white>";

        if (broadcastToAll) {
            Bukkit.broadcast(ColorUtil.parse(formatted));
            for (Player online : Bukkit.getOnlinePlayers()) {
                ColorUtil.sendActionBarPersistent(plugin.getBukkitPlugin(), online, actionBarText, 5);
            }
        } else {
            player.sendMessage(ColorUtil.parse(formatted));
            ColorUtil.sendActionBarPersistent(plugin.getBukkitPlugin(), player, actionBarText, 5);

            Location origin = player.getLocation();
            for (Player nearby : player.getWorld().getPlayers()) {
                if (nearby.equals(player) || !Bukkit.isOwnedByCurrentRegion(nearby)) {
                    continue;
                }
                if (nearby.getLocation().distanceSquared(origin) > BROADCAST_RADIUS_SQUARED) {
                    continue;
                }
                nearby.sendMessage(ColorUtil.parse(formatted));
                ColorUtil.sendActionBarPersistent(plugin.getBukkitPlugin(), nearby, actionBarText, 5);
            }
        }

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.4f);
            player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.5f, 0.5f);
        } catch (Throwable ignored) {
        }

        if (plugin.getTelegramBotManager() != null) {
            plugin.getTelegramBotManager().logAiDialogue(player, plainMessage, reply, broadcastToAll);
        }
    }

    private boolean isWatcherNearby(Player player) {
        if (plugin.getWatcherManager().hasWatcher(player)) {
            return true;
        }

        Location origin = player.getLocation();
        for (WatcherEntity entity : plugin.getWatcherManager().getActiveWatchers().values()) {
            Player target = entity.getTargetPlayer();
            if (target == null || !target.isOnline() || target.equals(player)) {
                continue;
            }
            if (!Bukkit.isOwnedByCurrentRegion(target)) {
                continue;
            }
            if (!target.getWorld().equals(origin.getWorld())) {
                continue;
            }
            if (target.getLocation().distanceSquared(origin) <= NEARBY_RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private boolean isProfaneOrInsulting(String text) {
        return PROFANITY_PATTERN.matcher(text).matches() ||
                text.contains("иди нахуй") ||
                text.contains("пошел нахуй") ||
                text.contains("пошел в жопу") ||
                text.contains("соси хуй") ||
                text.contains("ебал тебя") ||
                text.contains("ебаный") ||
                text.contains("пидорас") ||
                text.contains("заебал");
    }

    private boolean isLoreRelated(String text) {
        return LORE_PATTERN.matcher(text).matches();
    }

    private boolean isOfftopic(String text) {
        return OFFTOPIC_PATTERN.matcher(text).matches();
    }
}
