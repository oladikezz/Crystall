package site.deforce.SM_Clans.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.command.ModuleCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.gui.ClanMenuManager;
import site.deforce.SM_Clans.listeners.ClanCreationListener;
import site.deforce.SM_Clans.logging.ClanLogDatabase;
import site.deforce.SM_Clans.managers.ClanEconomyManager;
import site.deforce.SM_Clans.managers.ClanInviteManager;
import site.deforce.SM_Clans.managers.ClanManager;
import site.deforce.SM_Clans.managers.ClanRentManager;
import site.deforce.SM_Clans.managers.ClanSettingsManager;
import site.deforce.SM_Clans.managers.ClanTaxManager;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanLogEntry;
import site.deforce.SM_Clans.models.DefaultClanRole;
import site.deforce.SM_Clans.models.PendingPurchase;

public class ClanCommand extends ModuleCommand {
   private final SM_Clans module;
   private final DoAPI plugin;

   public ClanCommand(DoAPI plugin, SM_Clans module) {
      super(plugin);
      this.plugin = plugin;
      this.module = module;
   }

   public String getName() {
      return "clan";
   }

   public String getPermission() {
      return "smclans.clan";
   }

   public String getDescription() {
      return "Clan command management";
   }

   public String getUsage() {
      return "/clan [create|invite|kick|leave|disband|info|list|menu]";
   }

   public Collection<String> getAliases() {
      return Arrays.asList("clans", "c", "guild", "guilds");
   }

   public void execute(CommandSourceStack stack, String[] args) {
      this.executeSender(stack.getSender(), args);
   }

