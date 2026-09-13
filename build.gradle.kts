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

/**
 * bStats is the one thing that travels inside the jar. It is moved into this plugin's own
 * package on the way in, which is what keeps two plugins carrying different versions of it
 * from fighting over the same classes.
 */
tasks.shadowJar {
    archiveClassifier = ""

    // Nothing but bStats travels inside the jar. The drivers are fetched by the server at
    // startup and the rest is compile-time only.
    dependencies {
        exclude { it.moduleGroup != "org.bstats" }
    }
    relocate("org.bstats", "dev.chorus.core.libs.bstats")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier = "plain"
}

/**
 * The jar is built against 1.18.2 so that it runs on everything from there upwards, which
 * also means the compiler never sees what became of an API since. `apicheck` compiles the
 * same sources against the newest Paper release: a method that has been removed or renamed
 * in the years between fails here rather than on somebody's server. It produces nothing,
 * needs a JDK 25, and is deliberately not part of `build`.
 */
val modernApi: Configuration = configurations.create("modernApi")

dependencies {
    modernApi(libs.modern.paper.api)
    // 1.18.2 handed these down with the API; newer Paper does not. They are compile-time
    // only either way, so nothing about the jar changes.
    modernApi(libs.annotations)
    modernApi(libs.bstats)
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
    // Whatever that compiler's own release is: the point is to read the new API, and the
    // 17 the jar is built for cannot read class files written by 25.
    options.release = null
    options.compilerArgs = listOf("-Xlint:all")
}


/**
 * The Folia bridge reaches its schedulers by reflection, so no compiler ever sees those
 * signatures: a wrong name or one argument too many is invisible until a server starts, and
 * on Folia it does not even fail near the mistake. This resolves every one of them against
 * the newest Paper API and fails if any has moved.
 *
 * It needs its own JDK because those class files are written for 25, which a 21 cannot read,
 * and it hangs off `apicheck` because that is the other check that reads the new API. `build`
 * stays on a JDK 21 and leaves this one out.
 */
val foliacheck = tasks.register<Test>("foliacheck") {
    group = "verification"
    description = "Resolves the Folia scheduler signatures against the newest Paper API."

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*FoliaCallTest") }
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }

    inputs.files(modernApi)
    doFirst {
        systemProperty("chorus.modern.api", modernApi.asPath)
    }
    testLogging { events("passed", "failed") }
}

tasks.test {
    // Left to foliacheck, which has a JDK that can read the class files it needs.
    filter { excludeTestsMatching("*FoliaCallTest") }
}

tasks.named("apicheck") {
    finalizedBy(foliacheck)
}
