pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "proofu"

// JVM modules live under apps/ and packages/ next to the pnpm workspaces,
// but are addressed with flat Gradle paths (:domain, :api, :worker).
include(":domain", ":ai-gateway", ":api", ":worker")
project(":domain").projectDir = file("packages/domain")
project(":ai-gateway").projectDir = file("packages/ai-gateway")
project(":api").projectDir = file("apps/api")
project(":worker").projectDir = file("apps/worker")
