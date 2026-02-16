package com.example.fallguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class FallDetectionService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isFreeFallDetected = false
    private var isWaitingForNoMovement = false
    private var noMovementStartTime: Long = 0

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        // 1. 평상시 알림 (Foreground Service 유지용)
        val normalNotification = NotificationCompat.Builder(this, "FALL_CH")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("가족지키미가 작동 중입니다")
            .setContentText("실시간으로 낙상을 감시하고 있습니다.")
            .setPriority(NotificationCompat.PRIORITY_LOW) // 평소엔 조용하게
            .build()

        startForeground(1, normalNotification)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val totalAcceleration = sqrt(x * x + y * y + z * z)

            // 1단계: 자유낙하 감지 (무중력 상태에 가까운 3.0 이하)
            if (totalAcceleration < 3.0) {
                isFreeFallDetected = true
            }

            // 2단계: 바닥 충격 감지 (자유낙하 후 가속도가 급격히 50 이상으로 튐)
            if (isFreeFallDetected && totalAcceleration > 50.0) {
                isFreeFallDetected = false // 초기화
                isWaitingForNoMovement = true
                noMovementStartTime = System.currentTimeMillis()
                Log.d("FallGuard", "충격 감지! 정지 상태 확인 중...")
            }

            // 3단계: 정지 상태 확인 (수정 버전)
            if (isWaitingForNoMovement) {
                val currentTime = System.currentTimeMillis()
                val timeElapsed = currentTime - noMovementStartTime

                // 1.5초(1500ms) 동안 큰 움직임 없이 잘 버텼다면 낙상으로 확정!
                if (timeElapsed > 1500) {
                    isWaitingForNoMovement = false
                    Log.d("FallGuard", "낙상 확정: 1.5초간 정지 확인됨")
                    triggerFallAlert()
                }else {
                    // 아직 1.5초가 안 되었다면 계속 센서값을 지켜봄
                    // (여기에 추가적인 취소 로직을 넣지 않음으로써 반동에 의한 오작동 방지)
                    Log.d("FallGuard", "안정화 대기 중... 현재 값: $totalAcceleration")
                }
            }
        }
    }

    private fun triggerFallAlert() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("FALL_DETECTED", true)
        }

        // PendingIntent 생성 (반드시 FLAG_IMMUTABLE 또는 MUTABLE 명시)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 잠금화면 돌파를 위한 알림 재설정
        val emergencyNotification = NotificationCompat.Builder(this, "FALL_CH")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ 낙상 감지됨!")
            .setContentText("터치하여 알람을 취소하세요.")
            .setPriority(NotificationCompat.PRIORITY_MAX) // 최상위 우선순위
            .setCategory(NotificationCompat.CATEGORY_ALARM) // 알람 카테고리 (중요)
            .setFullScreenIntent(pendingIntent, true)    // ★ 이게 있어야 잠금화면에서 Activity가 뜹니다.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 잠금화면에서 내용 보이기
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2, emergencyNotification) // 새로운 ID로 알림 발송

        // 알림을 쏘고 나서 다시 한번 실행 시도
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        // IMPORTANCE_LOW를 IMPORTANCE_HIGH로 변경!
        val channel = NotificationChannel(
            "FALL_CH",
            "Fall Guard Service",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC // 잠금화면 노출 허용
            enableVibration(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 서비스가 강제 종료되어도 시스템이 다시 살려줌
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}