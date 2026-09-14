package com.example.zetaoverlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ZetaAccessibilityService : AccessibilityService() {

    private lateinit var overlayManager: OverlayManager
    private lateinit var mappingRepository: CharacterMappingRepository

    private val handler = Handler(Looper.getMainLooper())

    // 이벤트가 안정적으로 안 오는 WebView 특성 때문에, 이벤트를 기다리지 않고
    // 일정 주기로 스스로 화면을 다시 확인한다. 이러면 (1) 제타를 벗어나는 순간
    // 바로 감지해서 오버레이를 지울 수 있고, (2) 스크롤 중에도 위치가 계속 따라온다.
    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshOverlays()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayManager(this)
        mappingRepository = CharacterMappingRepository(this)
        handler.post(pollRunnable)
        Log.d(TAG, "서비스 연결됨. DEBUG_DUMP_TREE=$DEBUG_DUMP_TREE")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 이벤트가 오면 즉시 한 번 더 갱신해서 반응성을 높인다.
        // 하지만 실제 동기화(스크롤 추적, 앱 이탈 감지)는 위의 폴링이 담당한다.
        refreshOverlays()
    }

    private fun refreshOverlays() {
        val root = rootInActiveWindow
        if (root == null || root.packageName != TARGET_PACKAGE) {
            overlayManager.update(emptyList())
            return
        }

        // 1단계: 실제 제타 화면 구조를 모르므로, 먼저 화면 트리를 통째로 파일에 저장해둔다.
        // PC 연결 없이도, 우리 앱의 "덤프 불러오기/공유하기" 버튼으로 이 내용을 확인할 수 있다.
        if (DEBUG_DUMP_TREE) {
            val builder = StringBuilder()
            buildDumpText(root, 0, builder)
            DumpStore.save(this, builder.toString())
        }

        // 2단계: 실제 덤프로 확인된 구조 — 아바타는 ImageView가 아니라
        // "정사각형 Button이고, 그 Button의 text 자체가 캐릭터 이름"이다.
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

    override fun onInterrupt() {
        overlayManager.update(emptyList())
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        if (::overlayManager.isInitialized) {
            overlayManager.update(emptyList())
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ZetaOverlayService"
        private const val TARGET_PACKAGE = "com.scatterlab.messenger"

        // 화면을 다시 확인하는 주기(ms). 너무 짧으면 배터리/성능에 부담, 너무 길면 스크롤 싱크가 늦음.
        private const val POLL_INTERVAL_MS = 200L

        // 아바타로 인정할 Button 크기 범위(px). 실측 기준 96px 정사각형 (1080폭 기기).
        private const val AVATAR_MIN_PX = 60
        private const val AVATAR_MAX_PX = 220

        // 아바타 Button의 text가 캐릭터 이름이라 가정 — 너무 길면(문장이면) 이름이 아닌 걸로 간주
        private const val MAX_NAME_LENGTH = 12

        // 처음엔 true로 두고 실제 화면 구조를 Logcat에서 확인한 뒤 false로 바꾸세요.
        private const val DEBUG_DUMP_TREE = true
    }
}
