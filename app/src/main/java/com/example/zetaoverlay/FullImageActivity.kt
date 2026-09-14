package com.example.zetaoverlay

import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
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

        findViewById<ImageButton>(R.id.closeButton).setOnClickListener { finish() }
        imageView.setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_CHARACTER_NAME = "extra_character_name"
    }
}
