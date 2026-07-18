import java.security.MessageDigest

plugins {
    java
}

allprojects {
    group = "dev.pluglabs.plugtrace"
    version = "0.5.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        val java17Modules = setOf("core-domain", "storage-sqlite", "report", "api", "platform-common", "bukkit-modern")
        options.release.set(if (project.name in java17Modules) 17 else 21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<Jar>().configureEach {
        from(rootProject.file("LICENSE")) { into("META-INF") }
        from(rootProject.file("NOTICE")) { into("META-INF") }
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}

tasks.register("printArtifacts") {
    group = "plugtrace"
    description = "List built PlugTrace JAR paths and SHA-256 checksums"
    dependsOn(":paper-modern:build", ":folia:build", ":bukkit-modern:build", ":api:jar")
    doLast {
        val modules = listOf("paper-modern", "folia", "bukkit-modern", "api")
        val digest = MessageDigest.getInstance("SHA-256")
        for (module in modules) {
            val libs = file("$module/build/libs")
            if (!libs.isDirectory) {
                println("$module: (not built)")
                continue
            }
            val jars = libs.listFiles().orEmpty()
                .filter { f -> f.isFile && f.name.endsWith(".jar") && !f.name.contains("-sources") && !f.name.contains("-javadoc") }
                .filter { f -> f.name.contains("-0.5.0.jar") || f.name.endsWith("-0.5.0.jar") }
                .sortedBy { f -> f.name }
            for (jar in jars) {
                digest.reset()
                val hash = digest.digest(jar.readBytes()).joinToString("") { b -> "%02x".format(b) }
                println(jar.absolutePath)
                println("  sha256=$hash")
            }
        }
    }
}

tasks.register("matrixSmoke") {
    group = "plugtrace"
    description = "Phase 4 matrix smoke: unit tests + three fat JARs + claimed tiers"
    dependsOn(
        ":paper-modern:copyWebUi",
        ":core-domain:test",
        ":report:test",
        ":storage-sqlite:test",
        ":platform-common:test",
        ":paper-modern:test",
        ":paper-modern:build",
        ":folia:build",
        ":bukkit-modern:build",
        ":api:jar",
        "printArtifacts"
    )
    doLast {
        val webIndex = file("paper-modern/src/main/resources/web/index.html")
        check(webIndex.isFile) { "Missing bundled web UI at ${webIndex.path}" }
        println("")
        println("=== PlugTrace matrix smoke claims ===")
        println("paper-modern  | Paper-family | dogfood: Paper 1.21.4/J21, 26.1.2/J25, Purpur 1.21.4/J21; soak day-0")
        println("folia         | Folia        | dogfood: 1.21.11/Java 21 observation harness; soak day-0")
        println("bukkit-modern | Bukkit       | Spigot 1.20.1+1.20.4/Java 17 live PASS; Experimental capability set")
        println("pufferfish                   | Unverified (no downloadable jar from farm probes)")
        println("legacy/proxy/modloader       | deferred")
        println("Upgrade notes: DB schema v3, report 1.0.0, product 0.5.0")
        println("Web UI: bundled under paper-modern/src/main/resources/web (copied to folia/bukkit JARs)")
        println("Release checklist: private alpha; marketplace freeze active")
        println("OK - claims must match ARTIFACTS.md and /plugtrace compatibility")
    }
}
