package site.deforce.SM_Clans.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickCallback.Options;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import site.deforce.SM_Clans.SM_Clans;

public class ClanDialogManager {
   private final SM_Clans module;

   public ClanDialogManager(SM_Clans module) {
      super();
      this.module = module;
   }

   public void openTextEdit(Player player, String title, String priceLine, String inputKey, String inputLabel, String initial, int maxLength, Consumer<String> onSubmit) {
      DialogInput input = DialogInput.text(inputKey, Component.text(inputLabel)).initial(initial == null ? "" : initial).maxLength(rawInputCap(maxLength)).width(300).build();
      List<DialogBody> body = new ArrayList();
      if (priceLine != null && !priceLine.isEmpty()) {
         body.add(DialogBody.plainMessage(Component.text(priceLine, NamedTextColor.GRAY)));
      }

      ActionButton submit = ActionButton.builder(Component.text("Подтвердить", NamedTextColor.GREEN)).action(DialogAction.customClick((view, audience) -> {
         String value = view.getText(inputKey);
         if (value != null && !value.trim().isEmpty() && audience instanceof Player p) {
            String trimmed = value.trim();
            this.module.getPlugin().getSchedulerManager().runEntityTask(p, "clan-dialog-submit", () -> onSubmit.accept(trimmed));
         }
      }, (ClickCallback.Options)Options.builder().build())).build();
      ActionButton cancel = ActionButton.builder(Component.text("Отмена", NamedTextColor.RED)).build();
      Dialog dialog = Dialog.create((b) -> ((DialogRegistryEntry.Builder)b.empty()).base(DialogBase.builder(Component.text(title)).body(body).inputs(List.of(input)).canCloseWithEscape(true).build()).type(DialogType.confirmation(submit, cancel)));
      player.showDialog(dialog);
   }

   public void openCreationForm(Player player, String priceLine, int minTag, int maxTag, int minName, int maxName, BiConsumer<String, String> onSubmit) {
      DialogInput tagInput = DialogInput.text("tag", Component.text("Тег")).maxLength(rawInputCap(maxTag)).width(300).build();
      DialogInput nameInput = DialogInput.text("name", Component.text("Название")).maxLength(rawInputCap(maxName)).width(300).build();
      List<DialogBody> body = new ArrayList();
      body.add(DialogBody.plainMessage(Component.text("Тег: " + tagRange(minTag, maxTag) + ", название: " + minName + "-" + maxName + " симв.", NamedTextColor.GRAY)));
      if (priceLine != null && !priceLine.isEmpty()) {
         body.add(DialogBody.plainMessage(Component.text(priceLine, NamedTextColor.GRAY)));
      }

      ActionButton submit = ActionButton.builder(Component.text("Создать", NamedTextColor.GREEN)).action(DialogAction.customClick((view, audience) -> {
         String tag = view.getText("tag");
         String name = view.getText("name");
         if (tag != null && name != null && !tag.trim().isEmpty() && !name.trim().isEmpty() && audience instanceof Player p) {
            String tagTrim = tag.trim();
            String nameTrim = name.trim();
            this.module.getPlugin().getSchedulerManager().runEntityTask(p, "clan-create-submit", () -> onSubmit.accept(tagTrim, nameTrim));
         }
      }, (ClickCallback.Options)Options.builder().build())).build();
      ActionButton cancel = ActionButton.builder(Component.text("Отмена", NamedTextColor.RED)).build();
      Dialog dialog = Dialog.create((b) -> ((DialogRegistryEntry.Builder)b.empty()).base(DialogBase.builder(Component.text("Создание гильдии")).body(body).inputs(List.of(tagInput, nameInput)).canCloseWithEscape(true).build()).type(DialogType.confirmation(submit, cancel)));
      player.showDialog(dialog);
   }

   private static String tagRange(int min, int max) {
      return min + "-" + max;
   }

   private static int rawInputCap(int visibleMax) {
      return Math.min(2048, Math.max(256, visibleMax * 8));
   }
}
