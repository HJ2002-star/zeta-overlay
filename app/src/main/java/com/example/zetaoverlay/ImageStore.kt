package com.example.zetaoverlay

import android.content.Context
import android.net.Uri
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
}
