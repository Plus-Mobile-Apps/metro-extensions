package com.plusmobileapps.metro.extensions.graph.compiler

import com.google.auto.service.AutoService
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.plusmobileapps.metro.extensions.graph.AutoDependencyGraph
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private const val METRO_PACKAGE = "dev.zacsweers.metro"
private val DEPENDENCY_GRAPH = ClassName(METRO_PACKAGE, "DependencyGraph")
private val SINGLE_IN = ClassName(METRO_PACKAGE, "SingleIn")
private val CREATE_GRAPH = MemberName(METRO_PACKAGE, "createGraph")

internal class AutoDependencyGraphProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    @AutoService(SymbolProcessorProvider::class)
    @Suppress("unused")
    public class Provider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return AutoDependencyGraphProcessor(
                codeGenerator = environment.codeGenerator,
                logger = environment.logger,
            )
        }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(requireNotNull(AutoDependencyGraph::class.qualifiedName))
            .filterIsInstance<KSClassDeclaration>()
            .forEach { clazz ->
                if (validate(clazz)) generate(clazz)
            }
        return emptyList()
    }

    private fun validate(marker: KSClassDeclaration): Boolean {
        if (marker.classKind != ClassKind.INTERFACE) {
            logger.error(
                "${marker.qualifiedName?.asString()} must be an interface to use @AutoDependencyGraph.",
                marker,
            )
            return false
        }
        if (!marker.isPublic()) {
            logger.error(
                "${marker.qualifiedName?.asString()} must be public to use @AutoDependencyGraph.",
                marker,
            )
            return false
        }
        return true
    }

    private fun generate(marker: KSClassDeclaration) {
        val annotation = marker.annotations.first {
            it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                AutoDependencyGraph::class.qualifiedName
        }
        val scope = scopeFromAnnotation(annotation)

        val packageName = marker.packageName.asString()
        val simpleName = marker.simpleName.asString()
        val markerClassName = marker.toClassName()
        val graphClassName = ClassName(packageName, "${simpleName}Graph")
        val containingFile = requireNotNull(marker.containingFile)

        // @DependencyGraph(scope) @SingleIn(scope) internal abstract class FooGraph : Foo
        val graph = TypeSpec.classBuilder(graphClassName)
            .addModifiers(KModifier.INTERNAL, KModifier.ABSTRACT)
            .addAnnotation(
                AnnotationSpec.builder(DEPENDENCY_GRAPH).addMember("%T::class", scope).build(),
            )
            .addAnnotation(
                AnnotationSpec.builder(SINGLE_IN).addMember("%T::class", scope).build(),
            )
            .addSuperinterface(markerClassName)
            .addOriginatingKSFile(containingFile)
            .build()

        // fun createFoo(): Foo = createGraph<FooGraph>()
        val createFunction = FunSpec.builder("create$simpleName")
            .returns(markerClassName)
            .addStatement("return %M<%T>()", CREATE_GRAPH, graphClassName)
            .addOriginatingKSFile(containingFile)
            .build()

        FileSpec.builder(graphClassName)
            .addType(graph)
            .addFunction(createFunction)
            .build()
            .writeTo(codeGenerator, aggregating = false)
    }

    private fun scopeFromAnnotation(annotation: KSAnnotation): TypeName {
        val scopeArg = annotation.arguments
            .firstOrNull { it.name?.asString() == AutoDependencyGraph::scope.name }
            ?: throw IllegalArgumentException("@AutoDependencyGraph requires a `scope` argument.")
        val scopeType = scopeArg.value as? KSType
            ?: throw IllegalArgumentException("@AutoDependencyGraph `scope` must be a class reference.")
        return scopeType.toTypeName()
    }
}
