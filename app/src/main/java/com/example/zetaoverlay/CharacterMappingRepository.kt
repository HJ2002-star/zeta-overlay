package com.example.zetaoverlay

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

/**
 * 캐릭터 이름 -> 사용자가 지정한 이미지 Uri 매핑을 저장/조회한다.
 * SharedPreferences에 JSON 문자열로 저장하는 단순한 방식이라
 * 매핑 개수가 많아지면 Room DB로 옮기는 걸 추천.
 *
 * 로컬 저장(오버레이가 실제로 쓰는 값)은 항상 즉시/동기로 끝나고, Firestore 동기화는
 * 별도로 비동기 실행된다 — 네트워크가 없거나 실패해도 폰 기능에는 전혀 영향 없다.
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
        removeFromCloud(characterName)
    }

    /** 압축된 이미지(Base64)를 Firestore에 직접 기록한다. 실패해도 조용히 무시. */
    fun syncToFirestore(characterName: String, imageBase64: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("mappings").document(characterName)
                .set(mapOf("imageBase64" to imageBase64, "updatedAt" to FieldValue.serverTimestamp()))
                .addOnFailureListener { e -> Log.w(TAG, "Firestore 동기화 실패(무시): ${e.message}") }
        }
    }

    private fun removeFromCloud(characterName: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("mappings").document(characterName)
                .delete()
                .addOnFailureListener { e -> Log.w(TAG, "Firestore 삭제 동기화 실패(무시): ${e.message}") }
        }
    }

    private fun readJson(): JSONObject = JSONObject(prefs.getString(KEY_MAP, "{}") ?: "{}")

    companion object {
        private const val TAG = "CharacterMappingRepo"
        private const val PREFS_NAME = "zeta_overlay_mappings"
        private const val KEY_MAP = "mappings"
    }
}
