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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * /marry command — marriage system.
 *
 * Subcommands:
 *   /marry &lt;nick1&gt; &lt;nick2&gt;       — priest marries two players (priest / admin)
 *   /marry admin &lt;nick1&gt; &lt;nick2&gt;  — force-marry without confirmation (admin)
 *   /marry confirm                — confirm pending marriage request
 *   /marry deny                   — deny pending marriage request
 *   /marry info [nick]            — view marriage info
 *   /marry list                   — list all marriages
 *   /marry migrate                — migrate from local file DB to current DB (admin)
 *
 * Permissions (cascading):
 *   smmarry.admin  → includes priest + use
 *   smmarry.priest → includes use
 *   smmarry.use    → info, list, confirm, deny
 */
public class MarryCommand extends ModuleCommand {

    private static final String PRIEST_PERMISSION = "smmarry.priest";
    private static final String USE_PERMISSION    = "smmarry.use";
    private static final String ADMIN_PERMISSION  = "smmarry.admin";

    private final MarryModule module;
    private final MarryManager marryManager;
    private final MarryDatabase database;

    public MarryCommand(DoAPI plugin, MarryModule module) {
        super(plugin);
        this.module = module;
        this.marryManager = module.getMarryManager();
        this.database = module.getMarryDatabase();
    }

    // ── Meta ─────────────────────────────────────────────────────────

