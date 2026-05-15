package com.plusmobileapps.metro.extensions.assistedfactory.compiler.util

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.Annotatable
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName
import kotlin.reflect.KClass

internal object MetroAnnotations {
    private const val PACKAGE = "dev.zacsweers.metro"

    val Inject: ClassName = ClassName(PACKAGE, "Inject")
    val AssistedInject: ClassName = ClassName(PACKAGE, "AssistedInject")
    val AssistedFactory: ClassName = ClassName(PACKAGE, "AssistedFactory")
    val ContributesBinding: ClassName = ClassName(PACKAGE, "ContributesBinding")
    val SingleIn: ClassName = ClassName(PACKAGE, "SingleIn")
    val Origin: ClassName = ClassName(PACKAGE, "Origin")
    val Assisted: ClassName = ClassName(PACKAGE, "Assisted")
}

internal fun KClass<*>.requireQualifiedName(): String = requireNotNull(qualifiedName) {
    "Qualified name was null for $this"
}

internal fun KSDeclaration.requireQualifiedName(logger: KSPLogger): String {
    val qualifiedName = qualifiedName?.asString()
    requireNotNull(qualifiedName) {
        "Qualified name for $this cannot be null".also { logger.error(it, this) }
    }
    return qualifiedName
}

internal fun KSDeclaration.requireContainingFile(logger: KSPLogger): KSFile =
    requireNotNull(containingFile) {
        "Containing file was null for $this".also { logger.error(it, this) }
    }

/**
 * Returns e.g. `com.example.foo.MyClass` → `ComExampleFooMyClass`, so generated classes
 * in the lookup package don't collide across user packages.
 */
internal fun KSClassDeclaration.safeClassName(logger: KSPLogger): String {
    return requireQualifiedName(logger)
        .split(".")
        .joinToString(separator = "") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}

/**
 * Adds `@dev.zacsweers.metro.Origin(SourceClass::class)` so Metro contribution-merging
 * exclusions for [clazz] also drop this generated type.
 */
internal fun <T : Annotatable.Builder<T>> Annotatable.Builder<T>.addOriginAnnotation(
    clazz: KSClassDeclaration,
): T = addAnnotation(
    AnnotationSpec.builder(MetroAnnotations.Origin)
        .addMember("%T::class", clazz.toClassName())
        .build(),
)

internal fun KSClassDeclaration.findAnnotations(
    annotation: KClass<out Annotation>,
    logger: KSPLogger,
): List<KSAnnotation> {
    val fqName = annotation.requireQualifiedName()
    return annotations.filter {
        it.annotationType.resolve().declaration.requireQualifiedName(logger) == fqName
    }.toList()
}
