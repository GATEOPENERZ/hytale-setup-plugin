plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.0.0"
}


group = "io.github.gateopenerz"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.1")
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