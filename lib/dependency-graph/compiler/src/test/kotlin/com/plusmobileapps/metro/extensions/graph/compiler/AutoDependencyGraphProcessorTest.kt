@file:OptIn(ExperimentalCompilerApi::class)

package com.plusmobileapps.metro.extensions.graph.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

class AutoDependencyGraphProcessorTest {

    @Test
    fun `generates a DependencyGraph subtype plus a create function`() {
        compile(
            """
            package software.amazon.test

            import com.plusmobileapps.metro.extensions.graph.AutoDependencyGraph

            interface RootBloc {
                fun interface Factory {
                    fun create(): RootBloc
                }
            }

            @AutoDependencyGraph(Unit::class)
            interface DemoComponent {
                val rootBlocFactory: RootBloc.Factory
            }
            """,
        ) {
            val source = readGeneratedSource("software/amazon/test")

            source shouldContain "package software.amazon.test"
            source shouldContain "@DependencyGraph(Unit::class)"
            source shouldContain "@SingleIn(Unit::class)"
            source shouldContain "internal abstract class DemoComponentGraph : DemoComponent"
            source shouldContain "public fun createDemoComponent(): DemoComponent = createGraph<DemoComponentGraph>()"
        }
    }

    @Test
    fun `carries inherited @Provides default methods through the generated graph`() {
        compile(
            """
            package software.amazon.test

            import com.plusmobileapps.metro.extensions.graph.AutoDependencyGraph
            import dev.zacsweers.metro.Provides

            interface Repository

            @AutoDependencyGraph(Unit::class)
            interface DemoComponent {
                val repository: Repository

                @Provides fun repository(): Repository = object : Repository {}
            }
            """,
        ) {
            // The generated graph just extends the marker; the @Provides default method is
            // inherited, so the generated source itself only needs the supertype + create fun.
            val source = readGeneratedSource("software/amazon/test")
            source shouldContain "internal abstract class DemoComponentGraph : DemoComponent"
            source shouldContain "public fun createDemoComponent(): DemoComponent = createGraph<DemoComponentGraph>()"
        }
    }

    @Test
    fun `fails when annotation target is not an interface`() {
        val result = compileForResult(
            """
            package software.amazon.test

            import com.plusmobileapps.metro.extensions.graph.AutoDependencyGraph

            @AutoDependencyGraph(Unit::class)
            abstract class DemoComponent
            """,
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "must be an interface"
    }

    private lateinit var lastCompilation: KotlinCompilation

    // Note: we assert on the generated *source* rather than requiring full compilation, because the
    // generated `@DependencyGraph` + `createGraph<T>()` only resolve once Metro's compiler plugin
    // runs — which it doesn't under bare kctfork. The end-to-end "it compiles and runs" guarantee is
    // covered by `samples/dependency-graph` (a real module with the Metro Gradle plugin applied).
    private fun JvmCompilationResult.readGeneratedSource(packagePath: String): String {
        val generatedDir = lastCompilation.kspSourcesDir.resolve("kotlin").resolve(packagePath)
        require(generatedDir.exists()) {
            "Expected generated sources directory at $generatedDir but it was not created. " +
                "KSP output dir contents: ${lastCompilation.kspSourcesDir.walk().toList()}"
        }
        val generatedFile = generatedDir.listFiles()?.singleOrNull { it.name.endsWith(".kt") }
            ?: error("Expected exactly one generated .kt file in $generatedDir but found: ${generatedDir.listFiles()?.toList()}")
        return generatedFile.readText()
    }

    private fun compile(@Language("kotlin") source: String, block: JvmCompilationResult.() -> Unit) {
        compileForResult(source).run(block)
    }

    private fun compileForResult(@Language("kotlin") source: String): JvmCompilationResult {
        val compilation = KotlinCompilation().apply {
            inheritClassPath = true
            allWarningsAsErrors = false
            verbose = false
            messageOutputStream = System.out
            sources = listOf(SourceFile.kotlin("Source.kt", source))
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += AutoDependencyGraphProcessor.Provider()
            }
        }
        lastCompilation = compilation
        return compilation.compile()
    }
}
