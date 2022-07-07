package dev.banderkat.podtitles.utils

import kotlin.reflect.KClass
import kotlin.reflect.KParameter // this import is used
import kotlin.reflect.full.primaryConstructor

interface DynamicInitializer<T>

/**
 * Use reflection to construct an object using a map of field names to values. From:
 * https://discuss.kotlinlang.org/t/support-for-object-construction-from-parameters-in-a-map/4256/10
 */
fun <T : Any> KClass<T>.createInstance(values: Map<String, Any>): T {
    val cons = this.primaryConstructor!!
    val valMap = cons.parameters.associateBy({ it }, { values[it.name] })
    return cons.callBy(valMap)
}

