import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.metro)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":lib:assisted-factory:runtime"))
    ksp(project(":lib:assisted-factory:compiler"))

    testImplementation(platform(libs.junit.jupiter.bom))
    testImplementation(libs.junit.jupiter.core)
    testRuntimeOnly(libs.junit.jupiter.launcher)
    testImplementation(libs.kotest.assertions.core)
}
