# Assisted Factory Extension

An extension that generates a Metro [`@AssistedFactory`](https://zacsweers.github.io/metro/latest/injection-types/#assisted-injection) and a `@ContributesBinding` bridge class so that a user-defined factory interface is wired into your dependency graph automatically.

## Why?

Metro's first-party assisted injection requires you to declare both an `@AssistedInject` constructor and an `@AssistedFactory` interface that returns the **concrete** implementation:

```kotlin
@AssistedInject
class RealMovieRepository(
    @Assisted val id: String,
) : MovieRepository {
    @AssistedFactory
    fun interface MetroFactory {
        fun create(id: String): RealMovieRepository
    }
}
```

That works, but most call sites want to consume the **interface** (`MovieRepository.Factory`) rather than the concrete factory, both for ergonomics and for testability. Wiring that up by hand means writing a bridge class:

```kotlin
@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealMovieRepositoryFactory(
    private val metroFactory: RealMovieRepository.MetroFactory,
) : MovieRepository.Factory {
    override fun create(id: String): MovieRepository = metroFactory.create(id)
}
```

That's the boilerplate this extension removes.

## Usage

Annotate your `@AssistedInject` class with `@ContributesAssistedFactory`, pointing at your user-defined factory interface:

```kotlin
interface MovieRepository {
    fun get(): Movie

    interface Factory {
        fun create(id: String): MovieRepository
    }
}

@AssistedInject
@ContributesAssistedFactory(
    scope = AppScope::class,
    assistedFactory = MovieRepository.Factory::class,
)
class RealMovieRepository(
    @Assisted val id: String,
) : MovieRepository {
    override fun get(): Movie = TODO()
}
```

Both the Metro `@AssistedFactory` and the bridge class are generated for you, and `MovieRepository.Factory` becomes available on any graph that includes `AppScope`:

```kotlin
@Inject
class MovieDetailViewModel(
    private val factory: MovieRepository.Factory,
) {
    fun load(id: String) {
        val repository: MovieRepository = factory.create(id)
    }
}
```

The generated bridge is annotated with `@Origin(RealMovieRepository::class)`, so excluding `RealMovieRepository` from a Metro graph (for example in a test graph) also excludes the generated binding automatically.

## Setup

The extension publishes a `runtime` artifact (Kotlin Multiplatform, with the annotation) and a `compiler` artifact (JVM, the KSP processor).

Add the dependencies to your version catalog:

```toml
[versions]
metroExtensions = "{version}"

[libraries]
metroExtensions-assistedFactory-runtime = { module = "com.plusmobileapps.metro-extensions:assisted-factory-runtime", version.ref = "metroExtensions" }
metroExtensions-assistedFactory-compiler = { module = "com.plusmobileapps.metro-extensions:assisted-factory-compiler", version.ref = "metroExtensions" }
```

Then wire them into the module that owns your `@ContributesAssistedFactory`-annotated classes. The runtime artifact is multiplatform; the compiler artifact is consumed via KSP for each target you ship to:

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
    commonMainImplementation(libs.metroExtensions.assistedFactory.runtime)
    kspTargets.forEach {
        add(it, libs.metroExtensions.assistedFactory.compiler)
    }
}
```

## What gets generated

For the `RealMovieRepository` example above, the processor emits a single Kotlin source file containing two declarations:

```kotlin
package com.plusmobileapps.metro.extensions.assistedfactory.generated

@AssistedFactory
public fun interface <Source>MetroFactory {
    public fun create(id: String): RealMovieRepository
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Origin(RealMovieRepository::class)
public class <Source>(
    private val metroFactory: <Source>MetroFactory,
) : MovieRepository.Factory {
    override fun create(id: String): MovieRepository = metroFactory.create(id)
}
```

The `<Source>` name is derived from the source class's fully-qualified name so generated types do not collide across packages.
