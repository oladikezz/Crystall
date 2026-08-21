package site.deforce.SM_Accounts.utils;

import net.schalker.DoAPI.DoAPI;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiscordSRVHelper {

    private final DoAPI plugin;
    private final File accountsFile;

    public DiscordSRVHelper(DoAPI plugin) {
        this.plugin = plugin;
        // plugins/SMPS -> global plugins folder -> DiscordSRV -> accounts.aof
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        this.accountsFile = new File(new File(pluginsFolder, "DiscordSRV"), "accounts.aof");
    }

    /**
     * Remove line containing the specified UUID and/or Discord ID.
     * Logic: Iterate lines, if line contains UUID or Discord ID, skip it.
     */
    public void removeEntry(String discordId, UUID uuid) {
        if (!accountsFile.exists()) return;

        try {
            List<String> lines = Files.readAllLines(accountsFile.toPath());
            List<String> newLines = new ArrayList<>();
            boolean changed = false;

            for (String line : lines) {
                // Line format: DiscordID UUID
                if (uuid != null && line.contains(uuid.toString())) {
                    changed = true;
                    continue; // Skip this line
                }
                if (discordId != null && line.contains(discordId)) {
                    changed = true;
                    continue; // Skip this line
                }
                newLines.add(line);
            }

            if (changed) {
                Files.write(accountsFile.toPath(), newLines);
                plugin.getDebugSystem().log("SM_Accounts", "Removed entry from accounts.aof");
            }
        } catch (IOException e) {
            plugin.getDebugSystem().logError("Failed to update accounts.aof", e);
        }
    }

    /**
     * Check if entry exists and add if missing.
     * Line format: DiscordID UUID
     */
    public void addEntryIfNotExists(String discordId, UUID uuid) {
        if (!accountsFile.exists()) {
             // Maybe file doesn't exist? Should we create it?
             // DiscordSRV usually creates it. If not, we might not want to touch it or create it.
             // We can try to create parent dirs and file.
             if (accountsFile.getParentFile().exists()) {
                 try {
                     accountsFile.createNewFile();
                 } catch (IOException e) {
                     plugin.getDebugSystem().logError("Failed to create accounts.aof", e);
                     return;
                 }
             } else {
                 return; // DiscordSRV not installed?
             }
        }

        String entry = discordId + " " + uuid.toString();
        
        try {
            List<String> lines = Files.readAllLines(accountsFile.toPath());
            for (String line : lines) {
                if (line.trim().equals(entry)) {
                    // Entry already exists exactly
                    return;
                }
                // Check if user or discord ID is already linked to something else?
                // Depending on requirements, we might want to update it. 
                // But request says "if not, then add one".
                // I'll assume exact match check first.
            }
            
            // Append new line
            Files.writeString(accountsFile.toPath(), entry + System.lineSeparator(), StandardOpenOption.APPEND);
            plugin.getDebugSystem().log("SM_Accounts", "Added entry to accounts.aof: " + entry);
            
        } catch (IOException e) {
            plugin.getDebugSystem().logError("Failed to add entry to accounts.aof", e);
        }
    }
}

