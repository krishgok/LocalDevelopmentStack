// Foojay auto-provisions missing JDK toolchains (e.g. the JDK 17 that
// build.gradle.kts:39 requests via jvmToolchain(17)) on runners that only
// have GraalVM 21 installed. Without this plugin the Windows CI job fails
// because Gradle can't find a JDK 17 in the standard discovery paths.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "LocalDevelopmentStack"
