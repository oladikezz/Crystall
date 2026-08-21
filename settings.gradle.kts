rootProject.name = "Crystall"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// ─── Crystall Minestom Server Core ────────────────────────────────
include("core")

// ─── Core framework (the plugin every module compiles against) ─────
include("DoAPI")

// ─── Modules with standard Maven/Gradle layout (src/main/java) ─────
include("CM_Example")

// ─── Modules with source files directly in the module folder ────────
include("CM_Accounts")
include("CM_Announces")
include("CM_AdminList")
include("CM_Alert")
include("CM_AutoReplenish")
include("CM_Checker")
include("CM_Clans")
include("CM_Cosmetics")
include("CM_Crowns")
include("CM_DebugStick")
include("CM_Essentials")
include("CM_FastLeaves")
include("CM_Flags")
include("CM_Hat")
include("CM_Help")
include("CM_Invsee")
include("CM_ItemDespawn")
include("CM_ItemMeta")
include("CM_KeepInventory")
include("CM_Lightcraft")
include("CM_Marry")
include("CM_PhaseGuard")
include("CM_PlayerHeads")
include("CM_QuietBan")
include("CM_Scale")
include("CM_Spit")
include("CM_Stats")
include("CM_StonecutterAdditions")
include("CM_StreamerMode")
include("CM_TrafficOptimizer")
include("CM_TrollItems")
include("CM_UCosmetics")
include("CM_UserInfo")
include("CM_Vanish")
include("CM_Voodoos")
include("CM_Watcher")
