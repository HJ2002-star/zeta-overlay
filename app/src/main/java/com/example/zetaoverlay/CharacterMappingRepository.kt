package com.example.zetaoverlay

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * 캐릭터 이름 -> 사용자가 지정한 이미지 Uri 매핑을 저장/조회한다.
 * SharedPreferences에 JSON 문자열로 저장하는 단순한 방식이라
 * 매핑 개수가 많아지면 Room DB로 옮기는 걸 추천.
 */
class CharacterMappingRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveMapping(characterName: String, imageUri: Uri) {
        val json = readJson()
        json.put(characterName, imageUri.toString())
        prefs.edit().putString(KEY_MAP, json.toString()).apply()
    }

    fun getImageFor(characterName: String): Uri? {
        val json = readJson()
        if (!json.has(characterName)) return null
        return Uri.parse(json.getString(characterName))
    }

    fun getAll(): Map<String, String> {
        val json = readJson()
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    fun remove(characterName: String) {
        val json = readJson()
        json.remove(characterName)
        prefs.edit().putString(KEY_MAP, json.toString()).apply()
    }

    private fun readJson(): JSONObject = JSONObject(prefs.getString(KEY_MAP, "{}") ?: "{}")

    companion object {
        private const val PREFS_NAME = "zeta_overlay_mappings"
        private const val KEY_MAP = "mappings"
    }
}
