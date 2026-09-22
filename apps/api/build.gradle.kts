plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

description = "ProofU REST API (/api/v1)"

allOpen {
    annotations("jakarta.persistence.Entity", "jakarta.persistence.MappedSuperclass", "jakarta.persistence.Embeddable")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":ai-gateway"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jackson)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// migrations/ at the repository root is the single source of Flyway SQL.
// It is copied into the jar so the packaged artifact is self-contained.
tasks.processResources {
    from(rootProject.file("migrations")) {
        into("db/migration")
    }
}

springBoot {
    buildInfo()
}
