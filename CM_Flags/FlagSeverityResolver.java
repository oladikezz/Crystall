package net.schalker.SMPS.modules.flags;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Resolves the dynamic severity level for a flag based on its value and the
 * per-flag level configuration.
 *
 * Config format:
 * <pre>
 * flags:
 *   ore_pickup:
 *     levels:
 *       low:
 *         condition: BELOW
 *         value: 32
 *       medium:
 *         condition: BETWEEN
 *         value: 32
 *         second-value: 64
 *       high:
 *         condition: ABOVE
 *         value: 64
 * </pre>
 */
public class FlagSeverityResolver {
   private final Map<FlagType, Map<FlagType.FlagSeverity, LevelRule>> rules = new EnumMap<>(FlagType.class);

   public FlagSeverityResolver(FileConfiguration config) {
      this.reload(config);
   }

   public void reload(FileConfiguration config) {
      this.rules.clear();
      if (config == null) return;

      for (FlagType flagType : FlagType.values()) {
         String basePath = "flags." + flagType.getKey() + ".levels";
         ConfigurationSection levelsSection = config.getConfigurationSection(basePath);

         // Alias fallback: if exact key not found, try stripping dimension suffix
         // e.g. tnt_placement_overworld → tnt_placement, wither_summon_nether → wither_summon
         if (levelsSection == null) {
            String aliasKey = getAliasKey(flagType.getKey());
            if (aliasKey != null) {
               levelsSection = config.getConfigurationSection("flags." + aliasKey + ".levels");
            }
         }

         if (levelsSection == null) continue;

         Map<FlagType.FlagSeverity, LevelRule> flagRules = new EnumMap<>(FlagType.FlagSeverity.class);

         for (String levelKey : levelsSection.getKeys(false)) {
            FlagType.FlagSeverity severity = parseSeverity(levelKey);
            if (severity == null) continue;

            ConfigurationSection levelSection = levelsSection.getConfigurationSection(levelKey);
            if (levelSection == null) continue;

            String condStr = levelSection.getString("condition", "ABOVE").toUpperCase();
            Condition condition;
            try {
               condition = Condition.valueOf(condStr);
            } catch (IllegalArgumentException e) {
               continue;
            }

            int value = levelSection.getInt("value", 0);
            int secondValue = levelSection.getInt("second-value", 0);

            flagRules.put(severity, new LevelRule(condition, value, secondValue));
         }

         if (!flagRules.isEmpty()) {
            this.rules.put(flagType, flagRules);
         }
      }
   }

   /**
    * Resolve the severity for a given flag type and value.
    * Checks HIGH first, then MEDIUM, then LOW.
    * Falls back to the flag type's default severity if no level config exists or matches.
    */
   public FlagType.FlagSeverity resolve(FlagType flagType, int value) {
      return this.resolve(flagType, value, 1.0);
   }

   /**
    * Resolve the severity for a given flag type and value, scaling the
    * level boundaries by the playtime sensitivity multiplier.
    * Lower multiplier = lower boundaries = severity escalates faster.
    */
   public FlagType.FlagSeverity resolve(FlagType flagType, int value, double sensitivityMultiplier) {
      Map<FlagType.FlagSeverity, LevelRule> flagRules = this.rules.get(flagType);
      if (flagRules == null || flagRules.isEmpty()) {
         return flagType.getSeverity();
      }

      // Check from highest to lowest
      FlagType.FlagSeverity[] order = { FlagType.FlagSeverity.HIGH, FlagType.FlagSeverity.MEDIUM, FlagType.FlagSeverity.LOW };
      for (FlagType.FlagSeverity severity : order) {
         LevelRule rule = flagRules.get(severity);
         if (rule != null && rule.matches(value, sensitivityMultiplier)) {
            return severity;
         }
      }

      // If no rule matched, return the flag's default
      return flagType.getSeverity();
   }

   public boolean hasLevels(FlagType flagType) {
      return this.rules.containsKey(flagType);
   }

   /**
    * Try to derive an alias config key from a split enum key.
    * Known suffixes: _overworld, _nether, _end
    * e.g. "tnt_placement_overworld" → "tnt_placement"
    *      "wither_summon_nether"    → "wither_summon"
    *      "ore_pickup"              → null (no suffix)
    */
   private static String getAliasKey(String key) {
      String[] suffixes = { "_overworld", "_nether", "_end" };
      for (String suffix : suffixes) {
         if (key.endsWith(suffix)) {
            return key.substring(0, key.length() - suffix.length());
         }
      }
      return null;
   }

   private static FlagType.FlagSeverity parseSeverity(String key) {
      return switch (key.toLowerCase()) {
         case "low" -> FlagType.FlagSeverity.LOW;
         case "medium" -> FlagType.FlagSeverity.MEDIUM;
         case "high" -> FlagType.FlagSeverity.HIGH;
         default -> null;
      };
   }

   public enum Condition {
      BELOW,
      BETWEEN,
      ABOVE;

      public boolean matches(int value, int configValue, int secondValue) {
         return switch (this) {
            case BELOW -> value < configValue;
            case BETWEEN -> value >= configValue && value <= secondValue;
            case ABOVE -> value > configValue;
         };
      }
   }

   private record LevelRule(Condition condition, int value, int secondValue) {
      public boolean matches(int testValue) {
         return this.condition.matches(testValue, this.value, this.secondValue);
      }

      /**
       * Match with scaled boundaries. The multiplier is applied to the level
       * boundaries so that lower-playtime players reach higher severities faster.
       */
      public boolean matches(int testValue, double sensitivityMultiplier) {
         if (sensitivityMultiplier == 1.0) {
            return this.matches(testValue);
         }
         int scaledValue = (int) Math.max(1, Math.round(this.value * sensitivityMultiplier));
         int scaledSecond = (int) Math.max(1, Math.round(this.secondValue * sensitivityMultiplier));
         return this.condition.matches(testValue, scaledValue, scaledSecond);
      }
   }
}
