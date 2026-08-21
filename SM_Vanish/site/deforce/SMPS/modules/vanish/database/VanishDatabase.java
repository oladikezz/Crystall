package site.deforce.SMPS.modules.vanish.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.schalker.DoAPI.core.database.DatabaseManager;
import site.deforce.SMPS.modules.vanish.SM_Vanish;

public class VanishDatabase {
   private final SM_Vanish module;
   private final String tablePrefix;

   public VanishDatabase(SM_Vanish module) {
      super();
      this.module = module;
      String prefix = "sm_";

      try {
         prefix = module.getSMPS().getConfigManager().getConfig().getString("database.table-prefix", "sm_");
      } catch (Exception e) {
         module.log("Failed to load table prefix from config, using default 'sm_': " + e.getMessage());
      }

      this.tablePrefix = prefix;
   }

   private DatabaseManager getDb() {
      return this.module.getSMPS().getDatabaseManager();
   }

   public boolean isConnected() {
      DatabaseManager db = this.getDb();
      return db != null && db.isConnected();
   }

   private Connection getConnection() throws SQLException {
      return this.getDb().getConnection();
   }

   private String table(String name) {
      return this.tablePrefix + name;
   }

   public void createTables() {
      String sql = "CREATE TABLE IF NOT EXISTS %s (\n    uuid VARCHAR(36) PRIMARY KEY,\n    vanished TINYINT(1) NOT NULL DEFAULT 0,\n    vanished_at BIGINT(20) DEFAULT 0,\n    vanished_by VARCHAR(36) DEFAULT NULL,\n    tab_visibility_override TINYINT(1) DEFAULT NULL,\n    incognito TINYINT(1) NOT NULL DEFAULT 0,\n    nametag_visibility TINYINT(1) DEFAULT NULL\n)\n".formatted(this.table("vanish_states"));
      if (!this.isConnected()) {
         this.module.log("Database not connected, cannot create tables");
      } else {
         try {
            Connection conn = this.getConnection();

            try {
               PreparedStatement stmt = conn.prepareStatement(sql);

               try {
                  stmt.executeUpdate();
                  this.ensureTabVisibilityColumn(conn);
                  this.ensureIncognitoColumn(conn);
                  this.ensureNameTagVisibilityColumn(conn);
                  this.module.log("Table " + this.table("vanish_states") + " created/verified");
               } catch (Throwable var8) {
                  if (stmt != null) {
                     try {
                        stmt.close();
                     } catch (Throwable var7) {
                        var8.addSuppressed(var7);
                     }
                  }

                  throw var8;
               }

               if (stmt != null) {
                  stmt.close();
               }
            } catch (Throwable var9) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var6) {
                     var9.addSuppressed(var6);
                  }
               }

               throw var9;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException e) {
            this.module.log("Failed to create tables: " + e.getMessage());
         }

      }
   }

   private void ensureNameTagVisibilityColumn(Connection conn) {
      try {
         DatabaseMetaData meta = conn.getMetaData();
         String tableName = this.table("vanish_states");
         boolean hasColumn = false;
         ResultSet rs = meta.getColumns((String)null, (String)null, tableName, "nametag_visibility");

         try {
            hasColumn = rs.next();
         } catch (Throwable var20) {
            if (rs != null) {
               try {
                  rs.close();
               } catch (Throwable var14) {
                  var20.addSuppressed(var14);
               }
            }

            throw var20;
         }

         if (rs != null) {
            rs.close();
         }

         if (!hasColumn) {
            rs = meta.getColumns((String)null, (String)null, tableName.toUpperCase(), "NAMETAG_VISIBILITY");

            try {
               hasColumn = rs.next();
            } catch (Throwable var19) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var13) {
                     var19.addSuppressed(var13);
                  }
               }

               throw var19;
            }

            if (rs != null) {
               rs.close();
            }
         }

         if (!hasColumn) {
            PreparedStatement alter = conn.prepareStatement("ALTER TABLE " + tableName + " ADD COLUMN nametag_visibility TINYINT(1) DEFAULT NULL");

            try {
               alter.executeUpdate();
            } catch (Throwable var18) {
               if (alter != null) {
                  try {
                     alter.close();
                  } catch (Throwable var12) {
                     var18.addSuppressed(var12);
                  }
               }

               throw var18;
            }

            if (alter != null) {
               alter.close();
            }

            this.module.log("Added missing column nametag_visibility to " + tableName);
         }

         boolean hasLegacy = false;
         ResultSet legacyRs = meta.getColumns((String)null, (String)null, tableName, "debug_nametag_hidden");

         try {
            hasLegacy = legacyRs.next();
         } catch (Throwable var17) {
            if (legacyRs != null) {
               try {
                  legacyRs.close();
               } catch (Throwable var11) {
                  var17.addSuppressed(var11);
               }
            }

            throw var17;
         }

         if (legacyRs != null) {
            legacyRs.close();
         }

         if (!hasLegacy) {
            legacyRs = meta.getColumns((String)null, (String)null, tableName.toUpperCase(), "DEBUG_NAMETAG_HIDDEN");

            try {
               hasLegacy = legacyRs.next();
            } catch (Throwable var16) {
               if (legacyRs != null) {
                  try {
                     legacyRs.close();
                  } catch (Throwable var10) {
                     var16.addSuppressed(var10);
                  }
               }

               throw var16;
            }

            if (legacyRs != null) {
               legacyRs.close();
            }
         }

         if (hasLegacy) {
            PreparedStatement migrate = conn.prepareStatement("UPDATE " + tableName + " SET nametag_visibility = CASE WHEN debug_nametag_hidden = 1 THEN 0 ELSE 1 END WHERE nametag_visibility IS NULL");

            try {
               int migrated = migrate.executeUpdate();
               if (migrated > 0) {
                  this.module.log("Migrated legacy debug_nametag_hidden values into nametag_visibility: " + migrated);
               }
            } catch (Throwable var15) {
               if (migrate != null) {
                  try {
                     migrate.close();
                  } catch (Throwable var9) {
                     var15.addSuppressed(var9);
                  }
               }

               throw var15;
            }

            if (migrate != null) {
               migrate.close();
            }
         }
      } catch (SQLException e) {
         this.module.log("Failed to verify nametag_visibility column: " + e.getMessage());
      }

   }

   private void ensureTabVisibilityColumn(Connection conn) {
      try {
         DatabaseMetaData meta = conn.getMetaData();
         String tableName = this.table("vanish_states");
         boolean hasColumn = false;
         ResultSet rs = meta.getColumns((String)null, (String)null, tableName, "tab_visibility_override");

         try {
            hasColumn = rs.next();
         } catch (Throwable var13) {
            if (rs != null) {
               try {
                  rs.close();
               } catch (Throwable var10) {
                  var13.addSuppressed(var10);
               }
            }

            throw var13;
         }

         if (rs != null) {
            rs.close();
         }

         if (!hasColumn) {
            rs = meta.getColumns((String)null, (String)null, tableName.toUpperCase(), "TAB_VISIBILITY_OVERRIDE");

            try {
               hasColumn = rs.next();
            } catch (Throwable var12) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var9) {
                     var12.addSuppressed(var9);
                  }
               }

               throw var12;
            }

            if (rs != null) {
               rs.close();
            }
         }

         if (!hasColumn) {
            PreparedStatement alter = conn.prepareStatement("ALTER TABLE " + tableName + " ADD COLUMN tab_visibility_override TINYINT(1) DEFAULT NULL");

            try {
               alter.executeUpdate();
            } catch (Throwable var11) {
               if (alter != null) {
                  try {
                     alter.close();
                  } catch (Throwable var8) {
                     var11.addSuppressed(var8);
                  }
               }

               throw var11;
            }

            if (alter != null) {
               alter.close();
            }

            this.module.log("Added missing column tab_visibility_override to " + tableName);
         }
      } catch (SQLException e) {
         this.module.log("Failed to verify tab_visibility_override column: " + e.getMessage());
      }

   }

   private void ensureIncognitoColumn(Connection conn) {
      try {
         DatabaseMetaData meta = conn.getMetaData();
         String tableName = this.table("vanish_states");
         boolean hasColumn = false;
         ResultSet rs = meta.getColumns((String)null, (String)null, tableName, "incognito");

         try {
            hasColumn = rs.next();
         } catch (Throwable var13) {
            if (rs != null) {
               try {
                  rs.close();
               } catch (Throwable var10) {
                  var13.addSuppressed(var10);
               }
            }

            throw var13;
         }

         if (rs != null) {
            rs.close();
         }

         if (!hasColumn) {
            rs = meta.getColumns((String)null, (String)null, tableName.toUpperCase(), "INCOGNITO");

            try {
               hasColumn = rs.next();
            } catch (Throwable var12) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var9) {
                     var12.addSuppressed(var9);
                  }
               }

               throw var12;
            }

            if (rs != null) {
               rs.close();
            }
         }

         if (!hasColumn) {
            PreparedStatement alter = conn.prepareStatement("ALTER TABLE " + tableName + " ADD COLUMN incognito TINYINT(1) NOT NULL DEFAULT 0");

            try {
               alter.executeUpdate();
            } catch (Throwable var11) {
               if (alter != null) {
                  try {
                     alter.close();
                  } catch (Throwable var8) {
                     var11.addSuppressed(var8);
                  }
               }

               throw var11;
            }

            if (alter != null) {
               alter.close();
            }

            this.module.log("Added missing column incognito to " + tableName);
         }
      } catch (SQLException e) {
         this.module.log("Failed to verify incognito column: " + e.getMessage());
      }

   }

   public CompletableFuture<Boolean> isVanished(UUID uuid) {
      return CompletableFuture.supplyAsync(() -> this.isVanishedSync(uuid));
   }

   public boolean isVanishedSync(UUID uuid) {
      if (!this.isConnected()) {
         return false;
      } else {
         String sql = "SELECT vanished FROM " + this.table("vanish_states") + " WHERE uuid = ?";

         try {
            Connection conn = this.getConnection();

            boolean var6;
            label119: {
               try {
                  PreparedStatement stmt;
                  label110: {
                     stmt = conn.prepareStatement(sql);

                     try {
                        stmt.setString(1, uuid.toString());
                        ResultSet rs = stmt.executeQuery();

                        label88: {
                           try {
                              if (rs.next()) {
                                 var6 = rs.getBoolean("vanished");
                                 break label88;
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
                           break label110;
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
                     break label119;
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

               return false;
            }

            if (conn != null) {
               conn.close();
            }

            return var6;
         } catch (SQLException e) {
            this.module.log("Failed to read vanish state: " + e.getMessage());
            return false;
         }
      }
   }

   public CompletableFuture<Void> setVanished(UUID uuid, boolean vanished, UUID vanishedBy) {
      return CompletableFuture.runAsync(() -> this.setVanishedSync(uuid, vanished, vanishedBy));
   }

   public void setVanishedSync(UUID uuid, boolean vanished, UUID vanishedBy) {
      if (this.isConnected()) {
         long timestamp = vanished ? System.currentTimeMillis() : 0L;
         String vanishedByStr = vanishedBy != null ? vanishedBy.toString() : null;
         String updateSql = "UPDATE %s\nSET vanished = ?, vanished_at = ?, vanished_by = ?\nWHERE uuid = ?\n".formatted(this.table("vanish_states"));
         String insertSql = "INSERT INTO %s (uuid, vanished, vanished_at, vanished_by, tab_visibility_override)\nVALUES (?, ?, ?, ?, ?)\n".formatted(this.table("vanish_states"));

         try {
            Connection conn = this.getConnection();

            try {
               PreparedStatement updateStmt = conn.prepareStatement(updateSql);

               int updated;
               try {
                  updateStmt.setBoolean(1, vanished);
                  updateStmt.setLong(2, timestamp);
                  updateStmt.setString(3, vanishedByStr);
                  updateStmt.setString(4, uuid.toString());
                  updated = updateStmt.executeUpdate();
               } catch (Throwable var18) {
                  if (updateStmt != null) {
                     try {
                        updateStmt.close();
                     } catch (Throwable var16) {
                        var18.addSuppressed(var16);
                     }
                  }

                  throw var18;
               }

               if (updateStmt != null) {
                  updateStmt.close();
               }

               if (updated == 0) {
                  updateStmt = conn.prepareStatement(insertSql);

                  try {
                     updateStmt.setString(1, uuid.toString());
                     updateStmt.setBoolean(2, vanished);
                     updateStmt.setLong(3, timestamp);
                     updateStmt.setString(4, vanishedByStr);
                     updateStmt.setNull(5, -6);
                     updateStmt.executeUpdate();
                  } catch (Throwable var17) {
                     if (updateStmt != null) {
                        try {
                           updateStmt.close();
                        } catch (Throwable var15) {
                           var17.addSuppressed(var15);
                        }
                     }

                     throw var17;
                  }

                  if (updateStmt != null) {
                     updateStmt.close();
                  }
               }

               SM_Vanish var10000 = this.module;
               String var10001 = String.valueOf(uuid);
               var10000.log("Saved vanish state for " + var10001 + ": vanished=" + vanished);
            } catch (Throwable var19) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var14) {
                     var19.addSuppressed(var14);
                  }
               }

               throw var19;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException e) {
            this.module.log("Failed to save vanish state: " + e.getMessage());
         }

      }
   }

   public CompletableFuture<Void> clearVanishState(UUID uuid) {
      return CompletableFuture.runAsync(() -> {
         if (this.isConnected()) {
            String sql = "DELETE FROM " + this.table("vanish_states") + " WHERE uuid = ?";

            try {
               Connection conn = this.getConnection();

               try {
                  PreparedStatement stmt = conn.prepareStatement(sql);

                  try {
                     stmt.setString(1, uuid.toString());
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
            } catch (SQLException e) {
               this.module.log("Failed to clear vanish state: " + e.getMessage());
            }

         }
      });
   }

   public CompletableFuture<Set<UUID>> getAllVanishedPlayers() {
      return CompletableFuture.supplyAsync(() -> {
         Set<UUID> vanished = new HashSet();
         if (!this.isConnected()) {
            return vanished;
         } else {
            String sql = "SELECT uuid FROM " + this.table("vanish_states") + " WHERE vanished = 1";

            try {
               Connection conn = this.getConnection();

               try {
                  PreparedStatement stmt = conn.prepareStatement(sql);

                  try {
                     ResultSet rs = stmt.executeQuery();

                     try {
                        while(rs.next()) {
                           try {
                              vanished.add(UUID.fromString(rs.getString("uuid")));
                           } catch (IllegalArgumentException var11) {
                           }
                        }
                     } catch (Throwable var12) {
                        if (rs != null) {
                           try {
                              rs.close();
                           } catch (Throwable var10) {
                              var12.addSuppressed(var10);
                           }
                        }

                        throw var12;
                     }

                     if (rs != null) {
                        rs.close();
                     }
                  } catch (Throwable var13) {
                     if (stmt != null) {
                        try {
                           stmt.close();
                        } catch (Throwable var9) {
                           var13.addSuppressed(var9);
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
                     } catch (Throwable var8) {
                        var14.addSuppressed(var8);
                     }
                  }

                  throw var14;
               }

               if (conn != null) {
                  conn.close();
               }
            } catch (SQLException e) {
               this.module.log("Failed to get vanished players: " + e.getMessage());
            }

            return vanished;
         }
      });
   }

   public CompletableFuture<Void> setTabVisibilityOverride(UUID uuid, Boolean visible) {
      return CompletableFuture.runAsync(() -> {
         if (this.isConnected()) {
            String updateSql = "UPDATE %s\nSET tab_visibility_override = ?\nWHERE uuid = ?\n".formatted(this.table("vanish_states"));
            String insertSql = "INSERT INTO %s (uuid, vanished, vanished_at, vanished_by, tab_visibility_override)\nVALUES (?, 0, 0, NULL, ?)\n".formatted(this.table("vanish_states"));

            try {
               Connection conn = this.getConnection();

               try {
                  PreparedStatement updateStmt = conn.prepareStatement(updateSql);

                  int updated;
                  try {
                     if (visible == null) {
                        updateStmt.setNull(1, -6);
                     } else {
                        updateStmt.setBoolean(1, visible);
                     }

                     updateStmt.setString(2, uuid.toString());
                     updated = updateStmt.executeUpdate();
                  } catch (Throwable var14) {
                     if (updateStmt != null) {
                        try {
                           updateStmt.close();
                        } catch (Throwable var12) {
                           var14.addSuppressed(var12);
                        }
                     }

                     throw var14;
                  }

                  if (updateStmt != null) {
                     updateStmt.close();
                  }

                  if (updated == 0) {
                     updateStmt = conn.prepareStatement(insertSql);

                     try {
                        updateStmt.setString(1, uuid.toString());
                        if (visible == null) {
                           updateStmt.setNull(2, -6);
                        } else {
                           updateStmt.setBoolean(2, visible);
                        }

                        updateStmt.executeUpdate();
                     } catch (Throwable var13) {
                        if (updateStmt != null) {
                           try {
                              updateStmt.close();
                           } catch (Throwable var11) {
                              var13.addSuppressed(var11);
                           }
                        }

                        throw var13;
                     }

                     if (updateStmt != null) {
                        updateStmt.close();
                     }
                  }
               } catch (Throwable var15) {
                  if (conn != null) {
                     try {
                        conn.close();
                     } catch (Throwable var10) {
                        var15.addSuppressed(var10);
                     }
                  }

                  throw var15;
               }

               if (conn != null) {
                  conn.close();
               }
            } catch (SQLException e) {
               this.module.log("Failed to save tab visibility override: " + e.getMessage());
            }

         }
      });
   }

   public Boolean getTabVisibilityOverrideSync(UUID uuid) {
      if (!this.isConnected()) {
         return null;
      } else {
         String sql = "SELECT tab_visibility_override FROM " + this.table("vanish_states") + " WHERE uuid = ?";

         try {
            Connection conn = this.getConnection();

            Boolean var7;
            label135: {
               label149: {
                  try {
                     PreparedStatement stmt;
                     label137: {
                        label138: {
                           stmt = conn.prepareStatement(sql);

                           try {
                              label139: {
                                 stmt.setString(1, uuid.toString());
                                 ResultSet rs = stmt.executeQuery();

                                 label112: {
                                    label111: {
                                       try {
                                          if (rs.next()) {
                                             Object raw = rs.getObject("tab_visibility_override");
                                             if (raw == null) {
                                                var7 = null;
                                                break label111;
                                             }

                                             var7 = rs.getBoolean("tab_visibility_override");
                                             break label112;
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
                                       break label137;
                                    }

                                    if (rs != null) {
                                       rs.close();
                                    }
                                    break label139;
                                 }

                                 if (rs != null) {
                                    rs.close();
                                 }
                                 break label138;
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
                           break label149;
                        }

                        if (stmt != null) {
                           stmt.close();
                        }
                        break label135;
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

               return var7;
            }

            if (conn != null) {
               conn.close();
            }

            return var7;
         } catch (SQLException e) {
            this.module.log("Failed to load tab visibility override: " + e.getMessage());
            return null;
         }
      }
   }

   public boolean isIncognitoSync(UUID uuid) {
      if (!this.isConnected()) {
         return false;
      } else {
         String sql = "SELECT incognito FROM " + this.table("vanish_states") + " WHERE uuid = ?";

         try {
            Connection conn = this.getConnection();

            boolean var6;
            label119: {
               try {
                  PreparedStatement stmt;
                  label110: {
                     stmt = conn.prepareStatement(sql);

                     try {
                        stmt.setString(1, uuid.toString());
                        ResultSet rs = stmt.executeQuery();

                        label88: {
                           try {
                              if (rs.next()) {
                                 var6 = rs.getBoolean("incognito");
                                 break label88;
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
                           break label110;
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
                     break label119;
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

               return false;
            }

            if (conn != null) {
               conn.close();
            }

            return var6;
         } catch (SQLException e) {
            this.module.log("Failed to read incognito state: " + e.getMessage());
            return false;
         }
      }
   }

   public CompletableFuture<Void> setIncognito(UUID uuid, boolean incognito) {
      return CompletableFuture.runAsync(() -> this.setIncognitoSync(uuid, incognito));
   }

   public void setIncognitoSync(UUID uuid, boolean incognito) {
      if (this.isConnected()) {
         String updateSql = "UPDATE %s SET incognito = ? WHERE uuid = ?".formatted(this.table("vanish_states"));
         String insertSql = "INSERT INTO %s (uuid, vanished, vanished_at, vanished_by, tab_visibility_override, incognito)\nVALUES (?, 0, 0, NULL, NULL, ?)\n".formatted(this.table("vanish_states"));

         try {
            Connection conn = this.getConnection();

            try {
               PreparedStatement updateStmt = conn.prepareStatement(updateSql);

               int updated;
               try {
                  updateStmt.setBoolean(1, incognito);
                  updateStmt.setString(2, uuid.toString());
                  updated = updateStmt.executeUpdate();
               } catch (Throwable var14) {
                  if (updateStmt != null) {
                     try {
                        updateStmt.close();
                     } catch (Throwable var12) {
                        var14.addSuppressed(var12);
                     }
                  }

                  throw var14;
               }

               if (updateStmt != null) {
                  updateStmt.close();
               }

               if (updated == 0) {
                  updateStmt = conn.prepareStatement(insertSql);

                  try {
                     updateStmt.setString(1, uuid.toString());
                     updateStmt.setBoolean(2, incognito);
                     updateStmt.executeUpdate();
                  } catch (Throwable var13) {
                     if (updateStmt != null) {
                        try {
                           updateStmt.close();
                        } catch (Throwable var11) {
                           var13.addSuppressed(var11);
                        }
                     }

                     throw var13;
                  }

                  if (updateStmt != null) {
                     updateStmt.close();
                  }
               }

               SM_Vanish var10000 = this.module;
               String var10001 = String.valueOf(uuid);
               var10000.log("Saved incognito state for " + var10001 + ": " + incognito);
            } catch (Throwable var15) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var10) {
                     var15.addSuppressed(var10);
                  }
               }

               throw var15;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException e) {
            this.module.log("Failed to save incognito state: " + e.getMessage());
         }

      }
   }

   public Boolean getNameTagVisibilitySync(UUID uuid) {
      if (!this.isConnected()) {
         return null;
      } else {
         String sql = "SELECT nametag_visibility FROM " + this.table("vanish_states") + " WHERE uuid = ?";

         try {
            Connection conn = this.getConnection();

            Boolean var7;
            label135: {
               label149: {
                  try {
                     PreparedStatement stmt;
                     label137: {
                        label138: {
                           stmt = conn.prepareStatement(sql);

                           try {
                              label139: {
                                 stmt.setString(1, uuid.toString());
                                 ResultSet rs = stmt.executeQuery();

                                 label112: {
                                    label111: {
                                       try {
                                          if (rs.next()) {
                                             Object raw = rs.getObject("nametag_visibility");
                                             if (raw == null) {
                                                var7 = null;
                                                break label111;
                                             }

                                             var7 = rs.getBoolean("nametag_visibility");
                                             break label112;
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
                                       break label137;
                                    }

                                    if (rs != null) {
                                       rs.close();
                                    }
                                    break label139;
                                 }

                                 if (rs != null) {
                                    rs.close();
                                 }
                                 break label138;
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
                           break label149;
                        }

                        if (stmt != null) {
                           stmt.close();
                        }
                        break label135;
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

               return var7;
            }

            if (conn != null) {
               conn.close();
            }

            return var7;
         } catch (SQLException e) {
            this.module.log("Failed to read nametag visibility: " + e.getMessage());
            return null;
         }
      }
   }

   public CompletableFuture<Void> setNameTagVisibility(UUID uuid, Boolean visible) {
      return CompletableFuture.runAsync(() -> this.setNameTagVisibilitySync(uuid, visible));
   }

   public void setNameTagVisibilitySync(UUID uuid, Boolean visible) {
      if (this.isConnected()) {
         String updateSql = "UPDATE %s SET nametag_visibility = ? WHERE uuid = ?".formatted(this.table("vanish_states"));
         String insertSql = "INSERT INTO %s (uuid, vanished, vanished_at, vanished_by, tab_visibility_override, incognito, nametag_visibility)\nVALUES (?, 0, 0, NULL, NULL, 0, ?)\n".formatted(this.table("vanish_states"));

         try {
            Connection conn = this.getConnection();

            try {
               PreparedStatement updateStmt = conn.prepareStatement(updateSql);

               int updated;
               try {
                  if (visible == null) {
                     updateStmt.setNull(1, -6);
                  } else {
                     updateStmt.setBoolean(1, visible);
                  }

                  updateStmt.setString(2, uuid.toString());
                  updated = updateStmt.executeUpdate();
               } catch (Throwable var14) {
                  if (updateStmt != null) {
                     try {
                        updateStmt.close();
                     } catch (Throwable var12) {
                        var14.addSuppressed(var12);
                     }
                  }

                  throw var14;
               }

               if (updateStmt != null) {
                  updateStmt.close();
               }

               if (updated == 0) {
                  updateStmt = conn.prepareStatement(insertSql);

                  try {
                     updateStmt.setString(1, uuid.toString());
                     if (visible == null) {
                        updateStmt.setNull(2, -6);
                     } else {
                        updateStmt.setBoolean(2, visible);
                     }

                     updateStmt.executeUpdate();
                  } catch (Throwable var13) {
                     if (updateStmt != null) {
                        try {
                           updateStmt.close();
                        } catch (Throwable var11) {
                           var13.addSuppressed(var11);
                        }
                     }

                     throw var13;
                  }

                  if (updateStmt != null) {
                     updateStmt.close();
                  }
               }
            } catch (Throwable var15) {
               if (conn != null) {
                  try {
                     conn.close();
                  } catch (Throwable var10) {
                     var15.addSuppressed(var10);
                  }
               }

               throw var15;
            }

            if (conn != null) {
               conn.close();
            }
         } catch (SQLException e) {
            this.module.log("Failed to save nametag visibility: " + e.getMessage());
         }

      }
   }
}
