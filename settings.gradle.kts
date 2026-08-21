rootProject.name = "Crystall"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// ─── Crystall Minestom Server Core ────────────────────────────────
include("core")

// ─── Core framework (the plugin every module compiles against) ─────
include("DoAPI")

// ─── Modules with standard Maven/Gradle layout (src/main/java) ─────
include("SM_Example")

// ─── Modules with source files directly in the module folder ────────
include("SM_Accounts")
include("SM_Announces")
include("SM_AdminList")
include("SM_Alert")
include("SM_AutoReplenish")
include("SM_Checker")
include("SM_Clans")
include("SM_Cosmetics")
include("SM_Crowns")
include("SM_DebugStick")
include("SM_Essentials")
include("SM_FastLeaves")
include("SM_Flags")
include("SM_Hat")
include("SM_Help")
include("SM_Invsee")
include("SM_ItemDespawn")
include("SM_ItemMeta")
include("SM_KeepInventory")
include("SM_Lightcraft")
include("SM_Marry")
include("SM_PhaseGuard")
include("SM_PlayerHeads")
include("SM_QuietBan")
include("SM_Scale")
include("SM_Spit")
include("SM_Stats")
include("SM_StonecutterAdditions")
include("SM_StreamerMode")
include("SM_TrafficOptimizer")
include("SM_TrollItems")
include("SM_UCosmetics")
include("SM_UserInfo")
include("SM_Vanish")
include("SM_Voodoos")
include("SM_Watcher")
