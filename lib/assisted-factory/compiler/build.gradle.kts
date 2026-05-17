import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mavenPublish)
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
    implementation(libs.ksp.api)

    implementation(libs.kotlin.poet)
    implementation(libs.kotlin.poet.ksp)

    implementation(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    testImplementation(platform(libs.junit.jupiter.bom))
    testImplementation(libs.junit.jupiter.core)
    testRuntimeOnly(libs.junit.jupiter.launcher)

    testImplementation(libs.kotlin.compile.testing.core)
    testImplementation(libs.kotlin.compile.testing.ksp)
    testImplementation(libs.kotlin.reflect)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)

    testImplementation(libs.metro.runtime)
}

mavenPublishing {
    coordinates(
        "com.plusmobileapps.metro-extensions",
        "assisted-factory-compiler",
        libs.versions.assistedFactory.get(),
    )

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("Metro Extensions - Assisted Factory Compiler")
        description.set("KSP processor that generates Metro @AssistedFactory bindings for user-defined factory interfaces.")
        inceptionYear.set("2026")
        url.set("https://github.com/plusmobileapps/metro-extensions/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("plusmobileapps")
                name.set("Plus Mobile Apps LLC")
                url.set("https://github.com/plusmobileapps/")
            }
        }
        scm {
            url.set("https://github.com/plusmobileapps/metro-extensions/")
            connection.set("scm:git:git://github.com/plusmobileapps/metro-extensions.git")
            developerConnection.set("scm:git:ssh://git@github.com/plusmobileapps/metro-extensions.git")
        }
    }
}

val signingTasks = tasks.withType<Sign>()
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(signingTasks)
}
