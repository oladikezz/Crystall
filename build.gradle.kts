plugins {
    java
    id("com.gradleup.shadow") version "9.4.2" apply false
}

// ─── Shared configuration for every module ──────────────────────────
subprojects {
    if (project.name == "core") {
        return@subprojects
    }
    apply(plugin = "java")

    group = "net.schalker.SMPS.modules"
    version = "1.0.0" // fallback — overridden per-module from module.yml

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://repo.lucko.me/") { name = "lucko" }
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") { name = "placeholderapi" }
        maven("https://repo.codemc.io/repository/maven-releases/") { name = "codemc-releases" }
    }

    dependencies {
        // Core framework — modules compile against the DoAPI sources, not a prebuilt JAR
        if (project.name != "DoAPI") {
            "compileOnly"(project(":DoAPI"))
        }
        // Paper API (Folia 26.1.2 — latest stable build)
        "compileOnly"("io.papermc.paper:paper-api:26.1.2.build.72-stable")
        // LuckPerms API
        "compileOnly"("net.luckperms:api:5.4")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.jar {
        archiveBaseName.set(project.name)
        // Lazy provider — resolved after afterEvaluate sets the real version from module.yml
        archiveVersion.set(provider { project.version.toString() })
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

// =====================================================================
// DoAPI — the core plugin JAR (net.schalker.DoAPI)
// Standard src/main/java layout; bundles its runtime libraries.
// =====================================================================
project(":DoAPI") {
    apply(plugin = "com.gradleup.shadow")

    group = "net.schalker"
    version = "2.0.0"

    dependencies {
        "implementation"("com.zaxxer:HikariCP:6.2.1") {
            exclude(group = "org.slf4j")
        }
        "implementation"("com.h2database:h2:2.3.232")
        "implementation"("org.mariadb.jdbc:mariadb-java-client:3.5.1") {
            exclude(group = "org.slf4j")
            exclude(group = "com.github.waffle")
        }
        "implementation"("com.mysql:mysql-connector-j:9.1.0") {
            exclude(group = "com.google.protobuf")
        }
        "implementation"("org.xerial:sqlite-jdbc:3.47.1.0") {
            exclude(group = "org.slf4j")
        }
        // Paper already exposes slf4j-api to plugins — do not bundle it.
        "compileOnly"("org.slf4j:slf4j-api:2.0.16")
    }

    tasks.processResources {
        val tokens = mapOf("version" to project.version.toString())
        inputs.properties(tokens)
        filesMatching("paper-plugin.yml") { expand(tokens) }
    }

    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("DoAPI")
        archiveVersion.set(provider { project.version.toString() })
        archiveClassifier.set("")
        destinationDirectory.set(project.layout.buildDirectory.dir("libs"))

        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("module-info.class")
    }

    tasks.jar {
        actions.clear()
        dependsOn("shadowJar")
        doLast { /* shadowJar already produced the output */ }
    }
}

// =====================================================================
// Source-set configuration per layout pattern
// =====================================================================

// ─── Pattern A: source files directly in module root ────────────────
//     SM_Module/SomeClass.java
//     SM_Module/commands/...
//     SM_Module/resources/config.yml
// ─────────────────────────────────────────────────────────────────────
val flatModules = listOf(
    "SM_Accounts",
    "SM_Announces",
    "SM_AdminList",
    "SM_Alert",
    "SM_AutoReplenish",
    "SM_Checker",
    "SM_Cosmetics",
    "SM_Crowns",
    "SM_DebugStick",
    "SM_Essentials",
    "SM_FastLeaves",
    "SM_Flags",
    "SM_Hat",
    "SM_Help",
    "SM_ItemMeta",
    "SM_Lightcraft",
    "SM_PlayerHeads",
    "SM_QuietBan",
    "SM_Spit",
    "SM_Stats",
    "SM_TrafficOptimizer",
    "SM_UserInfo",
    "SM_KeepInventory",
    "SM_StreamerMode",
    "SM_Voodoos",
    "SM_StonecutterAdditions",
    "SM_Marry",
    "SM_Scale",
    "SM_Invsee",
    "SM_Clans",
    "SM_TrollItems",
    "SM_ItemDespawn",
    "SM_PhaseGuard"
)

flatModules.forEach { name ->
    project(":$name") {
        sourceSets {
            main {
                java {
                    setSrcDirs(listOf(project.projectDir))
                    exclude("resources/**", "build/**", "bin/**", ".gradle/**", "gradle/**", "net/**")
                }
                resources {
                    setSrcDirs(listOf("${project.projectDir}/resources"))
                }
            }
        }
    }
}

// ─── Pattern B: standard Maven/Gradle layout (src/main/java) ───────
//     SM_Module/src/main/java/...
//     SM_Module/src/main/resources/...
// ─────────────────────────────────────────────────────────────────────
// SM_Example uses the default layout so it needs no sourceSets override.

// =====================================================================
// Per-module extras (PlaceholderAPI, etc.)
// =====================================================================
project(":SM_Example") {
    dependencies {
        "compileOnly"("me.clip:placeholderapi:2.11.6")
    }
}

project(":SM_Watcher") {
    dependencies {
        "compileOnly"("com.github.retrooper:packetevents-spigot:2.7.0")
        "compileOnly"("com.google.code.gson:gson:2.11.0")
        "compileOnly"("net.kyori:adventure-text-minimessage:4.18.0")
    }
}

project(":SM_Announces") {
    dependencies {
        "compileOnly"("net.kyori:adventure-text-minimessage:4.18.0")
    }
}

// ─── SM_QuietBan: injects a handler into the player's netty pipeline ─
//     Netty itself ships with the server, so it stays compileOnly.
project(":SM_QuietBan") {
    dependencies {
        "compileOnly"("io.netty:netty-transport:4.2.7.Final")
    }
}

// ─── SM_TrafficOptimizer: ставит фильтр частиц в netty-пайплайн игрока ──
project(":SM_TrafficOptimizer") {
    dependencies {
        "compileOnly"("io.netty:netty-transport:4.2.7.Final")
    }
}

project(":SM_Flags") {
    dependencies {
        "compileOnly"("net.kyori:adventure-text-minimessage:4.18.0")
    }
}

project(":SM_Help") {
    dependencies {
        "compileOnly"("net.kyori:adventure-text-minimessage:4.18.0")
    }
}

project(":SM_StreamerMode") {
    dependencies {
        // StreamerMode targets the SMPS core installed on the server.
        "compileOnly"(files("${rootProject.projectDir}/libs/SMPS.jar"))
    }
}

project(":SM_Clans") {
    dependencies {
        "compileOnly"("me.clip:placeholderapi:2.11.6")
        "compileOnly"("net.kyori:adventure-text-minimessage:4.18.0")
    }
}

// ─── SM_Vanish: TAB API is provided via stubs/ for compilation ──────
//     The real classes come from the TAB plugin at runtime, so the
//     stubs must never end up inside the module JAR.
project(":SM_Vanish") {
    sourceSets {
        main {
            java {
                setSrcDirs(listOf(project.projectDir))
                exclude("resources/**", "build/**", "bin/**", ".gradle/**", "gradle/**", "net/**")
            }
            resources {
                setSrcDirs(listOf("${project.projectDir}/resources"))
            }
        }
    }

    dependencies {
        "compileOnly"("me.clip:placeholderapi:2.11.6")
    }

    tasks.jar {
        exclude("me/neznamy/**")
    }
}

// ─── SM_Accounts: bundles JDA via Shadow JAR ────────────────────────
project(":SM_Accounts") {
    apply(plugin = "com.gradleup.shadow")

    group = "site.deforce"

    dependencies {
        "compileOnly"("me.clip:placeholderapi:2.11.6")
        // JDA for Discord bot integration (bundled in fat JAR)
        "implementation"("net.dv8tion:JDA:5.2.1") {
            exclude(module = "opus-java")
        }
    }

    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("SM_Accounts")
        archiveVersion.set(provider { project.version.toString() })
        archiveClassifier.set("")
        destinationDirectory.set(project.layout.buildDirectory.dir("libs"))

        // Relocate JDA to avoid conflicts with other plugins
        relocate("net.dv8tion", "site.deforce.shaded.jda")
        relocate("okhttp3", "site.deforce.shaded.okhttp3")
        relocate("okio", "site.deforce.shaded.okio")
        relocate("com.neovisionaries", "site.deforce.shaded.neovisionaries")
        relocate("org.apache.commons.collections4", "site.deforce.shaded.commons.collections4")
        relocate("gnu.trove", "site.deforce.shaded.trove")

        // Do NOT use minimize() — JDA loads many classes via reflection/events
    }

    // Replace the standard jar task with shadowJar so :SM_Accounts:jar just works
    tasks.jar {
        actions.clear()
        dependsOn("shadowJar")
        doLast {
            // shadowJar already produced the output — nothing else needed
        }
    }
}

