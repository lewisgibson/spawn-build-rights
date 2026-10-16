plugins {
    // Lets Gradle auto-provision the Java 25 toolchain (via the Foojay Disco API) on any
    // machine or CI runner that does not already have a JDK 25 installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "SpawnBuildRights"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "PaperMC"
        }
    }
}
