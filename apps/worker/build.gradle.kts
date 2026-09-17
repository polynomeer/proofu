plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

description = "ProofU background worker: posting analysis, matching, document generation, exports"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.jackson)
    implementation(libs.spring.boot.starter.data.jdbc)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    // The worker never migrates in production; tests apply migrations/ to get a schema.
    testImplementation(libs.spring.boot.starter.flyway)
    testRuntimeOnly(libs.flyway.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processTestResources {
    from(rootProject.file("migrations")) {
        into("db/migration")
    }
}

springBoot {
    buildInfo()
}
