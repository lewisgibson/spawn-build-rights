import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    `java-library`
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.jmh)
}

group = "com.lewisgibson"
// The Minecraft version is read from the Paper API pin so the two cannot drift: 26.3.build.194-stable
// gives 26.3.
val gameVersion =
    libs.versions.paperApi
        .get()
        .substringBefore(".build")
// The plugin version, `<semver>+mc<minecraft version>`, set here and nowhere else. The build
// writes it into plugin.yml and the jar's name, and a push to main whose version has no GitHub
// release yet publishes it (docs/releasing.md).
version = "1.0.0+mc$gameVersion"

// Prints the version, for the release workflow.
tasks.register("printVersion") {
    val projectVersion = project.version.toString()
    doLast { println(projectVersion) }
}
description = "Extends vanilla spawn protection so players earn the right to build at spawn by playing"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// A dedicated source set for integration tests, which boot a mocked Paper server (MockBukkit)
// and exercise the Bukkit adapter end to end. Kept separate from the fast, pure-JVM unit tests.
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

// The server tests boot a REAL Paper server in Docker with the built jar installed. Two more
// source sets: `serverTestFixture` is a tiny second plugin, loaded beside the real one, whose
// only job is to fire a real block event for a fake player over RCON so the gate can be
// observed from outside; `serverTest` is the JUnit harness that drives Docker and RCON.
// Neither touches the plugin's own code.
sourceSets {
    create("serverTestFixture")
    create("serverTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations["serverTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["serverTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    // The server API is provided by the runtime; never bundle it.
    compileOnly(libs.paper.api)
    // Paper provides these at runtime: SQLite from the server itself, the rest through the
    // plugin.yml `libraries:` the build fills in below.
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.hikaricp)
    compileOnly(libs.postgresql)
    compileOnly(libs.mariadb.jdbc)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
    // The store's unit tests run against real SQLite files, through the pool as well. PostgreSQL,
    // MySQL and MariaDB are run for real in the server tests.
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.hikaricp)
    testImplementation(libs.postgresql)
    testImplementation(libs.mariadb.jdbc)

    // compileOnly does not propagate to test classpaths, so the integration tests need the API
    // (for org.bukkit.* types) alongside MockBukkit.
    // It is the 26.2 build MockBukkit's registry data matches, not the 26.3 the jar compiles
    // against: there is no mockbukkit-v26.3 yet (gradle/libs.versions.toml).
    "integrationTestImplementation"(libs.paper.api.mockbukkit)
    "integrationTestImplementation"(libs.mockbukkit)

    "serverTestFixtureCompileOnly"(libs.paper.api)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val integrationTest =
    tasks.register<Test>("integrationTest") {
        description = "Runs integration tests against a mocked Paper server (MockBukkit)."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        shouldRunAfter(tasks.test)
    }

// ---- Coverage ----------------------------------------------------------------
// The whole plugin is held to 100% line and branch coverage: `core` and `storage` by the unit
// tests (the store against a real SQLite file) and the Bukkit adapter by the MockBukkit
// integration tests. The two runs' execution data is merged, so a line counts as covered when
// either suite runs it. Nothing is excluded: code MockBukkit cannot run is reached through a small
// seam instead. The JMH and server-test source sets are not part of the gate.
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val coverageData =
    files(
        layout.buildDirectory.file("jacoco/test.exec"),
        layout.buildDirectory.file("jacoco/integrationTest.exec"),
    )

tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData.setFrom(coverageData)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, integrationTest)
    executionData.setFrom(coverageData)
    violationRules {
        rule {
            element = "PACKAGE"
            limit {
                counter = "LINE"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestCoverageVerification)
}

// ---- Server tests (Docker) ---------------------------------------------------
// `./gradlew serverTest` boots real Paper 26.3 servers in Docker (one per storage backend, plus
// a Postgres container) with the built jar and the fixture plugin installed, and observes the
// gate over RCON. Minutes, not seconds, and it needs Docker, so it is deliberately not part of
// `check`; see CONTRIBUTING.md, "Build and test".
val fixtureJar =
    tasks.register<Jar>("fixtureJar") {
        description = "Builds the test-only fixture plugin loaded beside the real one in server tests."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        archiveBaseName = "SpawnBuildRightsTestFixture"
        from(sourceSets["serverTestFixture"].output)
    }

val serverTest =
    tasks.register<Test>("serverTest") {
        description = "Boots real Paper servers in Docker and observes the gate over RCON."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = sourceSets["serverTest"].output.classesDirs
        classpath = sourceSets["serverTest"].runtimeClasspath
        dependsOn(tasks.jar, fixtureJar)
        // Never cached or skipped: the point is to run against today's Docker and today's jar.
        outputs.upToDateWhen { false }
        systemProperty(
            "sbr.pluginJar",
            tasks.jar
                .get()
                .archiveFile
                .get()
                .asFile.absolutePath,
        )
        systemProperty(
            "sbr.fixtureJar",
            fixtureJar
                .get()
                .archiveFile
                .get()
                .asFile.absolutePath,
        )
        systemProperty("sbr.version", project.version.toString())
        systemProperty(
            "sbr.workDir",
            layout.buildDirectory
                .dir("server-test")
                .get()
                .asFile.absolutePath,
        )
        testLogging {
            showStandardStreams = true
        }
    }

// ---- Formatting --------------------------------------------------------------
spotless {
    java {
        // AOSP variant keeps 4-space indentation (the default google style is 2-space).
        googleJavaFormat(libs.versions.googleJavaFormat.get()).aosp()
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

// ---- Packaging ---------------------------------------------------------------
// plugin.yml gets the plugin version and the library versions from here, so each is set once.
tasks.processResources {
    val tokens =
        mapOf(
            "version" to project.version.toString(),
            "hikari" to libs.versions.hikaricp.get(),
            "postgresql" to libs.versions.postgresql.get(),
            "mariadb" to libs.versions.mariadbJdbc.get(),
        )
    inputs.properties(tokens)
    filesMatching("plugin.yml") {
        expand(tokens)
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
}

// ---- Local test server -------------------------------------------------------
// `./gradlew runServer` boots a real Paper 26.3 server with the built plugin installed,
// for manual and multi-platform smoke testing (MockBukkit cannot see loader/Folia differences).
tasks.runServer {
    minecraftVersion(gameVersion)
}

// ---- Benchmarks --------------------------------------------------------------
// `./gradlew jmh` runs the JMH microbenchmarks in src/jmh over the hot-path core logic
// (the per-event build decision). Not part of `build` - benchmarking is on demand.
jmh {
    jmhVersion = libs.versions.jmh.get()
    warmupIterations = 3
    iterations = 5
    fork = 1
}
