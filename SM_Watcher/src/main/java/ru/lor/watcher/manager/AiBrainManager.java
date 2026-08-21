package ru.lor.watcher.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.lor.watcher.WatcherPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class AiBrainManager {

    private static final long PLAYER_COOLDOWN_MILLIS = 30_000L;
    private static final int MAX_CONCURRENT_REQUESTS = 4;
    private static final int MAX_RESPONSE_LENGTH = 160;
    private static final int MAX_TRACKED_PLAYERS = 500;

    private final WatcherPlugin plugin;
    private final HttpClient httpClient;
    private final Map<UUID, Long> lastRequestAt = new ConcurrentHashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger();

    private static final List<String> FALLBACK_SPAWN_MESSAGES = List.of(
            "выражает ледяной интерес к твоим ранам",
            "наблюдает с немым интересом за твоими движениями",
            "замер за твоей спиной во тьме",
            "чувствует как стынет твоя кровь",
            "вслушивается в неравномерный стук твоего сердца",
            "улавливает твой страх перед бездной",
            "шепчет неуловимое проклятие во тьму",
            "растворяется в вечернем тумане",
            "сжимает пространство вокруг твоей тени",
            "хранит абсолютное безмолвие",
            "вглядывается в самую глубину твоей души",
            "отсчитывает секунды твоего присутствия",
            "улавливает мелкую дрожь в твоих руках",
            "выражает тёмное удовлетворение твоим одиночеством"
    );

    private static final List<String> FALLBACK_CHAT_RESPONSES = List.of(
            "Смотрящий выражает ледяное спокойствие к твоим словам...",
            "Смотрящий видит нити твоей судьбы во тьме Междумирья...",
            "Смотрящий хранит безмолвие перед лицом неизбежного...",
            "Смотрящий чувствует, как тает твоё отмеренное время...",
            "Смотрящий заглядывает в бездну твоего смертного разума...",
            "Тьма Междумирья помнит всё, что ты пытаешься скрыть...",
            "Смотрящий наблюдает за каждым шагом твоей неизбежной участи..."
    );

    public AiBrainManager(WatcherPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public CompletableFuture<String> generateWatcherAction(Player player, String userMsg) {
        CompletableFuture<String> future = new CompletableFuture<>();

        if (!plugin.getConfigManager().getConfig().getBoolean("ai.enabled", true)) {
            future.complete(getRandomFallbackSpawnMessage());
            return future;
        }

        String apiKey = plugin.getConfigManager().getConfig().getString("ai.api-key", "");
        if (apiKey.isEmpty() || apiKey.equalsIgnoreCase("YOUR_API_KEY_HERE")) {
            future.complete(getRandomFallbackSpawnMessage());
            return future;
        }

        String endpoint = plugin.getConfigManager().getConfig().getString("ai.endpoint", "https://openrouter.ai/api/v1/chat/completions");
        String model = plugin.getConfigManager().getConfig().getString("ai.model", "openai/gpt-4o-mini");
        String systemPrompt = plugin.getConfigManager().getConfig().getString(
                "ai.system-prompt",
                "Ты — атмосфера мистического древнего Смотрящего в Minecraft. Будь предельно разнообразным, тёмным и загадочным. " +
                        "Твоя задача — сгенерировать ровно ОДНУ короткую атмосферную фразу (4-8 слов), живо реагируя на точную ситуацию игрока.\\n" +
                        "Каждый раз используй разные атмосферные глаголы: 'выражает', 'наблюдает', 'замер', 'чувствует', 'шепчет', 'вслушивается', 'улавливает', 'сжимает', 'растворяется', 'хранит'.\\n" +
                        "ПРИМЕРЫ:\\n" +
                        "- выражает ледяной интерес к твоим глубоким ранам\\n" +
                        "- наблюдает с теневым спокойствием за твоими шагами\\n" +
                        "- замер за твоей спиной во мраке\\n" +
                        "- чувствует как стынет твоя кровь от ужаса\\n" +
                        "- вслушивается в нервный стук твоего сердца\\n" +
                        "ВАЖНО: НЕ пиши слово 'Смотрящий', НЕ используй кавычки!"
        );

        if (!tryAcquireSlot(player.getUniqueId())) {
            future.complete(getRandomFallbackSpawnMessage());
            return future;
        }

        String playerContext = buildPlayerContext(player);
        String fullUserPrompt = "Контекст игрока: [" + playerContext + "]. " + (userMsg != null ? "Игрок сказал: " + userMsg : "Смотрящий появился за спиной.");

        Bukkit.getAsyncScheduler().runNow(plugin.getBukkitPlugin(), task -> {
            try {
                String responseText = queryLlm(endpoint, apiKey, model, systemPrompt, fullUserPrompt);
                String normalized = normalizeSpawnText(responseText);
                future.complete(normalized != null ? normalized : getRandomFallbackSpawnMessage());
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "[AiBrain] OpenRouter AI API error", t);
                future.complete(getRandomFallbackSpawnMessage());
            } finally {
                inFlight.decrementAndGet();
            }
        });

        return future;
    }

    public CompletableFuture<String> generateChatResponse(Player player, String playerSpeech) {
        CompletableFuture<String> future = new CompletableFuture<>();

        if (!plugin.getConfigManager().getConfig().getBoolean("ai.enabled", true)) {
            future.complete(getRandomFallbackChatResponse());
            return future;
        }

        String apiKey = plugin.getConfigManager().getConfig().getString("ai.api-key", "");
        if (apiKey.isEmpty() || apiKey.equalsIgnoreCase("YOUR_API_KEY_HERE")) {
            future.complete(getRandomFallbackChatResponse());
            return future;
        }

        String endpoint = plugin.getConfigManager().getConfig().getString("ai.endpoint", "https://openrouter.ai/api/v1/chat/completions");
        String model = plugin.getConfigManager().getConfig().getString("ai.model", "openai/gpt-4o-mini");
        String systemPrompt = plugin.getConfigManager().getConfig().getString("ai.chat-system-prompt",
                "Ты — древний мистический Смотрящий (Наблюдатель, The Watcher) из Междумирья в Minecraft. " +
                "Игрок обратился к тебе в чате. Твой стиль: " +
                "1. Отвечай от третьего лица в каноничном стиле: «Смотрящий выражает [чувство/действие]...», «Смотрящий видит...», «Смотрящий знает...» ИЛИ мрачным коротким пророчеством. " +
                "2. Если вопрос касается ЛОРА (Междумирье, Пустота, Око, Время, Тюрьма времени, Судьба, Смерть, Боги) — дай глубокий, пугающий философский намек. " +
                "3. Отвечай СТРОГО 1 предложением (до 8-14 слов). Без смайликов, без дружелюбия, без кавычек. Ты — безмолвное всевидящее божество.");

        if (!tryAcquireSlot(player.getUniqueId())) {
            future.complete(getRandomFallbackChatResponse());
            return future;
        }

        String playerContext = buildPlayerContext(player);
        String fullUserPrompt = "Игрок " + player.getName() + " говорит тебе: \"" + playerSpeech + "\". Контекст игрока: [" + playerContext + "]";

        Bukkit.getAsyncScheduler().runNow(plugin.getBukkitPlugin(), task -> {
            try {
                String responseText = queryLlm(endpoint, apiKey, model, systemPrompt, fullUserPrompt);
                if (responseText != null && !responseText.isBlank()) {
                    String trimmed = responseText.trim().replace("\"", "");
                    if (!trimmed.endsWith("...") && !trimmed.endsWith(".") && !trimmed.endsWith("!")) {
                        trimmed = trimmed + "...";
                    }
                    future.complete(trimmed);
                } else {
                    future.complete(getRandomFallbackChatResponse());
                }
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "[AiBrain] Failed to generate chat response", t);
                future.complete(getRandomFallbackChatResponse());
            } finally {
                inFlight.decrementAndGet();
            }
        });

        return future;
    }

    private synchronized boolean tryAcquireSlot(UUID playerId) {
        if (inFlight.get() >= MAX_CONCURRENT_REQUESTS) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long previous = lastRequestAt.get(playerId);
        if (previous != null && now - previous < PLAYER_COOLDOWN_MILLIS) {
            return false;
        }

        if (lastRequestAt.size() > MAX_TRACKED_PLAYERS) {
            Iterator<Map.Entry<UUID, Long>> iterator = lastRequestAt.entrySet().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().getValue() > PLAYER_COOLDOWN_MILLIS) {
                    iterator.remove();
                }
            }
        }

        lastRequestAt.put(playerId, now);
        inFlight.incrementAndGet();
        return true;
    }

    private String buildPlayerContext(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        String itemName = (handItem != null && !handItem.getType().isAir()) ? handItem.getType().name() : "ПУСТЫЕ_РУКИ";

        String helmet = player.getInventory().getHelmet() != null ? player.getInventory().getHelmet().getType().name() : "нет";
        String chest = player.getInventory().getChestplate() != null ? player.getInventory().getChestplate().getType().name() : "нет";

        String movement = "стоит";
        if (player.isSprinting()) movement = "бежит";
        else if (player.isSneaking()) movement = "крадётся";
        else if (player.isFlying()) movement = "летит";
        else if (player.isSwimming()) movement = "плывёт";

        int lightLevel = player.getLocation().getBlock().getLightLevel();
        long nearbyHostile = player.getNearbyEntities(16, 16, 16).stream()
                .filter(e -> e instanceof org.bukkit.entity.Monster).count();

        return String.format(
                "Игрок: %s. Биом: %s. Высота Y: %d. Здоровье: %.1f/20. Голод: %d/20. Уровень XP: %d. " +
                        "Предмет в руках: %s. Шлем: %s. Нагрудник: %s. Время: %s. Освещение: %d. " +
                        "Состояние: %s. Враждебных мобов рядом: %d.",
                player.getName(),
                player.getLocation().getBlock().getBiome().getKey().getKey(),
                player.getLocation().getBlockY(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getLevel(),
                itemName,
                helmet, chest,
                (player.getWorld().getTime() > 13000 ? "НОЧЬ" : "ДЕНЬ"),
                lightLevel,
                movement,
                nearbyHostile
        );
    }

    private String getRandomFallbackSpawnMessage() {
        String pick = FALLBACK_SPAWN_MESSAGES.get(ThreadLocalRandom.current().nextInt(FALLBACK_SPAWN_MESSAGES.size()));
        return "Смотрящий " + pick + "...";
    }

    private String getRandomFallbackChatResponse() {
        return FALLBACK_CHAT_RESPONSES.get(ThreadLocalRandom.current().nextInt(FALLBACK_CHAT_RESPONSES.size()));
    }

    private String queryLlm(String endpoint, String apiKey, String model, String systemPrompt, String userPrompt) throws Exception {
        JsonObject systemMsgObj = new JsonObject();
        systemMsgObj.addProperty("role", "system");
        systemMsgObj.addProperty("content", systemPrompt);

        JsonObject userMsgObj = new JsonObject();
        userMsgObj.addProperty("role", "user");
        userMsgObj.addProperty("content", userPrompt);

        com.google.gson.JsonArray messagesArr = new com.google.gson.JsonArray();
        messagesArr.add(systemMsgObj);
        messagesArr.add(userMsgObj);

        JsonObject bodyObj = new JsonObject();
        bodyObj.addProperty("model", model);
        bodyObj.add("messages", messagesArr);
        bodyObj.addProperty("max_tokens", 45);
        bodyObj.addProperty("temperature", 0.85);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://github.com/SchalkerMC/DoAPI")
                .header("X-Title", "Watcher Minecraft Plugin")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(bodyObj.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject resObj = JsonParser.parseString(response.body()).getAsJsonObject();
            if (resObj.has("choices") && resObj.getAsJsonArray("choices").size() > 0) {
                JsonObject choiceObj = resObj.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (choiceObj.has("message")) {
                    JsonObject msgObj = choiceObj.getAsJsonObject("message");
                    if (msgObj.has("content") && !msgObj.get("content").isJsonNull()) {
                        return sanitize(msgObj.get("content").getAsString());
                    }
                }
            }
        }
        return null;
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String collapsed = raw.replaceAll("\\s+", " ").trim();
        if (collapsed.length() > MAX_RESPONSE_LENGTH) {
            collapsed = collapsed.substring(0, MAX_RESPONSE_LENGTH).trim();
        }
        return collapsed;
    }

    private String normalizeSpawnText(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String extracted = raw.trim();

        if (extracted.startsWith("\"") && extracted.endsWith("\"") && extracted.length() > 1) {
            extracted = extracted.substring(1, extracted.length() - 1).trim();
        }

        while (extracted.endsWith(".")) {
            extracted = extracted.substring(0, extracted.length() - 1).trim();
        }

        while (extracted.toLowerCase().startsWith("смотрящий ")) {
            extracted = extracted.substring(10).trim();
        }

        if (extracted.toLowerCase().startsWith("выражает выражает ")) {
            extracted = extracted.substring(9).trim();
        }

        String lower = extracted.toLowerCase();
        boolean startsWithVerb = lower.startsWith("выражает ") ||
                lower.startsWith("наблюдает ") ||
                lower.startsWith("следит ") ||
                lower.startsWith("замер ") ||
                lower.startsWith("молчит ") ||
                lower.startsWith("чувствует ") ||
                lower.startsWith("смотрит ") ||
                lower.startsWith("сжимает ") ||
                lower.startsWith("шепчет ") ||
                lower.startsWith("улавливает ") ||
                lower.startsWith("вслушивается ") ||
                lower.startsWith("хранит ") ||
                lower.startsWith("растворяется ");

        if (!startsWithVerb) {
            extracted = "выражает " + extracted;
        }

        return "Смотрящий " + extracted + "...";
    }
}
