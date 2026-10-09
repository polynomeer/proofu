plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "com.proofu"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // ktlint parses with the Kotlin compiler it was built against. Spring's dependency
    // management would align that compiler to the project's Kotlin version on modules that
    // apply it (api, worker), and a newer compiler fails to parse ("Extensions storage is not
    // registered"). Keep ktlint's own classpath at the versions it asks for.
    // Registered after evaluation so this rule runs after the dependency-management plugin's.
    afterEvaluate {
        configurations.matching { it.name.startsWith("ktlint") }.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin" && requested.version != null) {
                    useVersion(requested.version!!)
                }
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
