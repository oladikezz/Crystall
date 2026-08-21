package net.schalker.SMPS.modules.streamermode.listeners;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.schalker.SMPS.SMPS;
import net.schalker.SMPS.core.listener.BaseListener;
import net.schalker.SMPS.modules.streamermode.StreamerModeModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class StreamChatListener extends BaseListener {
   private static final Pattern URL_PATTERN = Pattern.compile("(?i)(https?://\\S+|www\\.\\S+)");
   private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{2,5})?\\b");
   private static final AtomicLong DELIVERY_SEQUENCE = new AtomicLong();
   private final StreamerModeModule module;

   public StreamChatListener(SMPS plugin, StreamerModeModule module) {
      super(plugin);
      this.module = module;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onChat(AsyncChatEvent event) {
      String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
      String masked = maskForbidden(raw);
      if (raw.equals(masked)) {
         return;
      }

      List<Player> filteredViewers = new ArrayList<>();
      for (Audience viewer : event.viewers()) {
         if (viewer instanceof Player player && shouldMaskFor(player)) {
            filteredViewers.add(player);
         }
      }
      if (filteredViewers.isEmpty()) {
         return;
      }

      ChatRenderer originalRenderer = event.renderer();
      Player source = event.getPlayer();
      Component sourceDisplayName = source.displayName();
      Component message = event.message();

      event.viewers().removeAll(filteredViewers);
      for (Player viewer : filteredViewers) {
         Component rendered = originalRenderer.render(source, sourceDisplayName, message, viewer);
         Component filtered = maskForbidden(rendered);
         String taskName = "stream-chat-filter-" + DELIVERY_SEQUENCE.incrementAndGet();
         this.plugin.getSchedulerManager().runEntityTask(viewer, taskName, () -> {
            if (viewer.isOnline()) {
               viewer.sendMessage(filtered);
            }
         });
      }
   }

   private boolean shouldMaskFor(Audience viewer) {
      if (!(viewer instanceof Player player)) {
         return false;
      }

      return this.module.isChatFilterEnabled(player.getUniqueId());
   }

   private Component maskForbidden(Component input) {
      Component result = input;
      String mask = this.module.getMask();

      if (this.module.isLinkMaskEnabled()) {
         result = replacePattern(result, URL_PATTERN, mask);
      }
      if (this.module.isIpMaskEnabled()) {
         result = replacePattern(result, IP_PATTERN, mask);
      }
      for (Pattern pattern : this.module.getBannedWordPatterns()) {
         result = replacePattern(result, pattern, mask);
      }

      return result;
   }

   private Component replacePattern(Component input, Pattern pattern, String mask) {
      return input.replaceText(config -> config
         .match(pattern)
         .replacement(match -> match.content(mask))
      );
   }

   private String maskForbidden(String input) {
      String text = input;
      String mask = this.module.getMask();
      String replacement = Matcher.quoteReplacement(mask);

      if (this.module.isLinkMaskEnabled()) {
         text = URL_PATTERN.matcher(text).replaceAll(replacement);
      }
      if (this.module.isIpMaskEnabled()) {
         text = IP_PATTERN.matcher(text).replaceAll(replacement);
      }

      for (Pattern pattern : this.module.getBannedWordPatterns()) {
         text = pattern.matcher(text).replaceAll(replacement);
      }

      return text;
   }
}
