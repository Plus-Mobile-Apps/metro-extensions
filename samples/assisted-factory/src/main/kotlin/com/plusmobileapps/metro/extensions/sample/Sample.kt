package com.plusmobileapps.metro.extensions.sample

import com.plusmobileapps.metro.extensions.assistedfactory.ContributesAssistedFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.DependencyGraph

interface MovieRepository {
    fun get(): String

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
    override fun get(): String = "Movie $id"
}

@DependencyGraph(scope = AppScope::class)
interface AppGraph {
    val movieRepositoryFactory: MovieRepository.Factory
}
