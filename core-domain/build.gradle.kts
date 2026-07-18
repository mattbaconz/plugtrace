plugins {
    `java-library`
}

dependencies {
    // Pure domain — no Minecraft / Paper dependencies.
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "dev.pluglabs.plugtrace.domain.OfflineRestoreFinalizer"
    }
}

tasks.register<JavaExec>("finalizeRestore") {
    group = "plugtrace"
    description = "Offline restore finalize — run with the Minecraft server stopped"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.pluglabs.plugtrace.domain.OfflineRestoreFinalizer")
    doFirst {
        if (!project.hasProperty("plugtraceData")) {
            throw GradleException("Pass -PplugtraceData=<path-to-plugins/PlugTrace> [-PplanId=<id>]")
        }
        val data = project.property("plugtraceData").toString()
        if (project.hasProperty("planId")) {
            args(data, project.property("planId").toString())
        } else {
            args(data)
        }
    }
}
