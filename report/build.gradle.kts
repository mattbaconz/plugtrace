plugins {
    `java-library`
}

dependencies {
    api(project(":core-domain"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
}
