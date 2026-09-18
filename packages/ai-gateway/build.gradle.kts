plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

description = "ProofU AI Gateway: the single path to model providers (ADR-0005, ADR-0008)."

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

    implementation(libs.anthropic.java)
    implementation(libs.json.schema.validator)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    // JdbcTemplate for the budget guard and execution recorder; no Spring Boot, no context scanning.
    implementation(libs.spring.jdbc)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