    @Override public String getName()        { return "marry"; }
    @Override public String getPermission()  { return null; }          // handled internally
    @Override public String getDescription() { return "Система браков на сервере"; }
    @Override public String getUsage()       { return "/marry <args>"; }
    @Override public List<String> getAliases() { return List.of("wed"); }

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
            case "info"    -> handleInfo(sender, args);
            case "list"    -> handleList(sender);
            case "admin"   -> handleAdmin(sender, args);
            case "migrate" -> handleMigrate(sender);
            default        -> handleMarry(sender, args);
        }
    }

    // ── Usage (admin part hidden from non-admins) ────────────────────

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(module.getMessage("marry-usage"));
        if (hasAdminPermission(sender)) {
            sender.sendMessage(module.getMessage("marry-usage-admin"));
            sender.sendMessage(module.getMessage("marry-usage-migrate"));
        }
    }

    // ── /marry <nick1> <nick2>  (priest / admin) ────────────────────

    private void handleMarry(CommandSender sender, String[] args) {
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

        if (!module.getConfig().getBoolean("settings.allow-self-marry", false)) {
            if (sender instanceof Player sp) {
                if (sp.getUniqueId().equals(player1.getUniqueId())
                 || sp.getUniqueId().equals(player2.getUniqueId())) {
                    sender.sendMessage(module.getMessage("self-marry"));
                    return;
                }
            }
        }

        plugin.getSchedulerManager().runAsync("marry-check", () -> {
            int max = module.getConfig().getInt("settings.max-marriages-per-player", 1);
            int c1  = database.getMarriageCount(player1.getUniqueId());
            int c2  = database.getMarriageCount(player2.getUniqueId());

            plugin.getSchedulerManager().runGlobalTask("marry-check-result", () -> {
                if (c1 >= max) {
                    sender.sendMessage(module.getMessage("already-married").replace("{player}", player1.getName()));
                    return;
                }
                if (c2 >= max) {
                    sender.sendMessage(module.getMessage("already-married").replace("{player}", player2.getName()));
                    return;
                }

                long timeout = module.getConfig().getLong("settings.confirmation-timeout", 30);
                marryManager.createMarriageRequest(sender.getName(), player1, player2, timeout);

                sender.sendMessage(module.getMessage("marriage-request-sent")
                    .replace("{player1}", player1.getName())
                    .replace("{player2}", player2.getName())
                    .replace("{time}", String.valueOf(timeout)));
            });
        });
    }

    // ── /marry admin <nick1> <nick2>  (force-marry, admin only) ─────

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(module.getMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(module.getMessage("marry-admin-usage"));
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

        plugin.getSchedulerManager().runAsync("admin-marry", () -> {
            int c1  = database.getMarriageCount(player1.getUniqueId());
            int c2  = database.getMarriageCount(player2.getUniqueId());
            int max = module.getConfig().getInt("settings.max-marriages-per-player", 1);

            plugin.getSchedulerManager().runGlobalTask("admin-marry-check", () -> {
                if (c1 >= max) {
                    sender.sendMessage(module.getMessage("already-married").replace("{player}", player1.getName()));
                    return;
                }
                if (c2 >= max) {
                    sender.sendMessage(module.getMessage("already-married").replace("{player}", player2.getName()));
                    return;
                }

                plugin.getSchedulerManager().runAsync("admin-marry-create", () -> {
                    boolean ok = database.createMarriage(
                        player1.getUniqueId(), player1.getName(),
                        player2.getUniqueId(), player2.getName());

                    plugin.getSchedulerManager().runGlobalTask("admin-marry-notify", () -> {
                        if (ok) {
                            sender.sendMessage(module.getMessage("admin-marriage-success")
                                .replace("{player1}", player1.getName())
                                .replace("{player2}", player2.getName()));

                            if (player1.isOnline()) {
                                player1.sendMessage(module.getMessage("admin-marriage-notification")
                                    .replace("{admin}", adminName)
                                    .replace("{partner}", player2.getName()));
                                plugin.getSchedulerManager().runEntityTask(player1, "admin-marry-sound-p1",
                                    () -> { if (player1.isOnline()) playCatSound(player1, true); });
                            }
                            if (player2.isOnline()) {
                                player2.sendMessage(module.getMessage("admin-marriage-notification")
                                    .replace("{admin}", adminName)
                                    .replace("{partner}", player1.getName()));
                                plugin.getSchedulerManager().runEntityTask(player2, "admin-marry-sound-p2",
                                    () -> { if (player2.isOnline()) playCatSound(player2, true); });
                            }

                            if (module.getConfig().getBoolean("settings.broadcast-marriages", true)) {
                                String msg = module.getMessage("broadcast-marriage")
                                    .replace("{player1}", player1.getName())
                                    .replace("{player2}", player2.getName());
                                plugin.getServer().broadcast(net.kyori.adventure.text.Component.text(msg));
                            }
                        } else {
                            sender.sendMessage(module.getMessage("admin-marriage-failed"));
                        }
                    });
                });
            });
        });
    }

    // ── /marry confirm ───────────────────────────────────────────────

    private void handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(module.getMessage("only-player"));
            return;
        }
        marryManager.confirmRequest(player, MarryManager.RequestType.MARRIAGE);
    }

    // ── /marry deny ──────────────────────────────────────────────────

    private void handleDeny(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(module.getMessage("only-player"));
            return;
        }
        marryManager.denyRequest(player, MarryManager.RequestType.MARRIAGE);
    }

    // ── /marry info [nick] ──────────────────────────────────────────

    private void handleInfo(CommandSender sender, String[] args) {
        Player target;

        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(module.getMessage("only-player"));
                return;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(module.getMessage("player-not-found").replace("{player}", args[1]));
                return;
            }
        }

        Player fTarget = target;
        plugin.getSchedulerManager().runAsync("marry-info", () -> {
            MarryDatabase.MarriageInfo info = database.getPartner(fTarget.getUniqueId());

            plugin.getSchedulerManager().runGlobalTask("marry-info-result", () -> {
                if (info == null) {
                    boolean isSelf = sender instanceof Player p
                                  && fTarget.getUniqueId().equals(p.getUniqueId());
                    sender.sendMessage(isSelf
                        ? module.getMessage("info-self-not-married")
                        : module.getMessage("info-not-married").replace("{player}", fTarget.getName()));
                    return;
                }

                SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                sender.sendMessage(module.getMessage("info-married")
                    .replace("{player}", fTarget.getName())
                    .replace("{partner}", info.getPartnerName())
                    .replace("{date}", df.format(info.getMarriedDate())));
            });
        });
    }

    // ── /marry list ──────────────────────────────────────────────────

    private void handleList(CommandSender sender) {
        plugin.getSchedulerManager().runAsync("marry-list", () -> {
            List<MarryDatabase.FullMarriageInfo> list = database.getAllMarriages();

            plugin.getSchedulerManager().runGlobalTask("marry-list-result", () -> {
                if (list.isEmpty()) {
                    sender.sendMessage(module.getMessage("list-empty"));
                    return;
                }

                sender.sendMessage(module.getMessage("list-header"));
                SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy");
                for (MarryDatabase.FullMarriageInfo m : list) {
                    sender.sendMessage(module.getMessage("list-entry")
                        .replace("{player1}", m.getPlayer1Name())
                        .replace("{player2}", m.getPlayer2Name())
                        .replace("{date}", df.format(m.getMarriedDate())));
                }
                sender.sendMessage(module.getMessage("list-footer")
                    .replace("{count}", String.valueOf(list.size())));
            });
        });
    }

    // ── /marry migrate (admin only) ──────────────────────────────────

    private void handleMigrate(CommandSender sender) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(module.getMessage("no-permission"));
            return;
        }

        if (database == null) {
            sender.sendMessage(module.getMessage("migrate-no-database"));
            return;
        }

        sender.sendMessage(module.getMessage("migrate-started"));

        plugin.getSchedulerManager().runAsync("marry-migrate", () -> {
            MarryDatabase.MigrationResult result = database.migrateFromLocalFile();

            plugin.getSchedulerManager().runGlobalTask("marry-migrate-result", () -> {
                if (result.isSuccess()) {
                    if (result.getMigratedCount() == 0 && result.getSkippedCount() == 0) {
                        sender.sendMessage(module.getMessage("migrate-empty")
                            .replace("{details}", result.getMessage()));
                    } else {
                        sender.sendMessage(module.getMessage("migrate-success")
                            .replace("{migrated}", String.valueOf(result.getMigratedCount()))
                            .replace("{skipped}", String.valueOf(result.getSkippedCount()))
                            .replace("{details}", result.getMessage()));
                    }
                } else {
                    sender.sendMessage(module.getMessage("migrate-failed")
                        .replace("{error}", result.getMessage()));
                }
            });
        });
    }

    // ── Tab completion ───────────────────────────────────────────────

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> out = new ArrayList<>();

            for (String sub : List.of("confirm", "deny", "info", "list")) {
                if (sub.startsWith(input)) out.add(sub);
            }
            if (hasAdminPermission(sender) && "admin".startsWith(input)) {
                out.add("admin");
            }
            if (hasAdminPermission(sender) && "migrate".startsWith(input)) {
                out.add("migrate");
            }
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

            if (first.equals("admin") && hasAdminPermission(sender)) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) out.add(p.getName());
                }
                return out;
            }

            if (first.equals("info")
             || (!List.of("confirm", "deny", "list", "admin").contains(first) && hasPriestPermission(sender))) {
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
                || (!List.of("confirm", "deny", "info", "list", "admin").contains(first) && hasPriestPermission(sender));

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

    private void playCatSound(Player player, boolean isMarriage) {
        if (!module.getConfig().getBoolean("settings.sounds.enabled", true)) return;

        String soundName = isMarriage
            ? module.getConfig().getString("settings.sounds.marriage-sound", "ENTITY_CAT_PURR")
            : module.getConfig().getString("settings.sounds.divorce-sound", "ENTITY_CAT_HISS");

        Sound fallback = isMarriage ? Sound.ENTITY_CAT_PURR : Sound.ENTITY_CAT_HISS;
        Sound sound = resolveSound(soundName, fallback);
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