// ─── SM_UCosmetics: UltraCosmetics fork as SMPS module ──────────────
// Source layout: SM_UCosmetics/be/isach/ultracosmetics/...
// Resources:     SM_UCosmetics/resources/...
project(":SM_UCosmetics") {
    apply(plugin = "com.gradleup.shadow")

    repositories {
        maven("https://repo.codemc.io/repository/maven-public/") { name = "codemc" }        // AnvilGUI
        maven("https://maven.enginehub.org/repo/") { name = "enginehub" }                     // WorldGuard/WorldEdit
    }

    // WorldGuard + WorldEdit have strict version constraints that clash with Paper.
    // Force them off so Paper's versions win.
    configurations.all {
        resolutionStrategy {
            force("com.google.guava:guava:33.3.1-jre")
            force("com.google.code.gson:gson:2.11.0")
            force("it.unimi.dsi:fastutil:8.5.15")
        }
    }

    sourceSets {
        main {
            java {
                setSrcDirs(listOf(project.projectDir, "${project.projectDir}/stubs"))
                exclude(
                    "resources/**", "build/**", "bin/**", ".gradle/**", "gradle/**", "libs/**",
                    // Original UltraCosmetics source — reference only, not compiled
                    "UltraCosmetics-main/**",
                    // NMS module — requires server internals, compiled separately or loaded at runtime
                    "be/isach/ultracosmetics/v1_21_R7/**"
                )
            }
            resources {
                setSrcDirs(listOf("${project.projectDir}/resources"))
            }
        }
    }

    dependencies {
        // XSeries — cross-version Bukkit utilities
        "implementation"("com.github.cryptomorin:XSeries:13.1.0")
        // Adventure platform for Bukkit (BukkitAudiences)
        "implementation"("net.kyori:adventure-platform-bukkit:4.3.4")
        "implementation"("net.kyori:adventure-text-minimessage:4.18.0")
        "implementation"("net.kyori:adventure-text-serializer-legacy:4.18.0")
        "implementation"("net.kyori:adventure-text-serializer-plain:4.18.0")
        // AnvilGUI for pet rename
        "implementation"("net.wesjd:anvilgui:1.10.3-SNAPSHOT")
        // WorldGuard + WorldEdit for region-based cosmetic control (compileOnly — runtime optional)
        "compileOnly"("com.sk89q.worldguard:worldguard-bukkit:7.0.12") {
            isTransitive = false
        }
        "compileOnly"("com.sk89q.worldguard:worldguard-core:7.0.12") {
            isTransitive = false
        }
        "compileOnly"("com.sk89q.worldedit:worldedit-bukkit:7.3.10") {
            isTransitive = false
        }
        "compileOnly"("com.sk89q.worldedit:worldedit-core:7.3.10") {
            isTransitive = false
        }
        // MobChip 1.10.1 — pet pathfinding AI (local JAR, shaded into the module JAR)
        "implementation"(files("${rootProject.projectDir}/libs/mobchip.jar"))
        // LibsDisguises is provided via stubs/ for compilation.
        // Actual classes come from server plugins at runtime.
        // PlaceholderAPI (compileOnly — runtime optional)
        "compileOnly"("me.clip:placeholderapi:2.11.6")
    }

    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("SM_UCosmetics")
        archiveVersion.set(provider { project.version.toString() })
        archiveClassifier.set("")
        destinationDirectory.set(project.layout.buildDirectory.dir("libs"))

        // Relocate shaded libs to avoid conflicts
        relocate("com.cryptomorin.xseries", "be.isach.ultracosmetics.shaded.xseries")
        relocate("net.wesjd.anvilgui", "be.isach.ultracosmetics.shaded.anvilgui")

        // Exclude compile-only stubs (real classes provided at runtime by server plugins)
        exclude("me/libraryaddict/**")
    }

    tasks.jar {
        actions.clear()
        dependsOn("shadowJar")
        doLast { /* shadowJar already produced the output */ }
    }
}

