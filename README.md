# Metro Extensions

A collection of extensions on top of [Metro DI](https://github.com/ZacSweers/metro) to help reduce boilerplate with dependency injection on Kotlin Multiplatform.

## Extensions

* [Assisted Factory](https://plusmobileapps.com/metro-extensions/assisted-factory/) — bind a user-defined factory interface to an `@AssistedInject` class with a single annotation.

## How it works

Metro is implemented as a Kotlin compiler plugin and does not expose a public FIR/IR extension API. Its only documented third-party integration point is **KSP** — KSP processors run before Metro's plugin, so any Metro-annotated code they generate is picked up natively, and `@dev.zacsweers.metro.Origin` keeps contribution-merging exclusions aligned with the source type. The extensions in this repository are KSP processors that emit Metro-annotated code on your behalf.

## Resources

See the [documentation](https://plusmobileapps.com/metro-extensions/) for setup and usage.
