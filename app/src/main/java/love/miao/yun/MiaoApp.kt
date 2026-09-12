package love.miao.yun

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import love.miao.yun.util.CrashHandler

class MiaoApp : Application() {
    companion object {
        const val CHANNEL_ID = "miao_service_channel"
        private const val TAG = "MiaoApp"
    }

    override fun onCreate() {
        super.onCreate()
        // 在所有进程中都初始化崩溃捕获
        try {
            CrashHandler(this).init()
            Log.d(TAG, "CrashHandler initialized, process: ${getProcessName()}")
        } catch (e: Exception) {
            Log.e(TAG, "CrashHandler init failed", e)
        }
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
