package com.plusmobileapps.metro.extensions.sample

import dev.zacsweers.metro.createGraph
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SampleTest {
    @Test
    fun `assisted factory is bound and resolves a new instance per call`() {
        val graph = createGraph<AppGraph>()

        graph.movieRepositoryFactory.create("42").get() shouldBe "Movie 42"
        graph.movieRepositoryFactory.create("7").get() shouldBe "Movie 7"
    }
}
