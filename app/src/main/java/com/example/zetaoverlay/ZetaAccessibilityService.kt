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

        // 2단계: 제타는 메시지마다 아바타+이름이 반복되는 그룹채팅 구조이므로,
        // 화면 전체에서 (아바타, 이름) 쌍을 여러 개 찾아 각각 매핑을 조회한다.
        val avatars = mutableListOf<Rect>()
        collectAvatarBounds(root, avatars)

        val names = mutableListOf<Pair<Rect, String>>()
        collectNameCandidates(root, names)

        val overlayItems = avatars.mapNotNull { avatarBounds ->
            val matchedName = findClosestName(avatarBounds, names) ?: return@mapNotNull null
            val imageUri = mappingRepository.getImageFor(matchedName) ?: return@mapNotNull null
            // id에 y좌표를 대략적인 구간(30px)으로 묶어서, 스크롤 중 미세한 좌표 변화로
            // 매번 새 오버레이가 생성/삭제되는 걸 줄인다. 필요하면 구간 크기를 조정할 것.
            val id = "$matchedName@${avatarBounds.top / 30}"
            OverlayItem(id, avatarBounds, imageUri)
        }

        overlayManager.update(overlayItems)
    }

    /**
     * TODO(임시 휴리스틱): 정사각형에 가까운 ImageView를 아바타로 간주.
     * dumpNodeTree 로그로 실제 크기/viewIdResourceName을 확인한 뒤
     * 더 정확한 조건(예: 특정 id 접두사)으로 좁혀도 좋다.
     */
    private fun collectAvatarBounds(node: AccessibilityNodeInfo, out: MutableList<Rect>) {
        if (node.className == "android.widget.ImageView") {
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val ratio = bounds.width().toFloat() / bounds.height().coerceAtLeast(1)
            if (bounds.width() in AVATAR_MIN_PX..AVATAR_MAX_PX && ratio in 0.7f..1.4f) {
                out.add(bounds)
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectAvatarBounds(it, out) }
        }
    }

    private fun collectNameCandidates(node: AccessibilityNodeInfo, out: MutableList<Pair<Rect, String>>) {
        if (node.className == "android.widget.TextView" && !node.text.isNullOrBlank()) {
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            out.add(bounds to node.text.toString())
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectNameCandidates(it, out) }
        }
    }

    /**
     * 아바타 우측, 세로로 겹치는 범위에 있는 텍스트 중 가장 가까운 것을
     * "캐릭터 이름"으로 간주한다. (스크린샷 기준: 이름이 아바타 오른쪽 상단에 위치)
     */
    private fun findClosestName(avatar: Rect, candidates: List<Pair<Rect, String>>): String? {
        return candidates
            .filter { (bounds, _) ->
                val horizontallyToTheRight = bounds.left >= avatar.right - HORIZONTAL_TOLERANCE_PX
                val verticallyNear = bounds.centerY() in (avatar.top - VERTICAL_TOLERANCE_PX)..(avatar.bottom + VERTICAL_TOLERANCE_PX)
                horizontallyToTheRight && verticallyNear
            }
            .minByOrNull { (bounds, _) -> kotlin.math.abs(bounds.top - avatar.top) }
            ?.second
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

        // 아바타로 인정할 ImageView 크기 범위(px). 실제 기기 해상도에 맞춰 dumpNodeTree로 확인 후 조정.
        private const val AVATAR_MIN_PX = 60
        private const val AVATAR_MAX_PX = 220

        // 이름 텍스트가 아바타 오른쪽으로 이 정도 안쪽에 있어도 허용(음수 = 살짝 겹쳐도 됨)
        private const val HORIZONTAL_TOLERANCE_PX = 20
        // 이름 텍스트 세로 중심이 아바타 상하 범위에서 이 정도 벗어나도 허용
        private const val VERTICAL_TOLERANCE_PX = 40

        // 처음엔 true로 두고 실제 화면 구조를 Logcat에서 확인한 뒤 false로 바꾸세요.
        private const val DEBUG_DUMP_TREE = true
    }
}
