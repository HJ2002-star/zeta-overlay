package com.example.zetaoverlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView

/** 화면에 동시에 떠 있는 아바타 오버레이 하나에 대한 정보. */
data class OverlayItem(val id: String, val name: String, val bounds: Rect, val imageUri: Uri)

/**
 * TYPE_ACCESSIBILITY_OVERLAY를 사용해 다른 앱 화면 위에 이미지를 그린다.
 * 접근성 서비스에 이미 바인딩되어 있으므로 SYSTEM_ALERT_WINDOW 권한이 별도로 필요 없다.
 *
 * 제타는 메시지마다 아바타가 반복되는 그룹채팅 구조라, 화면에 동시에 여러 개의
 * 오버레이가 떠 있어야 한다. id로 각 오버레이를 구분해 추가/갱신/제거한다.
 */
class OverlayManager(private val service: AccessibilityService) {

    private val windowManager =
        service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private val overlays = mutableMapOf<String, ImageView>()

    /** 이번 화면에서 보이는 아바타 목록으로 전체 오버레이 상태를 갱신한다. */
    fun update(items: List<OverlayItem>) {
        val activeIds = items.map { it.id }.toSet()

        // 화면에서 사라진(스크롤아웃된) 오버레이 제거
        val stale = overlays.keys.filter { it !in activeIds }
        stale.forEach { id -> overlays.remove(id)?.let(::removeSafely) }

        items.forEach { item ->
            val existing = overlays[item.id]
            if (existing == null) {
                addOverlay(item)
            } else {
                updateOverlayPosition(existing, item.bounds)
            }
        }
    }

    fun clearAll() {
        overlays.values.forEach(::removeSafely)
        overlays.clear()
    }

    private fun addOverlay(item: OverlayItem) {
        val imageView = ImageView(service).apply {
            setImageURI(item.imageUri)
            scaleType = ImageView.ScaleType.CENTER_CROP
            // 아바타를 원형으로 잘라서 보여준다.
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            isClickable = true
            // 제타 자체의 "프사 눌러서 크게 보기"처럼, 우리 오버레이도 누르면 원본(내가 지정한
            // 이미지)을 전체화면으로 보여준다.
            setOnClickListener {
                val intent = Intent(service, FullImageActivity::class.java).apply {
                    putExtra(FullImageActivity.EXTRA_IMAGE_URI, item.imageUri.toString())
                    putExtra(FullImageActivity.EXTRA_CHARACTER_NAME, item.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { service.startActivity(intent) }
            }
        }

        val params = WindowManager.LayoutParams(
            item.bounds.width(),
            item.bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // FLAG_NOT_TOUCHABLE을 빼서 이 영역만 탭을 받을 수 있게 한다 (그 바깥은 그대로
            // 원래 화면으로 터치가 통과됨). FLAG_NOT_FOCUSABLE은 유지해서 키보드 포커스는 안 뺏는다.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = item.bounds.left
            y = item.bounds.top
        }

        runCatching { windowManager.addView(imageView, params) }
            .onSuccess { overlays[item.id] = imageView }
    }

    private fun updateOverlayPosition(view: ImageView, bounds: Rect) {
        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width()
        params.height = bounds.height()
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun removeSafely(view: ImageView) {
        runCatching { windowManager.removeView(view) }
    }
}
