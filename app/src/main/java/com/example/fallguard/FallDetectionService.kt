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
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class FallDetectionService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isFreeFallDetected = false
    private var lastImpactTime: Long = 0

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        // 1. 평상시 알림 (Foreground Service 유지용)
        val normalNotification = NotificationCompat.Builder(this, "FALL_CH")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FallGuard가 작동 중입니다")
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
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val totalAcceleration = sqrt(x * x + y * y + z * z)

            // 자유낙하 감지
            if (totalAcceleration < 3.0) isFreeFallDetected = true

            // 충격 감지
            if (isFreeFallDetected && totalAcceleration > 25.0) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastImpactTime > 3000) { // 중복 방지 시간을 3초로 약간 늘림
                    lastImpactTime = currentTime
                    isFreeFallDetected = false

                    // ★ 핵심: 실제로 낙상이 감지되었을 때만 화면을 깨움
                    triggerFallAlert()
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