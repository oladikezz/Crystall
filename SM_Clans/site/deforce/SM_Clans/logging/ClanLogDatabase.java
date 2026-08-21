package site.deforce.SM_Clans.logging;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import site.deforce.SM_Clans.SM_Clans;
import site.deforce.SM_Clans.models.ClanLogEntry;

public class ClanLogDatabase {
   private final SM_Clans module;
   private final Object lock = new Object();
   private Connection connection;
   private volatile boolean ready = false;

   public ClanLogDatabase(SM_Clans module) {
      super();
      this.module = module;
   }

   public void initialize() {
      try {
         Class.forName("org.h2.Driver");
         File folder = new File(this.module.getPlugin().getDataFolder(), "modules/SM_Clans");
         if (!folder.exists() && !folder.mkdirs()) {
            this.module.getPlugin().getDebugSystem().log("ClanLogDB", "Could not create log folder: " + folder.getPath());
         }

         String path = (new File(folder, "logs")).getAbsolutePath();
         String url = "jdbc:h2:file:" + path + ";DB_CLOSE_ON_EXIT=FALSE";
         synchronized(this.lock) {
            this.connection = DriverManager.getConnection(url);
            this.createTable();
         }

         this.ready = true;
         this.module.getPlugin().getDebugSystem().log("ClanLogDB", "Log database ready at " + path);
      } catch (Exception exception) {
         this.module.getPlugin().getDebugSystem().logError("SM_Clans", "Failed to open clan log database", exception);
      }

   }

   private void createTable() throws SQLException {
      Statement stmt = this.connection.createStatement();

      try {
         stmt.execute("CREATE TABLE IF NOT EXISTS clan_logs (\n    id BIGINT AUTO_INCREMENT PRIMARY KEY,\n    ts BIGINT NOT NULL,\n    action VARCHAR(32) NOT NULL,\n    actor_name VARCHAR(64),\n    actor_uuid VARCHAR(36),\n    clan_tag VARCHAR(64),\n    clan_id VARCHAR(64),\n    target VARCHAR(256),\n    amount BIGINT,\n    balance BIGINT\n)\n");
         stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_ts ON clan_logs(ts)");
         stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_actor ON clan_logs(actor_name)");
         stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_clan ON clan_logs(clan_tag)");
         stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_action ON clan_logs(action)");
      } catch (Throwable var5) {
         if (stmt != null) {
            try {
               stmt.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }
         }

         throw var5;
      }

      if (stmt != null) {
         stmt.close();
      }

   }

   public void shutdown() {
      this.ready = false;
      synchronized(this.lock) {
         if (this.connection != null) {
            try {
               this.connection.close();
            } catch (SQLException var4) {
            }

            this.connection = null;
         }

      }
   }

   public boolean isReady() {
      return this.ready;
   }

   public void record(String action, String actorName, String actorUuid, String clanTag, String clanId, String target, Long amount, Long balance) {
      if (this.ready) {
         long ts = System.currentTimeMillis();
         this.module.getPlugin().getSchedulerManager().runAsync("clan-log-write", () -> {
            synchronized(this.lock) {
               if (this.connection != null) {
                  try {
                     PreparedStatement ps = this.connection.prepareStatement("INSERT INTO clan_logs (ts, action, actor_name, actor_uuid, clan_tag, clan_id, target, amount, balance) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

                     try {
                        ps.setLong(1, ts);
                        ps.setString(2, action);
                        ps.setString(3, actorName);
                        ps.setString(4, actorUuid);
                        ps.setString(5, clanTag);
                        ps.setString(6, clanId);
                        ps.setString(7, target);
                        if (amount != null) {
                           ps.setLong(8, amount);
                        } else {
                           ps.setNull(8, -5);
                        }

                        if (balance != null) {
                           ps.setLong(9, balance);
                        } else {
                           ps.setNull(9, -5);
                        }

                        ps.executeUpdate();
                     } catch (Throwable var17) {
                        if (ps != null) {
                           try {
                              ps.close();
                           } catch (Throwable var16) {
                              var17.addSuppressed(var16);
                           }
                        }

                        throw var17;
                     }

                     if (ps != null) {
                        ps.close();
                     }
                  } catch (SQLException exception) {
                     this.module.getPlugin().getDebugSystem().log("ClanLogDB", "Failed to write log: " + exception.getMessage());
                  }

               }
            }
         });
      }
   }

   public List<ClanLogEntry> lookup(String actorName, String action, String clanTag, long sinceMillis, int limit, int offset) {
      List<ClanLogEntry> results = new ArrayList();
      if (!this.ready) {
         return results;
      } else {
         StringBuilder sql = new StringBuilder("SELECT id, ts, action, actor_name, clan_tag, target, amount, balance FROM clan_logs WHERE 1=1");
         List<Object> params = new ArrayList();
         if (actorName != null && !actorName.isBlank()) {
            sql.append(" AND LOWER(actor_name) = LOWER(?)");
            params.add(actorName);
         }

         if (action != null && !action.isBlank()) {
            sql.append(" AND UPPER(action) = UPPER(?)");
            params.add(action);
         }

         if (clanTag != null && !clanTag.isBlank()) {
            sql.append(" AND LOWER(clan_tag) = LOWER(?)");
            params.add(clanTag);
         }

         if (sinceMillis > 0L) {
            sql.append(" AND ts >= ?");
            params.add(sinceMillis);
         }

         sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
         params.add(Math.max(1, limit));
         params.add(Math.max(0, offset));
         synchronized(this.lock) {
            if (this.connection == null) {
               return results;
            } else {
               try {
                  PreparedStatement ps = this.connection.prepareStatement(sql.toString());

                  try {
                     for(int i = 0; i < params.size(); ++i) {
                        ps.setObject(i + 1, params.get(i));
                     }

                     ResultSet rs = ps.executeQuery();

                     try {
                        while(rs.next()) {
                           long amount = rs.getLong("amount");
                           Long amountVal = rs.wasNull() ? null : amount;
                           long balance = rs.getLong("balance");
                           Long balanceVal = rs.wasNull() ? null : balance;
                           results.add(new ClanLogEntry(rs.getLong("id"), rs.getLong("ts"), rs.getString("action"), rs.getString("actor_name"), rs.getString("clan_tag"), rs.getString("target"), amountVal, balanceVal));
                        }
                     } catch (Throwable var23) {
                        if (rs != null) {
                           try {
                              rs.close();
                           } catch (Throwable var22) {
                              var23.addSuppressed(var22);
                           }
                        }

                        throw var23;
                     }

                     if (rs != null) {
                        rs.close();
                     }
                  } catch (Throwable var24) {
                     if (ps != null) {
                        try {
                           ps.close();
                        } catch (Throwable var21) {
                           var24.addSuppressed(var21);
                        }
                     }

                     throw var24;
                  }

                  if (ps != null) {
                     ps.close();
                  }
               } catch (SQLException exception) {
                  this.module.getPlugin().getDebugSystem().log("ClanLogDB", "Lookup failed: " + exception.getMessage());
               }

               return results;
            }
         }
      }
   }
}
