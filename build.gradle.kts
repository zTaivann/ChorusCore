plugins {
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.hikaricp)
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.mariadb.client)

    // Without this it drags in an ancient Bukkit that would shadow paper-api.
    compileOnly(libs.vault.api) { isTransitive = false }
    compileOnly(libs.placeholder.api) { isTransitive = false }

    // The checks run against the real Paper API and a real SQLite file, so they need
    // the same things at test time that the server provides at runtime.
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
