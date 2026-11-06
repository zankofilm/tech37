// Local smoke-test stub only. Android/JVM unit tests use the org.json dependency instead.
package org.json
class JSONObject {
    private val map = mutableMapOf<String, String>()
    fun put(key:String, value:String):JSONObject { map[key] = value; return this }
    fun optString(key:String):String = map[key].orEmpty()
}
