plugins {
    java
}

evaluationDependsOn(":paper-modern")

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":storage-sqlite"))
    implementation(project(":report"))
    implementation(project(":api"))
    implementation(project(":platform-common"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    // Adventure MiniMessage is paper-bundled; Spigot API alone does not expose it for shared paper sources.
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    compileOnly("net.kyori:adventure-text-serializer-plain:4.17.0")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

sourceSets {
    main {
        java {
            srcDir(project(":paper-modern").file("src/main/java"))
        }
    }
}

tasks.jar {
    archiveBaseName.set("PlugTrace-bukkit-modern")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(":paper-modern:copyWebUi")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(project(":paper-modern").file("src/main/resources/web")) { into("web") }
}
