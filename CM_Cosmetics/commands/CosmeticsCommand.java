package net.schalker.SMPS.modules.cosmetics.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.cosmetics.CosmeticsModule;
import net.schalker.SMPS.modules.cosmetics.models.Cosmetic;
import net.schalker.SMPS.modules.cosmetics.models.CosmeticCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CosmeticsCommand extends ModuleCommand {
    private final CosmeticsModule module;

    public CosmeticsCommand(DoAPI plugin, CosmeticsModule module) {
        super(plugin);
        this.module = module;
    }

    @Override
    public String getName() {
        return "cosmetics";
    }

    @Override
    public String getPermission() {
        return "smcosm";
    }

    @Override
    public String getDescription() {
        return "Manage cosmetics";
    }

    @Override
    public String getUsage() {
        return "/cosmetics <equip|unequip|list|menu|clear|reload|restart> [category] [id]";
    }

    @Override
    public Collection<String> getAliases() {
        return Arrays.asList("cos", "cosmetic");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        try {
            if (!this.module.ensureReady()) {
                sender.sendMessage(Component.text("Cosmetics are temporarily unavailable.", NamedTextColor.YELLOW));
                return;
            }

            if (args.length == 0) {
                if (sender instanceof Player player) {
                    this.openMenuSafe(player);
                } else {
                    this.sendHelp(sender);
                }
                return;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "equip" -> this.handleEquip(sender, args);
                case "unequip" -> this.handleUnequip(sender, args);
                case "list" -> this.handleList(sender, args);
                case "menu" -> this.handleMenu(sender, args);
                case "clear" -> this.handleClear(sender, args);
                case "reload" -> this.handleReload(sender);
                case "restart" -> this.handleRestart(sender);
                default -> this.sendHelp(sender);
            }
        } catch (Throwable throwable) {
            sender.sendMessage(Component.text("Cosmetics are temporarily unavailable.", NamedTextColor.YELLOW));
            this.plugin.getDebugSystem().log("CosmeticsCommand", "Suppressed command exception: " + throwable.getClass().getSimpleName());
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Cosmetics ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/cosmetics equip <category> <id>", NamedTextColor.YELLOW)
            .append(Component.text(" - Equip cosmetic", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cosmetics unequip <category>", NamedTextColor.YELLOW)
            .append(Component.text(" - Unequip cosmetic", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cosmetics list [category]", NamedTextColor.YELLOW)
            .append(Component.text(" - List cosmetics", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cosmetics menu", NamedTextColor.YELLOW)
            .append(Component.text(" - Open GUI", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/cosmetics clear", NamedTextColor.YELLOW)
            .append(Component.text(" - Unequip all", NamedTextColor.GRAY)));

        if (sender.hasPermission("smcosm.admin")) {
            sender.sendMessage(Component.text("/cosmetics reload", NamedTextColor.RED)
                .append(Component.text(" - Reload cosmetics config", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/cosmetics restart", NamedTextColor.RED)
                .append(Component.text(" - Restart module without server restart", NamedTextColor.GRAY)));
        }
    }

    private void handleEquip(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /cosmetics equip <category> <id>", NamedTextColor.RED));
            return;
        }

        String categoryStr = args[1];
        String cosmeticId = args[2];

        CosmeticCategory category = CosmeticCategory.fromId(categoryStr);
        if (category == null) {
            category = CosmeticCategory.fromName(categoryStr);
        }

        if (category == null) {
            sender.sendMessage(Component.text("Unknown category: " + categoryStr, NamedTextColor.RED));
            return;
        }

        Cosmetic cosmetic = this.module.getCosmeticsManager().getCosmetic(cosmeticId);
        if (cosmetic == null) {
            sender.sendMessage(Component.text("Cosmetic not found: " + cosmeticId, NamedTextColor.RED));
            return;
        }

        if (cosmetic.getCategory() != category) {
            sender.sendMessage(Component.text("Cosmetic does not belong to category " + category.getDisplayName(), NamedTextColor.RED));
            return;
        }

        if (!cosmetic.hasPermission(player)) {
            sender.sendMessage(Component.text("No permission for this cosmetic.", NamedTextColor.RED));
            return;
        }

        boolean success = this.module.getUserCosmeticsManager().equip(player, cosmetic);
        if (success) {
            sender.sendMessage(Component.text("Equipped ", NamedTextColor.GREEN)
                .append(Component.text(cosmetic.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" (" + category.getDisplayName() + ")", NamedTextColor.GRAY)));
        } else {
            sender.sendMessage(Component.text("Failed to equip cosmetic.", NamedTextColor.RED));
        }
    }

    private void handleUnequip(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /cosmetics unequip <category>", NamedTextColor.RED));
            return;
        }

        String categoryStr = args[1];
        CosmeticCategory category = CosmeticCategory.fromId(categoryStr);
        if (category == null) {
            category = CosmeticCategory.fromName(categoryStr);
        }

        if (category == null) {
            sender.sendMessage(Component.text("Unknown category: " + categoryStr, NamedTextColor.RED));
            return;
        }

        boolean success = this.module.getUserCosmeticsManager().unequip(player, category);
        if (success) {
            sender.sendMessage(Component.text("Unequipped category ", NamedTextColor.GREEN)
                .append(Component.text(category.getDisplayName(), NamedTextColor.YELLOW)));
        } else {
            sender.sendMessage(Component.text("No equipped cosmetic in this category.", NamedTextColor.RED));
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("=== Cosmetic Categories ===", NamedTextColor.GOLD));
            for (CosmeticCategory category : CosmeticCategory.values()) {
                int count = this.module.getCosmeticsManager().getCosmeticsCount(category);
                if (count > 0) {
                    sender.sendMessage(Component.text("  " + category.getId(), NamedTextColor.YELLOW)
                        .append(Component.text(" - " + category.getDisplayName() + " (" + count + ")", NamedTextColor.GRAY)));
                }
            }
            sender.sendMessage(Component.text("Use /cosmetics list <category>", NamedTextColor.GRAY));
            return;
        }

        String categoryStr = args[1];
        CosmeticCategory category = CosmeticCategory.fromId(categoryStr);
        if (category == null) {
            category = CosmeticCategory.fromName(categoryStr);
        }

        if (category == null) {
            sender.sendMessage(Component.text("Unknown category: " + categoryStr, NamedTextColor.RED));
            return;
        }

        Collection<Cosmetic> cosmetics = this.module.getCosmeticsManager().getCosmeticsByCategory(category);
        if (cosmetics.isEmpty()) {
            sender.sendMessage(Component.text("No cosmetics in category " + category.getDisplayName(), NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("=== " + category.getDisplayName() + " ===", NamedTextColor.GOLD));
        for (Cosmetic cosmetic : cosmetics) {
            if (sender instanceof Player player && !cosmetic.isVisibleTo(player)) {
                continue;
            }
            Component line = Component.text("  " + cosmetic.getId(), NamedTextColor.YELLOW)
                .append(Component.text(" - " + cosmetic.getName(), NamedTextColor.GRAY));
            sender.sendMessage(line);
        }
    }

    private void handleMenu(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            this.module.getMessageManager().send(sender, "general.player-only");
            return;
        }
        if (this.module.getMenuManager() == null) {
            sender.sendMessage(Component.text("Cosmetics menu is unavailable right now. Try again in a moment.", NamedTextColor.YELLOW));
            return;
        }
        this.openMenuSafe(player);
    }

    private void openMenuSafe(Player player) {
        try {
            this.module.getMenuManager().openMainMenu(player);
        } catch (Throwable throwable) {
            if (this.module.ensureReady() && this.module.getMenuManager() != null) {
                try {
                    this.module.getMenuManager().openMainMenu(player);
                    return;
                } catch (Throwable ignored) {
                }
            }
            player.sendMessage(Component.text("Cosmetics menu is temporarily unavailable.", NamedTextColor.YELLOW));
            this.plugin.getDebugSystem().log("CosmeticsCommand", "Suppressed menu open exception: " + throwable.getClass().getSimpleName());
        }
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        this.module.getUserCosmeticsManager().unequipAll(player);
        sender.sendMessage(Component.text("All cosmetics unequipped.", NamedTextColor.GREEN));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("smcosm.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        try {
            this.module.reload();
            sender.sendMessage(Component.text("Cosmetics config reloaded.", NamedTextColor.GREEN));
        } catch (Exception exception) {
            sender.sendMessage(Component.text("Reload failed. Check console.", NamedTextColor.RED));
            this.plugin.getLogger().severe("Cosmetics reload command failed: " + exception.getMessage());
            this.plugin.getDebugSystem().logError("Cosmetics reload command failed", exception);
        }
    }

    private void handleRestart(CommandSender sender) {
        if (!sender.hasPermission("smcosm.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }

        try {
            this.plugin.getPluginReloader().reloadModule(CosmeticsModule.MODULE_NAME, sender);
        } catch (Exception ex) {
            this.module.reload();
            sender.sendMessage(Component.text("PluginReloader unavailable, fallback reload used.", NamedTextColor.YELLOW));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (!this.module.ensureReady() || this.module.getCosmeticsManager() == null) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        String currentArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length <= 1) {
            suggestions.addAll(Arrays.asList("equip", "unequip", "list", "menu", "clear", "reload", "restart"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("equip") || subCommand.equals("unequip") || subCommand.equals("list")) {
                for (CosmeticCategory category : CosmeticCategory.values()) {
                    if (this.module.getCosmeticsManager().getCosmeticsCount(category) > 0) {
                        suggestions.add(category.getId());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("equip")) {
            CosmeticCategory category = CosmeticCategory.fromId(args[1]);
            if (category == null) {
                category = CosmeticCategory.fromName(args[1]);
            }
            if (category != null) {
                Player player = stack.getSender() instanceof Player p ? p : null;
                for (Cosmetic cosmetic : this.module.getCosmeticsManager().getCosmeticsByCategory(category)) {
                    if (player != null && !cosmetic.isVisibleTo(player)) {
                        continue;
                    }
                    suggestions.add(cosmetic.getId());
                }
            }
        }

        return suggestions.stream()
            .filter(s -> s.toLowerCase().startsWith(currentArg))
            .collect(Collectors.toList());
    }
}
