package net.schalker.SMPS.modules.marry.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import net.schalker.SMPS.modules.marry.MarryModule;
import net.schalker.SMPS.modules.marry.database.MarryDatabase;
import net.schalker.SMPS.modules.marry.managers.MarryManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * /divorce command — divorce system.
 *
 * Subcommands:
 *   /divorce &lt;nick1&gt; &lt;nick2&gt;       — priest divorces two players (priest / admin)
 *   /divorce admin &lt;nick1&gt; &lt;nick2&gt;  — force-divorce without confirmation (admin)
 *   /divorce confirm               — confirm pending divorce request
 *   /divorce deny                  — deny pending divorce request
 *
 * Permissions (cascading):
 *   smmarry.admin  → includes priest + use
 *   smmarry.priest → includes use
 *   smmarry.use    → confirm, deny
 */
public class DivorceCommand extends ModuleCommand {

    private static final String PRIEST_PERMISSION = "smmarry.priest";
    private static final String USE_PERMISSION    = "smmarry.use";
    private static final String ADMIN_PERMISSION  = "smmarry.admin";

    private final MarryModule module;
    private final MarryManager marryManager;
    private final MarryDatabase database;

    public DivorceCommand(DoAPI plugin, MarryModule module) {
        super(plugin);
        this.module = module;
        this.marryManager = module.getMarryManager();
        this.database = module.getMarryDatabase();
    }

    // ── Meta ─────────────────────────────────────────────────────────

    @Override public String getName()        { return "divorce"; }
    @Override public String getPermission()  { return null; }          // handled internally
    @Override public String getDescription() { return "Развод на сервере"; }
    @Override public String getUsage()       { return "/divorce <args>"; }
    @Override public List<String> getAliases() { return List.of(); }

    // ── Permission helpers (admin ⊃ priest ⊃ use) ───────────────────

    private boolean hasUsePermission(CommandSender s) {
        return s.hasPermission(USE_PERMISSION)
            || s.hasPermission(PRIEST_PERMISSION)
            || s.hasPermission(ADMIN_PERMISSION);
    }

    private boolean hasPriestPermission(CommandSender s) {
        return s.hasPermission(PRIEST_PERMISSION)
            || s.hasPermission(ADMIN_PERMISSION);
    }

    private boolean hasAdminPermission(CommandSender s) {
        return s.hasPermission(ADMIN_PERMISSION);
    }

