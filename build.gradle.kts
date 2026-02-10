plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "io.github.gateopenerz"
version = "1.0.6"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gson)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    website.set("https://github.com/gateopenerz/hytale-setup-plugin")
    vcsUrl.set("https://github.com/gateopenerz/hytale-setup-plugin.git")
    plugins {
        create("hytaleSetupServer") {
            id = "io.github.gateopenerz.hytale-server"
            implementationClass = "com.gateopenerz.hytaleserver.HytaleServerPlugin"
            displayName = "Hytale Server Setup Plugin"
            description = "Downloads & runs Hytale Server (Release/Pre-release)"
            tags.set(listOf("hytale", "server"))
        }
    }
}