plugins {
    `java-library`
}

dependencies {
    api(project(":core-domain"))
    // Classes only + desktop OS natives (Android/FreeBSD/musl omitted).
    // Arch trimming for Linux/Windows extras happens in paper-modern shadowJar.
    implementation("org.xerial:sqlite-jdbc:3.53.2.1:without-natives")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1:natives-windows")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1:natives-linux")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1:natives-mac")
    // Gson is provided by Spigot/Paper at runtime; keep for unit tests + compile.
    compileOnly("com.google.code.gson:gson:2.11.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
}
