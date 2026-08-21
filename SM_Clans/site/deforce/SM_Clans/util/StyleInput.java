package site.deforce.SM_Clans.util;

import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class StyleInput {
   private static final Pattern MINI_TAG = Pattern.compile("</?[a-zA-Z#][^<>]*>");
   private static final LegacyComponentSerializer TO_LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

   private StyleInput() {
      super();
   }

   public static String miniToLegacy(String input) {
      if (input != null && !input.isEmpty() && MINI_TAG.matcher(input).find()) {
         try {
            Component component = MiniMessage.miniMessage().deserialize(input);
            return TO_LEGACY.serialize(component);
         } catch (RuntimeException var2) {
            return input;
         }
      } else {
         return input;
      }
   }
}
