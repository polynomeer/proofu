plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

description = "ProofU document renderer: DOCX/Markdown/JSON from document versions (ADR-0009)."

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(project(":domain"))

    implementation(libs.poi.ooxml)
    implementation(libs.openpdf)
    implementation(libs.jackson.databind)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
