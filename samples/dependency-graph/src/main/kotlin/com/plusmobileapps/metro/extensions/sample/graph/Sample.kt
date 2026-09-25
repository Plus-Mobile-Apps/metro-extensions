package com.plusmobileapps.metro.extensions.sample.graph

import com.plusmobileapps.metro.extensions.graph.AutoDependencyGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject

@Inject
class Greeter {
    fun greet(): String = "hello"
}

/**
 * `@AutoDependencyGraph` generates `SampleGraphGraph` (annotated with Metro's `@DependencyGraph`
 * and `@SingleIn`) plus a top-level `createSampleGraph()` accessor — no hand-written graph
 * annotations or `@DependencyGraph.Factory` needed.
 */
@AutoDependencyGraph(AppScope::class)
interface SampleGraph {
    val greeter: Greeter
}
