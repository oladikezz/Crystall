# SMPS (Schalker Modular Plugin System) Documentation

**Version:** 1.1.0  
**Platform:** Folia 
**Author:** deforce_

---

## Table of Contents

1. [Overview](#overview)
2. [Requirements](#requirements)
3. [Core Systems](#core-systems)
4. [Module Development](#module-development)
5. [API Reference](#api-reference)
6. [Commands](#commands)
7. [Configuration](#configuration)
8. [Database Support](#database-support)
9. [Text Transformation (Colors + Tiny Caps)](#text-transformation-colors--tiny-caps)

---

## Overview

SMPS is a modular plugin system for Folia servers that provides:
- **Hot-loading** of external module JARs at runtime
- **Folia-compatible** scheduler system
- **Shared database** connection pool (MySQL, MariaDB, SQLite, H2)
- **Centralized** listener, command, and config management
- **Debug logging** system with file output

Modules are standalone JAR files placed in `plugins/SMPS/modules/` folder.

---

## Requirements

- **Folia** server (Paper with multithreading support)
- Java 21+
- HikariCP (bundled for database pooling)

---

## Core Systems

### 1. ModuleManager
Manages loading, unloading, enabling, and disabling of external module JARs.

```java
SMPS plugin = ...;
ModuleManager moduleManager = plugin.getModuleManager();

// Get all loaded modules
Collection<IModule> modules = moduleManager.getAllModules();

// Get specific module
IModule module = moduleManager.getModule("ModuleName");

// Get module count
int total = moduleManager.getModuleCount();
int enabled = moduleManager.getEnabledModuleCount();

// Load module from file
moduleManager.loadModuleByFileName("MyModule.jar");

// Unload module
moduleManager.unloadModule("ModuleName");

// Enable/disable module
moduleManager.enableModule("ModuleName");
moduleManager.disableModule("ModuleName");

// Reload all modules
moduleManager.reloadAllModules();

// Module config management
// Get module's data folder (plugins/SMPS/modules/ModuleName/)
File folder = moduleManager.getModuleDataFolder("ModuleName");

// Load module config (auto-extracts default from JAR if missing)
YamlConfiguration config = moduleManager.loadModuleConfig("ModuleName");
YamlConfiguration custom = moduleManager.loadModuleConfig("ModuleName", "messages.yml");

// Manually save default config from module's JAR
moduleManager.saveModuleDefaultConfig("ModuleName"); // extracts config.yml
moduleManager.saveModuleDefaultConfig("ModuleName", "messages.yml", "messages.yml");
```

### 2. SchedulerManager
Folia-compatible task scheduler with proper thread context handling.

#### Global Tasks (no world/entity binding)
```java
SchedulerManager scheduler = plugin.getSchedulerManager();

// Run immediately in global context
scheduler.runGlobalTask("task-name", () -> {
    Bukkit.broadcast(Component.text("Hello!"));
});

// Run with delay (ticks)
scheduler.runTaskLater("task-name", () -> { ... }, 20L); // 1 second

// Run repeating task
scheduler.runTaskTimer("task-name", () -> { ... }, 20L, 100L); // delay, period
```

#### Region Tasks (for blocks/chunks)
```java
Location location = player.getLocation();

// Run in specific region
scheduler.runRegionTask(location, "block-task", () -> {
    location.getBlock().setType(Material.STONE);
});

// With delay
scheduler.runRegionTaskLater(location, "task-name", () -> { ... }, 20L);

// Repeating
scheduler.runRegionTaskTimer(location, "task-name", () -> { ... }, 20L, 100L);

// By chunk coordinates
scheduler.runRegionTask(world, chunkX, chunkZ, "task-name", () -> { ... });
```

#### Entity Tasks (for players/mobs)
```java
Player player = ...;

// Run for entity (REQUIRED for player operations in Folia!)
scheduler.runEntityTask(player, "player-task", () -> {
    player.sendMessage("Hello!");
});

// With delay
scheduler.runEntityTaskLater(player, "task-name", () -> { ... }, 20L);

// Repeating
scheduler.runEntityTaskTimer(player, "task-name", () -> { ... }, 20L, 100L);
```

#### Async Tasks (for DB, network, files)
```java
// Run async immediately
scheduler.runAsync("db-save", () -> {
    database.save(data);
});

// With delay
scheduler.runAsyncLater("task-name", () -> { ... }, 20L);

// Repeating
scheduler.runAsyncTimer("task-name", () -> { ... }, 20L, 100L);
```

#### Sync Utilities (get values from main thread)
```java
// Call from async, get result from global context
String result = scheduler.callGlobalSync("operation", () -> {
    return Bukkit.getServer().getName();
}, 2000L); // timeout ms

// From region context
BlockData data = scheduler.callRegionSync(location, () -> {
    return location.getBlock().getBlockData();
}, 2000L);

// From entity context
String name = scheduler.callEntitySync(player, () -> {
    return player.getName();
}, 2000L);
```

#### Task Management
```java
// Cancel task
scheduler.cancelTask("task-name");

// Check if task exists
boolean exists = scheduler.hasTask("task-name");

// Get active task count
int count = scheduler.getTaskCount();

// Cancel all tasks
scheduler.cancelAllTasks();
```

### 3. ListenerManager
Manages Bukkit event listeners with automatic cleanup.

```java
ListenerManager listenerManager = plugin.getListenerManager();

// Register listener
listenerManager.registerListener(new MyListener());

// Unregister listener
listenerManager.unregisterListener(myListener);

// Get listener count
int count = listenerManager.getListenerCount();

// Unregister all (called on disable)
listenerManager.unregisterAllListeners();
```

### 4. CommandManager
Manages commands using Paper's Brigadier command system.

```java
CommandManager commandManager = plugin.getCommandManager();

// Register module command (with auto Folia entity scheduling for players)
commandManager.registerModuleCommand(new MyModuleCommand(plugin));

// Check if command registered
boolean registered = commandManager.isCommandRegistered("mycommand");
```

### 5. ConfigManager
Manages the main plugin configuration.

```java
ConfigManager configManager = plugin.getConfigManager();

// Get config
FileConfiguration config = configManager.getConfig();

// Reload config
configManager.reloadConfig();

// Read values
boolean debug = config.getBoolean("debug");
String level = config.getString("detail-level");
```

### 6. DebugSystem
Logging system with console, file output, and Discord webhook error notifications.

```java
DebugSystem debug = plugin.getDebugSystem();

// Simple log (only when debug enabled)
debug.log("Something happened");

// Log with category
debug.log("ModuleName", "Action performed");

// Log error with stacktrace (auto-detects module from stacktrace)
debug.logError("Failed to save", exception);

// Log error with explicit module name (sent to Discord webhook if configured)
debug.logError("MyModule", "Failed to save player data", exception);

// Log warning (sent to Discord webhook as yellow embed if configured)
debug.logWarning("Something looks wrong");

// Log warning with module name
debug.logWarning("MyModule", "Config value missing, using default");

// Log warning with throwable
debug.logWarning("MyModule", "Non-critical error during sync", exception);

// Enable/disable debug mode
debug.setDebugEnabled(true);

// Check if enabled
boolean enabled = debug.isDebugEnabled();

// Get server info string
String info = debug.getSystemInfo();
// "Server: Folia | Version: 1.21.1 | Players: 5/100 | TPS: 20.00"

// Access webhook manager directly
WebhookManager webhook = debug.getWebhookManager();
```

### Discord Webhook (Error Notifications)

SMPS can send errors to a Discord channel via webhook. Configure in `config.yml`:

```yaml
webhook:
  url: "https://discord.com/api/webhooks/YOUR_ID/YOUR_TOKEN"
  enabled: true
  send-warnings: true  # Also send warnings (yellow embeds), not just errors (red)
```

**Features:**
- **Errors** are sent as **red** Discord embeds
- **Warnings** are sent as **yellow** Discord embeds (configurable via `send-warnings`)
- If the text exceeds 4000 characters, it's sent as a `.txt` file attachment instead
- SMPS auto-detects which module caused the error by scanning the stacktrace
- Rate limited to 1 message per 5 seconds to prevent spam
- Embed includes: error/warning message, module name, server version, SMPS version, timestamp, full stacktrace

### 7. DatabaseManager
Shared database connection pool supporting multiple database types and multiple connections.

```java
DatabaseManager db = plugin.getDatabaseManager();

// Check primary connection
boolean connected = db.isConnected();

// Get connection from primary pool
try (Connection conn = db.getConnection()) {
    // Use connection
}

// Get connection from a specific database (multi-db support)
try (Connection conn = db.getConnection("database2")) {
    // Use connection from database2
}

// Get table name with prefix
String tableName = db.table("my_table"); // "sm_my_table"

// Get database type
DatabaseType type = db.getDatabaseType();
// MYSQL, MARIADB, SQLITE, H2

// --- Multi-database API ---

// Get all database entries
Map<String, DatabaseManager.DatabaseEntry> all = db.getAllDatabases();

// Get a specific database entry
DatabaseManager.DatabaseEntry entry = db.getDatabase("database2");

// Check permissions for a connection
String perms = entry.getPermissions(); // "READ_WRITE", "READ_ONLY", "NONE"

// Check if a specific database is connected
boolean db2Connected = db.isConnected("database2");

// Connected / total counts
int connectedCount = db.getConnectedCount();
int totalCount = db.getTotalCount();

// --- Query API (operates on primary database) ---

// Execute update (sync)
int affected = db.executeUpdate("INSERT INTO table (col) VALUES (?)", value);

// Execute query (sync)
List<String> names = db.executeQuery(
    "SELECT name FROM table WHERE id = ?",
    rs -> {
        List<String> result = new ArrayList<>();
        while (rs.next()) {
            result.add(rs.getString("name"));
        }
        return result;
    },
    playerId
);

// Async operations
CompletableFuture<Integer> future = db.executeUpdateAsync("INSERT ...", params);
CompletableFuture<List<Data>> dataFuture = db.executeQueryAsync("SELECT ...", handler, params);

// Batch operations
db.executeBatch("INSERT INTO table (col) VALUES (?)", ps -> {
    for (String value : values) {
        ps.setString(1, value);
        ps.addBatch();
    }
});

// Migration tracking
boolean applied = db.isMigrationApplied("modulename", 1);
db.registerMigration("modulename", 1);
int currentVersion = db.getCurrentMigrationVersion("modulename");

// Reconnect all databases (used on config reload)
db.reconnect();
```

### 8. PluginReloader
Handles module reloading and lifecycle management.

```java
PluginReloader reloader = plugin.getPluginReloader();

// Reload specific module
reloader.reloadModule("ModuleName", sender);

// Enable/disable module
reloader.enableModule("ModuleName", sender);
reloader.disableModule("ModuleName", sender);
```

---

## Module Development

### Module Structure

Each module is a separate JAR file with:

```
MyModule.jar
├── module.yml          # Module descriptor (required)
└── com/example/
    └── MyModule.class  # Main class implementing IModule
```

### module.yml

```yaml
name: MyModule
version: 1.0.0
author: YourName
description: Description of your module
main: com.example.MyModule
```

### IModule Interface

```java
package net.schalker.SMPS.api;

public interface IModule {
    void onEnable();      // Called when module is enabled
    void onDisable();     // Called when module is disabled
    void reload();        // Called on /sm reload or /sm module reload <name>
    ModuleInfo getModuleInfo();  // Return module metadata
    boolean isEnabled();  // Return current enabled state
}
```

### Example Module Implementation

```java
package com.example;

import net.schalker.SMPS.SMPS;
import net.schalker.SMPS.api.IModule;
import net.schalker.SMPS.core.module.ModuleInfo;
import org.bukkit.configuration.file.YamlConfiguration;

public class MyModule implements IModule {
    
    private final SMPS plugin;
    private final ModuleInfo info;
    private boolean enabled = false;
    private YamlConfiguration config;
    
    // Constructor with SMPS parameter (preferred)
    public MyModule(SMPS plugin) {
        this.plugin = plugin;
        this.info = new ModuleInfo("MyModule", "1.0.0", "Author", "Description");
    }
    
    @Override
    public void onEnable() {
        this.enabled = true;
        
        // Load config (auto-extracts default from JAR if missing)
        this.config = plugin.getModuleManager().loadModuleConfig("MyModule");
        
        // Register listeners
        plugin.getListenerManager().registerListener(new MyListener(plugin));
        
        // Register commands
        plugin.getCommandManager().registerModuleCommand(new MyCommand(plugin));
        
        // Schedule tasks
        plugin.getSchedulerManager().runAsyncTimer("my-task", () -> {
            // Do something
        }, 20L, 200L);
        
        // Use database
        if (plugin.isDatabaseConnected()) {
            initDatabase();
        }
        
        plugin.getDebugSystem().log("MyModule", "Enabled!");
    }
    
    @Override
    public void onDisable() {
        this.enabled = false;
        
        // Cancel tasks
        plugin.getSchedulerManager().cancelTask("my-task");
        
        plugin.getDebugSystem().log("MyModule", "Disabled!");
    }
    
    @Override
    public void reload() {
        // Reload config
        this.config = plugin.getModuleManager().loadModuleConfig("MyModule");
        plugin.getDebugSystem().log("MyModule", "Reloaded!");
    }
    
    @Override
    public ModuleInfo getModuleInfo() {
        return this.info;
    }
    
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
```

### ModuleDatabase (for modules using database)

```java
package com.example;

import net.schalker.SMPS.SMPS;
import net.schalker.SMPS.core.database.ModuleDatabase;

public class MyModuleDatabase extends ModuleDatabase {
    
    public MyModuleDatabase(SMPS plugin) {
        super(plugin, "mymodule"); // prefix: sm_mymodule_
    }
    
    @Override
    public void createTables() {
        String sql;
        if (isSqliteOrH2()) {
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    data TEXT
                )
                """.formatted(table("data"));
        } else {
            sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    data TEXT
                )
                """.formatted(table("data"));
        }
        
        try (var conn = getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to create tables", e);
        }
    }
    
    public void saveData(UUID playerUuid, String data) {
        // Use getConnection() from parent class
        try (var conn = getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO " + table("data") + " (player_uuid, data) VALUES (?, ?)"
             )) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, data);
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getDebugSystem().logError("Failed to save data", e);
        }
    }
}
```

### ModuleCommand

```java
package com.example;

import net.schalker.SMPS.SMPS;
import net.schalker.SMPS.core.command.ModuleCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class MyCommand extends ModuleCommand {
    
    public MyCommand(SMPS plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "mycommand";
    }
    
    @Override
    public String getPermission() {
        return "mymodule.command";
    }
    
    @Override
    public String getDescription() {
        return "My module command";
    }
    
    @Override
    public String getUsage() {
        return "/mycommand <args>";
    }
    
    @Override
    public Collection<String> getAliases() {
        return Arrays.asList("mc", "mycmd");
    }
    
    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        var sender = stack.getSender();
        // Command is already running in correct Folia context (entity scheduler for players)
        sender.sendMessage("Hello from my command!");
    }
    
    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            return List.of("option1", "option2", "option3");
        }
        return List.of();
    }
}
```

---

## Commands

### Core Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/sm` or `/smps` | `smps.use` | Main command, shows help |
| `/sm reload` | `smps.reload` | Reload config and all modules |
| `/sm debug` | `smps.debug` | Toggle debug mode |
| `/sm info` | `smps.use` | Show plugin info |
| `/sm database` | `smps.database` | Database management |
| `/sm database info` | `smps.database` | Show database info |
| `/sm database reset confirm` | `*` or OP | Reset all database tables |

### Module Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/sm module list` | `sm.module.control` | List all loaded modules |
| `/sm module info <name>` | `sm.module.control` | Show module info |
| `/sm module enable <name>` | `sm.module.control` | Enable a module |
| `/sm module disable <name>` | `sm.module.control` | Disable a module |
| `/sm module reload <name>` | `sm.module.control` | Reload a module |
| `/sm module load <file>` | `sm.module.control` | Load module from JAR file |
| `/sm module unload <name>` | `sm.module.control` | Unload a module |
| `/sm module scan` | `sm.module.control` | Scan for new module JARs |

---

## Configuration

### config.yml

```yaml
# Debug mode - enables detailed logging
debug: true

# Log to file in plugins/SMPS/debug/
file-logging: true

# Detail level: BASIC, DETAILED, VERBOSE
detail-level: DETAILED

# Auto-load modules from plugins/SMPS/modules/ on startup
auto-load-modules: false

# Logging options
log-module-events: true
log-commands: true
log-listeners: true
log-scheduler: true

# Primary database configuration
database:
  # Type: mysql, mariadb, sqlite, h2
  type: sqlite
  
  # For MySQL/MariaDB
  host: localhost
  port: 3306
  database: smps
  username: root
  password: ""
  
  # For SQLite/H2
  file: database
  
  # Table prefix
  table-prefix: sm_
  
  # Connection pool settings
  pool:
    size: 10
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000

# Additional database connections (optional)
# Add database2, database3, ... to connect to multiple databases
# on the same server (or different servers).
# The first (database) is primary, used by modules by default.
# /sm database shows info for all connections and their permissions.
#
# Example: connecting to another database on the same server
# database2:
#   type: mysql
#   host: localhost
#   port: 3306
#   database: s3_SkinsRestorer
#   username: root
#   password: ''
#   pool:
#     size: 5
#     connection-timeout: 30000
#     idle-timeout: 600000
#     max-lifetime: 1800000
#   table-prefix: sr_
#   file: database2
#
# database3:
#   type: mysql
#   host: localhost
#   port: 3306
#   database: s3_other
#   username: root
#   password: ''
#   pool:
#     size: 5
#     connection-timeout: 30000
#     idle-timeout: 600000
#     max-lifetime: 1800000
#   table-prefix: sm_
#   file: database3
```

---

## Database Support

### Supported Databases

| Type | Driver | Use Case |
|------|--------|----------|
| **MySQL** | `com.mysql.cj.jdbc.Driver` | Production servers |
| **MariaDB** | `org.mariadb.jdbc.Driver` | Production servers |
| **SQLite** | `org.sqlite.JDBC` | Single-server, file-based |
| **H2** | `org.h2.Driver` | Development, fallback |

### Multiple Database Connections

SMPS supports connecting to multiple databases simultaneously. This is useful when your MySQL server hosts multiple databases (e.g., `s3_smps`, `s3_SkinsRestorer`, `s3_other`) and you want SMPS to connect to and display info about all of them.

Configure them in `config.yml` using keys `database`, `database2`, `database3`, etc. Keys don't need to be sequential (e.g., you can have `database2` and `database5`).

- The first key (`database`) is always the **primary** connection used by modules by default
- Additional connections (`database2`, `database3`, ...) are also initialized and available via key
- **Backward compatible**: modules that call `getConnection()` without a key still use the primary database — nothing breaks
- `/sm database` shows info for **all** connections including type, status, permissions, and tables
- SMPS automatically detects permissions for each connection: **Read/Write**, **Read Only**, or **No Access**

#### Permission Detection

On initialization, SMPS tests each connection by:
1. Attempting to create a temporary table (write test)
2. Attempting to insert and read data
3. Falling back to a `SELECT 1` query (read test)

Results are displayed as:
- `✔ Read/Write` – Full access
- `⚠ Read Only` – Can read but not write
- `✖ No Access` – Connection failed or no permissions

#### Accessing Multiple Databases in Code

```java
DatabaseManager db = plugin.getDatabaseManager();

// Primary connection (default)
Connection primary = db.getConnection();

// Specific connection by key
Connection secondary = db.getConnection("database2");

// Check if a specific database is connected
boolean connected = db.isConnected("database2");

// Get all database entries
Map<String, DatabaseManager.DatabaseEntry> all = db.getAllDatabases();

// Get info about a specific database
DatabaseManager.DatabaseEntry entry = db.getDatabase("database2");
String perms = entry.getPermissions(); // "READ_WRITE", "READ_ONLY", "NONE"
boolean isUp = entry.isConnected();
DatabaseConfig cfg = entry.getConfig();

// Get connected/total counts
int connected = db.getConnectedCount();
int total = db.getTotalCount();
```

### Automatic Fallback

If the primary database (`database`) is unavailable, SMPS automatically falls back to H2 to ensure modules keep working. Additional connections (`database2`, `database3`, ...) do **not** fall back — they simply report as disconnected in `/sm database`.

### Table Naming

All tables use the configured prefix (default: `sm_`):
- Core: `sm_migrations`
- Modules: `sm_<modulename>_<tablename>`

---

## File Structure

```
plugins/SMPS/
├── config.yml           # Main configuration
├── data/
│   └── database.db      # SQLite/H2 database file
├── debug/
│   └── debug_*.log      # Debug log files
└── modules/
    ├── Module1.jar      # External module
    └── Module2.jar      # External module
```

---

## Quick Start for Module Developers

1. Create a new Java project with SMPS as dependency
2. Implement `IModule` interface
3. Create `module.yml` in resources
4. Build JAR file
5. Place JAR in `plugins/SMPS/modules/`
6. Run `/sm module scan` then `/sm module load <name>`

_p.s. If you want to load a newer version of an already loaded module, use `/sm module hotswap <name> <new-jar-file>` to unload the old module and load the new one in a single command. Alternatively, you can manually run `/sm module unload <name>` followed by `/sm module load <new-jar>`._

### Accessing SMPS Services

```java
// In your module class:
public class MyModule implements IModule {
    private final SMPS plugin;
    
    public MyModule(SMPS plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void onEnable() {
        // Scheduler
        plugin.getSchedulerManager().runAsync("task", () -> {...});
        
        // Listeners
        plugin.getListenerManager().registerListener(new MyListener());
        
        // Commands
        plugin.getCommandManager().registerModuleCommand(new MyCommand(plugin));
        
        // Database
        if (plugin.isDatabaseConnected()) {
            Connection conn = plugin.getDatabaseManager().getConnection();
        }
        
        // Debug logging
        plugin.getDebugSystem().log("MyModule", "Started!");
        
        // Config access
        FileConfiguration config = plugin.getConfigManager().getConfig();
    }
}
```

---

## Best Practices

1. **Always use SchedulerManager** - Never use Bukkit scheduler directly on Folia
2. **Use entity scheduler for players** - Required for player operations in Folia
3. **Check database connection** - Use `plugin.isDatabaseConnected()` before DB operations
4. **Clean up on disable** - Cancel tasks, unregister listeners
5. **Use ModuleDatabase** - Provides proper table prefixing and connection handling
6. **Log with categories** - Use `debug.log("Category", "message")` for organized logs

---

## Text Transformation (Colors + Tiny Caps)

SMPS provides comprehensive text formatting utilities for color parsing and tiny caps (small capitals) formatting that both the core plugin and modules can reuse.

### Overview

Text transformation in SMPS consists of two complementary systems:

1. **Dynamic Colors** - Parse hex colors (`#RRGGBB`, `&#RRGGBB`) and legacy codes (`&a`, `&f`, etc.)
2. **Tiny Caps** - Convert text to Unicode small capital letters for stylized titles and headings

### Configuration

Colors are configured in `config.yml`:

```yaml
# ======== Основные настройки ========
# Префикс сообщений
prefix: "&6[&eSMPS&6]&r"

# Color scheme for plugin messages
main-color: "&#f44d89"          # Hex format (# or &#)
secondary-color: "&#FFA1C4"     # Hex format (# or &#)

# Alternative legacy formats:
# main-color: "&d"              # Legacy Minecraft color codes
# secondary-color: "&b"
```

**Supported Color Formats:**
- `&#RRGGBB` - RGB hex format (user-friendly, recommended)
- `#RRGGBB` - Standard hex format
- `&a`, `&b`, `&c`, etc. - Legacy Minecraft color codes

### SMPS Convenience Methods

These methods on the SMPS plugin instance handle color configuration and application:

```java
SMPS plugin = ...;

// Get configured colors from config.yml
String mainColor = plugin.getMainColor();        // returns "&#f44d89"
String secondaryColor = plugin.getSecondaryColor();  // returns "&#FFA1C4"

// Apply dynamic colors to a message
// Replaces &[MAIN] and &[SECONDARY] placeholders with configured colors
// Also processes hex and legacy color codes
String message = "&[MAIN]Title &[SECONDARY]Subtitle &7(gray)";
String colored = plugin.applyColors(message);
// Result: "§x§f§4§4§d§8§9Title §x§f§f§a§1§c§4Subtitle §7(gray)"

// Convert text to tiny caps format
String title = plugin.applyTinyCaps("Schalker Modular");
// Result: "ꜱᴄʜᴀʟᴋᴇʀ ᴍᴏᴅᴜʟᴀʀ"
```

### TextFormatter Utility Class

Located in `net.schalker.SMPS.core.util`, provides low-level text formatting:

```java
import net.schalker.SMPS.core.util.TextFormatter;

// Colorize text (processes hex and legacy color codes)
String colored = TextFormatter.colorize("&[MAIN]§lHeader §7(sub)");
String colored2 = TextFormatter.colorize("&#e579b0&ls&#f088b9&lᴄ");

// Convert to tiny caps
String tiny = TextFormatter.toTinyCaps("Module Info");
// Result: "ᴍᴏᴅᴜʟᴇ ɪɴꜰᴏ"

// Hex to TextColor (Adventure API)
TextColor color = TextFormatter.hexToTextColor("#ff00ff");
if (color != null) {
    // Use with Adventure API
}

// Hex to legacy fallback
String legacyColor = TextFormatter.hexToLegacyColor("#ff00ff");
// Result: "§d" (closest Minecraft color)
```

### Tiny Caps Character Map

Tiny caps uses Unicode small capital letters for stylization:

| Original | A  | B  | C  | D  | E  | F  | G  | H  | I  | J  | K  | L  | M  |
|----------|----|----|----|----|----|----|----|----|----|----|----|----|
| Tiny     | ᴀ  | ʙ  | ᴄ  | ᴅ  | ᴇ  | ꜰ  | ɢ  | ʜ  | ɪ  | ᴊ  | ᴋ  | ʟ  | ᴍ  |

| Original | N  | O  | P  | Q  | R  | S  | T  | U  | V  | W  | X  | Y  | Z  |
|----------|----|----|----|----|----|----|----|----|----|----|----|----|
| Tiny     | ɴ  | ᴏ  | ᴘ  | ϙ  | ʀ  | ꜱ  | ᴛ  | ᴜ  | ᴠ  | ᴡ  | x  | ʏ  | ᴢ  |

**Note:** The system converts all input to lowercase before applying tiny caps, so both "HELLO" and "Hello" produce "ʜᴇʟʟᴏ".

### Practical Examples

#### Example 1: Styled Section Header
```java
String title = plugin.applyTinyCaps("Server Status");
sender.sendMessage(
    plugin.applyColors(
        "§f§l======== &[MAIN]§l" + title + " &[SECONDARY]§l========"
    )
);
// Output: Stylized header with main color title and secondary color borders
```

#### Example 2: Module Info with Mixed Colors
```java
String moduleName = plugin.applyTinyCaps("Database Module");
sender.sendMessage(
    plugin.applyColors("&[MAIN]§l" + moduleName + " &[SECONDARY]v1.0.0")
);
```

#### Example 3: Colored Gradient with Tiny Caps (SimpleChat)
If using SimpleChat plugin for RGB support:
```java
String title = plugin.applyTinyCaps("Loading");
sender.sendMessage(
    "&#e579b0&l" + title.substring(0, 3) +
    "&#f088b9&l" + title.substring(3, 6) +
    "&#d97fb8&l" + title.substring(6)
);
// Creates a gradient effect across the text
```

#### Example 4: Dynamic Color Replacement in Commands
```java
@Override
public void execute(CommandSourceStack stack, String[] args) {
    var sender = stack.getSender();
    
    String separator = "§f§l" + "=".repeat(50);
    String title = plugin.applyTinyCaps("Module List");
    
    sender.sendMessage(
        plugin.applyColors("&[SECONDARY]" + separator)
    );
    sender.sendMessage(
        plugin.applyColors("&[MAIN]§l" + title)
    );
    sender.sendMessage(
        plugin.applyColors("&[SECONDARY]" + separator)
    );
    
    // List modules with alternating colors
    for (IModule module : modules) {
        String status = module.isEnabled() ? "&a✔" : "&c✗";
        sender.sendMessage(
            plugin.applyColors("&[SECONDARY]  " + status + " &[MAIN]" + module.getModuleInfo().getName())
        );
    }
}
```

### Color Processing Order

The `applyColors()` method processes colors in this order:

1. Replace `&[MAIN]` with configured main-color
2. Replace `&[SECONDARY]` with configured secondary-color
3. Parse hex colors (`&#RRGGBB` and `#RRGGBB`) to Minecraft RGB format
4. Parse legacy color codes (`&a`, `&b`, etc.)

This means you can mix all formats in a single string:
```java
plugin.applyColors("&[MAIN]Main &[SECONDARY]Secondary &c(Red) &#ff00ff(Hex)");
```

### Integration with Other Plugins

#### SimpleChat Plugin
SMPS colors work seamlessly with SimpleChat plugin that supports `&#RRGGBB` format:

```java
// Config color (SMPS)
// main-color: "&#e579b0"

// In your message (works with SimpleChat)
sender.sendMessage(
    plugin.applyColors("&[MAIN]&lText") +
    " &#f088b9&l(SimpleChat color)"
);
```

### Module Color Usage Best Practices

When developing modules that use colors:

1. **Use plugin convenience methods** - Always call `plugin.getMainColor()` and `plugin.getSecondaryColor()` instead of hardcoding colors
2. **Support config reloads** - Cache colors in your module and refresh on reload
3. **Combine with formatting codes** - Pair colors with `§l` (bold), `§7` (gray), `§n` (underline) for better appearance
4. **Test with different color schemes** - Ensure your output looks good with various main/secondary color combinations

Example module pattern:
```java
@Override
public void reload() {
    this.mainColor = plugin.getMainColor();
    this.secondaryColor = plugin.getSecondaryColor();
    
    // Re-initialize color-dependent messages
    initializeMessages();
}

private void initializeMessages() {
    this.successMessage = plugin.applyColors("&[MAIN]§l✔ &[SECONDARY]Success!");
    this.errorMessage = plugin.applyColors("&[MAIN]§l✗ &[SECONDARY]Error!");
}
```

### API Stability

- `SMPS.getMainColor()` - Stable, returns configured color
- `SMPS.getSecondaryColor()` - Stable, returns configured color
- `SMPS.applyColors(String)` - Stable, processes all color formats
- `SMPS.applyTinyCaps(String)` - Stable, converts to tiny caps
- `TextFormatter` - Public utility class, all methods stable

---
*Documentation generated for SMPS v1.1.0*
