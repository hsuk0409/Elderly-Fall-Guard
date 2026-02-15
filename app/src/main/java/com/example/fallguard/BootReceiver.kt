package com.example.fallguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 부팅 완료 신호인지 확인
        val action = intent.action
        Log.d("FallGuard", "BootReceiver 수신됨: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON") { // 일부 기종(HTC 등) 추가 대응

            val serviceIntent = Intent(context, FallDetectionService::class.java)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d("FallGuard", "부팅 후 서비스 시작 명령 전송 완료")
            } catch (e: Exception) {
                Log.e("FallGuard", "부팅 후 서비스 시작 실패: ${e.message}")
            }
        }
    }
}