    // ── Dispatch ─────────────────────────────────────────────────────

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (!hasUsePermission(sender)) {
            sender.sendMessage(module.getMessage("no-permission"));
            return;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "confirm" -> handleConfirm(sender);
            case "deny"    -> handleDeny(sender);
            case "admin"   -> handleAdmin(sender, args);
            default        -> handleDivorce(sender, args);
        }
    }

    // ── Usage (admin part hidden from non-admins) ────────────────────

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(module.getMessage("divorce-usage"));
        if (hasAdminPermission(sender)) {
            sender.sendMessage(module.getMessage("divorce-usage-admin"));
        }
    }

    // ── /divorce <nick1> <nick2>  (priest / admin) ──────────────────

    private void handleDivorce(CommandSender sender, String[] args) {
        if (!hasPriestPermission(sender)) {
            sender.sendMessage(module.getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return;
        }

        String name1 = args[0];
        String name2 = args[1];

        if (name1.equalsIgnoreCase(name2)) {
            sender.sendMessage(module.getMessage("both-same-player"));
            return;
        }

        Player player1 = Bukkit.getPlayer(name1);
        Player player2 = Bukkit.getPlayer(name2);

        if (player1 == null || !player1.isOnline()) {
            sender.sendMessage(module.getMessage("player-not-online").replace("{player}", name1));
            return;
        }
        if (player2 == null || !player2.isOnline()) {
            sender.sendMessage(module.getMessage("player-not-online").replace("{player}", name2));
            return;
        }

        plugin.getSchedulerManager().runAsync("divorce-check", () -> {
            boolean areMarried = database.areMarried(player1.getUniqueId(), player2.getUniqueId());

            plugin.getSchedulerManager().runGlobalTask("divorce-check-result", () -> {
                if (!areMarried) {
                    sender.sendMessage(module.getMessage("not-married")
                        .replace("{player1}", player1.getName())
                        .replace("{player2}", player2.getName()));
                    return;
                }

                long timeout = module.getConfig().getLong("settings.confirmation-timeout", 30);
                marryManager.createDivorceRequest(sender.getName(), player1, player2, timeout);

                sender.sendMessage(module.getMessage("divorce-request-sent")
                    .replace("{player1}", player1.getName())
                    .replace("{player2}", player2.getName())
                    .replace("{time}", String.valueOf(timeout)));
            });
        });
    }

    // ── /divorce admin <nick1> <nick2>  (force-divorce, admin only) ─

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(module.getMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(module.getMessage("divorce-admin-usage"));
            return;
        }

        String name1 = args[1];
        String name2 = args[2];

        if (name1.equalsIgnoreCase(name2)) {
            sender.sendMessage(module.getMessage("both-same-player"));
            return;
        }

        Player player1 = Bukkit.getPlayer(name1);
        Player player2 = Bukkit.getPlayer(name2);

        if (player1 == null) {
            sender.sendMessage(module.getMessage("player-not-found").replace("{player}", name1));
            return;
        }
        if (player2 == null) {
            sender.sendMessage(module.getMessage("player-not-found").replace("{player}", name2));
            return;
        }

        String adminName = sender.getName();

        plugin.getSchedulerManager().runAsync("admin-divorce", () -> {
            boolean areMarried = database.areMarried(player1.getUniqueId(), player2.getUniqueId());

            plugin.getSchedulerManager().runGlobalTask("admin-divorce-check", () -> {
                if (!areMarried) {
                    sender.sendMessage(module.getMessage("not-married")
                        .replace("{player1}", player1.getName())
                        .replace("{player2}", player2.getName()));
                    return;
                }

                plugin.getSchedulerManager().runAsync("admin-divorce-delete", () -> {
                    boolean ok = database.deleteMarriage(player1.getUniqueId(), player2.getUniqueId());

                    plugin.getSchedulerManager().runGlobalTask("admin-divorce-notify", () -> {
                        if (ok) {
                            sender.sendMessage(module.getMessage("admin-divorce-success")
                                .replace("{player1}", player1.getName())
                                .replace("{player2}", player2.getName()));

                            if (player1.isOnline()) {
                                player1.sendMessage(module.getMessage("admin-divorce-notification")
                                    .replace("{admin}", adminName)
                                    .replace("{partner}", player2.getName()));
                                plugin.getSchedulerManager().runEntityTask(player1, "admin-divorce-sound-p1",
                                    () -> { if (player1.isOnline()) playCatSound(player1); });
                            }
                            if (player2.isOnline()) {
                                player2.sendMessage(module.getMessage("admin-divorce-notification")
                                    .replace("{admin}", adminName)
                                    .replace("{partner}", player1.getName()));
                                plugin.getSchedulerManager().runEntityTask(player2, "admin-divorce-sound-p2",
                                    () -> { if (player2.isOnline()) playCatSound(player2); });
                            }

                            if (module.getConfig().getBoolean("settings.broadcast-divorces", false)) {
                                String msg = module.getMessage("broadcast-divorce")
                                    .replace("{player1}", player1.getName())
                                    .replace("{player2}", player2.getName());
                                plugin.getServer().broadcast(net.kyori.adventure.text.Component.text(msg));
                            }
                        } else {
                            sender.sendMessage(module.getMessage("admin-divorce-failed"));
                        }
                    });
                });
            });
        });
    }

    // ── /divorce confirm ─────────────────────────────────────────────

    private void handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(module.getMessage("only-player"));
            return;
        }
        marryManager.confirmRequest(player, MarryManager.RequestType.DIVORCE);
    }

    // ── /divorce deny ────────────────────────────────────────────────

    private void handleDeny(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(module.getMessage("only-player"));
            return;
        }
        marryManager.denyRequest(player, MarryManager.RequestType.DIVORCE);
    }

    // ── Tab completion ───────────────────────────────────────────────

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> out = new ArrayList<>();

            for (String sub : List.of("confirm", "deny")) {
                if (sub.startsWith(input)) out.add(sub);
            }
            if (hasAdminPermission(sender) && "admin".startsWith(input)) {
                out.add("admin");
            }
            // Player names for priest/admin (divorce <nick1> …)
            if (hasPriestPermission(sender)) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) out.add(p.getName());
                }
            }
            return out;
        }

        if (args.length == 2) {
            String first = args[0].toLowerCase();
            String input = args[1].toLowerCase();
            List<String> out = new ArrayList<>();

            // /divorce admin <nick1>  or  /divorce <nick1> <nick2>
            if ((first.equals("admin") && hasAdminPermission(sender))
             || (!List.of("confirm", "deny", "admin").contains(first) && hasPriestPermission(sender))) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) out.add(p.getName());
                }
            }
            return out;
        }

        if (args.length == 3) {
            String first = args[0].toLowerCase();
            String input = args[2].toLowerCase();
            List<String> out = new ArrayList<>();

            boolean needPlayers = (first.equals("admin") && hasAdminPermission(sender))
                || (!List.of("confirm", "deny", "admin").contains(first) && hasPriestPermission(sender));

            if (needPlayers) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (!p.getName().equalsIgnoreCase(args[1]) && p.getName().toLowerCase().startsWith(input))
                        out.add(p.getName());
                }
            }
            return out;
        }

        return List.of();
    }

    // ── Sound helpers ────────────────────────────────────────────────

    private void playCatSound(Player player) {
        if (!module.getConfig().getBoolean("settings.sounds.enabled", true)) return;

        String soundName = module.getConfig().getString("settings.sounds.divorce-sound", "ENTITY_CAT_HISS");
        Sound sound = resolveSound(soundName, Sound.ENTITY_CAT_HISS);
        float volume = (float) module.getConfig().getDouble("settings.sounds.volume", 1.0);
        float pitch  = (float) module.getConfig().getDouble("settings.sounds.pitch", 1.0);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private Sound resolveSound(String name, Sound fallback) {
        try {
            String key = name.toLowerCase().replace('_', '.');
            Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
            return sound != null ? sound : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}

