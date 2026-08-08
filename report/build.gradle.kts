plugins {
    `java-library`
}

dependencies {
    api(project(":core-domain"))
    compileOnly("com.google.code.gson:gson:2.11.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
}
