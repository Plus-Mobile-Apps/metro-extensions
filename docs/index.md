# Metro Extensions

A collection of extensions on top of [Metro DI](https://github.com/ZacSweers/metro) to help reduce boilerplate with dependency injection on Kotlin Multiplatform.

## Extensions

* [Assisted Factory](assisted-factory.md) — bind a user-defined factory interface to an `@AssistedInject` class with a single annotation.

## How it works

Metro is implemented as a Kotlin compiler plugin and does not expose a public FIR/IR extension API. Its only documented third-party integration point is [KSP](https://kotlinlang.org/docs/ksp-overview.html): KSP processors run **before** Metro's plugin, so any Metro-annotated code they generate is picked up natively. Generated types use `@dev.zacsweers.metro.Origin(SourceClass::class)` so Metro's [contribution-merging](https://zacsweers.github.io/metro/latest/aggregation/) exclusions stay aligned with the source type — when the source is excluded from a graph, the generated binding is excluded too.

The extensions in this repository are KSP processors that emit Metro-annotated code on your behalf.

## Setup

Before using any of the Metro extensions, set up your project per the [Metro installation guide](https://zacsweers.github.io/metro/latest/installation/).
