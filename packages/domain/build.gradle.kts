plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

description = "ProofU domain model and invariants. No framework dependencies."

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    api(platform(libs.spring.boot.bom))

    implementation(libs.uuid.generator)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
