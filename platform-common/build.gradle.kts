plugins {
    `java-library`
}

dependencies {
    api(project(":core-domain"))
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
}
