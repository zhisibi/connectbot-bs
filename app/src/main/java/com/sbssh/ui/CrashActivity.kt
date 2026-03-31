package com.sbssh.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sbssh.util.CrashHandler

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logText = intent.getStringExtra(EXTRA_CRASH_LOG)
            ?: CrashHandler.getLatestCrashLog()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val copyButton = Button(this).apply {
            text = "复制崩溃日志"
            setOnClickListener {
                val clipboard = getSystemService(Activity.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", logText))
            }
        }

        val closeButton = Button(this).apply {
            text = "关闭"
            setOnClickListener { finish() }
        }

        val textView = TextView(this).apply {
            text = logText
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        root.addView(copyButton)
        root.addView(closeButton)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
    }

    companion object {
        const val EXTRA_CRASH_LOG = "extra_crash_log"
    }
}
