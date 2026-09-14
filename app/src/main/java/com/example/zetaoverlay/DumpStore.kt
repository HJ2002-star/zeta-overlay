package com.example.zetaoverlay

import android.content.Context
import java.io.File

/**
 * PC/ADB 없이도 확인할 수 있도록, 화면 덤프를 Logcat 대신(또는 함께) 파일로 저장한다.
 * 매번 덮어써서 항상 "가장 최근에 감지된 화면"만 남는다.
 */
object DumpStore {
    private const val FILE_NAME = "zeta_dump.txt"

    fun save(context: Context, text: String) {
        runCatching {
            File(context.filesDir, FILE_NAME).writeText(text)
        }
    }

    fun read(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else ""
    }
}
