package com.breezsdk

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import org.json.JSONArray
import org.json.JSONObject

fun serialize(arr: ReadableArray): String {
    return jsonOf(arr).toString()
}

fun serialize(map: ReadableMap): String {
    return jsonOf(map).toString()
}

fun deserializeArray(json: String?): ReadableArray? {
    if (json != null) {
        return readableArrayOf(JSONArray(json))
    }

    return null
}

fun deserializeMap(json: String?): ReadableMap? {
    if (json != null) {
        return readableMapOf(JSONObject(json))
    }

    return null
}

fun jsonOf(arr: ReadableArray): JSONArray {
    var json = JSONArray()
    for (key in arr.toArrayList().indices) {
        when (arr.getType(key)) {
            ReadableType.Boolean -> json.put(arr.getBoolean(key))
            ReadableType.Number -> json.put(arr.getDouble(key))
            ReadableType.String -> json.put(arr.getString(key))
            ReadableType.Map -> json.put(jsonOf(arr.getMap(key)))
            ReadableType.Array -> json.put(jsonOf(arr.getArray(key)))
        }
    }
    return json
}

fun jsonOf(map: ReadableMap): JSONObject {
    var json = JSONObject()
    var iterator = map.keySetIterator()
    while (iterator.hasNextKey()) {
        var key = iterator.nextKey()
        when (map.getType(key)) {
            ReadableType.Null -> json.put(key, JSONObject.NULL)
            ReadableType.Boolean -> json.put(key, map.getBoolean(key))
            ReadableType.Number -> json.put(key, map.getDouble(key))
            ReadableType.String -> json.put(key, map.getString(key))
            ReadableType.Map -> map.getMap(key)?.let { json.put(key, jsonOf(it)) }
            ReadableType.Array -> map.getArray(key)?.let { json.put(key, jsonOf(it)) }
        }
    }
    return json
}

fun readableArrayOf(json: JSONArray): ReadableArray {
    val arr = Arguments.createArray()
    for (key in 0 until json.length()) {
        var value = json.get(key)
        when (value) {
            null -> arr.pushNull()
            is Byte -> arr.pushInt(value.toInt())
            is Boolean -> arr.pushBoolean(value)
            is Double -> arr.pushDouble(value)
            is Int -> arr.pushInt(value)
            is Long -> arr.pushDouble(value.toDouble())
            is JSONObject -> arr.pushMap(readableMapOf(value))
            is JSONArray -> arr.pushArray(readableArrayOf(value))
            is String -> arr.pushString(value)
        }
    }
    return arr
}

fun readableMapOf(json: JSONObject): ReadableMap {
    val map = Arguments.createMap()
    var iterator = json.keys()
    while (iterator.hasNext()) {
        var key = iterator.next()
        var value = json.get(key)
        when (value) {
            null -> map.putNull(key)
            is Byte -> map.putInt(key, value.toInt())
            is Boolean -> map.putBoolean(key, value)
            is Double -> map.putDouble(key, value)
            is Int -> map.putInt(key, value)
            is Long -> map.putDouble(key, value.toDouble())
            is JSONObject -> map.putMap(key, readableMapOf(value))
            is JSONArray -> map.putArray(key, readableArrayOf(value))
            is String -> map.putString(key, value)
        }
    }
    return map
}