allprojects {
    group = "net.myserver"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

tasks.register("build") {
    group = "build"
    description = "Assembles the Crystall Core server Fat JAR with all 35 modules."
    dependsOn(":core:jar")
}

tasks.register("run") {
    group = "application"
    description = "Runs the Crystall Core Minecraft server."
    dependsOn(":core:run")
}
