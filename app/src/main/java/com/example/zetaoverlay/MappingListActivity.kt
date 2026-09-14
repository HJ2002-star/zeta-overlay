package com.example.zetaoverlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** 캐릭터별로 등록된 이미지 매핑을 한눈에 보고, 각각 이미지 변경/삭제를 할 수 있는 화면. */
class MappingListActivity : AppCompatActivity() {

    private lateinit var repository: CharacterMappingRepository
    private lateinit var container: LinearLayout

    // "변경" 버튼을 누른 캐릭터 이름을 잠깐 들고 있다가, 이미지 선택 결과가 오면 사용한다.
    private var pendingReplaceName: String? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val name = pendingReplaceName
        if (uri != null && name != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            repository.saveMapping(name, uri)
            Toast.makeText(this, "'$name' 이미지 변경 완료", Toast.LENGTH_SHORT).show()
            refreshList()
        }
        pendingReplaceName = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapping_list)
        title = "캐릭터별 매핑 관리"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = CharacterMappingRepository(this)
        container = findViewById(R.id.mappingContainer)
        refreshList()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun refreshList() {
        container.removeAllViews()
        val mappings = repository.getAll()

        if (mappings.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "등록된 매핑이 없어요. 메인 화면에서 캐릭터를 추가해보세요."
                setPadding(0, 32, 0, 32)
            })
            return
        }

        mappings.forEach { (name, uriString) ->
            container.addView(buildRow(name, Uri.parse(uriString)))
        }
    }

    private fun buildRow(name: String, uri: Uri): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 16)
        }

        val thumbnail = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(120, 120)
            scaleType = ImageView.ScaleType.CENTER_CROP
            runCatching { setImageURI(uri) }
        }

        val nameView = TextView(this).apply {
            text = name
            textSize = 16f
            setPadding(24, 0, 24, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val changeButton = Button(this).apply {
            text = "변경"
            setOnClickListener {
                pendingReplaceName = name
                pickImageLauncher.launch(arrayOf("image/*"))
            }
        }

        val deleteButton = Button(this).apply {
            text = "삭제"
            setOnClickListener {
                repository.remove(name)
                Toast.makeText(this@MappingListActivity, "'$name' 매핑 삭제됨", Toast.LENGTH_SHORT).show()
                refreshList()
            }
        }

        row.addView(thumbnail)
        row.addView(nameView)
        row.addView(changeButton)
        row.addView(deleteButton)
        return row
    }
}
