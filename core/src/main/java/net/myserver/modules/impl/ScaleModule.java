package net.myserver.modules.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.GlobalEventHandler;
import net.myserver.modules.CrystallModule;
import net.myserver.permissions.RoleManager;

public class ScaleModule implements CrystallModule {
    private Command scaleCmd;

    @Override
    public String getId() {
        return "scale";
    }

    @Override
    public String getName() {
        return "Scale";
    }

    @Override
    public String getDescription() {
        return "Изменение масштаба и размера сущностей (/scale <значение>)";
    }

    @Override
    public void onEnable(GlobalEventHandler eventHandler) {
        scaleCmd = new Command("scale", "size") {
            {
                var scaleArg = ArgumentType.Float("value");

                addSyntax((sender, context) -> {
                    if (!(sender instanceof Player player)) return;
                    if (!RoleManager.isStaff(player)) {
                        player.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
                        return;
                    }

                    float scale = context.get(scaleArg);
                    if (scale < 0.1f || scale > 5.0f) {
                        player.sendMessage(Component.text("Значение масштаба должно быть от 0.1 до 5.0!", NamedTextColor.RED));
                        return;
                    }

                    var attr = player.getAttribute(Attribute.SCALE);
                    if (attr != null) {
                        attr.setBaseValue(scale);
                        player.sendMessage(Component.text("Масштаб игрока установлен на: " + scale, NamedTextColor.GREEN));
                    }
                }, scaleArg);
            }
        };

        MinecraftServer.getCommandManager().register(scaleCmd);
    }

    @Override
    public void onDisable() {
        if (scaleCmd != null) {
            MinecraftServer.getCommandManager().unregister(scaleCmd);
        }
    }
}