   public void executeSender(CommandSender sender, String[] args) {
      if (!this.module.isEnabled()) {
         sender.sendMessage(Component.text("§c[Clans] Module is currently disabled."));
      } else {
         ClanMenuManager menuManager = this.module.getMenuManager();
         ClanManager clanManager = this.module.getClanManager();
         ClanInviteManager clanInviteManager = this.module.getClanInviteManager();
         ClanSettingsManager clanSettingsManager = this.module.getClanSettingsManager();
         ClanCreationListener creationListener = this.module.getCreationListener();
         if (menuManager != null && clanManager != null && clanInviteManager != null && clanSettingsManager != null && creationListener != null) {
            if (sender instanceof Player) {
               Player player = (Player)sender;
               if (!player.hasPermission(this.getPermission())) {
                  this.sendMessage(player, this.getMessage("no-permission"));
               } else if (args.length == 0) {
                  menuManager.openMainMenu(player);
               } else {
                  String subCommand = args[0].toLowerCase();
                  String[] subArgs = (String[])Arrays.copyOfRange(args, 1, args.length);
                  switch (subCommand) {
                     case "create" -> this.handleCreate(player, subArgs);
                     case "invite" -> this.handleInvite(player, subArgs);
                     case "kick" -> this.handleKick(player, subArgs);
                     case "leave" -> this.handleLeave(player, subArgs);
                     case "disband" -> this.handleDisband(player, subArgs);
                     case "profile" -> this.handleProfile(player, subArgs);
                     case "list" -> this.handleList(player, subArgs);
                     case "menu" -> menuManager.openMainMenu(player);
                     case "privacy" -> this.handlePrivacy(player, subArgs);
                     case "accept" -> this.handleAccept(player, subArgs);
                     case "deny" -> this.handleDeny(player, subArgs);
                     case "role" -> this.handleRole(player, subArgs);
                     case "setname" -> this.handleSetName(player, subArgs);
                     case "settag" -> this.handleSetTag(player, subArgs);
                     case "chat" -> this.handleChatToggle(player, subArgs);
                     case "promote" -> this.handlePromote(player, subArgs);
                     case "admin" -> this.handleAdmin(player, subArgs);
                     case "setflag" -> this.handleSetFlag(player, subArgs);
                     case "bank" -> this.handleBank(player, subArgs);
                     case "deposit" -> this.handleDeposit(player, subArgs);
                     case "withdraw" -> this.handleWithdraw(player, subArgs);
                     case "buyslots" -> this.handleBuySlots(player, subArgs);
                     case "logs" -> this.handleLogs(player, subArgs);
                     case "taxes" -> this.handleTaxes(player, subArgs);
                     default -> this.sendMessage(player, this.getMessage("unknown-command"));
                  }
               }
            } else {
               sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(this.getMessage("only-players")));
            }

         } else {
            sender.sendMessage(Component.text("§c[Clans] Module is still loading, please wait..."));
         }
      }
   }

   private void handleCreate(Player player, String[] ignoredArgs) {
      ClanCreationListener creationListener = this.module.getCreationListener();
      if (creationListener != null) {
         creationListener.startClanCreation(player);
      }
   }

   private void handleInvite(Player player, String[] args) {
      ClanInviteManager clanInviteManager = this.module.getClanInviteManager();
      if (clanInviteManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.invite"));
         } else {
            clanInviteManager.invitePlayer(player, args[0]);
         }

      }
   }

   private void handleKick(Player player, String[] args) {
      ClanInviteManager clanInviteManager = this.module.getClanInviteManager();
      if (clanInviteManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.kick"));
         } else {
            clanInviteManager.kickPlayer(player, args[0]);
         }

      }
   }

   private void handleLeave(Player player, String[] ignoredArgs) {
      ClanInviteManager clanInviteManager = this.module.getClanInviteManager();
      if (clanInviteManager != null) {
         clanInviteManager.leaveClan(player);
      }
   }

   private void handleDisband(Player player, String[] ignoredArgs) {
      ClanManager clanManager = this.module.getClanManager();
      if (clanManager != null) {
         Clan clan = clanManager.getPlayerClan(player.getUniqueId());
         if (clan == null) {
            this.sendMessage(player, this.getMessage("not-in-clan"));
         } else if (!clan.getLeaderId().equals(player.getUniqueId())) {
            this.sendMessage(player, this.getMessage("only-leader-can-disband"));
         } else {
            this.module.requestDisbandConfirm(player.getUniqueId());
            this.sendMessage(player, this.getMessage("disband.confirm-required"));
            this.sendMessage(player, this.getMessage("disband.confirm-hint"));
         }

      }
   }

   private void handleProfile(Player player, String[] args) {
      ClanMenuManager menuManager = this.module.getMenuManager();
      if (menuManager != null) {
         if (args.length == 0) {
            menuManager.openOwnClanProfile(player);
         } else {
            Clan clan = this.findClanByTagOrName(args[0]);
            if (clan == null) {
               this.sendMessage(player, this.getMessage("clan-not-found"));
               return;
            }

            menuManager.openClanProfile(player, clan);
         }

      }
   }

   private void handleList(Player player, String[] ignoredArgs) {
      ClanMenuManager menuManager = this.module.getMenuManager();
      if (menuManager != null) {
         menuManager.openClanList(player);
      }
   }

   private void handlePrivacy(Player player, String[] args) {
      ClanSettingsManager clanSettingsManager = this.module.getClanSettingsManager();
      if (clanSettingsManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.privacy"));
         } else {
            clanSettingsManager.changePrivacy(player, args[0]);
         }

      }
   }

   private void handleAccept(Player player, String[] args) {
      ClanInviteManager clanInviteManager = this.module.getClanInviteManager();
      if (clanInviteManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.accept"));
         } else {
            clanInviteManager.acceptInvite(player, args[0]);
         }

      }
   }

   private void handleDeny(Player player, String[] args) {
      ClanInviteManager clanInviteManager = this.module.getClanInviteManager();
      if (clanInviteManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.deny"));
         } else {
            clanInviteManager.denyInvite(player, args[0]);
         }

      }
   }

   private void handleSetName(Player player, String[] args) {
      ClanSettingsManager clanSettingsManager = this.module.getClanSettingsManager();
      if (clanSettingsManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.setname"));
         } else {
            String newName = String.join(" ", args);
            ClanEconomyManager econ = this.module.getClanEconomyManager();
            long cost = econ == null ? 0L : (long)econ.getNameCost();
            this.module.requestPurchase(player, new PendingPurchase(PendingPurchase.Type.CHANGE_NAME, newName, cost, "Название → " + newName));
         }

      }
   }

   private void handleSetTag(Player player, String[] args) {
      ClanSettingsManager clanSettingsManager = this.module.getClanSettingsManager();
      if (clanSettingsManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.settag"));
         } else {
            ClanEconomyManager econ = this.module.getClanEconomyManager();
            long cost = econ == null ? 0L : (long)econ.getTagCost();
            this.module.requestPurchase(player, new PendingPurchase(PendingPurchase.Type.CHANGE_TAG, args[0], cost, "Тег → " + args[0]));
         }

      }
   }

   private void handleRole(Player player, String[] args) {
      ClanSettingsManager clanSettingsManager = this.module.getClanSettingsManager();
      if (clanSettingsManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.role"));
         } else {
            String action = args[0].toLowerCase();
            if ("assign".equals(action)) {
               if (args.length < 3) {
                  this.sendMessage(player, this.getMessage("usage.role"));
               } else {
                  clanSettingsManager.assignRole(player, args[1], args[2]);
               }
            } else {
               this.sendMessage(player, this.getMessage("unknown-action"));
            }
         }

      }
   }

   private void handleChatToggle(Player player, String[] ignoredArgs) {
      ClanManager clanManager = this.module.getClanManager();
      if (clanManager != null) {
         Clan clan = clanManager.getPlayerClan(player.getUniqueId());
         if (clan == null) {
            this.sendMessage(player, this.getMessage("not-in-clan"));
         } else if (!clan.getLeaderId().equals(player.getUniqueId())) {
            this.sendMessage(player, this.getMessage("chat.no-permission-toggle"));
         } else {
            boolean newState = !clan.isChatEnabled();
            clan.setChatEnabled(newState);
            clanManager.saveClan(clan);
            this.sendMessage(player, this.getMessage(newState ? "chat.toggled-on" : "chat.toggled-off"));
         }

      }
   }

   private void handlePromote(Player player, String[] args) {
      ClanManager clanManager = this.module.getClanManager();
      if (clanManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.promote"));
         } else {
            Clan clan = clanManager.getPlayerClan(player.getUniqueId());
            if (clan == null) {
               this.sendMessage(player, this.getMessage("not-in-clan"));
            } else if (!clan.getLeaderId().equals(player.getUniqueId())) {
               this.sendMessage(player, this.getMessage("no-permission"));
            } else {
               String targetName = args[0];
               this.module.requestPromoteConfirm(player.getUniqueId(), targetName);
               this.sendMessage(player, this.getMessage("promote.confirm-required").replace("{player}", targetName));
               this.sendMessage(player, this.getMessage("promote.confirm-hint"));
            }
         }

      }
   }

   private void handleAdmin(Player player, String[] args) {
      if (!player.hasPermission("smclans.clan.admin")) {
         this.sendMessage(player, this.getMessage("admin.no-permission"));
      } else if (args.length >= 1 && args[0].equalsIgnoreCase("restore")) {
         this.handleAdminRestore(player, args);
      } else {
         ClanMenuManager menuManager = this.module.getMenuManager();
         if (menuManager != null) {
            menuManager.openAdminClanMenu(player);
         }

      }
   }

   private void handleAdminRestore(Player admin, String[] args) {
      ClanRentManager rentManager = this.module.getClanRentManager();
      if (rentManager != null) {
         if (args.length < 2) {
            rentManager.listArchived(admin);
         } else {
            rentManager.restore(admin, args[1]);
         }
      }
   }

   private void handleSetFlag(Player player, String[] args) {
      ClanSettingsManager clanSettingsManager = this.module.getClanSettingsManager();
      if (clanSettingsManager != null) {
         ItemStack mainHand = player.getInventory().getItemInMainHand();
         boolean holdingBanner = mainHand != null && mainHand.getType().name().endsWith("_BANNER");
         ClanEconomyManager econ = this.module.getClanEconomyManager();
         if (holdingBanner && econ != null) {
            this.module.requestPurchase(player, new PendingPurchase(PendingPurchase.Type.SET_FLAG, (String)null, (long)econ.getCustomBannerCost(), "Кастомное знамя гильдии"));
         } else {
            clanSettingsManager.setClanFlag(player);
         }

      }
   }

   private void handleBank(Player player, String[] ignoredArgs) {
      ClanMenuManager menuManager = this.module.getMenuManager();
      if (menuManager != null) {
         menuManager.openBankMenu(player);
      }
   }

   private void handleDeposit(Player player, String[] args) {
      ClanEconomyManager economyManager = this.module.getClanEconomyManager();
      if (economyManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.deposit"));
         } else {
            Integer amount = this.parseAmount(args[0]);
            if (amount == null) {
               this.sendMessage(player, this.getMessage("economy.invalid-amount"));
            } else {
               economyManager.deposit(player, amount);
            }
         }
      }
   }

   private void handleWithdraw(Player player, String[] args) {
      ClanEconomyManager economyManager = this.module.getClanEconomyManager();
      if (economyManager != null) {
         if (args.length < 1) {
            this.sendMessage(player, this.getMessage("usage.withdraw"));
         } else {
            Integer amount = this.parseAmount(args[0]);
            if (amount == null) {
               this.sendMessage(player, this.getMessage("economy.invalid-amount"));
            } else {
               economyManager.withdraw(player, amount);
            }
         }
      }
   }

   private void handleBuySlots(Player player, String[] ignoredArgs) {
      ClanEconomyManager economyManager = this.module.getClanEconomyManager();
      if (economyManager != null) {
         long cost = (long)economyManager.getSlotCostPerSlot() * (long)economyManager.getSlotsPerPurchase();
         int slots = economyManager.getSlotsPerPurchase();
         this.module.requestPurchase(player, new PendingPurchase(PendingPurchase.Type.BUY_SLOTS, (String)null, cost, "+" + slots + " слот(ов)"));
      }
   }

   private void handleLogs(Player player, String[] args) {
      if (!player.hasPermission("smclans.clan.admin")) {
         this.sendMessage(player, this.getMessage("admin.no-permission"));
      } else {
         ClanLogDatabase logDb = this.module.getLogDatabase();
         if (logDb != null && logDb.isReady()) {
            String userFilter = null;
            String actionFilter = null;
            String clanFilter = null;
            long sinceMillis = 0L;
            int page = 1;

            for(String arg : args) {
               String lower = arg.toLowerCase();
               if (!lower.startsWith("user:") && !lower.startsWith("u:")) {
                  if (!lower.startsWith("action:") && !lower.startsWith("a:")) {
                     if (!lower.startsWith("clan:") && !lower.startsWith("c:")) {
                        if (!lower.startsWith("time:") && !lower.startsWith("t:")) {
                           if (!lower.startsWith("page:") && !lower.startsWith("p:")) {
                              if (userFilter == null) {
                                 userFilter = arg;
                              }
                           } else {
                              try {
                                 page = Math.max(1, Integer.parseInt(arg.substring(arg.indexOf(58) + 1)));
                              } catch (NumberFormatException var17) {
                              }
                           }
                        } else {
                           long window = this.parseTimeWindow(arg.substring(arg.indexOf(58) + 1));
                           if (window > 0L) {
                              sinceMillis = System.currentTimeMillis() - window;
                           }
                        }
                     } else {
                        clanFilter = arg.substring(arg.indexOf(58) + 1);
                     }
                  } else {
                     actionFilter = arg.substring(arg.indexOf(58) + 1);
                  }
               } else {
                  userFilter = arg.substring(arg.indexOf(58) + 1);
               }
            }

            int pageSize = 8;
            String finalUserFilter = userFilter;
            String finalActionFilter = actionFilter;
            String finalClanFilter = clanFilter;
            long finalSinceMillis = sinceMillis;
            int finalPage = page;
            this.plugin.getSchedulerManager().runAsync("clan-logs-lookup", () -> {
               List<ClanLogEntry> entries = logDb.lookup(finalUserFilter, finalActionFilter, finalClanFilter, finalSinceMillis, 9, (finalPage - 1) * 8);
               boolean hasNext = entries.size() > 8;
               List<ClanLogEntry> pageEntries = hasNext ? entries.subList(0, 8) : entries;
               this.plugin.getSchedulerManager().runEntityTask(player, "clan-logs-print", () -> {
                  if (player.isOnline()) {
                     this.sendMessage(player, "§8§m                    §r §6Логи гильдий §7(стр. " + finalPage + ") §8§m                    ");
                     if (pageEntries.isEmpty()) {
                        this.sendMessage(player, "§7Записи не найдены.");
                     } else {
                        for(ClanLogEntry entry : pageEntries) {
                           this.sendMessage(player, this.formatLogEntry(entry));
                        }

                        if (hasNext) {
                           this.sendMessage(player, "§7Дальше: §e/clan logs ... page:" + (finalPage + 1));
                        }

                     }
                  }
               });
            });
         } else {
            this.sendMessage(player, "§c[Логи] База логов недоступна.");
         }
      }
   }

   private void handleTaxes(Player player, String[] args) {
      if (!player.hasPermission("smclans.taxes")) {
         this.sendMessage(player, this.getMessage("taxes.no-permission"));
      } else {
         ClanTaxManager taxManager = this.module.getTaxManager();
         if (taxManager != null) {
            if (args.length != 0 && !args[0].equalsIgnoreCase("balance")) {
               if (args[0].equalsIgnoreCase("withdraw")) {
                  long amount = 0L;
                  if (args.length >= 2 && !args[1].equalsIgnoreCase("all")) {
                     try {
                        amount = Long.parseLong(args[1]);
                     } catch (NumberFormatException var7) {
                        this.sendMessage(player, this.getMessage("taxes.withdraw-usage"));
                        return;
                     }

                     if (amount <= 0L) {
                        this.sendMessage(player, this.getMessage("taxes.withdraw-usage"));
                        return;
                     }
                  }

                  taxManager.withdraw(player, amount);
               } else {
                  this.sendMessage(player, this.getMessage("taxes.withdraw-usage"));
               }
            } else {
               this.sendMessage(player, this.getMessage("taxes.balance").replace("{balance}", String.valueOf(taxManager.getBalance())).replace("{total}", String.valueOf(taxManager.getTotalCollected())));
            }
         }
      }
   }

   private long parseTimeWindow(String input) {
      if (input != null && !input.isEmpty()) {
         long total = 0L;
         StringBuilder number = new StringBuilder();

         for(char ch : input.toCharArray()) {
            if (Character.isDigit(ch)) {
               number.append(ch);
            } else if (number.length() != 0) {
               long value = Long.parseLong(number.toString());
               long var10001;
               switch (Character.toLowerCase(ch)) {
                  case 'd' -> var10001 = value * 24L * 60L * 60L * 1000L;
                  case 'h' -> var10001 = value * 60L * 60L * 1000L;
                  case 'm' -> var10001 = value * 60L * 1000L;
                  case 's' -> var10001 = value * 1000L;
                  case 'w' -> var10001 = value * 7L * 24L * 60L * 60L * 1000L;
                  default -> var10001 = 0L;
               }

               total += var10001;
               number.setLength(0);
            }
         }

         if (number.length() > 0) {
            total += Long.parseLong(number.toString()) * 24L * 60L * 60L * 1000L;
         }

         return total;
      } else {
         return 0L;
      }
   }

   private String formatLogEntry(ClanLogEntry entry) {
      String when = (new SimpleDateFormat("dd.MM HH:mm")).format(new Date(entry.getTimestamp()));
      StringBuilder line = new StringBuilder();
      line.append("§8[").append(when).append("] ");
      line.append("§b").append(entry.getAction()).append(" §7| ");
      line.append("§f").append(entry.getActorName() == null ? "SYSTEM" : entry.getActorName());
      if (entry.getClanTag() != null && !entry.getClanTag().isEmpty()) {
         line.append(" §7@ §e[").append(entry.getClanTag()).append("]");
      }

      if (entry.getTarget() != null && !entry.getTarget().isEmpty()) {
         line.append(" §7→ §f").append(entry.getTarget());
      }

      if (entry.getAmount() != null) {
         line.append(" §6(").append(entry.getAmount()).append(" ар)");
      }

      if (entry.getBalance() != null) {
         line.append(" §8казна: ").append(entry.getBalance());
      }

      return line.toString();
   }

   private Integer parseAmount(String input) {
      if (input != null && !input.equalsIgnoreCase("all")) {
         try {
            int value = Integer.parseInt(input);
            return value <= 0 ? null : value;
         } catch (NumberFormatException var3) {
            return null;
         }
      } else {
         return -1;
      }
   }

   public Collection<String> suggest(CommandSourceStack stack, String[] args) {
      return this.suggestSender(stack.getSender(), args);
   }

   public Collection<String> suggestSender(CommandSender sender, String[] args) {
      if (this.module.isEnabled() && this.module.getClanManager() != null) {
         List<String> completions = new ArrayList();
         if (sender instanceof Player) {
            Player player = (Player)sender;
            boolean isAdmin = player.hasPermission("smclans.clan.admin");
            if (args.length <= 1) {
               String input = args.length > 0 ? args[0].toLowerCase() : "";
               List<String> commands = new ArrayList(Arrays.asList("create", "invite", "kick", "leave", "disband", "profile", "list", "menu", "privacy", "accept", "deny", "role", "setname", "settag", "chat", "promote", "setflag", "bank", "deposit", "withdraw", "buyslots"));
               if (isAdmin) {
                  commands.add("admin");
                  commands.add("logs");
               }

               if (player.hasPermission("smclans.taxes")) {
                  commands.add("taxes");
               }

               completions.addAll(this.filterStartsWith(commands, input));
               return completions;
            }

            String subCommand = args[0].toLowerCase();
            String input = args[args.length - 1].toLowerCase();
            if (subCommand.equals("logs") && isAdmin) {
               if (input.startsWith("action:")) {
                  List<String> actions = Arrays.asList("BUY_SLOTS", "CHANGE_NAME", "CHANGE_TAG", "CHANGE_DESCRIPTION", "CHANGE_BANNER", "SET_FLAG", "CREATE", "DISBAND", "RENT", "DEPOSIT", "WITHDRAW", "JOIN", "LEAVE", "KICK", "ROLE_CHANGE", "TRANSFER", "TAX_COLLECT", "TAX_WITHDRAW");
                  return (Collection)actions.stream().map((a) -> "action:" + a).filter((s) -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
               }

               if (input.startsWith("clan:")) {
                  return (Collection)this.module.getClanManager().getAllClans().stream().map(Clan::getTag).map(this::stripColorCodes).map((t) -> "clan:" + t).filter((s) -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
               }

               if (input.startsWith("user:")) {
                  return (Collection)Bukkit.getOnlinePlayers().stream().map((p) -> "user:" + p.getName()).filter((s) -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
               }

               return this.filterStartsWith(Arrays.asList("user:", "action:", "clan:", "time:7d", "page:1"), input);
            }

            if (args.length == 2) {
               switch (subCommand) {
                  case "invite":
                     return this.filterStartsWith((List)Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), input);
                  case "kick":
                  case "promote":
                  case "role":
                     Clan clan = this.module.getClanManager().getPlayerClan(player.getUniqueId());
                     if (clan != null) {
                        List<String> members = new ArrayList();

                        for(UUID memberId : clan.getMembers().keySet()) {
                           String name = Bukkit.getOfflinePlayer(memberId).getName();
                           if (name != null) {
                              members.add(name);
                           }
                        }

                        return this.filterStartsWith(members, input);
                     }
                     break;
                  case "accept":
                  case "deny":
                     return this.filterStartsWith((List)Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), input);
                  case "profile":
                     List<String> clanTags = (List)this.module.getClanManager().getAllClans().stream().map(Clan::getTag).map(this::stripColorCodes).collect(Collectors.toList());
                     return this.filterStartsWith(clanTags, input);
                  case "privacy":
                     return this.filterStartsWith(Arrays.asList("public", "private", "invite_only"), input);
                  case "deposit":
                  case "withdraw":
                     return this.filterStartsWith(Arrays.asList("all", "1", "16", "32", "64"), input);
                  case "taxes":
                     if (player.hasPermission("smclans.taxes")) {
                        return this.filterStartsWith(Arrays.asList("balance", "withdraw"), input);
                     }
                     break;
                  case "setname":
                  case "settag":
                  case "create":
                     return completions;
                  case "admin":
                     if (isAdmin) {
                        return this.filterStartsWith(Arrays.asList("restore"), input);
                     }
               }
            } else if (args.length == 3) {
               if (subCommand.equals("taxes") && args[1].equalsIgnoreCase("withdraw") && player.hasPermission("smclans.taxes")) {
                  return this.filterStartsWith(Arrays.asList("all", "16", "32", "64"), input);
               }

               if (subCommand.equals("role") && args[1].equalsIgnoreCase("assign")) {
                  return this.filterStartsWith(Arrays.asList("assign"), input);
               }
            } else if (args.length == 4 && subCommand.equals("role") && args[1].equalsIgnoreCase("assign")) {
               return this.filterStartsWith((List)Arrays.stream(DefaultClanRole.values()).map(DefaultClanRole::getId).collect(Collectors.toList()), input);
            }
         }

         return completions;
      } else {
         return new ArrayList();
      }
   }

   private List<String> filterStartsWith(List<String> list, String prefix) {
      return (List)list.stream().filter((s) -> s.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
   }

   private Clan findClanByTagOrName(String input) {
      if (input != null && !input.isEmpty()) {
         ClanManager clanManager = this.module.getClanManager();
         if (clanManager == null) {
            return null;
         } else {
            Clan clan = clanManager.getClanByTag(input);
            if (clan != null) {
               return clan;
            } else {
               String cleanInput = this.stripColorCodes(input).trim();

               for(Clan candidate : clanManager.getAllClans()) {
                  String cleanName = this.stripColorCodes(candidate.getName()).trim();
                  if (cleanName.equalsIgnoreCase(cleanInput)) {
                     return candidate;
                  }
               }

               return null;
            }
         }
      } else {
         return null;
      }
   }

   private String stripColorCodes(String text) {
      if (text == null) {
         return "";
      } else {
         String noLegacy = text.replaceAll("(?i)[&§][0-9a-fk-orx]", "");
         return noLegacy.replaceAll("(?i)(?:&#|#)[0-9a-f]{6}", "");
      }
   }

   private String normalizeHexColors(String text) {
      return text == null ? "" : text.replaceAll("(?<![&§])(#[0-9a-fA-F]{6})", "&$1");
   }

   private String getMessage(String key) {
      FileConfiguration config = this.module.getMessages();
      if (config == null) {
         return "§cMessage not found: " + key;
      } else {
         String message = config.getString(key, "§cMessage not found: " + key);
         String prefix = config.getString("prefix", "");
         FileConfiguration mainConfig = this.module.getConfig();
         if (mainConfig != null) {
            boolean prefixEnabled = mainConfig.getBoolean("prefix.enabled", true);
            if (!prefixEnabled) {
               prefix = "";
            }
         }

         String combined = message.replace("<prefix>", prefix);
         return this.module.getPlugin().applyColors(combined);
      }
   }

   private void sendMessage(Player player, String message) {
      if (message != null && !message.isEmpty()) {
         this.plugin.getSchedulerManager().runEntityTask(player, "clan-command-message", () -> {
            if (player.isOnline()) {
               player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
            }

         });
      }

   }

   private void notifyClanMembers(Clan clan, String message) {
      if (message != null && !message.isEmpty()) {
         for(UUID memberId : clan.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
               this.sendMessage(member, message);
            }
         }

      }
   }
}
