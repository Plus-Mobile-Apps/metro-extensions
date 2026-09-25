# Dependency Graph Extension

An extension that generates a Metro [`@DependencyGraph`](https://zacsweers.github.io/metro/latest/dependency-graphs/) (plus `@SingleIn`) and a top-level `create…()` accessor from a plain marker interface — so a simple, parameterless graph needs no hand-written graph annotations or `@DependencyGraph.Factory`.

## Why?

A Metro graph whose factory takes no arguments still requires a fair amount of ceremony:

```kotlin
@DependencyGraph(AppScope::class)
@SingleIn(AppScope::class)
interface OnboardingDemoComponent {
    val onboardingRootBlocFactory: OnboardingRootBloc.Factory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(): OnboardingDemoComponent
    }
}

val component = createGraphFactory<OnboardingDemoComponent.Factory>().create()
```

The two graph annotations repeat the scope, and the nested parameterless `Factory` exists only so a call site can construct the graph. That's the boilerplate this extension removes.

## Usage

Annotate a plain marker interface with `@AutoDependencyGraph`, passing the scope:

```kotlin
@AutoDependencyGraph(AppScope::class)
interface OnboardingDemoComponent {
    val onboardingRootBlocFactory: OnboardingRootBloc.Factory
}
```

The graph and a typed accessor are generated for you:

```kotlin
val component = createOnboardingDemoComponent()
val bloc = component.onboardingRootBlocFactory.create(/* … */)
```

The marker keeps everything a Metro graph normally would — accessors (`val …Factory`) and `@Provides` default methods are inherited by the generated graph. Use this for graphs whose factory takes no arguments; when the graph needs constructor parameters (for example an Android `Context`), declare Metro's `@DependencyGraph` + `@DependencyGraph.Factory` by hand.

## Setup

The extension publishes a `runtime` artifact (Kotlin Multiplatform, with the annotation) and a `compiler` artifact (JVM, the KSP processor).

Add the dependencies to your version catalog:

```toml
[versions]
metroExtensions = "{version}"

[libraries]
metroExtensions-dependencyGraph-runtime = { module = "com.plusmobileapps.metro-extensions:dependency-graph-runtime", version.ref = "metroExtensions" }
metroExtensions-dependencyGraph-compiler = { module = "com.plusmobileapps.metro-extensions:dependency-graph-compiler", version.ref = "metroExtensions" }
```

Then wire them into the module that owns your `@AutoDependencyGraph`-annotated interfaces. The runtime artifact is multiplatform; the compiler artifact is consumed via KSP for each target you ship to:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    id("dev.zacsweers.metro")
}

dependencies {
    val kspTargets = listOf(
        "kspAndroid",
        "kspJvm",
        "kspIosX64",
        "kspIosArm64",
        "kspIosSimulatorArm64",
    )
    commonMainImplementation(libs.metroExtensions.dependencyGraph.runtime)
    kspTargets.forEach {
        add(it, libs.metroExtensions.dependencyGraph.compiler)
    }
}
```

> KSP generates the `create…()` accessor into each target's source set, so call it from platform code (an Android `Application`, a desktop `main()`, etc.). Code in `commonMain` should reference the marker interface type.

## What gets generated

For the `OnboardingDemoComponent` example above, the processor emits a single Kotlin source file in the marker's own package:

```kotlin
package com.plusmobileapps.chefmate.onboarding.demo

@DependencyGraph(AppScope::class)
@SingleIn(AppScope::class)
internal abstract class OnboardingDemoComponentGraph : OnboardingDemoComponent

public fun createOnboardingDemoComponent(): OnboardingDemoComponent =
    createGraph<OnboardingDemoComponentGraph>()
```

An `abstract class` supertype is used so the generated graph matches Metro's `createGraph<T>()` form; accessors stay abstract for Metro to implement, and any `@Provides` default methods on the marker are inherited.

The marker must be a public interface.
