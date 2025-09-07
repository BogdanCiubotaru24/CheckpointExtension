plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.0.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.typewritermc.com/beta")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.typewritermc:engine-paper:0.9.0")
    compileOnly("com.github.retrooper.packetevents:api:2.2.1")
    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
}

typewriter {
    namespace = "gamesofeducation"
    extension {
        name = "Checkpoint"
        shortDescription = "Save and restore player fact checkpoints"
        description = """
            Adds actions to snapshot all of a player's facts into an artifact and later fully replace a player's facts with that snapshot.
            Checkpoints are global artifacts which can be applied by admins to any player.
        """.trimIndent()
        engineVersion = "0.9.0-beta-165"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        paper()
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}



tasks.jar {
    archiveBaseName.set("CheckpointExtension")
    archiveFileName.set("CheckpointExtension.jar")
}