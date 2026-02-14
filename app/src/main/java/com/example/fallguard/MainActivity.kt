package com.example.fallguard

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {
    // 낙상 감지를 위한 변수들
    private var isFreeFallDetected = false
    private var lastImpactTime: Long = 0

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var tvSensorData: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSensorData = findViewById(R.id.tv_sensor_data)

        // 1. 센서 매니저 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // 2. 가속도계 센서 가져오기
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    // 센서 값이 변할 때마다 호출됨
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val totalAcceleration = sqrt(x * x + y * y + z * z)

            // 화면 업데이트
            val result = "Total: ${String.format("%.2f", totalAcceleration)}"
            tvSensorData.text = result

            // --- 낙상 감지 로직 시작 ---

            // 1. 자유낙하 감지 (손에서 놓침)
            if (totalAcceleration < 3.0) {
                isFreeFallDetected = true
            }

            // 2. 충격 감지 (바닥에 부딪힘)
            if (isFreeFallDetected && totalAcceleration > 25.0) {
                val currentTime = System.currentTimeMillis()

                // 너무 짧은 시간 내의 중복 감지 방지 (0.5초)
                if (currentTime - lastImpactTime > 500) {
                    lastImpactTime = currentTime
                    isFreeFallDetected = false // 상태 초기화

                    // 낙상 발생 알림 실행!
                    onFallDetected()
                }
            }
        }
    }

    // 낙상 감지 시 실행될 함수
    private fun onFallDetected() {
        // 로그캣에 찍기 (컴퓨터용)
        Log.d("FallDetection", "낙상 발생!!!")

        // 휴대폰 화면에 띄우기 (컴퓨터 연결 안 됐을 때 확인용)
        Toast.makeText(this, "⚠️ 낙상이 감지되었습니다!", Toast.LENGTH_SHORT).show()

        tvSensorData.text = "⚠️ 낙상 감지됨! ⚠️"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // 앱이 화면에 보일 때 센서 시작
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    // 앱이 백그라운드로 가면 센서 중지 (배터리 절약)
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}