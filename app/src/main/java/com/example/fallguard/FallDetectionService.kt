package com.example.fallguard

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
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
        val notification = NotificationCompat.Builder(this, "FALL_CH")
            .setContentTitle("FallGuard 서비스 작동 중")
            .setContentText("낙상을 실시간으로 감시하고 있습니다.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val totalAcceleration = sqrt(x * x + y * y + z * z)

            if (totalAcceleration < 3.0) isFreeFallDetected = true

            if (isFreeFallDetected && totalAcceleration > 25.0) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastImpactTime > 2000) { // 2초 중복 방지
                    lastImpactTime = currentTime
                    isFreeFallDetected = false

                    // ★ 핵심: 낙상 감지 시 MainActivity 호출
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("FALL_DETECTED", true)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("FALL_CH", "Fall Guard Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 서비스가 강제 종료되어도 시스템이 다시 살려줌
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}