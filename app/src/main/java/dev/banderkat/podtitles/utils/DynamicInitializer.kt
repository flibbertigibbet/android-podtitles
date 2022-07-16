package dev.banderkat.podtitles.utils

import android.util.Log
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.typeOf

/**
 * Use reflection to construct an object using a map of field names to values. Based on:
 * https://discuss.kotlinlang.org/t/support-for-object-construction-from-parameters-in-a-map/4256/10
 */
fun <T : Any> KClass<T>.createInstance(values: Map<String, Any>): T {
    val tag = "DynamicInit"
    val cons = this.primaryConstructor!!
    val valMap = cons.parameters
        .filter { values[it.name] != null }
        .associateBy({ it },
        { param ->
            val value = values[param.name]
            // cast the value to the expected parameter type
            when (param.type) {
                typeOf<String>() -> value as String
                typeOf<Int>() -> value as Int
                typeOf<Long>() -> value as Long
                typeOf<Boolean>() -> value as Boolean
                else -> {
                    Log.w(tag, "Field ${param.name} has unhandled parameter type ${param.type}")
                    value
                }
            }
        })
    return cons.callBy(valMap)
}

