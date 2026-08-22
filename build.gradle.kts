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
//     CM_Module/SomeClass.java
//     CM_Module/commands/...
//     CM_Module/resources/config.yml
// ─────────────────────────────────────────────────────────────────────
val flatModules = listOf(
    "CM_Accounts",
    "CM_Announces",
    "CM_AdminList",
    "CM_Alert",
    "CM_AutoReplenish",
    "CM_Checker",
    "CM_Cosmetics",
    "CM_Crowns",
    "CM_DebugStick",
    "CM_Essentials",
    "CM_FastLeaves",
    "CM_Flags",
    "CM_Hat",
    "CM_Help",
    "CM_ItemMeta",
    "CM_Lightcraft",
    "CM_PlayerHeads",
    "CM_QuietBan",
    "CM_Spit",
    "CM_Stats",
    "CM_TrafficOptimizer",
    "CM_UserInfo",
    "CM_KeepInventory",
    "CM_StreamerMode",
    "CM_Voodoos",
    "CM_StonecutterAdditions",
    "CM_Marry",
    "CM_Scale",
    "CM_Invsee",
    "CM_Clans",
    "CM_TrollItems",
    "CM_ItemDespawn",
    "CM_PhaseGuard"
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
//     CM_Module/src/main/java/...
//     CM_Module/src/main/resources/...
// ─────────────────────────────────────────────────────────────────────
// CM_Example uses the default layout so it needs no sourceSets override.

// =====================================================================
// Per-module extras (PlaceholderAPI, etc.)
// =====================================================================
project(":CM_Example") {
    dependencies {
        "compileOnly"("me.clip:placeholderapi:2.11.6")
    }
}

project(":CM_Watcher") {
    dependencies {
        "compileOnly"("com.github.retrooper:packetevents-spigot:2.7.0")
        "compileOnly"("com.google.code.gson:gson:2.11.0")
        "compileOnly"("net.kyori:adventure-text-minimessage:4.18.0")
    }
}

project(":CM_Announces") {
    dependencies {
        "compileOnly"("net.kyori:adventure-text-minimessage:4.18.0")
    }
}

// ─── CM_QuietBan: injects a handler into the player's netty pipeline ─
//     Netty itself ships with the server, so it stays compileOnly.
project(":CM_QuietBan") {
    dependencies {
        "compileOnly"("io.netty:netty-transport:4.2.7.Final")
    }
}

// ─── CM_TrafficOptimizer: ставит фильтр частиц в netty-пайплайн игрока ──
project(":CM_TrafficOptimizer") {
    dependencies {
        "compileOnly"("io.netty:netty-transport:4.2.7.Final")
    }
}

project(":CM_Flags") {
    dependencies {
        "compileOnly"("net.kyori:adventure-text-minimessage:4.18.0")
    }
}

project(":CM_Help") {
    dependencies {
        "compileOnly"("net.kyori:adventure-text-minimessage:4.18.0")
    }
}

project(":CM_StreamerMode") {
    dependencies {
        // StreamerMode targets the SMPS core installed on the server.
        "compileOnly"(files("${rootProject.projectDir}/libs/SMPS.jar"))
    }
}

project(":CM_Clans") {
    dependencies {
        "compileOnly"("me.clip:placeholderapi:2.11.6")
        "compileOnly"("net.kyori:adventure-text-minimessage:4.18.0")
    }
}

// ─── CM_Vanish: TAB API is provided via stubs/ for compilation ──────
//     The real classes come from the TAB plugin at runtime, so the
//     stubs must never end up inside the module JAR.
project(":CM_Vanish") {
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

// ─── CM_Accounts: bundles JDA via Shadow JAR ────────────────────────
project(":CM_Accounts") {
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
        archiveBaseName.set("CM_Accounts")
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

    // Replace the standard jar task with shadowJar so :CM_Accounts:jar just works
    tasks.jar {
        actions.clear()
        dependsOn("shadowJar")
        doLast {
            // shadowJar already produced the output — nothing else needed
        }
    }
}

// =====================================================================
// build_all — build everything and collect the JARs in one folder
// =====================================================================
val modulesExcludedFromBuildAll = setOf("core")

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
