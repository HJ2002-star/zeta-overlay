package com.example.zetaoverlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.google.firebase.auth.FirebaseAuth

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
        setupCloudAuth()

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

            // 클라우드 동기화는 로컬 저장과 별개 — 실패해도 위 로컬 저장에는 영향 없음.
            ImageStore.encodeForFirestore(this, savedUri)?.let { base64 ->
                repository.syncToFirestore(name, base64)
            }
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

    /** 익명 로그인을 한 번 해두고, 발급된 UID를 화면에 표시 + 복사할 수 있게 한다. */
    private fun setupCloudAuth() {
        val auth = FirebaseAuth.getInstance()
        val uidLabel = findViewById<TextView>(R.id.textUid)

        fun showUid(uid: String) {
            uidLabel.text = "UID: $uid"
        }

        val current = auth.currentUser
        if (current != null) {
            showUid(current.uid)
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { result -> result.user?.uid?.let(::showUid) }
                .addOnFailureListener { uidLabel.text = "동기화 로그인 실패 (오프라인이어도 로컬 기능은 정상 동작)" }
        }

        findViewById<Button>(R.id.buttonCopyUid).setOnClickListener {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "아직 UID가 발급되지 않았어요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("UID", uid))
            Toast.makeText(this, "UID 복사됨", Toast.LENGTH_SHORT).show()
        }
    }
}
