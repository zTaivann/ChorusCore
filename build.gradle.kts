plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly(libs.paper.api)

    // Shaded in below: bStats refuses to run from its own package name.
    implementation(libs.bstats)
    implementation(libs.chorus.commons)
    compileOnly(libs.hikaricp)
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.mariadb.client)

    // Without this it drags in an ancient Bukkit that would shadow paper-api.
    compileOnly(libs.vault.api) { isTransitive = false }
    compileOnly(libs.placeholder.api) { isTransitive = false }

    // Tests need what the server provides at runtime.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.paper.api)
    testImplementation(libs.hikaricp)
    testRuntimeOnly(libs.sqlite.jdbc)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
    options.compilerArgs.add("-Xlint:all")
}

tasks.processResources {
    val tokens = mapOf(
        "version" to project.version.toString(),
        "hikari" to libs.versions.hikari.get(),
        "sqlite" to libs.versions.sqlite.get(),
        "mariadb" to libs.versions.mariadb.get(),
    )
    inputs.properties(tokens)
    filesMatching("plugin.yml") { expand(tokens) }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

/** Shades bStats and chorus-commons into the jar, relocated so plugins don't clash. */
tasks.shadowJar {
    archiveClassifier = ""

    // Drivers are fetched by the server; everything else is compile-time only.
    dependencies {
        exclude { it.moduleGroup != "org.bstats" && it.moduleGroup != "dev.chorus" }
    }
    relocate("org.bstats", "dev.chorus.core.libs.bstats")
    relocate("dev.chorus.commons", "dev.chorus.core.libs.commons")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier = "plain"
}

/**
 * Compiles the sources against the newest Paper API to catch removed or renamed methods.
 * Needs JDK 25 and is not part of `build`.
 */
val modernApi: Configuration = configurations.create("modernApi")

dependencies {
    modernApi(libs.modern.paper.api)
    // Newer Paper no longer provides these; compile-time only.
    modernApi(libs.annotations)
    modernApi(libs.bstats)
    modernApi(libs.chorus.commons)
    modernApi(libs.hikaricp)
    modernApi(libs.sqlite.jdbc)
    modernApi(libs.mariadb.client)
    modernApi(libs.vault.api) { isTransitive = false }
    modernApi(libs.placeholder.api) { isTransitive = false }
}

tasks.register<JavaCompile>("apicheck") {
    group = "verification"
    description = "Compiles the same sources against the newest Paper API."

    source = sourceSets.main.get().allJava
    classpath = modernApi
    destinationDirectory = layout.buildDirectory.dir("apicheck")
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(25)
    }

    options.encoding = "UTF-8"
    // Use the toolchain's own release: Java 17 can't read the new API's class files.
    options.release = null
    options.compilerArgs = listOf("-Xlint:all")
}


// The Folia signatures themselves are checked in chorus-commons, by its `foliacheck`.
