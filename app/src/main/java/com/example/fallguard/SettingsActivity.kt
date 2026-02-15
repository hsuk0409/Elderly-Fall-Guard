package com.example.fallguard

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("FallGuardPrefs", Context.MODE_PRIVATE)
        val editTexts = listOf(
            findViewById(R.id.etNum1),
            findViewById(R.id.etNum2),
            findViewById(R.id.etNum3),
            findViewById(R.id.etNum4),
            findViewById<EditText>(R.id.etNum5)
        )

        // 기존 저장된 번호 불러오기
        editTexts.forEachIndexed { index, editText ->
            editText.setText(prefs.getString("contact_$index", ""))
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val editor = prefs.edit()
            editTexts.forEachIndexed { index, editText ->
                editor.putString("contact_$index", editText.text.toString())
            }
            editor.apply()
            Toast.makeText(this, "저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}