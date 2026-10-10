package com.whl.quickjs.wrapper

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Array as ReflectArray

class JSUtils<T> {

    fun toArray(ctx: QuickJSContext, items: List<T>?): JSArray {
        val array = ctx.createNewJSArray()
        if (items == null || items.isEmpty()) return array
        for (i in items.indices) array.set(toJSValue(ctx, items[i]), i)
        return array
    }

    fun toByteArray(ctx: QuickJSContext, bytes: ByteArray?): JSArray {
        val array = ctx.createNewJSArray()
        if (bytes == null || bytes.isEmpty()) return array
        for (i in bytes.indices) array.set(bytes[i].toInt() and 0xFF, i)
        return array
    }

    fun toArray(ctx: QuickJSContext, arrays: Array<T>?): JSArray {
        val array = ctx.createNewJSArray()
        if (arrays == null || arrays.isEmpty()) return array
        for (i in arrays.indices) array.set(toJSValue(ctx, arrays[i]), i)
        return array
    }

    fun toObj(ctx: QuickJSContext, map: Map<*, *>?): JSObject {
        val obj = ctx.createNewJSObject()
        if (map == null || map.isEmpty()) return obj
        for (entry in map.entries) {
            val key = entry.key ?: continue
            ctx.setProperty(obj, key.toString(), toJSValue(ctx, entry.value))
        }
        return obj
    }

    fun toJSValue(ctx: QuickJSContext, value: Any?): Any? {
        if (value == null) return null
        if (value is JSObject || value is JSCallFunction) return value
        if (value is Map<*, *>) return toObj(ctx, value)
        if (value is List<*>) return listToArray(ctx, value)
        if (value is ByteArray) return toByteArray(ctx, value)
        if (value.javaClass.isArray) {
            val array = ctx.createNewJSArray()
            val length = ReflectArray.getLength(value)
            for (i in 0 until length) {
                array.set(toJSValue(ctx, ReflectArray.get(value, i)), i)
            }
            return array
        }
        return value
    }

    private fun listToArray(ctx: QuickJSContext, items: List<*>): JSArray {
        val array = ctx.createNewJSArray()
        for (i in items.indices) array.set(toJSValue(ctx, items[i]), i)
        return array
    }

    companion object {

        @JvmStatic
        fun isEmpty(obj: Any?): Boolean = when {
            obj == null -> true
            obj is CharSequence -> obj.length == 0
            obj is Collection<*> -> obj.isEmpty()
            obj is Map<*, *> -> obj.isEmpty()
            obj.javaClass.isArray -> ReflectArray.getLength(obj) == 0
            else -> false
        }

        @JvmStatic
        fun isNotEmpty(str: CharSequence?): Boolean = !isEmpty(str)

        @JvmStatic
        fun isNotEmpty(obj: Any?): Boolean = !isEmpty(obj)

        @JvmStatic
        fun toJsonObject(`object`: JSObject?): JSONObject {
            if (`object` == null) return JSONObject()
            return try {
                JSONObject(`object`.stringify())
            } catch (e: JSONException) {
                JSONObject()
            }
        }

        @JvmStatic
        fun toJsonArray(array: JSArray?): JSONArray {
            if (array == null) return JSONArray()
            return try {
                JSONArray(array.stringify())
            } catch (e: JSONException) {
                JSONArray()
            }
        }
    }
}
