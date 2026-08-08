plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":storage-sqlite"))
    implementation(project(":report"))
    implementation(project(":api"))
    implementation(project(":platform-common"))
    implementation("org.bstats:bstats-bukkit:3.1.0")
    // Unified jar: Spigot API + Adventure compiles to Java 17 and loads on
    // Paper / Purpur / Folia / Spigot 1.20.x (Paper API 1.21 requires JVM 21 to compile).
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    compileOnly("net.kyori:adventure-text-serializer-plain:4.17.0")
    compileOnly("net.kyori:adventure-text-serializer-ansi:4.17.0")
    compileOnly("net.kyori:ansi:1.1.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    testImplementation("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("com.google.code.gson:gson:2.11.0")
}

val webUiDir = rootProject.layout.projectDirectory.dir("web-ui")
val webResourcesDir = layout.projectDirectory.dir("src/main/resources/web")

tasks.register<Exec>("buildWebUi") {
    group = "plugtrace"
    description = "Build React web UI with pnpm"
    workingDir = webUiDir.asFile
    commandLine(
        if (System.getProperty("os.name").lowercase().contains("windows")) "pnpm.cmd" else "pnpm",
        "run",
        "build"
    )
    onlyIf { webUiDir.asFile.resolve("package.json").exists() }
}

tasks.register<Delete>("cleanWebUiResources") {
    group = "plugtrace"
    delete(webResourcesDir.asFile)
}

tasks.register<Copy>("copyWebUi") {
    group = "plugtrace"
    description = "Copy web-ui/dist into paper-modern resources/web"
    dependsOn("buildWebUi", "cleanWebUiResources")
    from(webUiDir.dir("dist"))
    into(webResourcesDir)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("copyWebUi")
}

tasks.named<Jar>("sourcesJar") {
    dependsOn("copyWebUi")
}

val keptSqliteNatives = setOf(
    "org/sqlite/native/Windows/x86_64/",
    "org/sqlite/native/Linux/x86_64/",
    "org/sqlite/native/Linux/aarch64/",
    "org/sqlite/native/Mac/x86_64/",
    "org/sqlite/native/Mac/aarch64/",
)

tasks.shadowJar {
    archiveBaseName.set("PlugTrace")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn("copyWebUi")
    relocate("org.bstats", "dev.pluglabs.plugtrace.libs.bstats")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    // Drop uncommon sqlite JNI arches (keeps Win/Linux/macOS x86_64 + Linux/macOS aarch64).
    exclude { element ->
        val path = element.relativePath.pathString.replace('\\', '/')
        path.startsWith("org/sqlite/native/") && keptSqliteNatives.none { path.startsWith(it) }
    }
}

// Keep `:paper-modern:jar` as the release entrypoint used by scripts/docs.
tasks.jar {
    enabled = false
    archiveBaseName.set("PlugTrace")
    dependsOn(tasks.shadowJar)
}

tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}
