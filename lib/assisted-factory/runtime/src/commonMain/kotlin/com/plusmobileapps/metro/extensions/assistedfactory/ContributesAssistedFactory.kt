package com.plusmobileapps.metro.extensions.assistedfactory

import kotlin.reflect.KClass

/**
 * Generates a Metro `@AssistedFactory` and a `@ContributesBinding` bridge class that adapts
 * Metro's auto-generated factory to a user-defined factory interface.
 *
 * Without this extension you would write the bridge class by hand:
 *
 * ```
 * interface MovieRepository {
 *     interface Factory {
 *         fun create(id: String): MovieRepository
 *     }
 * }
 *
 * @AssistedInject
 * class RealMovieRepository(
 *     @Assisted val id: String,
 * ) : MovieRepository {
 *     @AssistedFactory
 *     fun interface MetroFactory {
 *         fun create(id: String): RealMovieRepository
 *     }
 * }
 *
 * @Inject
 * @ContributesBinding(AppScope::class)
 * @SingleIn(AppScope::class)
 * class RealMovieRepositoryFactory(
 *     private val metroFactory: RealMovieRepository.MetroFactory,
 * ) : MovieRepository.Factory {
 *     override fun create(id: String): MovieRepository = metroFactory.create(id)
 * }
 * ```
 *
 * With `@ContributesAssistedFactory` everything except the source class is generated for you:
 *
 * ```
 * @AssistedInject
 * @ContributesAssistedFactory(
 *     scope = AppScope::class,
 *     assistedFactory = MovieRepository.Factory::class,
 * )
 * class RealMovieRepository(
 *     @Assisted val id: String,
 * ) : MovieRepository
 * ```
 *
 * The generated bridge class is annotated with `@Origin(RealMovieRepository::class)` so Metro's
 * contribution-merging exclusions remove the factory binding whenever the source class is
 * excluded from a graph.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class ContributesAssistedFactory(
    /** The scope in which to contribute the assisted factory binding. */
    val scope: KClass<*>,

    /** The user-defined factory interface that will be bound to the generated implementation. */
    val assistedFactory: KClass<*>,
)
