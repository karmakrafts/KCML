import java.time.Duration

pluginManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

nmcpSettings {
    providers.environmentVariable("OSSRH_USERNAME").orNull?.let { username ->
        centralPortal {
            uploadSnapshotsParallelism = Runtime.getRuntime().availableProcessors() * 4
            this.username = username
            password = providers.environmentVariable("OSSRH_PASSWORD").get()
            validationTimeout = Duration.ofMinutes(30)
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include("kcml-gradle-api")