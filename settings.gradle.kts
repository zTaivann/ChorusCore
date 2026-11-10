plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ChorusCore"

// Shared sources, shaded into the jar and relocated; see chorus-commons/build.gradle.kts.
includeBuild("../chorus-commons")
