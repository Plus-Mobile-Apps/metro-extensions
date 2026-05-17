import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    jvm()

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    macosArm64()

    watchosArm64()
    watchosSimulatorArm64()

    tvosArm64()
    tvosSimulatorArm64()

    applyDefaultHierarchyTemplate()
}

android {
    namespace = "com.plusmobileapps.metro.extensions.assistedfactory.runtime"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

mavenPublishing {
    coordinates(
        "com.plusmobileapps.metro-extensions",
        "assisted-factory-runtime",
        libs.versions.assistedFactory.get(),
    )

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("Metro Extensions - Assisted Factory Runtime")
        description.set("Runtime marker annotations for the Metro assisted-factory KSP extension.")
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
