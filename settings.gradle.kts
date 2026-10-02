pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}
stonecutter {
    create(rootProject) {
        version("1.20.1-fabric", "1.20.1").buildscript("build.fabric.gradle")
        version("1.20.1-forge", "1.20.1").buildscript("build.forge.gradle")
        version("1.21.1-neoforge", "1.21.1").buildscript("build.neoforge.gradle")
        vcsVersion = "1.20.1-fabric"
    }
}
rootProject.name = "maris-decoration"
