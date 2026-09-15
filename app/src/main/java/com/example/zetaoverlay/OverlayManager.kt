package com.example.zetaoverlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

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

    // "크게 보기" 미리보기 창. 다른 앱으로 넘어가지 않도록 액티비티를 띄우지 않고,
    // 이것도 그냥 화면 전체 크기의 오버레이 창으로 그린다. X를 누르면 이 창만 지운다.
    private var previewView: View? = null

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
        hideFullScreenPreview()
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
            // 제타 자체의 "프사 눌러서 크게 보기"처럼, 누르면 크게 보여준다.
            // 다른 앱(액티비티)으로 넘어가면 제타로 안 돌아오는 문제가 있어서,
            // 화면 전체 크기의 오버레이 창을 하나 더 띄우는 방식으로 바꿨다 — 앱 전환이 아예 없다.
            setOnClickListener {
                showFullScreenPreview(item.imageUri, item.name)
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

    private fun showFullScreenPreview(imageUri: Uri, name: String) {
        hideFullScreenPreview()

        val root = FrameLayout(service).apply {
            setBackgroundColor(Color.BLACK)
        }

        val imageView = ImageView(service).apply {
            setImageURI(imageUri)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(imageView)

        val nameLabel = TextView(service).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 20f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = 100 }
        }
        root.addView(nameLabel)

        val closeButton = TextView(service).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 26f
            setPadding(40, 40, 40, 40)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        }
        root.addView(closeButton)

        // X, 이름, 배경/이미지 아무데나 눌러도 그냥 닫힌다.
        val closeClickListener = View.OnClickListener { hideFullScreenPreview() }
        root.setOnClickListener(closeClickListener)
        imageView.setOnClickListener(closeClickListener)
        closeButton.setOnClickListener(closeClickListener)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE
        )

        runCatching { windowManager.addView(root, params) }.onSuccess { previewView = root }
    }

    private fun hideFullScreenPreview() {
        previewView?.let { runCatching { windowManager.removeView(it) } }
        previewView = null
    }
}
