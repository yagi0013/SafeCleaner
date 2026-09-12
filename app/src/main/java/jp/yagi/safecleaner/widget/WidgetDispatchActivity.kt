package jp.yagi.safecleaner.widget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import jp.yagi.safecleaner.MainActivity
import jp.yagi.safecleaner.SafeCleanerApp
import jp.yagi.safecleaner.accessibility.AccessibilityStatus
import jp.yagi.safecleaner.model.CleanerMode

class WidgetDispatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SafeCleanerApp
        if (!AccessibilityStatus.isEnabled(this)) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_ACCESSIBILITY_REQUIRED, true),
            )
            Toast.makeText(this, "ユーザー補助サービスを有効にしてください", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val mode = intent.getStringExtra(EXTRA_MODE)?.let {
            runCatching { CleanerMode.valueOf(it) }.getOrNull()
        } ?: CleanerMode.CLEAN_ALL
        if (!app.cleanerController.start(mode)) {
            Toast.makeText(this, "現在クリーン処理中です", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object { const val EXTRA_MODE = "cleaner_mode" }
}
