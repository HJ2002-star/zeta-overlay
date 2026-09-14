package com.example.zetaoverlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ZetaAccessibilityService : AccessibilityService() {

    private lateinit var overlayManager: OverlayManager
    private lateinit var mappingRepository: CharacterMappingRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayManager(this)
        mappingRepository = CharacterMappingRepository(this)
        Log.d(TAG, "서비스 연결됨. DEBUG_DUMP_TREE=$DEBUG_DUMP_TREE")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != TARGET_PACKAGE) return

        val root = rootInActiveWindow ?: return

        // 1단계: 실제 제타 화면 구조를 모르므로, 먼저 화면 트리를 통째로 파일에 저장해둔다.
        // PC 연결 없이도, 우리 앱의 "덤프 불러오기/공유하기" 버튼으로 이 내용을 확인할 수 있다.
        if (DEBUG_DUMP_TREE) {
            val builder = StringBuilder()
            buildDumpText(root, 0, builder)
            DumpStore.save(this, builder.toString())
        }

        // 2단계: 실제 덤프로 확인된 구조 — 아바타는 ImageView가 아니라
        // "정사각형 Button이고, 그 Button의 text 자체가 캐릭터 이름"이다.
        // (제타는 WebView로 채팅 화면을 그리는데, 아바타 버튼의 접근성 텍스트가 곧 이름으로 노출됨)
        val overlayItems = mutableListOf<OverlayItem>()
        collectAvatarButtons(root, overlayItems)

        overlayManager.update(overlayItems)
    }

    private fun collectAvatarButtons(node: AccessibilityNodeInfo, out: MutableList<OverlayItem>) {
        if (node.className == "android.widget.Button" && !node.text.isNullOrBlank()) {
            val name = node.text.toString()
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val ratio = bounds.width().toFloat() / bounds.height().coerceAtLeast(1)
            val looksLikeAvatar = bounds.width() in AVATAR_MIN_PX..AVATAR_MAX_PX &&
                ratio in 0.7f..1.4f &&
                name.length <= MAX_NAME_LENGTH

            if (looksLikeAvatar) {
                mappingRepository.getImageFor(name)?.let { imageUri ->
                    // id에 y좌표를 대략적인 구간(30px)으로 묶어서, 스크롤 중 미세한 좌표 변화로
                    // 매번 새 오버레이가 생성/삭제되는 걸 줄인다.
                    val id = "$name@${bounds.top / 30}"
                    out.add(OverlayItem(id, bounds, imageUri))
                }
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectAvatarButtons(it, out) }
        }
    }

    private fun buildDumpText(node: AccessibilityNodeInfo, depth: Int, builder: StringBuilder) {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        builder.appendLine(
            "${"  ".repeat(depth)}${node.className} id=${node.viewIdResourceName} " +
                "text=${node.text} bounds=$bounds"
        )
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { buildDumpText(it, depth + 1, builder) }
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "ZetaOverlayService"
        private const val TARGET_PACKAGE = "com.scatterlab.messenger"

        // 아바타로 인정할 Button 크기 범위(px). 실측 기준 96px 정사각형 (1080폭 기기).
        private const val AVATAR_MIN_PX = 60
        private const val AVATAR_MAX_PX = 220

        // 아바타 Button의 text가 캐릭터 이름이라 가정 — 너무 길면(문장이면) 이름이 아닌 걸로 간주
        private const val MAX_NAME_LENGTH = 12

        // 처음엔 true로 두고 실제 화면 구조를 Logcat에서 확인한 뒤 false로 바꾸세요.
        private const val DEBUG_DUMP_TREE = true
    }
}
