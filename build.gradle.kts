plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("kapt") version "1.9.22"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.graalvm.buildtools.native") version "0.10.1"
}

group = "com.localdevstack"
version = "1.2.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("info.picocli:picocli:4.7.5")
    kapt("info.picocli:picocli-codegen:4.7.5")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
    // @TempDir reads java.io.tmpdir. Point it inside the project so paths
    // satisfy LocalDevStackCli's CWD safety check.
    val testTmp = layout.buildDirectory.dir("test-tmp").get().asFile
    doFirst { testTmp.mkdirs() }
    systemProperty("java.io.tmpdir", testTmp.absolutePath)
}

application {
    mainClass.set("com.localdevstack.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("localdevstack")
            mainClass.set("com.localdevstack.MainKt")
            buildArgs.addAll(
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "--initialize-at-build-time=kotlin",
                "--initialize-at-build-time=picocli"
            )
        }
    }
}
