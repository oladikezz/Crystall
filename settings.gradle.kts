rootProject.name = "Crystall"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// ─── Crystall Minestom Server Core (with all 35 built-in modules) ─────
include("core")