// =====================================================================
// build_all — build everything and collect the JARs in one folder
// =====================================================================
// SM_UCosmetics is missing most of the upstream UltraCosmetics source tree
// (be.isach.ultracosmetics.{player,config,cosmetics,listeners,menu,run,version,worldguard}),
// so it cannot compile. It is skipped so one broken module does not block the other 26.
val modulesExcludedFromBuildAll = setOf("SM_UCosmetics", "core")

val buildAll = tasks.register<Sync>("build_all") {
    group = "build"
    description = "Builds DoAPI and every module, collecting the JARs into build/dist"
    into(layout.buildDirectory.dir("dist"))

    val skipped = modulesExcludedFromBuildAll
    doLast {
        skipped.forEach { logger.lifecycle("build_all: skipped $it (excluded in build.gradle.kts)") }
    }
}

subprojects {
    afterEvaluate {
        if (project.name in modulesExcludedFromBuildAll) {
            return@afterEvaluate
        }

        val archive = if (tasks.findByName("shadowJar") != null) {
            tasks.named<Jar>("shadowJar")
        } else {
            tasks.named<Jar>("jar")
        }

        val moduleName = project.name
        buildAll.configure {
            if (moduleName == "DoAPI") {
                from(archive)
            } else {
                into("modules") {
                    from(archive)
                }
            }
        }
    }
}

// =====================================================================
// Read version from each module's module.yml (single source of truth)
// =====================================================================
subprojects {
    afterEvaluate {
        if (project.name == "core") {
            return@afterEvaluate
        }
        // Search for module.yml in the module's resource source sets
        val candidates = listOf(
            file("${projectDir}/resources/module.yml"),          // flat
            file("${projectDir}/${project.name}/resources/module.yml"), // nested
            file("${projectDir}/src/main/resources/module.yml") // standard
        )
        val moduleYml = candidates.firstOrNull { it.exists() }
        if (moduleYml != null) {
            // module.yml is YAML — parse version line manually
            moduleYml.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("version:")) {
                    val ver = trimmed.removePrefix("version:").trim()
                        .removeSurrounding("'").removeSurrounding("\"")
                    if (ver.isNotEmpty()) {
                        project.version = ver
                    }
                }
            }
        }
    }
}
