package com.example.zetaoverlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 갤러리(Photo Picker)에서 고른 이미지를 앱 내부 저장소로 즉시 복사해서 저장한다.
 * Photo Picker로 받은 Uri는 takePersistableUriPermission이 안 통해서(재부팅 후 접근이
 * 끊길 수 있음), 아예 파일 자체를 우리 앱 안에 복사해두면 그런 걱정이 없어진다.
 */
object ImageStore {

    // Firestore 문서 하나가 1MB 제한이라, 클라우드 동기화용 썸네일은 작게 줄인다.
    private const val THUMBNAIL_MAX_DIMENSION = 200
    private const val THUMBNAIL_JPEG_QUALITY = 70

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
     * 로컬 이미지를 작은 썸네일로 줄여서 Base64 문자열로 인코딩한다. Firebase Storage(카드 등록
     * 필요)를 안 쓰고, 이 문자열 그대로를 Firestore 문서 필드에 저장해 클라우드 동기화한다.
     * 실패해도(디코딩 오류 등) 로컬 저장/오버레이 동작에는 전혀 영향 없다.
     */
    fun encodeForFirestore(context: Context, localUri: Uri): String? {
        return runCatching {
            val original = context.contentResolver.openInputStream(localUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@runCatching null

            val scale = THUMBNAIL_MAX_DIMENSION.toFloat() / maxOf(original.width, original.height)
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                original
            }

            val output = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }
}
