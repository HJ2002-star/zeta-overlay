package com.example.zetaoverlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** 오버레이(내가 지정한 캐릭터 이미지)를 탭했을 때, 그 이미지를 전체화면으로 보여준다. */
class FullImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_full_image)

        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        val name = intent.getStringExtra(EXTRA_CHARACTER_NAME)

        findViewById<TextView>(R.id.characterNameLabel).text = name.orEmpty()

        val imageView = findViewById<ImageView>(R.id.fullImageView)
        if (uriString != null) {
            runCatching { imageView.setImageURI(Uri.parse(uriString)) }
        }

        findViewById<ImageButton>(R.id.closeButton).setOnClickListener { closeAndReturnToZeta() }
        imageView.setOnClickListener { closeAndReturnToZeta() }

        // 뒤로가기도 같은 동작을 하게 한다.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    closeAndReturnToZeta()
                }
            }
        )
    }

    /**
     * 서비스에서 NEW_TASK로 띄운 화면이라, 그냥 finish()만 하면 제타로 안 돌아가고
     * 홈 화면 등으로 빠질 수 있다. 그래서 닫을 때 제타를 명시적으로 다시 앞으로 불러온다.
     */
    private fun closeAndReturnToZeta() {
        packageManager.getLaunchIntentForPackage(ZETA_PACKAGE)?.let { launchIntent ->
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            startActivity(launchIntent)
        }
        finish()
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_CHARACTER_NAME = "extra_character_name"
        private const val ZETA_PACKAGE = "com.scatterlab.messenger"
    }
}
