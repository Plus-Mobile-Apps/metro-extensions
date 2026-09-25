package com.plusmobileapps.metro.extensions.sample.graph

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SampleTest {
    @Test
    fun `generated create function builds a working Metro graph`() {
        val graph = createSampleGraph()

        graph.greeter.greet() shouldBe "hello"
    }
}
