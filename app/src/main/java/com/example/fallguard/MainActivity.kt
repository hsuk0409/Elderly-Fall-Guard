package com.example.fallguard

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.telephony.SmsManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnCancel: Button
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 연결
        tvStatus = findViewById(R.id.tv_status)
        tvCountdown = findViewById(R.id.tv_countdown)
        btnCancel = findViewById(R.id.btn_cancel)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)

        // 설정 화면 진입 (5번 클릭)
        var clickCount = 0
        tvTitle.setOnClickListener {
            clickCount++
            if (clickCount >= 5) {
                clickCount = 0
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        // 화면 깨우기 및 권한 요청
        setupLockScreenFlags()
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ), 1)

        // 서비스 시작
        startForegroundService(Intent(this, FallDetectionService::class.java))

        btnCancel.setOnClickListener { stopFallAlert() }

        // 서비스로부터 낙상 신호를 받았는지 확인
        if (intent.getBooleanExtra("FALL_DETECTED", false)) {
            onFallDetected()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 새로운 인텐트로 교체
        if (intent.getBooleanExtra("FALL_DETECTED", false)) {
            onFallDetected()
        }
    }

    // 낙상 감지 시 실행될 함수
    private fun onFallDetected() {
        if (countDownTimer != null) return
        tvStatus.text = "⚠️ 낙상 감지됨! ⚠️"
        btnCancel.visibility = View.VISIBLE

        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvCountdown.text = "${millisUntilFinished / 1000}초"
            }
            override fun onFinish() {
                sendAlertToAll()
                resetAndExit()   // 발송 후 초기화 및 화면 끄기
            }
        }.start()
    }

    private fun stopFallAlert() {
        Toast.makeText(this, "알람이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        resetAndExit() // 취소 후 초기화 및 화면 끄기
    }

    private fun sendAlertToAll() {
        val prefs = getSharedPreferences("FallGuardPrefs", Context.MODE_PRIVATE)
        val contacts = (0..4).mapNotNull { prefs.getString("contact_$it", "").takeIf { it!!.isNotEmpty() } }

        if (contacts.isEmpty()) {
            tvStatus.text = "❌ 번호를 설정해주세요"
            return
        }

        // 위치 가져오기 (간략화)
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch(e: SecurityException) { null }
        val locMsg = if(loc != null) "\n위치: ${loc.latitude},${loc.longitude}" else "\n(위치 정보 없음)"

        val smsManager = getSystemService(SmsManager::class.java)
        contacts.forEach { num ->
            try { smsManager.sendTextMessage(num, null, "[FallGuard] 긴급 상황!$locMsg", null, null) } catch(e: Exception) {}
        }
        tvStatus.text = "✅ ${contacts.size}명에게 전송 완료"
        btnCancel.visibility = View.GONE
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        }
    }

    private fun resetAndExit() {
        // 1. 타이머가 돌고 있다면 멈춤
        countDownTimer?.cancel()
        countDownTimer = null

        // 2. UI 상태 초기화
        tvStatus.text = "상태: 모니터링 중"
        tvCountdown.text = ""
        btnCancel.visibility = View.GONE

        // 3. 화면 끄기 (잠시 후 종료)
        Toast.makeText(this, "상황이 종료되어 화면을 끕니다.", Toast.LENGTH_SHORT).show()

        // 2초 뒤에 앱 화면을 닫음 (화면이 자동으로 꺼지도록 유도)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            finishAndRemoveTask() // 앱을 최근 앱 목록에서도 정리하며 종료
        }, 2000)
    }
}