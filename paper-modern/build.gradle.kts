plugins {
    java
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":storage-sqlite"))
    implementation(project(":report"))
    implementation(project(":api"))
    implementation(project(":platform-common"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
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
    mustRunAfter("copyWebUi")
}

tasks.jar {
    archiveBaseName.set("PlugTrace")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
