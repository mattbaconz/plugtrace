plugins {
    java
}

evaluationDependsOn(":paper-modern")

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":storage-sqlite"))
    implementation(project(":report"))
    implementation(project(":api"))
    implementation(project(":platform-common"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
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
    archiveBaseName.set("PlugTrace-folia")
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
