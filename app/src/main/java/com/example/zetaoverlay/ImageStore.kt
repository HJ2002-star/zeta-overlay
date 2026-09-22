package com.example.zetaoverlay

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File

/**
 * 갤러리(Photo Picker)에서 고른 이미지를 앱 내부 저장소로 즉시 복사해서 저장한다.
 * Photo Picker로 받은 Uri는 takePersistableUriPermission이 안 통해서(재부팅 후 접근이
 * 끊길 수 있음), 아예 파일 자체를 우리 앱 안에 복사해두면 그런 걱정이 없어진다.
 */
object ImageStore {

    fun copyToInternalStorage(context: Context, sourceUri: Uri, characterName: String): Uri? {
        val safeName = characterName.replace(Regex("[^A-Za-z0-9가-힣]"), "_")
        val destFile = File(context.filesDir, "avatar_$safeName.jpg")

        return runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(destFile)
        }.getOrNull()
    }

    /**
     * 로컬에 이미 저장된 파일을 Firebase Storage로 올리고, 완료되면 다운로드 URL을 콜백으로 준다.
     * 실패해도(네트워크 없음 등) 로컬 저장/오버레이 동작에는 전혀 영향 없음 — 그냥 클라우드
     * 동기화만 안 되는 것뿐이라 조용히 넘어간다.
     */
    fun uploadToCloud(localUri: Uri, characterName: String, onResult: (String?) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onResult(null)
            return
        }
        val safeName = characterName.replace(Regex("[^A-Za-z0-9가-힣]"), "_")
        val ref = FirebaseStorage.getInstance().reference.child("images/$uid/$safeName.jpg")

        ref.putFile(localUri)
            .continueWithTask { ref.downloadUrl }
            .addOnSuccessListener { url -> onResult(url.toString()) }
            .addOnFailureListener { onResult(null) }
    }
}
