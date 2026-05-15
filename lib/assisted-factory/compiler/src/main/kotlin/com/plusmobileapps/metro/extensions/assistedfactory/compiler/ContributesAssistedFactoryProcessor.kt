@file:OptIn(KspExperimental::class)

package com.plusmobileapps.metro.extensions.assistedfactory.compiler

import com.google.auto.service.AutoService
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
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
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.plusmobileapps.metro.extensions.assistedfactory.ContributesAssistedFactory
import com.plusmobileapps.metro.extensions.assistedfactory.compiler.util.MetroAnnotations
import com.plusmobileapps.metro.extensions.assistedfactory.compiler.util.addOriginAnnotation
import com.plusmobileapps.metro.extensions.assistedfactory.compiler.util.findAnnotations
import com.plusmobileapps.metro.extensions.assistedfactory.compiler.util.requireContainingFile
import com.plusmobileapps.metro.extensions.assistedfactory.compiler.util.requireQualifiedName
import com.plusmobileapps.metro.extensions.assistedfactory.compiler.util.safeClassName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

public const val LOOKUP_PACKAGE: String = "com.plusmobileapps.metro.extensions.assistedfactory.generated"

private const val ASSISTED_ANNOTATION_FQN = "dev.zacsweers.metro.Assisted"
private const val ASSISTED_INJECT_ANNOTATION_FQN = "dev.zacsweers.metro.AssistedInject"

internal class ContributesAssistedFactoryProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    @AutoService(SymbolProcessorProvider::class)
    @Suppress("unused")
    public class Provider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return ContributesAssistedFactoryProcessor(
                codeGenerator = environment.codeGenerator,
                logger = environment.logger,
            )
        }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(ContributesAssistedFactory::class.requireQualifiedName())
            .filterIsInstance<KSClassDeclaration>()
            .forEach { clazz ->
                validate(clazz)
                generate(clazz)
            }
        return emptyList()
    }

    private fun validate(annotatedClass: KSClassDeclaration) {
        require(annotatedClass.isPublic()) {
            "${annotatedClass.toClassName()} must be public to use @ContributesAssistedFactory."
        }
        require(annotatedClass.hasAssistedInjectAnnotation()) {
            "${annotatedClass.toClassName()} must be annotated with @AssistedInject (Metro requires @AssistedInject, not @Inject, for classes with @Assisted parameters)."
        }
    }

    private fun generate(sourceClass: KSClassDeclaration) {
        val annotations = sourceClass.findAnnotations(ContributesAssistedFactory::class, logger)
        check(annotations.isNotEmpty()) {
            "Could not find @ContributesAssistedFactory on $sourceClass."
        }
        val annotation = annotations.first()
        checkNoDuplicateAssistedFactories(sourceClass, annotations)

        val constructor = sourceClass.getConstructors().firstOrNull { ctor ->
            ctor.parameters.any { it.hasAssistedAnnotation() }
        } ?: throw IllegalArgumentException(
            "${sourceClass.toClassName()} must have a constructor with at least one @Assisted parameter.",
        )
        val assistedParameters = constructor.parameters.filter { it.hasAssistedAnnotation() }

        val userFactoryType = assistedFactoryFromAnnotation(annotation)
        val userFactoryDeclaration = userFactoryType.declaration as? KSClassDeclaration
            ?: throw IllegalArgumentException(
                "assistedFactory on ${sourceClass.toClassName()} must reference a class declaration.",
            )
        require(userFactoryDeclaration.classKind == ClassKind.INTERFACE) {
            "${userFactoryDeclaration.qualifiedName?.asString()} must be an interface to be used as an assisted factory."
        }

        val userFactoryMethod = singleAbstractMethod(userFactoryDeclaration)
        val boundType = userFactoryMethod.returnType?.resolve()
            ?: throw IllegalArgumentException(
                "Could not resolve return type of ${userFactoryDeclaration.qualifiedName?.asString()}.${userFactoryMethod.simpleName.asString()}",
            )

        val scope = scopeFromAnnotation(annotation)

        val safeName = sourceClass.safeClassName(logger)
        val metroFactoryClassName = ClassName(LOOKUP_PACKAGE, "${safeName}MetroFactory")
        val bridgeClassName = ClassName(LOOKUP_PACKAGE, safeName)

        val sourceClassName = sourceClass.toClassName()
        val userFactoryClassName = userFactoryDeclaration.toClassName()
        val boundTypeName = boundType.toTypeName()

        val metroFactory = buildMetroAssistedFactory(
            metroFactoryClassName = metroFactoryClassName,
            sourceClassName = sourceClassName,
            assistedParameters = assistedParameters.map { param ->
                ParameterSpec.builder(
                    param.name?.asString() ?: error("Anonymous @Assisted parameter"),
                    param.type.toTypeName(),
                ).build()
            },
        )

        val bridge = buildBridgeClass(
            bridgeClassName = bridgeClassName,
            sourceClass = sourceClass,
            metroFactoryClassName = metroFactoryClassName,
            userFactoryClassName = userFactoryClassName,
            userFactoryMethod = userFactoryMethod,
            boundTypeName = boundTypeName,
            scope = scope,
        )

        FileSpec.builder(bridgeClassName)
            .addType(metroFactory)
            .addType(bridge)
            .build()
            .writeTo(codeGenerator, aggregating = false)
    }

    private fun buildMetroAssistedFactory(
        metroFactoryClassName: ClassName,
        sourceClassName: ClassName,
        assistedParameters: List<ParameterSpec>,
    ): TypeSpec {
        val createMethod = FunSpec.builder("create")
            .addModifiers(KModifier.ABSTRACT)
            .addParameters(assistedParameters)
            .returns(sourceClassName)
            .build()

        return TypeSpec.funInterfaceBuilder(metroFactoryClassName)
            .addAnnotation(AnnotationSpec.builder(MetroAnnotations.AssistedFactory).build())
            .addFunction(createMethod)
            .build()
    }

    private fun buildBridgeClass(
        bridgeClassName: ClassName,
        sourceClass: KSClassDeclaration,
        metroFactoryClassName: ClassName,
        userFactoryClassName: ClassName,
        userFactoryMethod: KSFunctionDeclaration,
        boundTypeName: TypeName,
        scope: TypeName,
    ): TypeSpec {
        val factoryParam = ParameterSpec.builder("metroFactory", metroFactoryClassName).build()
        val factoryProperty = PropertySpec.builder("metroFactory", metroFactoryClassName)
            .initializer("metroFactory")
            .addModifiers(KModifier.PRIVATE)
            .build()

        val overrideParams = userFactoryMethod.parameters.map { param ->
            ParameterSpec.builder(
                param.name?.asString() ?: error("Anonymous parameter on ${userFactoryMethod.simpleName.asString()}"),
                param.type.toTypeName(),
            ).build()
        }

        val overrideMethod = FunSpec.builder(userFactoryMethod.simpleName.asString())
            .addModifiers(KModifier.OVERRIDE)
            .addParameters(overrideParams)
            .returns(boundTypeName)
            .addStatement(
                "return metroFactory.create(${overrideParams.joinToString { it.name }})",
            )
            .build()

        return TypeSpec.classBuilder(bridgeClassName)
            .addOriginatingKSFile(sourceClass.requireContainingFile(logger))
            .addAnnotation(AnnotationSpec.builder(MetroAnnotations.Inject).build())
            .addAnnotation(
                AnnotationSpec.builder(MetroAnnotations.ContributesBinding)
                    .addMember("%T::class", scope)
                    .build(),
            )
            .addAnnotation(
                AnnotationSpec.builder(MetroAnnotations.SingleIn)
                    .addMember("%T::class", scope)
                    .build(),
            )
            .addOriginAnnotation(sourceClass)
            .addSuperinterface(userFactoryClassName)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(factoryParam)
                    .build(),
            )
            .addProperty(factoryProperty)
            .addFunction(overrideMethod)
            .build()
    }

    private fun checkNoDuplicateAssistedFactories(
        clazz: KSClassDeclaration,
        annotations: List<KSAnnotation>,
    ) {
        annotations
            .mapNotNull { assistedFactoryFromAnnotationOrNull(it) }
            .map { it.declaration.requireQualifiedName(logger) }
            .takeIf { it.isNotEmpty() }
            ?.reduce { previous, next ->
                check(previous != next) {
                    "The same assisted factory type should not be contributed twice on ${clazz.toClassName()}: $next.".also {
                        logger.error(it, clazz)
                    }
                }
                previous
            }
    }

    private fun assistedFactoryFromAnnotationOrNull(annotation: KSAnnotation): KSType? {
        return annotation.arguments
            .firstOrNull { it.name?.asString() == ContributesAssistedFactory::assistedFactory.name }
            ?.value as? KSType
    }

    private fun assistedFactoryFromAnnotation(annotation: KSAnnotation): KSType {
        return assistedFactoryFromAnnotationOrNull(annotation)
            ?: throw IllegalArgumentException(
                "@ContributesAssistedFactory requires an `assistedFactory` argument.",
            )
    }

    private fun scopeFromAnnotation(annotation: KSAnnotation): TypeName {
        val scopeArg = annotation.arguments
            .firstOrNull { it.name?.asString() == ContributesAssistedFactory::scope.name }
            ?: throw IllegalArgumentException("@ContributesAssistedFactory requires a `scope` argument.")
        val scopeType = scopeArg.value as? KSType
            ?: throw IllegalArgumentException("@ContributesAssistedFactory `scope` must be a class reference.")
        return scopeType.toTypeName()
    }

    private fun singleAbstractMethod(factoryInterface: KSClassDeclaration): KSFunctionDeclaration {
        val abstractMethods = factoryInterface.getAllFunctions()
            .filterNot {
                val name = it.simpleName.asString()
                name == "equals" || name == "hashCode" || name == "toString"
            }
            .toList()
        check(abstractMethods.size == 1) {
            "${factoryInterface.qualifiedName?.asString()} must declare exactly one abstract method to be used as an assisted factory."
        }
        return abstractMethods.single()
    }

    private fun KSAnnotated.hasAssistedAnnotation(): Boolean =
        annotations.any {
            it.annotationType.resolve().declaration.requireQualifiedName(logger) == ASSISTED_ANNOTATION_FQN
        }

    private fun KSAnnotated.hasAssistedInjectAnnotation(): Boolean =
        annotations.any {
            it.annotationType.resolve().declaration.requireQualifiedName(logger) == ASSISTED_INJECT_ANNOTATION_FQN
        }
}
