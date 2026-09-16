package com.example.zetaoverlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var repository: CharacterMappingRepository
    private var pickedImageUri: Uri? = null

    // 갤러리 사진 선택기(Photo Picker)를 바로 띄운다 — Files 앱 같은 중간 선택 화면 없이
    // 곧바로 사진 그리드가 뜬다. 여기서 받은 Uri는 오래 못 쓰므로, 저장(Save) 시점에
    // ImageStore로 앱 내부에 복사해서 영구 보관한다.
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedImageUri = uri
            findViewById<ImageView>(R.id.imagePreview).setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = CharacterMappingRepository(this)

        findViewById<Button>(R.id.buttonPickImage).setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        findViewById<Button>(R.id.buttonSave).setOnClickListener {
            val name = findViewById<EditText>(R.id.editCharacterName).text.toString().trim()
            val uri = pickedImageUri

            if (name.isEmpty() || uri == null) {
                Toast.makeText(this, "이름과 이미지를 모두 선택해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val savedUri = ImageStore.copyToInternalStorage(this, uri, name)
            if (savedUri == null) {
                Toast.makeText(this, "이미지 저장에 실패했어요. 다시 시도해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            repository.saveMapping(name, savedUri)
            Toast.makeText(this, "'$name' 매핑 저장 완료", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.buttonManageMappings).setOnClickListener {
            startActivity(Intent(this, MappingListActivity::class.java))
        }

        findViewById<Button>(R.id.buttonOpenAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.buttonLoadDump).setOnClickListener {
            val text = DumpStore.read(this)
            findViewById<TextView>(R.id.textDumpContent).text =
                text.ifBlank { "아직 저장된 덤프가 없어요. 접근성 권한을 켜고 제타 채팅방을 한 번 열어보세요." }
        }

        findViewById<Button>(R.id.buttonShareDump).setOnClickListener {
            val text = DumpStore.read(this)
            if (text.isBlank()) {
                Toast.makeText(this, "저장된 덤프가 없어요. 먼저 제타 채팅방을 열어보세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(shareIntent, "덤프 공유하기"))
        }
    }
}
