@file:OptIn(ExperimentalCompilerApi::class)

package com.plusmobileapps.metro.extensions.assistedfactory.compiler

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

class ContributesAssistedFactoryProcessorTest {

    @Test
    fun `generates an AssistedFactory plus a bridge class implementing the user factory`() {
        compile(
            """
            package software.amazon.test

            import com.plusmobileapps.metro.extensions.assistedfactory.ContributesAssistedFactory
            import dev.zacsweers.metro.Assisted
            import dev.zacsweers.metro.AssistedInject

            interface Base
            interface BaseFactory {
                fun create(id: String): Base
            }

            @AssistedInject
            @ContributesAssistedFactory(
                scope = Unit::class,
                assistedFactory = BaseFactory::class,
            )
            class Impl(
                @Assisted val id: String,
            ) : Base
            """,
        ) {
            val source = readGeneratedSource()

            source shouldContain "package $LOOKUP_PACKAGE"

            source shouldContain "@AssistedFactory"
            source shouldContain "public fun interface SoftwareAmazonTestImplMetroFactory"
            source shouldContain "public fun create(id: String): Impl"

            source shouldContain "@Inject"
            source shouldContain "@ContributesBinding(Unit::class)"
            source shouldContain "@SingleIn(Unit::class)"
            source shouldContain "@Origin(Impl::class)"
            source shouldContain "public class SoftwareAmazonTestImpl"
            source shouldContain "metroFactory: SoftwareAmazonTestImplMetroFactory"
            source shouldContain ": BaseFactory"
            source shouldContain "override fun create(id: String): Base = metroFactory.create(id)"
        }
    }

    @Test
    fun `supports nested factory interfaces`() {
        compile(
            """
            package software.amazon.test

            import com.plusmobileapps.metro.extensions.assistedfactory.ContributesAssistedFactory
            import dev.zacsweers.metro.Assisted
            import dev.zacsweers.metro.AssistedInject

            interface Base {
                interface Factory {
                    fun create(id: String): Base
                }
            }

            @AssistedInject
            @ContributesAssistedFactory(
                scope = Unit::class,
                assistedFactory = Base.Factory::class,
            )
            class Impl(
                @Assisted val id: String,
            ) : Base
            """,
        ) {
            val source = readGeneratedSource()
            source shouldContain "@Origin(Impl::class)"
            source shouldContain ": Base.Factory"
            source shouldContain "override fun create(id: String): Base = metroFactory.create(id)"
        }
    }

    @Test
    fun `supports multiple assisted parameters`() {
        compile(
            """
            package software.amazon.test

            import com.plusmobileapps.metro.extensions.assistedfactory.ContributesAssistedFactory
            import dev.zacsweers.metro.Assisted
            import dev.zacsweers.metro.AssistedInject

            interface Base
            interface BaseFactory {
                fun create(id: String, count: Int): Base
            }

            @AssistedInject
            @ContributesAssistedFactory(
                scope = Unit::class,
                assistedFactory = BaseFactory::class,
            )
            class Impl(
                @Assisted val id: String,
                @Assisted val count: Int,
            ) : Base
            """,
        ) {
            val source = readGeneratedSource()
            source shouldContain "public fun create(id: String, count: Int): Impl"
            source shouldContain "override fun create(id: String, count: Int): Base = metroFactory.create(id, count)"
        }
    }

    private lateinit var lastCompilation: KotlinCompilation

    private fun JvmCompilationResult.readGeneratedSource(): String {
        compilationStatusOk()
        val generatedDir = lastCompilation.kspSourcesDir.resolve("kotlin").resolve(
            LOOKUP_PACKAGE.replace('.', '/'),
        )
        require(generatedDir.exists()) {
            "Expected generated sources directory at $generatedDir but it was not created. " +
                "KSP output dir contents: ${lastCompilation.kspSourcesDir.walk().toList()}"
        }
        val generatedFile = generatedDir.listFiles()?.singleOrNull { it.name.endsWith(".kt") }
            ?: error("Expected exactly one generated .kt file in $generatedDir but found: ${generatedDir.listFiles()?.toList()}")
        return generatedFile.readText()
    }

    private fun JvmCompilationResult.compilationStatusOk() {
        exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    private fun compile(@Language("kotlin") source: String, block: JvmCompilationResult.() -> Unit) {
        val compilation = KotlinCompilation().apply {
            inheritClassPath = true
            allWarningsAsErrors = false
            verbose = false
            messageOutputStream = System.out
            sources = listOf(SourceFile.kotlin("Source.kt", source))
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += ContributesAssistedFactoryProcessor.Provider()
            }
        }
        lastCompilation = compilation
        compilation.compile().run(block)
    }
}
