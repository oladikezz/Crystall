package net.myserver.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.myserver.permissions.RoleManager;

public class ReportCommand extends Command {
    public ReportCommand() {
        super("report");

        var targetArg = ArgumentType.Word("player");
        var reasonArg = ArgumentType.StringArray("reason");

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            String targetName = context.get(targetArg);
            String[] reasonArray = context.get(reasonArg);
            String reason = String.join(" ", reasonArray);

            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);
            if (target != null) {
                Pos pos = target.getPosition();
                ReportManager.ReportEntry entry = new ReportManager.ReportEntry(
                        player.getUsername(), targetName, reason, pos.x(), pos.y(), pos.z()
                );
                ReportManager.addReport(entry);

                player.sendMessage(Component.text("Жалоба на " + targetName + " успешно отправлена администраторам.", NamedTextColor.GREEN));

                // Уведомление админам
                Component alert = Component.text("[Репорт] " + player.getUsername() + " пожаловался на " + targetName + " (" + reason + ")", NamedTextColor.RED);
                for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                    String role = RoleManager.getRole(p.getUuid());
                    if (role.equals("admin") || role.equals("moderator")) {
                        p.sendMessage(alert);
                    }
                }
            } else {
                player.sendMessage(Component.text("Игрок " + targetName + " не найден.", NamedTextColor.RED));
            }
        }, targetArg, reasonArg);
    }
}
