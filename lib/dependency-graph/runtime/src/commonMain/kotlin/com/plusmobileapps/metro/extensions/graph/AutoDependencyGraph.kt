package com.plusmobileapps.metro.extensions.graph

import kotlin.reflect.KClass

/**
 * Generates a Metro `@DependencyGraph` (plus `@SingleIn`) from a plain marker interface, along with
 * a top-level `create<Name>()` accessor — removing the boilerplate of declaring the graph
 * annotations and a parameterless `@DependencyGraph.Factory` by hand.
 *
 * Without this extension you write:
 *
 * ```
 * @DependencyGraph(AppScope::class)
 * @SingleIn(AppScope::class)
 * interface OnboardingDemoComponent {
 *     val onboardingRootBlocFactory: OnboardingRootBloc.Factory
 *
 *     @DependencyGraph.Factory
 *     fun interface Factory {
 *         fun create(): OnboardingDemoComponent
 *     }
 * }
 *
 * val component = createGraphFactory<OnboardingDemoComponent.Factory>().create()
 * ```
 *
 * With `@AutoDependencyGraph` you write only the marker interface:
 *
 * ```
 * @AutoDependencyGraph(AppScope::class)
 * interface OnboardingDemoComponent {
 *     val onboardingRootBlocFactory: OnboardingRootBloc.Factory
 * }
 *
 * val component = createOnboardingDemoComponent()
 * ```
 *
 * The processor generates, in the marker's own package:
 *
 * ```
 * @DependencyGraph(AppScope::class)
 * @SingleIn(AppScope::class)
 * internal abstract class OnboardingDemoComponentGraph : OnboardingDemoComponent
 *
 * fun createOnboardingDemoComponent(): OnboardingDemoComponent =
 *     createGraph<OnboardingDemoComponentGraph>()
 * ```
 *
 * The marker must be a public interface. Accessors are left abstract for Metro to implement, and
 * any `@Provides` default methods declared on the marker are inherited by the generated graph.
 *
 * Use this for graphs whose factory takes no arguments. When the graph needs constructor
 * parameters (e.g. an Android `Context`), declare Metro's `@DependencyGraph` + `@DependencyGraph.Factory`
 * by hand instead.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class AutoDependencyGraph(
    /** The scope the generated `@DependencyGraph` and `@SingleIn` are bound to. */
    val scope: KClass<*>,
)
