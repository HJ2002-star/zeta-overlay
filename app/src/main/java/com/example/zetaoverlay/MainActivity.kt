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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var repository: CharacterMappingRepository
    private var pickedImageUri: Uri? = null

    // OpenDocument는 영구 권한(FLAG_GRANT_READ_URI_PERMISSION)을 받을 수 있어서
    // 재부팅 후에도, 액티비티가 없는 서비스 컨텍스트에서도 이미지를 읽을 수 있다.
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            pickedImageUri = uri
            findViewById<ImageView>(R.id.imagePreview).setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = CharacterMappingRepository(this)

        findViewById<Button>(R.id.buttonPickImage).setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        findViewById<Button>(R.id.buttonSave).setOnClickListener {
            val name = findViewById<EditText>(R.id.editCharacterName).text.toString().trim()
            val uri = pickedImageUri

            if (name.isEmpty() || uri == null) {
                Toast.makeText(this, "이름과 이미지를 모두 선택해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            repository.saveMapping(name, uri)
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
