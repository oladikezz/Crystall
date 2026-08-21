package ru.lor.watcher.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;
import ru.lor.watcher.events.WatcherDespawnEvent;
import ru.lor.watcher.model.WatcherBehaviorType;
import ru.lor.watcher.model.WatcherLog;
import ru.lor.watcher.model.WatcherPositionType;
import ru.lor.watcher.model.WatcherSpawnSettings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class TelegramBotManager {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final List<String> PRESET_PHRASES = List.of(
            "Смотрящий выражает ледяной холод...",
            "Смотрящий выражает глубокий интерес...",
            "Смотрящий заглядывает в твою душу...",
            "Смотрящий безмолвно замер за твоей спиной...",
            "Смотрящий чувствует первобытный страх...",
            "Смотрящий сжимает пространство вокруг тебя...",
            "Смотрящий шепчет из абсолютной темноты...",
            "Смотрящий запечатывает твой разум...",
            "Смотрящий растворяется в небытии...",
            "Смотрящий ждёт твоего последнего шага..."
    );

    private static final long REJECTION_LOG_INTERVAL_MILLIS = 60_000L;

    private final WatcherPlugin plugin;
    private final HttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private volatile ScheduledTask pollTask;
    private volatile long lastRejectionLog = 0L;
    private volatile long lastUpdateId = 0;

    // Temporary user configurations in Telegram before spawning (ChatID -> Settings)
    private final Map<Long, WatcherSpawnSettings> pendingSettings = new ConcurrentHashMap<>();

    public TelegramBotManager(WatcherPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void start() {
        if (!plugin.getConfigManager().getConfig().getBoolean("telegram.enabled", false)) {
            return;
        }

        String token = plugin.getConfigManager().getConfig().getString("telegram.bot-token", "");
        if (token == null || token.isBlank() || token.equalsIgnoreCase("YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("[TelegramBot] Token not set in config.yml (telegram.bot-token)");
            return;
        }

        String adminChatId = plugin.getConfigManager().getConfig().getString("telegram.admin-chat-id", "");
        if (adminChatId == null || adminChatId.isBlank()) {
            plugin.getLogger().severe("[TelegramBot] telegram.admin-chat-id не задан — бот НЕ запущен. "
                    + "Без него любой, кто знает токен, смог бы управлять Смотрящим.");
            return;
        }

        running.set(true);
        plugin.getLogger().info("[TelegramBot] Starting Telegram Bot Long-Polling & Event Logger...");

        sendSystemLog("🟢 <b>Плагин Watcher (Смотрящий) успешно запущен!</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👥 <b>Игроков онлайн:</b> <code>" + Bukkit.getOnlinePlayers().size() + "</code>\n" +
                "🕒 <b>Время сервера:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>\n" +
                "🤖 <i>Telegram Бот & Система Логирования активны.</i>");

        pollTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin.getBukkitPlugin(), task -> {
            if (!running.get()) {
                task.cancel();
                return;
            }
            pollUpdates(token);
        }, 1, 2, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void stop() {
        if (running.getAndSet(false)) {
            sendSystemLog("🔴 <b>Плагин Watcher (Смотрящий) остановлен/перезагружается.</b>");
        }
        ScheduledTask task = this.pollTask;
        this.pollTask = null;
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignored) {
            }
        }
    }

    private void pollUpdates(String token) {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + token + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=5";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                parseAndProcessUpdates(token, resp.body());
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Telegram polling error", t);
        } finally {
            polling.set(false);
        }
    }

    private void parseAndProcessUpdates(String token, String json) {
        if (json == null || json.isBlank()) {
            return;
        }

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return;
            }
            root = parsed.getAsJsonObject();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINE, "Telegram JSON parse error", throwable);
            return;
        }

        if (!optBoolean(root, "ok") || !root.has("result") || !root.get("result").isJsonArray()) {
            return;
        }

        JsonArray updates = root.getAsJsonArray("result");
        for (JsonElement element : updates) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject update = element.getAsJsonObject();

            long updateId = optLong(update, "update_id", -1L);
            if (updateId > lastUpdateId) {
                lastUpdateId = updateId;
            }

            try {
                processUpdate(token, update);
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.FINE, "Telegram update handling error", throwable);
            }
        }
    }

    private void processUpdate(String token, JsonObject update) {
        JsonObject message = optObject(update, "message");
        if (message != null) {
            long chatId = optLong(optObject(message, "chat"), "id", Long.MIN_VALUE);
            String text = optString(message, "text");
            if (chatId != Long.MIN_VALUE && text != null && !text.isBlank()) {
                handleChatMessage(token, chatId, text);
            }
            return;
        }

        JsonObject callback = optObject(update, "callback_query");
        if (callback == null) {
            return;
        }

        long chatId = optLong(optObject(callback, "from"), "id", Long.MIN_VALUE);
        String data = optString(callback, "data");
        if (chatId == Long.MIN_VALUE || data == null || data.isBlank()) {
            return;
        }

        if (!isAuthorized(chatId)) {
            logRejected(chatId);
            return;
        }

        String queryId = optString(callback, "id");
        if (queryId != null) {
            answerCallbackQuery(token, queryId);
        }
        handleCallbackQuery(token, chatId, data);
    }

    private boolean optBoolean(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return false;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Throwable throwable) {
            return false;
        }
    }

    private JsonObject optObject(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
    }

    private String optString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsString();
        } catch (Throwable throwable) {
            return null;
        }
    }

    private long optLong(JsonObject object, String key, long fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsLong();
        } catch (Throwable throwable) {
            return fallback;
        }
    }

    private boolean isAuthorized(long chatId) {
        String allowed = plugin.getConfigManager().getConfig().getString("telegram.admin-chat-id", "");
        if (allowed == null || allowed.isBlank()) {
            return false;
        }
        for (String id : allowed.split(",")) {
            if (id.trim().equals(String.valueOf(chatId))) {
                return true;
            }
        }
        return false;
    }

    private void logRejected(long chatId) {
        long now = System.currentTimeMillis();
        if (now - lastRejectionLog < REJECTION_LOG_INTERVAL_MILLIS) {
            return;
        }
        lastRejectionLog = now;
        plugin.getLogger().warning("[TelegramBot] Отклонён запрос от неавторизованного чата " + chatId);
    }

    private void handleChatMessage(String token, long chatId, String text) {
        if (!isAuthorized(chatId)) {
            logRejected(chatId);
            return;
        }

        String lower = text.trim().toLowerCase(Locale.ROOT);

        if (lower.startsWith("/start") || lower.startsWith("/menu") || lower.startsWith("/help")) {
            sendMainMenu(token, chatId);
        } else if (lower.startsWith("/players") || lower.startsWith("/list") || lower.startsWith("/online")) {
            sendPlayersMenu(token, chatId);
        } else if (lower.startsWith("/spawn")) {
            String[] parts = text.trim().split("\\s+", 2);
            if (parts.length < 2) {
                sendMessage(token, chatId, "⚠️ <b>Использование:</b> <code>/spawn <имя_игрока></code>");
            } else {
                spawnWatcherTelegram(token, chatId, parts[1], getOrCreateSettings(chatId));
            }
        } else if (lower.startsWith("/despawn")) {
            String[] parts = text.trim().split("\\s+", 2);
            if (parts.length < 2) {
                sendMessage(token, chatId, "⚠️ <b>Использование:</b> <code>/despawn <имя_игрока></code>");
            } else {
                despawnWatcherTelegram(token, chatId, parts[1]);
            }
        } else if (lower.startsWith("/jumpscare")) {
            String[] parts = text.trim().split("\\s+", 2);
            if (parts.length < 2) {
                sendMessage(token, chatId, "⚠️ <b>Использование:</b> <code>/jumpscare <имя_игрока></code>");
            } else {
                triggerJumpscareTelegram(token, chatId, parts[1]);
            }
        } else if (lower.startsWith("/say") || lower.startsWith("/message")) {
            String[] parts = text.trim().split("\\s+", 3);
            if (parts.length < 3) {
                sendMessage(token, chatId, "⚠️ <b>Использование:</b> <code>/say <имя_игрока> <текст></code>");
            } else {
                sendMessageToPlayerTelegram(token, chatId, parts[1], parts[2], false);
            }
        } else if (lower.startsWith("/broadcast")) {
            String[] parts = text.trim().split("\\s+", 2);
            if (parts.length < 2) {
                sendMessage(token, chatId, "⚠️ <b>Использование:</b> <code>/broadcast <текст></code>");
            } else {
                broadcastMessageTelegram(token, chatId, parts[1]);
            }
        } else if (lower.startsWith("/logs")) {
            sendLogsMenu(token, chatId);
        } else if (lower.startsWith("/settings")) {
            sendSettingsMenu(token, chatId);
        } else {
            sendMainMenu(token, chatId);
        }
    }

    private void handleCallbackQuery(String token, long chatId, String data) {
        if (!isAuthorized(chatId)) {
            logRejected(chatId);
            return;
        }

        if (data.equals("menu_main")) {
            sendMainMenu(token, chatId);
        } else if (data.equals("menu_players")) {
            sendPlayersMenu(token, chatId);
        } else if (data.equals("menu_settings")) {
            sendSettingsMenu(token, chatId);
        } else if (data.equals("menu_logs")) {
            sendLogsMenu(token, chatId);
        } else if (data.startsWith("inspect_")) {
            String target = data.replace("inspect_", "");
            sendPlayerInspector(token, chatId, target);
        } else if (data.startsWith("cfg_spawn_")) {
            String target = data.replace("cfg_spawn_", "");
            sendSpawnConfigMenu(token, chatId, target);
        } else if (data.startsWith("menu_phrases_")) {
            String target = data.replace("menu_phrases_", "");
            sendPhrasesMenu(token, chatId, target);
        } else if (data.startsWith("act_spawn_")) {
            String target = data.replace("act_spawn_", "");
            spawnWatcherTelegram(token, chatId, target, getOrCreateSettings(chatId));
        } else if (data.startsWith("act_despawn_")) {
            String target = data.replace("act_despawn_", "");
            despawnWatcherTelegram(token, chatId, target);
        } else if (data.startsWith("act_jumpscare_")) {
            String target = data.replace("act_jumpscare_", "");
            triggerJumpscareTelegram(token, chatId, target);
        } else if (data.startsWith("set_dist_")) {
            // set_dist_5.0_PlayerName
            String[] parts = data.split("_", 4);
            double dist = Double.parseDouble(parts[2]);
            String target = parts[3];
            getOrCreateSettings(chatId).setSpawnDistance(dist);
            sendSpawnConfigMenu(token, chatId, target);
        } else if (data.startsWith("set_pos_")) {
            // set_pos_BEHIND_PlayerName
            String[] parts = data.split("_", 4);
            WatcherPositionType pos = WatcherPositionType.valueOf(parts[2]);
            String target = parts[3];
            getOrCreateSettings(chatId).setPositionType(pos);
            sendSpawnConfigMenu(token, chatId, target);
        } else if (data.startsWith("set_beh_")) {
            // set_beh_STALKER_PlayerName
            String[] parts = data.split("_", 4);
            WatcherBehaviorType beh = WatcherBehaviorType.valueOf(parts[2]);
            String target = parts[3];
            getOrCreateSettings(chatId).setBehaviorType(beh);
            sendSpawnConfigMenu(token, chatId, target);
        } else if (data.startsWith("set_snd_")) {
            // set_snd_ANCIENT_HORROR_PlayerName
            String[] parts = data.split("_", 4);
            String sound = parts[2];
            String target = parts[3];
            getOrCreateSettings(chatId).setSoundPreset(sound);
            sendSpawnConfigMenu(token, chatId, target);
        } else if (data.startsWith("sendp_")) {
            // sendp_0_PlayerName
            String[] parts = data.split("_", 3);
            int idx = Integer.parseInt(parts[1]);
            String target = parts[2];
            String phrase = PRESET_PHRASES.get(idx);
            sendMessageToPlayerTelegram(token, chatId, target, phrase, false);
        } else if (data.startsWith("toggle_")) {
            handleSettingToggle(token, chatId, data.replace("toggle_", ""));
        } else if (data.equals("despawn_all")) {
            plugin.getWatcherManager().despawnAll();
            sendMessage(token, chatId, "🧹 <b>Все активные Смотрящие на сервере принудительно удалены!</b>");
            sendMainMenu(token, chatId);
        }
    }

    private WatcherSpawnSettings getOrCreateSettings(long chatId) {
        return pendingSettings.computeIfAbsent(chatId, k -> {
            WatcherSpawnSettings s = new WatcherSpawnSettings();
            s.setSpawnDistance(4.5);
            s.setDurationSeconds(30);
            s.setPositionType(WatcherPositionType.BEHIND);
            s.setBehaviorType(WatcherBehaviorType.STATIC);
            s.setSoundPreset("ANCIENT_HORROR");
            s.setFreezingEnabled(true);
            s.setJumpscareEnabled(true);
            s.setAiMessageEnabled(true);
            return s;
        });
    }

    // ==========================================
    // GUI DASHBOARD MENUS
    // ==========================================

    private void sendMainMenu(String token, long chatId) {
        int online = Bukkit.getOnlinePlayers().size();
        int active = plugin.getWatcherManager().getActiveWatchersCount();
        long maxMem = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;

        String msg = "🌌 <b>WATCHER CONTROL PANEL v2.0</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "🟢 <b>Статус:</b> <i>В сети & Мониторит</i>\n" +
                "👥 <b>Игроков онлайн:</b> <code>" + online + "</code>\n" +
                "👁 <b>Смотрящих активно:</b> <code>" + active + "</code>\n" +
                "💾 <b>ОЗУ Сервера:</b> <code>" + usedMem + "MB / " + maxMem + "MB</code>\n" +
                "🕒 <b>Время:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>\n\n" +
                "<i>Выберите раздел управления ниже:</i>";

        String keyboard = """
                {"inline_keyboard": [
                    [{"text": "👥 Список Игроков & Инспектор", "callback_data": "menu_players"}],
                    [{"text": "⚙️ Настройки & Авто-Спавн", "callback_data": "menu_settings"}, {"text": "📋 Живой Лог Событий", "callback_data": "menu_logs"}],
                    [{"text": "🧹 Снять всех Смотрящих", "callback_data": "despawn_all"}, {"text": "🔄 Обновить", "callback_data": "menu_main"}]
                ]}
                """;

        sendMessageWithKeyboard(token, chatId, msg, keyboard);
    }

    private void sendPlayersMenu(String token, long chatId) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            String msg = "👥 <b>ИГРОКИ ОНЛАЙН</b>\n━━━━━━━━━━━━━━━━━━━━━━\nНа сервере сейчас никого нет.";
            String keyboard = "{\"inline_keyboard\": [[{\"text\": \"← Назад в Меню\", \"callback_data\": \"menu_main\"}]]}";
            sendMessageWithKeyboard(token, chatId, msg, keyboard);
            return;
        }

        StringBuilder sb = new StringBuilder("👥 <b>ВЫБЕРИТЕ ИГРОКА ДЛЯ УПРАВЛЕНИЯ</b>\n━━━━━━━━━━━━━━━━━━━━━━\n");
        List<String> rows = new ArrayList<>();

        for (Player p : players) {
            boolean active = plugin.getWatcherManager().hasWatcher(p);
            String status = active ? "👁 [Смотрящий]" : "💤";
            sb.append("• <b>").append(p.getName()).append("</b> ").append(status).append("\n");

            rows.add("[{\"text\": \"" + (active ? "👁 " : "👤 ") + p.getName() + " (Инспектор)\", \"callback_data\": \"inspect_" + p.getName() + "\"}]");
        }
        rows.add("[{\"text\": \"← Назад в Главное Меню\", \"callback_data\": \"menu_main\"}]");

        String keyboard = "{\"inline_keyboard\": [" + String.join(",", rows) + "]}";
        sendMessageWithKeyboard(token, chatId, sb.toString(), keyboard);
    }

    private void sendPlayerInspector(String token, long chatId, String playerName) {
        Player p = Bukkit.getPlayerExact(playerName);
        if (p == null || !p.isOnline()) {
            sendMessage(token, chatId, "❌ <b>Игрок '" + playerName + "' не в сети.</b>");
            sendPlayersMenu(token, chatId);
            return;
        }

        Location loc = p.getLocation();
        boolean hasWatcher = plugin.getWatcherManager().hasWatcher(p);
        ItemStack hand = p.getInventory().getItemInMainHand();
        String item = (hand != null && !hand.getType().isAir()) ? hand.getType().name() : "Пусто";
        int light = loc.getBlock().getLightLevel();
        String timeStr = loc.getWorld().getTime() > 13000 ? "🌙 Ночь" : "☀️ День";

        String msg = "👤 <b>ИНСПЕКТОР ИГРОКА:</b> <code>" + p.getName() + "</code>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "💖 <b>Здоровье:</b> <code>" + String.format("%.1f", p.getHealth()) + " / 20.0</code> HP | 🍗 <code>" + p.getFoodLevel() + "/20</code>\n" +
                "📍 <b>Координаты:</b> <code>X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + "</code>\n" +
                "🌲 <b>Биом:</b> <code>" + loc.getBlock().getBiome().getKey().getKey() + "</code> (" + loc.getWorld().getName() + ")\n" +
                "💡 <b>Освещение:</b> <code>" + light + "/15</code> (" + timeStr + ")\n" +
                "🗡 <b>Предмет в руке:</b> <code>" + item + "</code>\n" +
                "👁 <b>Статус Смотрящего:</b> " + (hasWatcher ? "<b>АКТИВЕН 👁</b>" : "<i>Отсутствует</i>") + "\n\n" +
                "<i>Выберите действие для этого игрока:</i>";

        List<String> rows = new ArrayList<>();
        if (hasWatcher) {
            rows.add("[{\"text\": \"❌ Снять Смотрящего\", \"callback_data\": \"act_despawn_" + p.getName() + "\"}, {\"text\": \"😱 Запустить Скример\", \"callback_data\": \"act_jumpscare_" + p.getName() + "\"}]");
        } else {
            rows.add("[{\"text\": \"👁 Быстрый Спавн (Дефолт)\", \"callback_data\": \"act_spawn_" + p.getName() + "\"}]");
            rows.add("[{\"text\": \"⚙️ Настроить параметры спавна\", \"callback_data\": \"cfg_spawn_" + p.getName() + "\"}]");
        }

        rows.add("[{\"text\": \"💬 Сказать атмосферную фразу\", \"callback_data\": \"menu_phrases_" + p.getName() + "\"}]");
        rows.add("[{\"text\": \"👥 К списку игроков\", \"callback_data\": \"menu_players\"}, {\"text\": \"← Главное Меню\", \"callback_data\": \"menu_main\"}]");

        String keyboard = "{\"inline_keyboard\": [" + String.join(",", rows) + "]}";
        sendMessageWithKeyboard(token, chatId, msg, keyboard);
    }

    private void sendSpawnConfigMenu(String token, long chatId, String playerName) {
        WatcherSpawnSettings s = getOrCreateSettings(chatId);

        String msg = "⚙️ <b>КОНСТРУКТОР СПАВНА:</b> <code>" + playerName + "</code>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📏 <b>Дистанция:</b> <code>" + s.getSpawnDistance() + "м</code>\n" +
                "🧭 <b>Позиция:</b> <code>" + s.getPositionType().name() + "</code>\n" +
                "🧠 <b>Поведение:</b> <code>" + s.getBehaviorType().name() + "</code>\n" +
                "🔊 <b>Звуковой пресет:</b> <code>" + s.getSoundPreset() + "</code>\n\n" +
                "<i>Нажимайте на кнопки для изменения параметров перед спавном:</i>";

        List<String> rows = new ArrayList<>();
        // Row 1: Distances
        rows.add("[{\"text\": \"2м\", \"callback_data\": \"set_dist_2.0_" + playerName + "\"}, {\"text\": \"4.5м\", \"callback_data\": \"set_dist_4.5_" + playerName + "\"}, {\"text\": \"8м\", \"callback_data\": \"set_dist_8.0_" + playerName + "\"}, {\"text\": \"15м\", \"callback_data\": \"set_dist_15.0_" + playerName + "\"}]");
        // Row 2: Positions
        rows.add("[{\"text\": \"Сзади\", \"callback_data\": \"set_pos_BEHIND_" + playerName + "\"}, {\"text\": \"Слева\", \"callback_data\": \"set_pos_LEFT_" + playerName + "\"}, {\"text\": \"Справа\", \"callback_data\": \"set_pos_RIGHT_" + playerName + "\"}]");
        rows.add("[{\"text\": \"Спереди\", \"callback_data\": \"set_pos_FRONT_" + playerName + "\"}, {\"text\": \"Сверху\", \"callback_data\": \"set_pos_ROOF_" + playerName + "\"}, {\"text\": \"Случайно\", \"callback_data\": \"set_pos_RANDOM_" + playerName + "\"}]");
        // Row 3: Behaviors
        rows.add("[{\"text\": \"Статичный\", \"callback_data\": \"set_beh_STATIC_" + playerName + "\"}, {\"text\": \"Сталкер\", \"callback_data\": \"set_beh_STALKER_" + playerName + "\"}, {\"text\": \"Моргающий\", \"callback_data\": \"set_beh_BLINKING_" + playerName + "\"}]");
        // Row 4: Sounds
        rows.add("[{\"text\": \"Древний Ужас\", \"callback_data\": \"set_snd_ANCIENT_HORROR_" + playerName + "\"}, {\"text\": \"Шёпот\", \"callback_data\": \"set_snd_SPECTRAL_WHISPER_" + playerName + "\"}]");
        // Row 5: Action Launch!
        rows.add("[{\"text\": \"🚀 ЗАСПАВНИТЬ С ЭТИМИ ПАРАМЕТРАМИ\", \"callback_data\": \"act_spawn_" + playerName + "\"}]");
        rows.add("[{\"text\": \"← Назад в Инспектор\", \"callback_data\": \"inspect_" + playerName + "\"}]");

        String keyboard = "{\"inline_keyboard\": [" + String.join(",", rows) + "]}";
        sendMessageWithKeyboard(token, chatId, msg, keyboard);
    }

    private void sendPhrasesMenu(String token, long chatId, String targetPlayer) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < PRESET_PHRASES.size(); i++) {
            String p = PRESET_PHRASES.get(i);
            if (i > 0) rows.append(",");
            rows.append("[{\"text\": \"📜 ").append(p.length() > 34 ? p.substring(0, 34) + "..." : p).append("\", \"callback_data\": \"sendp_").append(i).append("_").append(targetPlayer).append("\"}]");
        }
        rows.append(",[{\"text\": \"← Назад в Инспектор\", \"callback_data\": \"inspect_").append(targetPlayer).append("\"}]");

        String msg = "💬 <b>АТМОСФЕРНЫЕ ФРАЗЫ СМОТРЯЩЕГО</b>\n━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 Цель: <code>" + targetPlayer + "</code>\n\n" +
                "<i>Нажмите на фразу, чтобы Смотрящий произнес её игроку:</i>";
        String keyboard = "{\"inline_keyboard\": [" + rows + "]}";
        sendMessageWithKeyboard(token, chatId, msg, keyboard);
    }

    private void sendSettingsMenu(String token, long chatId) {
        boolean auto = plugin.getAutonomousStalkerManager() != null ? plugin.getAutonomousStalkerManager().isEnabled() : plugin.getConfigManager().getConfig().getBoolean("autonomous.enabled", true);
        boolean stream = plugin.getAutonomousStalkerManager() != null ? plugin.getAutonomousStalkerManager().isMediaTriggerEnabled() : plugin.getConfigManager().getConfig().getBoolean("streamer-trigger.enabled", true);
        boolean ai = plugin.getConfigManager().getConfig().getBoolean("ai.enabled", true);
        boolean freeze = plugin.getConfigManager().getConfig().getBoolean("freezing.enabled", true);

        String msg = "⚙️ <b>НАСТРОЙКИ СЕРВЕРА & ТРИГГЕРОВ</b>\n━━━━━━━━━━━━━━━━━━━━━━\n" +
                "• <b>Авто-Спавн (1-5 раз в день):</b> " + (auto ? "🟢 ВКЛ" : "🔴 ВЫКЛ") + "\n" +
                "• <b>Стримерский Триггер:</b> " + (stream ? "🟢 ВКЛ" : "🔴 ВЫКЛ") + "\n" +
                "• <b>ИИ Ответы в Чате:</b> " + (ai ? "🟢 ВКЛ" : "🔴 ВЫКЛ") + "\n" +
                "• <b>Аура Заморозки:</b> " + (freeze ? "🟢 ВКЛ" : "🔴 ВЫКЛ") + "\n\n" +
                "<i>Нажимайте на кнопки для переключения:</i>";

        String keyboard = String.format("""
                {"inline_keyboard": [
                    [{"text": "🤖 Авто-Спавн: %s", "callback_data": "toggle_autonomous"}],
                    [{"text": "🎥 Стримерский Триггер: %s", "callback_data": "toggle_streamer"}],
                    [{"text": "🧠 ИИ-Ответы в чате: %s", "callback_data": "toggle_ai"}],
                    [{"text": "❄️ Аура Заморозки: %s", "callback_data": "toggle_freezing"}],
                    [{"text": "← Главное Меню", "callback_data": "menu_main"}]
                ]}
                """, auto ? "🟢 ВКЛ" : "🔴 ВЫКЛ", stream ? "🟢 ВКЛ" : "🔴 ВЫКЛ", ai ? "🟢 ВКЛ" : "🔴 ВЫКЛ", freeze ? "🟢 ВКЛ" : "🔴 ВЫКЛ");

        sendMessageWithKeyboard(token, chatId, msg, keyboard);
    }

    private void handleSettingToggle(String token, long chatId, String settingKey) {
        String path = switch (settingKey) {
            case "autonomous" -> "autonomous.enabled";
            case "streamer" -> "streamer-trigger.enabled";
            case "ai" -> "ai.enabled";
            case "freezing" -> "freezing.enabled";
            default -> null;
        };

        if (path != null) {
            boolean current = plugin.getConfigManager().getConfig().getBoolean(path, true);
            boolean updated = !current;
            plugin.getConfigManager().getConfig().set(path, updated);
            plugin.getConfigManager().saveConfig();

            // Synchronize with active runtime manager instances immediately!
            if (settingKey.equals("autonomous") && plugin.getAutonomousStalkerManager() != null) {
                plugin.getAutonomousStalkerManager().setEnabled(updated);
            } else if (settingKey.equals("streamer") && plugin.getAutonomousStalkerManager() != null) {
                plugin.getAutonomousStalkerManager().setMediaTriggerEnabled(updated);
            }

            logSettingChange("Telegram Admin", path, updated ? "ВКЛ" : "ВЫКЛ");
            sendSettingsMenu(token, chatId);
        }
    }

    private void sendLogsMenu(String token, long chatId) {
        List<WatcherLog> logs = plugin.getLogManager().getLogs();

        StringBuilder sb = new StringBuilder("📋 <b>ПОСЛЕДНИЕ СОБЫТИЯ СМОТРЯЩЕГО</b>\n━━━━━━━━━━━━━━━━━━━━━━\n");
        if (logs.isEmpty()) {
            sb.append("<i>Лог пуст.</i>\n");
        } else {
            int count = 0;
            for (WatcherLog log : logs) {
                if (++count > 8) break;
                sb.append("• <code>[").append(log.getFormattedTime()).append("]</code> ")
                        .append("Спавн у <b>").append(log.getTargetPlayerName()).append("</b> ")
                        .append("(<i>").append(log.getExecutorName()).append("</i>, ")
                        .append(log.getPosition()).append(")\n");
            }
        }

        String keyboard = "{\"inline_keyboard\": [[{\"text\": \"🔄 Обновить логи\", " +
                "\"callback_data\": \"menu_logs\"}, {\"text\": \"← Главное Меню\", \"callback_data\": \"menu_main\"}]]}";

        sendMessageWithKeyboard(token, chatId, sb.toString(), keyboard);
    }

    // ==========================================
    // ACTION CONTROLLERS
    // ==========================================

    private void spawnWatcherTelegram(String token, long chatId, String playerName, WatcherSpawnSettings settings) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            sendMessage(token, chatId, "❌ <b>Игрок '" + playerName + "' не найден онлайн.</b>");
            return;
        }

        player.getScheduler().run(plugin.getBukkitPlugin(), task -> {
            boolean ok = plugin.getWatcherManager().spawnWatcher(player, settings != null ? settings : new WatcherSpawnSettings(), "TELEGRAM_BOT");
            if (ok) {
                sendMessage(token, chatId, "👁 <b>Смотрящий успешно заспавнен у игрока:</b> <code>" + player.getName() + "</code>!");
                sendPlayerInspector(token, chatId, player.getName());
            } else {
                sendMessage(token, chatId, "⚠️ <b>У игрока '" + player.getName() + "' уже есть активный Смотрящий.</b>");
            }
        }, null);
    }

    private void despawnWatcherTelegram(String token, long chatId, String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            sendMessage(token, chatId, "❌ <b>Игрок '" + playerName + "' не найден онлайн.</b>");
            return;
        }

        if (plugin.getWatcherManager().hasWatcher(player)) {
            plugin.getWatcherManager().despawnWatcher(player.getUniqueId(), WatcherDespawnEvent.DespawnReason.MANUAL_DESPAWN);
            sendMessage(token, chatId, "🗑 <b>Смотрящий успешно убран у игрока:</b> <code>" + player.getName() + "</code>.");
            sendPlayerInspector(token, chatId, player.getName());
        } else {
            sendMessage(token, chatId, "⚠️ <b>У игрока '" + player.getName() + "' нет активного Смотрящего.</b>");
        }
    }

    private void triggerJumpscareTelegram(String token, long chatId, String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            sendMessage(token, chatId, "❌ <b>Игрок '" + playerName + "' не найден онлайн.</b>");
            return;
        }

        player.getScheduler().run(plugin.getBukkitPlugin(), task -> {
            if (plugin.getWatcherManager().hasWatcher(player)) {
                plugin.getWatcherManager().getWatcher(player).triggerApproachReaction(player);
                sendMessage(token, chatId, "😱 <b>Скример принудительно запущен на игрока:</b> <code>" + player.getName() + "</code>!");
                sendPlayerInspector(token, chatId, player.getName());
            } else {
                sendMessage(token, chatId, "⚠️ <b>У игрока '" + player.getName() + "' нет активного Смотрящего.</b>");
            }
        }, null);
    }

    private void sendMessageToPlayerTelegram(String token, long chatId, String playerName, String message, boolean broadcast) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            sendMessage(token, chatId, "❌ <b>Игрок '" + playerName + "' не найден онлайн.</b>");
            return;
        }

        player.getScheduler().run(plugin.getBukkitPlugin(), task -> {
            String broadcastFormat = plugin.getConfigManager().getBroadcastFormat();
            String formatted = broadcastFormat.replace("{message}", message);
            String actionBarText = "<#a855f7><b>[Смотрящий]</b></#a855f7> <white>" + message + "</white>";

            if (broadcast) {
                Bukkit.broadcast(ru.lor.watcher.utils.ColorUtil.parse(formatted));
                for (Player onlineP : Bukkit.getOnlinePlayers()) {
                    ru.lor.watcher.utils.ColorUtil.sendActionBarPersistent(plugin.getBukkitPlugin(), onlineP, actionBarText, 5);
                }
                sendMessage(token, chatId, "📢 <b>Фраза отправлена ВСЕМ игрокам:</b>\n«<i>" + message + "</i>»");
            } else {
                player.sendMessage(ru.lor.watcher.utils.ColorUtil.parse(formatted));
                ru.lor.watcher.utils.ColorUtil.sendActionBarPersistent(plugin.getBukkitPlugin(), player, actionBarText, 5);
                sendMessage(token, chatId, "💬 <b>Фраза отправлена игроку</b> <code>" + player.getName() + "</code>:\n«<i>" + message + "</i>»");
            }
        }, null);
    }

    private void broadcastMessageTelegram(String token, long chatId, String message) {
        String broadcastFormat = plugin.getConfigManager().getBroadcastFormat();
        String formatted = broadcastFormat.replace("{message}", message);
        String actionBarText = "<#a855f7><b>[Смотрящий]</b></#a855f7> <white>" + message + "</white>";

        Bukkit.broadcast(ru.lor.watcher.utils.ColorUtil.parse(formatted));
        for (Player onlineP : Bukkit.getOnlinePlayers()) {
            ru.lor.watcher.utils.ColorUtil.sendActionBarPersistent(plugin.getBukkitPlugin(), onlineP, actionBarText, 5);
        }
        sendMessage(token, chatId, "📢 <b>Трансляция на сервер отправлена:</b>\n«<i>" + message + "</i>»");
    }

    // ==========================================
    // COMPREHENSIVE TELEGRAM EVENT LOGGING
    // ==========================================

    private boolean isLoggingEnabled(String subcategory) {
        if (!plugin.getConfigManager().getConfig().getBoolean("telegram.enabled", false)) return false;
        if (!plugin.getConfigManager().getConfig().getBoolean("telegram.logging.enabled", true)) return false;
        if (subcategory != null) {
            return plugin.getConfigManager().getConfig().getBoolean("telegram.logging." + subcategory, true);
        }
        return true;
    }

    private void sendLogToTelegram(String formattedMessage) {
        if (!running.get()) return;

        String token = plugin.getConfigManager().getConfig().getString("telegram.bot-token", "");
        if (token == null || token.isBlank()) return;

        String logChatId = plugin.getConfigManager().getConfig().getString("telegram.log-chat-id", "");
        if (logChatId == null || logChatId.isBlank()) {
            logChatId = plugin.getConfigManager().getConfig().getString("telegram.admin-chat-id", "");
        }

        if (logChatId == null || logChatId.isBlank()) return;

        for (String idStr : logChatId.split(",")) {
            try {
                long targetChatId = Long.parseLong(idStr.trim());
                sendMessage(token, targetChatId, formattedMessage);
            } catch (Exception ignored) {}
        }
    }

    public void logSpawn(Player target, String caller, WatcherSpawnSettings settings) {
        if (!isLoggingEnabled("spawns")) return;

        Location loc = target.getLocation();
        String distStr = settings != null ? String.format("%.1fм", settings.getSpawnDistance()) : "Дефолт";
        String posStr = settings != null ? settings.getPositionType().name() : "BEHIND";
        String behStr = settings != null ? settings.getBehaviorType().name() : "STATIC";

        String msg = "👁 <b>[СПАВН] Смотрящий материализовался!</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 <b>Игрок:</b> <code>" + target.getName() + "</code> (HP: " + String.format("%.1f", target.getHealth()) + "/20)\n" +
                "🕹 <b>Источник:</b> <i>" + caller + "</i>\n" +
                "📍 <b>Локация:</b> <code>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</code> (" + loc.getWorld().getName() + ")\n" +
                "🌲 <b>Биом:</b> <code>" + loc.getBlock().getBiome().getKey().getKey() + "</code>\n" +
                "⚙️ <b>Параметры:</b> " + posStr + " | " + distStr + " | " + behStr + "\n" +
                "🕒 <b>Время:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>";

        sendLogToTelegram(msg);
    }

    public void logDespawn(Player target, WatcherDespawnEvent.DespawnReason reason, long lifetimeSeconds) {
        if (!isLoggingEnabled("despawns")) return;

        String pName = (target != null) ? target.getName() : "Неизвестно";
        String reasonStr = switch (reason) {
            case EXPIRED -> "⏳ Время действия истекло";
            case APPROACHED_OR_ATTACKED -> "😱 Игрок подошёл/атаковал (Скример)";
            case DISTANCE_EXCEEDED -> "🏃 Игрок ушёл слишком далеко";
            case PLAYER_QUIT -> "🚪 Игрок вышел с сервера";
            case MANUAL_DESPAWN -> "🛑 Вручную (Команда/GUI/TG)";
            case PLUGIN_DISABLE -> "🔌 Выключение плагина";
        };

        String msg = "💨 <b>[ДЕСПАВН] Смотрящий исчез во тьме!</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 <b>Игрок:</b> <code>" + pName + "</code>\n" +
                "❓ <b>Причина:</b> <i>" + reasonStr + "</i>\n" +
                "⏱ <b>Время активности:</b> <code>" + lifetimeSeconds + " сек.</code>\n" +
                "🕒 <b>Время:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>";

        sendLogToTelegram(msg);
    }

    public void logJumpscare(Player target, String reason, Location loc) {
        if (!isLoggingEnabled("jumpscares")) return;

        String msg = "😱 <b>[СКРИМЕР] Сработала реакция ужаса!</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 <b>Игрок:</b> <code>" + target.getName() + "</code> (HP: " + String.format("%.1f", target.getHealth()) + "/20)\n" +
                "⚠️ <b>Триггер:</b> <i>" + reason + "</i>\n" +
                "📍 <b>Координаты:</b> <code>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</code>\n" +
                "💫 <b>Эффекты:</b> <i>Тьма, Слепота, Замедление, Тошнота</i>\n" +
                "🕒 <b>Время:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>";

        sendLogToTelegram(msg);
    }

    public void logRitual(Player player, Location loc, String blockType, int soulBlocks) {
        if (!isLoggingEnabled("rituals")) return;

        String msg = "🌌 <b>[РИТУАЛ] Совершён древний ритуал призыва!</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 <b>Призвавший:</b> <code>" + player.getName() + "</code>\n" +
                "🔮 <b>Центральный блок:</b> <code>" + blockType + "</code> (Око Эндера)\n" +
                "🕯 <b>Источников душ рядом:</b> <code>" + soulBlocks + "</code>\n" +
                "📍 <b>Локация:</b> <code>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</code> (" + loc.getWorld().getName() + ")\n" +
                "🕒 <b>Время:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>";

        sendLogToTelegram(msg);
    }

    public void logMediaTrigger(Player player, String mediaLink, String rawMessage) {
        if (!isLoggingEnabled("media-triggers")) return;

        String msg = "🎥 <b>[МЕДИА-ТРИГГЕР] Обнаружена трансляция / ссылка!</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 <b>Стример:</b> <code>" + player.getName() + "</code>\n" +
                "🔗 <b>Ссылка / Триггер:</b> <code>" + escapeHtml(mediaLink) + "</code>\n" +
                "💬 <b>Чат:</b> <i>«" + escapeHtml(rawMessage) + "»</i>\n" +
                "👁 <i>Смотрящий направлен к стримеру для нагнетания атмосферы!</i>\n" +
                "🕒 <b>Время:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>";

        sendLogToTelegram(msg);
    }

    public void logAiDialogue(Player player, String userQuery, String aiResponse, boolean broadcastToAll) {
        if (!isLoggingEnabled("ai-dialogue")) return;

        String scope = broadcastToAll ? "🌐 Весь сервер" : "🤫 Локально (рядом)";
        String msg = "💬 <b>[ИИ-ДИАЛОГ] Смотрящий ответил в чате!</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 <b>Игрок:</b> <code>" + player.getName() + "</code>\n" +
                "❓ <b>Вопрос:</b> <i>«" + (userQuery != null ? escapeHtml(userQuery) : "Появление") + "»</i>\n" +
                "👁 <b>Ответ:</b> <b>«" + escapeHtml(aiResponse) + "»</b>\n" +
                "📢 <b>Зона:</b> " + scope + "\n" +
                "🕒 <b>Время:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>";

        sendLogToTelegram(msg);
    }

    public void logWrathPunishment(Player player, String reason, String playerMessage) {
        if (!isLoggingEnabled("jumpscares")) return;

        Location loc = player.getLocation();
        String msg = "⚡ <b>[ГНЕВ СМОТРЯЩЕГО] Игрок наказан за дерзость!</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 <b>Игрок:</b> <code>" + player.getName() + "</code>\n" +
                "💬 <b>Сказано:</b> <i>«" + escapeHtml(playerMessage) + "»</i>\n" +
                "⚠️ <b>Причина:</b> <i>" + reason + "</i>\n" +
                "📍 <b>Локация:</b> <code>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</code>\n" +
                "❄️ <b>Наложенные кары:</b> <i>Заморозка, Полная тьма, Слепота, Замедление IV, Тошнота</i>\n" +
                "🕒 <b>Время:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>";

        sendLogToTelegram(msg);
    }

    public void logAdminAction(String adminName, String actionDetails) {
        if (!isLoggingEnabled("admin-actions")) return;

        String msg = "👮 <b>[АДМИН] Выполнено действие управления:</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 <b>Админ:</b> <code>" + adminName + "</code>\n" +
                "🛠 <b>Действие:</b> <i>" + escapeHtml(actionDetails) + "</i>\n" +
                "🕒 <b>Время:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>";

        sendLogToTelegram(msg);
    }

    public void logSettingChange(String executor, String settingName, String newValue) {
        if (!isLoggingEnabled("settings-changes")) return;

        String msg = "🔧 <b>[НАСТРОЙКИ] Изменен параметр плагина:</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 <b>Инициатор:</b> <i>" + executor + "</i>\n" +
                "⚙️ <b>Параметр:</b> <code>" + settingName + "</code> ➔ <b>" + newValue + "</b>\n" +
                "🕒 <b>Время:</b> <code>" + LocalDateTime.now().format(TIME_FORMATTER) + "</code>";

        sendLogToTelegram(msg);
    }

    private void sendSystemLog(String text) {
        sendLogToTelegram(text);
    }

    // ==========================================
    // HTTP UTILITIES
    // ==========================================

    private void sendMessage(String token, long chatId, String text) {
        sendTelegramRequest(token, "sendMessage", "chat_id=" + chatId + "&text=" + encode(text) + "&parse_mode=HTML");
    }

    private void sendMessageWithKeyboard(String token, long chatId, String text, String keyboardJson) {
        sendTelegramRequest(token, "sendMessage", "chat_id=" + chatId + "&text=" + encode(text) + "&parse_mode=HTML&reply_markup=" + encode(keyboardJson));
    }

    private void answerCallbackQuery(String token, String callbackQueryId) {
        sendTelegramRequest(token, "answerCallbackQuery", "callback_query_id=" + callbackQueryId);
    }

    private void sendTelegramRequest(String token, String method, String postData) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/" + method))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(postData))
                    .build();

            httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Error sending Telegram request (" + method + ")", t);
        }
    }

    private String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

}
