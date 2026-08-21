package site.deforce.SM_Clans.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.schalker.DoAPI.DoAPI;
import net.schalker.DoAPI.core.database.ModuleDatabase;
import site.deforce.SM_Clans.models.Clan;
import site.deforce.SM_Clans.models.ClanMember;
import site.deforce.SM_Clans.models.ClanPrivacy;

public class DatabaseManager extends ModuleDatabase {
   public DatabaseManager(DoAPI plugin) {
      super(plugin, "clans");
   }

   public void createTables() {
      try {
         Connection conn = this.getConnection();

         try {
            Statement stmt = conn.createStatement();

            try {
               if (this.isSqliteOrH2()) {
                  stmt.execute("   CREATE TABLE IF NOT EXISTS %s (\n      clan_id TEXT PRIMARY KEY,\n      tag TEXT NOT NULL UNIQUE,\n      name TEXT NOT NULL,\n      description TEXT DEFAULT '',\n      leader_id TEXT NOT NULL,\n      privacy TEXT NOT NULL DEFAULT 'PUBLIC',\n      created_at INTEGER NOT NULL,\n      max_members INTEGER NOT NULL DEFAULT 50,\n      chat_enabled INTEGER NOT NULL DEFAULT 1,\n      banner_color TEXT DEFAULT 'WHITE',\n      tag_enabled INTEGER NOT NULL DEFAULT 1,\n      profile_public INTEGER NOT NULL DEFAULT 1,\n      friendly_fire INTEGER NOT NULL DEFAULT 0,\n      flag_data TEXT DEFAULT NULL,\n      balance INTEGER NOT NULL DEFAULT 0,\n      last_rent_at INTEGER NOT NULL DEFAULT 0,\n      treasury_data TEXT DEFAULT ''\n   )\n".formatted(this.table("clans")));
                  stmt.execute("   CREATE TABLE IF NOT EXISTS %s (\n      player_id TEXT NOT NULL,\n      clan_id TEXT NOT NULL,\n      role_id TEXT NOT NULL,\n      joined_at INTEGER NOT NULL,\n      last_seen INTEGER NOT NULL,\n      PRIMARY KEY (player_id, clan_id)\n   )\n".formatted(this.table("members")));
                  stmt.execute("   CREATE TABLE IF NOT EXISTS %s (\n      clan_id TEXT PRIMARY KEY,\n      tag TEXT NOT NULL,\n      name TEXT NOT NULL,\n      description TEXT DEFAULT '',\n      leader_id TEXT NOT NULL,\n      privacy TEXT NOT NULL DEFAULT 'PUBLIC',\n      created_at INTEGER NOT NULL,\n      max_members INTEGER NOT NULL DEFAULT 50,\n      chat_enabled INTEGER NOT NULL DEFAULT 1,\n      banner_color TEXT DEFAULT 'WHITE',\n      tag_enabled INTEGER NOT NULL DEFAULT 1,\n      profile_public INTEGER NOT NULL DEFAULT 1,\n      friendly_fire INTEGER NOT NULL DEFAULT 0,\n      flag_data TEXT DEFAULT NULL,\n      balance INTEGER NOT NULL DEFAULT 0,\n      last_rent_at INTEGER NOT NULL DEFAULT 0,\n      treasury_data TEXT DEFAULT '',\n      archived_at INTEGER NOT NULL,\n      reason TEXT DEFAULT ''\n   )\n".formatted(this.table("archived_clans")));
                  stmt.execute("   CREATE TABLE IF NOT EXISTS %s (\n      player_id TEXT NOT NULL,\n      clan_id TEXT NOT NULL,\n      role_id TEXT NOT NULL,\n      joined_at INTEGER NOT NULL,\n      last_seen INTEGER NOT NULL,\n      PRIMARY KEY (player_id, clan_id)\n   )\n".formatted(this.table("archived_members")));
                  stmt.execute("   CREATE TABLE IF NOT EXISTS %s (\n      id INTEGER PRIMARY KEY,\n      balance INTEGER NOT NULL DEFAULT 0,\n      total_collected INTEGER NOT NULL DEFAULT 0\n   )\n".formatted(this.table("taxes")));
               } else {
                  stmt.execute("   CREATE TABLE IF NOT EXISTS %s (\n      clan_id VARCHAR(36) PRIMARY KEY,\n      tag VARCHAR(64) NOT NULL UNIQUE,\n      name VARCHAR(255) NOT NULL,\n      description VARCHAR(256) DEFAULT '',\n      leader_id VARCHAR(36) NOT NULL,\n      privacy VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',\n      created_at BIGINT NOT NULL,\n      max_members INT NOT NULL DEFAULT 50,\n      chat_enabled BOOLEAN NOT NULL DEFAULT 1,\n      banner_color VARCHAR(20) DEFAULT 'WHITE',\n      tag_enabled BOOLEAN NOT NULL DEFAULT 1,\n      profile_public BOOLEAN NOT NULL DEFAULT 1,\n      friendly_fire BOOLEAN NOT NULL DEFAULT 0,\n      flag_data VARCHAR(512) DEFAULT NULL,\n      balance BIGINT NOT NULL DEFAULT 0,\n      last_rent_at BIGINT NOT NULL DEFAULT 0,\n      treasury_data VARCHAR(512) DEFAULT ''\n   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n".formatted(this.table("clans")));
                  stmt.execute("   CREATE TABLE IF NOT EXISTS %s (\n      player_id VARCHAR(36) NOT NULL,\n      clan_id VARCHAR(36) NOT NULL,\n      role_id VARCHAR(50) NOT NULL,\n      joined_at BIGINT NOT NULL,\n      last_seen BIGINT NOT NULL,\n      PRIMARY KEY (player_id, clan_id),\n      INDEX idx_clan (clan_id)\n   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n".formatted(this.table("members")));
                  stmt.execute("   CREATE TABLE IF NOT EXISTS %s (\n      clan_id VARCHAR(36) PRIMARY KEY,\n      tag VARCHAR(64) NOT NULL,\n      name VARCHAR(255) NOT NULL,\n      description VARCHAR(256) DEFAULT '',\n      leader_id VARCHAR(36) NOT NULL,\n      privacy VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',\n      created_at BIGINT NOT NULL,\n      max_members INT NOT NULL DEFAULT 50,\n      chat_enabled BOOLEAN NOT NULL DEFAULT 1,\n      banner_color VARCHAR(20) DEFAULT 'WHITE',\n      tag_enabled BOOLEAN NOT NULL DEFAULT 1,\n      profile_public BOOLEAN NOT NULL DEFAULT 1,\n      friendly_fire BOOLEAN NOT NULL DEFAULT 0,\n      flag_data VARCHAR(512) DEFAULT NULL,\n      balance BIGINT NOT NULL DEFAULT 0,\n      last_rent_at BIGINT NOT NULL DEFAULT 0,\n      treasury_data VARCHAR(512) DEFAULT '',\n      archived_at BIGINT NOT NULL,\n      reason VARCHAR(64) DEFAULT ''\n   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n".formatted(this.table("archived_clans")));
                  stmt.execute("   CREATE TABLE IF NOT EXISTS %s (\n      player_id VARCHAR(36) NOT NULL,\n      clan_id VARCHAR(36) NOT NULL,\n      role_id VARCHAR(50) NOT NULL,\n      joined_at BIGINT NOT NULL,\n      last_seen BIGINT NOT NULL,\n      PRIMARY KEY (player_id, clan_id),\n      INDEX idx_arch_clan (clan_id)\n   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n".formatted(this.table("archived_members")));
                  stmt.execute("   CREATE TABLE IF NOT EXISTS %s (\n      id INT PRIMARY KEY,\n      balance BIGINT NOT NULL DEFAULT 0,\n      total_collected BIGINT NOT NULL DEFAULT 0\n   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n".formatted(this.table("taxes")));
               }

               this.addColumnIfMissing(stmt, this.table("clans"), "balance", this.isSqliteOrH2() ? "INTEGER NOT NULL DEFAULT 0" : "BIGINT NOT NULL DEFAULT 0");
               this.addColumnIfMissing(stmt, this.table("clans"), "last_rent_at", this.isSqliteOrH2() ? "INTEGER NOT NULL DEFAULT 0" : "BIGINT NOT NULL DEFAULT 0");
               String treasuryDef = this.isSqliteOrH2() ? "TEXT DEFAULT ''" : "VARCHAR(512) DEFAULT ''";
               this.addColumnIfMissing(stmt, this.table("clans"), "treasury_data", treasuryDef);
               this.addColumnIfMissing(stmt, this.table("archived_clans"), "treasury_data", treasuryDef);
               this.plugin.getDebugSystem().log("ClansDB", "Tables created successfully");
            } catch (Throwable var7) {
               if (stmt != null) {
                  try {
                     stmt.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }
               }

               throw var7;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var8) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var5) {
                  var8.addSuppressed(var5);
               }
            }

            throw var8;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException exception) {
         this.plugin.getDebugSystem().logError("Failed to create tables", exception);
      }

   }

   private void addColumnIfMissing(Statement stmt, String tableName, String columnName, String definition) {
      try {
         stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
         this.plugin.getDebugSystem().log("ClansDB", "Added missing column " + columnName + " to " + tableName);
      } catch (SQLException var6) {
      }

   }

   public void saveClan(Clan clan) throws SQLException {
      String sql;
      if (this.isSqliteOrH2()) {
         sql = "   MERGE INTO %s (clan_id, tag, name, description, leader_id, privacy, created_at, max_members, chat_enabled, banner_color, tag_enabled, profile_public, friendly_fire, flag_data, balance, last_rent_at, treasury_data)\n   KEY(clan_id)\n   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n".formatted(this.table("clans"));
      } else {
         sql = "   REPLACE INTO %s\n   (clan_id, tag, name, description, leader_id, privacy, created_at, max_members, chat_enabled, banner_color, tag_enabled, profile_public, friendly_fire, flag_data, balance, last_rent_at, treasury_data)\n   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n".formatted(this.table("clans"));
      }

      Connection conn = this.getConnection();

      try {
         PreparedStatement stmt = conn.prepareStatement(sql);

         try {
            stmt.setString(1, clan.getClanId());
            stmt.setString(2, clan.getTag());
            stmt.setString(3, clan.getName());
            stmt.setString(4, clan.getDescription());
            stmt.setString(5, clan.getLeaderId().toString());
            stmt.setString(6, clan.getPrivacy().name());
            stmt.setLong(7, clan.getCreatedAt());
            stmt.setInt(8, clan.getMaxMembers());
            stmt.setBoolean(9, clan.isChatEnabled());
            stmt.setString(10, clan.getBannerColor());
            stmt.setBoolean(11, clan.isTagEnabled());
            stmt.setBoolean(12, clan.isProfilePublic());
            stmt.setBoolean(13, clan.isFriendlyFire());
            stmt.setString(14, clan.getFlagData());
            stmt.setLong(15, clan.getBalance());
            stmt.setLong(16, clan.getLastRentAt());
            stmt.setString(17, clan.getTreasuryData());
            stmt.executeUpdate();
         } catch (Throwable var9) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var10) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var7) {
               var10.addSuppressed(var7);
            }
         }

         throw var10;
      }

      if (conn != null) {
         conn.close();
      }

      for(ClanMember member : clan.getMembers().values()) {
         this.saveClanMember(member);
      }

   }

   public void saveClanMember(ClanMember member) throws SQLException {
      String sql;
      if (this.isSqliteOrH2()) {
         sql = "   MERGE INTO %s (player_id, clan_id, role_id, joined_at, last_seen)\n   KEY(player_id, clan_id)\n   VALUES (?, ?, ?, ?, ?)\n".formatted(this.table("members"));
      } else {
         sql = "   REPLACE INTO %s\n   (player_id, clan_id, role_id, joined_at, last_seen)\n   VALUES (?, ?, ?, ?, ?)\n".formatted(this.table("members"));
      }

      Connection conn = this.getConnection();

      try {
         PreparedStatement stmt = conn.prepareStatement(sql);

         try {
            stmt.setString(1, member.getPlayerId().toString());
            stmt.setString(2, member.getClanId());
            stmt.setString(3, member.getRoleId());
            stmt.setLong(4, member.getJoinedAt());
            stmt.setLong(5, member.getLastSeen());
            stmt.executeUpdate();
         } catch (Throwable var9) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var10) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var7) {
               var10.addSuppressed(var7);
            }
         }

         throw var10;
      }

      if (conn != null) {
         conn.close();
      }

   }

   public Clan loadClan(String clanId) throws SQLException {
      String sql = "SELECT * FROM %s WHERE clan_id = ?".formatted(this.table("clans"));
      Connection conn = this.getConnection();

      Clan var16;
      label108: {
         try {
            PreparedStatement stmt;
            label110: {
               stmt = conn.prepareStatement(sql);

               try {
                  stmt.setString(1, clanId);
                  ResultSet rs = stmt.executeQuery();

                  label93: {
                     try {
                        if (rs.next()) {
                           Clan clan = new Clan(rs.getString("clan_id"), rs.getString("tag"), rs.getString("name"), UUID.fromString(rs.getString("leader_id")));
                           clan.setCreatedAt(rs.getLong("created_at"));
                           clan.setPrivacy(ClanPrivacy.valueOf(rs.getString("privacy")));
                           clan.setMaxMembers(rs.getInt("max_members"));

                           try {
                              clan.setDescription(rs.getString("description"));
                              clan.setChatEnabled(rs.getBoolean("chat_enabled"));
                              clan.setBannerColor(rs.getString("banner_color"));
                              clan.setTagEnabled(rs.getBoolean("tag_enabled"));
                              clan.setProfilePublic(rs.getBoolean("profile_public"));
                              clan.setFriendlyFire(rs.getBoolean("friendly_fire"));
                              clan.setFlagData(rs.getString("flag_data"));
                              clan.setBalance(rs.getLong("balance"));
                              long lastRent = rs.getLong("last_rent_at");
                              clan.setLastRentAt(lastRent > 0L ? lastRent : System.currentTimeMillis());
                              clan.setTreasuryData(rs.getString("treasury_data"));
                           } catch (SQLException var12) {
                           }

                           this.loadClanMembers(clan);
                           var16 = clan;
                           break label93;
                        }
                     } catch (Throwable var13) {
                        if (rs != null) {
                           try {
                              rs.close();
                           } catch (Throwable var11) {
                              var13.addSuppressed(var11);
                           }
                        }

                        throw var13;
                     }

                     if (rs != null) {
                        rs.close();
                     }
                     break label110;
                  }

                  if (rs != null) {
                     rs.close();
                  }
               } catch (Throwable var14) {
                  if (stmt != null) {
                     try {
                        stmt.close();
                     } catch (Throwable var10) {
                        var14.addSuppressed(var10);
                     }
                  }

                  throw var14;
               }

               if (stmt != null) {
                  stmt.close();
               }
               break label108;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var15) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var9) {
                  var15.addSuppressed(var9);
               }
            }

            throw var15;
         }

         if (conn != null) {
            conn.close();
         }

         return null;
      }

      if (conn != null) {
         conn.close();
      }

      return var16;
   }

   private void loadClanMembers(Clan clan) throws SQLException {
      String sql = "SELECT * FROM %s WHERE clan_id = ?".formatted(this.table("members"));
      Connection conn = this.getConnection();

      try {
         PreparedStatement stmt = conn.prepareStatement(sql);

         try {
            stmt.setString(1, clan.getClanId());
            ResultSet rs = stmt.executeQuery();

            try {
               while(rs.next()) {
                  ClanMember member = new ClanMember(UUID.fromString(rs.getString("player_id")), rs.getString("clan_id"), rs.getString("role_id"));
                  member.setJoinedAt(rs.getLong("joined_at"));
                  member.setLastSeen(rs.getLong("last_seen"));
                  clan.addMember(member);
               }
            } catch (Throwable var11) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (rs != null) {
               rs.close();
            }
         } catch (Throwable var12) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var9) {
                  var12.addSuppressed(var9);
               }
            }

            throw var12;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var13) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var8) {
               var13.addSuppressed(var8);
            }
         }

         throw var13;
      }

      if (conn != null) {
         conn.close();
      }

   }

   public List<Clan> loadAllClans() throws SQLException {
      List<Clan> clans = new ArrayList();
      String sql = "SELECT clan_id FROM %s".formatted(this.table("clans"));
      Connection conn = this.getConnection();

      try {
         Statement stmt = conn.createStatement();

         try {
            ResultSet rs = stmt.executeQuery(sql);

            try {
               while(rs.next()) {
                  String clanId = rs.getString("clan_id");
                  Clan clan = this.loadClan(clanId);
                  if (clan != null) {
                     clans.add(clan);
                  }
               }
            } catch (Throwable var11) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (rs != null) {
               rs.close();
            }
         } catch (Throwable var12) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var9) {
                  var12.addSuppressed(var9);
               }
            }

            throw var12;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var13) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var8) {
               var13.addSuppressed(var8);
            }
         }

         throw var13;
      }

      if (conn != null) {
         conn.close();
      }

      return clans;
   }

   public void deleteClan(String clanId) throws SQLException {
      String sql = "DELETE FROM %s WHERE clan_id = ?".formatted(this.table("clans"));
      Connection conn = this.getConnection();

      try {
         PreparedStatement stmt = conn.prepareStatement(sql);

         try {
            stmt.setString(1, clanId);
            stmt.executeUpdate();
         } catch (Throwable var9) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var10) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var7) {
               var10.addSuppressed(var7);
            }
         }

         throw var10;
      }

      if (conn != null) {
         conn.close();
      }

   }

   public void deleteClanMember(UUID playerId) throws SQLException {
      String sql = "DELETE FROM %s WHERE player_id = ?".formatted(this.table("members"));
      Connection conn = this.getConnection();

      try {
         PreparedStatement stmt = conn.prepareStatement(sql);

         try {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
         } catch (Throwable var9) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var10) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var7) {
               var10.addSuppressed(var7);
            }
         }

         throw var10;
      }

      if (conn != null) {
         conn.close();
      }

   }

   public void archiveClan(Clan clan, String reason) throws SQLException {
      String sql;
      if (this.isSqliteOrH2()) {
         sql = "   MERGE INTO %s (clan_id, tag, name, description, leader_id, privacy, created_at, max_members, chat_enabled, banner_color, tag_enabled, profile_public, friendly_fire, flag_data, balance, last_rent_at, treasury_data, archived_at, reason)\n   KEY(clan_id)\n   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n".formatted(this.table("archived_clans"));
      } else {
         sql = "   REPLACE INTO %s\n   (clan_id, tag, name, description, leader_id, privacy, created_at, max_members, chat_enabled, banner_color, tag_enabled, profile_public, friendly_fire, flag_data, balance, last_rent_at, treasury_data, archived_at, reason)\n   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n".formatted(this.table("archived_clans"));
      }

      Connection conn = this.getConnection();

      try {
         PreparedStatement stmt = conn.prepareStatement(sql);

         try {
            stmt.setString(1, clan.getClanId());
            stmt.setString(2, clan.getTag());
            stmt.setString(3, clan.getName());
            stmt.setString(4, clan.getDescription());
            stmt.setString(5, clan.getLeaderId().toString());
            stmt.setString(6, clan.getPrivacy().name());
            stmt.setLong(7, clan.getCreatedAt());
            stmt.setInt(8, clan.getMaxMembers());
            stmt.setBoolean(9, clan.isChatEnabled());
            stmt.setString(10, clan.getBannerColor());
            stmt.setBoolean(11, clan.isTagEnabled());
            stmt.setBoolean(12, clan.isProfilePublic());
            stmt.setBoolean(13, clan.isFriendlyFire());
            stmt.setString(14, clan.getFlagData());
            stmt.setLong(15, clan.getBalance());
            stmt.setLong(16, clan.getLastRentAt());
            stmt.setString(17, clan.getTreasuryData());
            stmt.setLong(18, System.currentTimeMillis());
            stmt.setString(19, reason != null ? reason : "");
            stmt.executeUpdate();
         } catch (Throwable var13) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var12) {
                  var13.addSuppressed(var12);
               }
            }

            throw var13;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var14) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var11) {
               var14.addSuppressed(var11);
            }
         }

         throw var14;
      }

      if (conn != null) {
         conn.close();
      }

      String memberSql;
      if (this.isSqliteOrH2()) {
         memberSql = "   MERGE INTO %s (player_id, clan_id, role_id, joined_at, last_seen)\n   KEY(player_id, clan_id)\n   VALUES (?, ?, ?, ?, ?)\n".formatted(this.table("archived_members"));
      } else {
         memberSql = "   REPLACE INTO %s\n   (player_id, clan_id, role_id, joined_at, last_seen)\n   VALUES (?, ?, ?, ?, ?)\n".formatted(this.table("archived_members"));
      }

      Connection memberConn = this.getConnection();

      try {
         PreparedStatement stmt = memberConn.prepareStatement(memberSql);

         try {
            for(ClanMember member : clan.getMembers().values()) {
               stmt.setString(1, member.getPlayerId().toString());
               stmt.setString(2, member.getClanId());
               stmt.setString(3, member.getRoleId());
               stmt.setLong(4, member.getJoinedAt());
               stmt.setLong(5, member.getLastSeen());
               stmt.addBatch();
            }

            stmt.executeBatch();
         } catch (Throwable var15) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var10) {
                  var15.addSuppressed(var10);
               }
            }

            throw var15;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var16) {
         if (memberConn != null) {
            try {
               memberConn.close();
            } catch (Throwable var9) {
               var16.addSuppressed(var9);
            }
         }

         throw var16;
      }

      if (memberConn != null) {
         memberConn.close();
      }

   }

   public Clan loadArchivedClanByToken(String token) throws SQLException {
      if (token != null && !token.isEmpty()) {
         List<Clan> all = this.loadAllArchivedClans();

         for(Clan clan : all) {
            if (clan.getClanId().equalsIgnoreCase(token)) {
               return clan;
            }
         }

         if (token.length() >= 4) {
            String lower = token.toLowerCase();
            Clan prefixMatch = null;
            boolean ambiguous = false;

            for(Clan clan : all) {
               if (clan.getClanId().toLowerCase().startsWith(lower)) {
                  if (prefixMatch != null) {
                     ambiguous = true;
                     break;
                  }

                  prefixMatch = clan;
               }
            }

            if (prefixMatch != null && !ambiguous) {
               return prefixMatch;
            }
         }

         String wantedTag = stripColorCodes(token);

         for(Clan clan : all) {
            if (stripColorCodes(clan.getTag()).equalsIgnoreCase(wantedTag)) {
               return clan;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static String stripColorCodes(String text) {
      if (text == null) {
         return "";
      } else {
         String noLegacy = text.replaceAll("(?i)[&§][0-9a-fk-orx]", "");
         return noLegacy.replaceAll("(?i)(?:&#|#)[0-9a-f]{6}", "");
      }
   }

   public List<Clan> loadAllArchivedClans() throws SQLException {
      List<Clan> clans = new ArrayList();
      String sql = "SELECT * FROM %s ORDER BY archived_at DESC".formatted(this.table("archived_clans"));
      Connection conn = this.getConnection();

      try {
         Statement stmt = conn.createStatement();

         try {
            ResultSet rs = stmt.executeQuery(sql);

            try {
               while(rs.next()) {
                  clans.add(this.readArchivedClan(rs));
               }
            } catch (Throwable var11) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (rs != null) {
               rs.close();
            }
         } catch (Throwable var12) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var9) {
                  var12.addSuppressed(var9);
               }
            }

            throw var12;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var13) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var8) {
               var13.addSuppressed(var8);
            }
         }

         throw var13;
      }

      if (conn != null) {
         conn.close();
      }

      return clans;
   }

   private Clan readArchivedClan(ResultSet rs) throws SQLException {
      Clan clan = new Clan(rs.getString("clan_id"), rs.getString("tag"), rs.getString("name"), UUID.fromString(rs.getString("leader_id")));
      clan.setCreatedAt(rs.getLong("created_at"));
      clan.setPrivacy(ClanPrivacy.valueOf(rs.getString("privacy")));
      clan.setMaxMembers(rs.getInt("max_members"));
      clan.setDescription(rs.getString("description"));
      clan.setChatEnabled(rs.getBoolean("chat_enabled"));
      clan.setBannerColor(rs.getString("banner_color"));
      clan.setTagEnabled(rs.getBoolean("tag_enabled"));
      clan.setProfilePublic(rs.getBoolean("profile_public"));
      clan.setFriendlyFire(rs.getBoolean("friendly_fire"));
      clan.setFlagData(rs.getString("flag_data"));
      clan.setBalance(rs.getLong("balance"));
      clan.setLastRentAt(rs.getLong("last_rent_at"));

      try {
         clan.setTreasuryData(rs.getString("treasury_data"));
      } catch (SQLException var12) {
      }

      String memberSql = "SELECT * FROM %s WHERE clan_id = ?".formatted(this.table("archived_members"));
      Connection conn = this.getConnection();

      try {
         PreparedStatement stmt = conn.prepareStatement(memberSql);

         try {
            stmt.setString(1, clan.getClanId());
            ResultSet rs2 = stmt.executeQuery();

            try {
               while(rs2.next()) {
                  ClanMember member = new ClanMember(UUID.fromString(rs2.getString("player_id")), rs2.getString("clan_id"), rs2.getString("role_id"));
                  member.setJoinedAt(rs2.getLong("joined_at"));
                  member.setLastSeen(rs2.getLong("last_seen"));
                  clan.addMember(member);
               }
            } catch (Throwable var13) {
               if (rs2 != null) {
                  try {
                     rs2.close();
                  } catch (Throwable var11) {
                     var13.addSuppressed(var11);
                  }
               }

               throw var13;
            }

            if (rs2 != null) {
               rs2.close();
            }
         } catch (Throwable var14) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var10) {
                  var14.addSuppressed(var10);
               }
            }

            throw var14;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var15) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var9) {
               var15.addSuppressed(var9);
            }
         }

         throw var15;
      }

      if (conn != null) {
         conn.close();
      }

      return clan;
   }

   public void deleteArchivedClan(String clanId) throws SQLException {
      Connection conn = this.getConnection();

      try {
         PreparedStatement stmt = conn.prepareStatement("DELETE FROM %s WHERE clan_id = ?".formatted(this.table("archived_clans")));

         try {
            stmt.setString(1, clanId);
            stmt.executeUpdate();
         } catch (Throwable var10) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var8) {
                  var10.addSuppressed(var8);
               }
            }

            throw var10;
         }

         if (stmt != null) {
            stmt.close();
         }

         stmt = conn.prepareStatement("DELETE FROM %s WHERE clan_id = ?".formatted(this.table("archived_members")));

         try {
            stmt.setString(1, clanId);
            stmt.executeUpdate();
         } catch (Throwable var9) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var7) {
                  var9.addSuppressed(var7);
               }
            }

            throw var9;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var11) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var6) {
               var11.addSuppressed(var6);
            }
         }

         throw var11;
      }

      if (conn != null) {
         conn.close();
      }

   }

   public long[] loadTaxState() throws SQLException {
      String select = "SELECT balance, total_collected FROM %s WHERE id = 1".formatted(this.table("taxes"));
      Connection conn = this.getConnection();

      long[] var5;
      label140: {
         try {
            Statement stmt;
            label141: {
               stmt = conn.createStatement();

               try {
                  ResultSet rs = stmt.executeQuery(select);

                  label143: {
                     try {
                        if (rs.next()) {
                           var5 = new long[]{rs.getLong("balance"), rs.getLong("total_collected")};
                           break label143;
                        }
                     } catch (Throwable var15) {
                        if (rs != null) {
                           try {
                              rs.close();
                           } catch (Throwable var9) {
                              var15.addSuppressed(var9);
                           }
                        }

                        throw var15;
                     }

                     if (rs != null) {
                        rs.close();
                     }
                     break label141;
                  }

                  if (rs != null) {
                     rs.close();
                  }
               } catch (Throwable var16) {
                  if (stmt != null) {
                     try {
                        stmt.close();
                     } catch (Throwable var8) {
                        var16.addSuppressed(var8);
                     }
                  }

                  throw var16;
               }

               if (stmt != null) {
                  stmt.close();
               }
               break label140;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var17) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var7) {
                  var17.addSuppressed(var7);
               }
            }

            throw var17;
         }

         if (conn != null) {
            conn.close();
         }

         try {
            conn = this.getConnection();

            try {
               PreparedStatement stmt = conn.prepareStatement("INSERT INTO %s (id, balance, total_collected) VALUES (1, 0, 0)".formatted(this.table("taxes")));

               try {
                  stmt.executeUpdate();
               } catch (Throwable var12) {
                  if (stmt != null) {
                     try {
                        stmt.close();
                     } catch (Throwable var11) {
                        var12.addSuppressed(var11);
                     }
                  }

                  throw var12;
               }

               if (stmt != null) {
                  stmt.close();
               }
            } catch (Throwable var13) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var10) {
                     var13.addSuppressed(var10);
                  }
               }

               throw var13;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException var14) {
         }

         return new long[]{0L, 0L};
      }

      if (conn != null) {
         conn.close();
      }

      return var5;
   }

   public void saveTaxState(long balance, long totalCollected) throws SQLException {
      String sql = this.isSqliteOrH2() ? "MERGE INTO %s (id, balance, total_collected) KEY(id) VALUES (1, ?, ?)".formatted(this.table("taxes")) : "REPLACE INTO %s (id, balance, total_collected) VALUES (1, ?, ?)".formatted(this.table("taxes"));
      Connection conn = this.getConnection();

      try {
         PreparedStatement stmt = conn.prepareStatement(sql);

         try {
            stmt.setLong(1, balance);
            stmt.setLong(2, totalCollected);
            stmt.executeUpdate();
         } catch (Throwable var12) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var11) {
                  var12.addSuppressed(var11);
               }
            }

            throw var12;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var13) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var10) {
               var13.addSuppressed(var10);
            }
         }

         throw var13;
      }

      if (conn != null) {
         conn.close();
      }

   }

   public String getClanIdByTag(String tag) throws SQLException {
      String sql = "SELECT clan_id FROM %s WHERE tag = ?".formatted(this.table("clans"));
      Connection conn = this.getConnection();

      String var6;
      label95: {
         try {
            PreparedStatement stmt;
            label97: {
               stmt = conn.prepareStatement(sql);

               try {
                  stmt.setString(1, tag);
                  ResultSet rs = stmt.executeQuery();

                  label80: {
                     try {
                        if (rs.next()) {
                           var6 = rs.getString("clan_id");
                           break label80;
                        }
                     } catch (Throwable var11) {
                        if (rs != null) {
                           try {
                              rs.close();
                           } catch (Throwable var10) {
                              var11.addSuppressed(var10);
                           }
                        }

                        throw var11;
                     }

                     if (rs != null) {
                        rs.close();
                     }
                     break label97;
                  }

                  if (rs != null) {
                     rs.close();
                  }
               } catch (Throwable var12) {
                  if (stmt != null) {
                     try {
                        stmt.close();
                     } catch (Throwable var9) {
                        var12.addSuppressed(var9);
                     }
                  }

                  throw var12;
               }

               if (stmt != null) {
                  stmt.close();
               }
               break label95;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var13) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var8) {
                  var13.addSuppressed(var8);
               }
            }

            throw var13;
         }

         if (conn != null) {
            conn.close();
         }

         return null;
      }

      if (conn != null) {
         conn.close();
      }

      return var6;
   }
}
