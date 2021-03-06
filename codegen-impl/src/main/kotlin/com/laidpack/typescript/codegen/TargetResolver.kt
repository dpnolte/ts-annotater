package com.laidpack.typescript.codegen

import com.laidpack.typescript.codegen.moshi.TargetType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asTypeName
import javax.lang.model.element.Element
import javax.tools.Diagnostic

internal object TargetResolver {
    fun resolve(element: Element, context: TargetContext): List<TargetType> {
        context.abortOnError = true
        context.targetingTypscriptAnnotatedType = true

        val targetTypes = mutableListOf<TargetType>()
        val rootType = processTargetType(element, context)
        if (rootType != null)
            targetTypes.add(rootType)

        /**
         * extra bodyType can stem from 3 sources:
         // 1. declared types in properties - e.g., bodyType Y in class X { val test = List<Y>() } -> see WrappedBodyType.resolvePropertyType
         // 2. super classes --> see TargetType.resolveSuperTypes
         // 3. bounds in bodyType variables - e.g., bodyType Y in class X <T : Y> { val test = T } --> see WrappedBodyType.resolveGenericClassDeclaration
         // TODO: don't capture target types in the context instance (see typesToBeAddedToScope)
         // TODO: normalize types and clean up ;-)
        **/
        context.targetingTypscriptAnnotatedType = false
        context.abortOnError = false
        var extraTypes = context.typesToBeAddedToScope.toMap()
        while (extraTypes.isNotEmpty()) {
            extraTypes.forEach {
                if (!context.typesWithinScope.contains(it.key)) {
                    val derivedType = processTargetType(it.value, context)
                    if (derivedType != null)
                        targetTypes.add(derivedType)
                }
                context.typesToBeAddedToScope.remove(it.key)
            }
            extraTypes = context.typesToBeAddedToScope.toMap()
        }

        return targetTypes
    }

    private fun processTargetType(element: Element, context: TargetContext): TargetType? {
        if (isDuplicateType(element, context)) return null

        val type = TargetType.get(element, context)
        if (type != null) {
            context.typesWithinScope.add(type.name.canonicalName)
            if (context.targetingTypscriptAnnotatedType) context.typesWithTypeScriptAnnotation.add(type.name.canonicalName)
        }

        return type
    }


    private fun isDuplicateType(element: Element, context: TargetContext): Boolean {
        val typeName = element.asType().asTypeName()
        if (typeName is ClassName && context.typesWithinScope.contains(typeName.canonicalName)) {
            // error on duplicated annotated types
            if (context.typesWithTypeScriptAnnotation.contains(typeName.canonicalName) && context.targetingTypscriptAnnotatedType) {
                context.messager.printMessage(Diagnostic.Kind.ERROR, "Multiple types with a duplicate name: '${typeName.canonicalName}'. Please rename or remove the @TypeScript annotation?")
            }
            return true// ignore duplicate base types
        }
        return false
    }

}