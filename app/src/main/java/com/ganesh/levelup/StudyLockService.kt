package com.ganesh.levelup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat

class StudyLockService : Service() {

    private val CHANNEL_ID = "ca_inter_study_lock"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayAttached = false
    private val handler = Handler(Looper.getMainLooper())

    private val blockedApps = setOf(
        "com.instagram.android",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.snapchat.android",
        "com.netflix.mediaclient",
        "org.telegram.messenger",
        "com.pubg.imobile",
        "com.twitter.android",
        "com.reddit.frontpage"
    )

    private val pollingRunnable = object : Runnable {
        override fun run() {
            inspectForegroundApp()
            handler.postDelayed(this, 300)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initNotificationChannel()
        startForeground(1001, buildPersistentNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        handler.post(pollingRunnable)
    }

    private fun inspectForegroundApp() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1200

        val usageEvents = usm.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var topPackageName: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                topPackageName = event.packageName
            }
        }

        topPackageName?.let { pkg ->
            if (blockedApps.contains(pkg)) {
                deployOverlay(pkg)
            } else if (pkg != "com.ganesh.levelup" && isOverlayAttached) {
                retractOverlay()
            }
        }
    }

    private fun deployOverlay(packageName: String) {
        if (isOverlayAttached) return

        handler.post {
            try {
                val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutParamsType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val view = inflater.inflate(R.layout.overlay_lock, null)

                val tvName = view.findViewById<TextView>(R.id.tvBlockedAppName)
                tvName.text = "Restricted App: $packageName\nComplete your CA study target first!"

                val btnHome = view.findViewById<Button>(R.id.btnOverlayHome)
                btnHome.setOnClickListener {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(homeIntent)
                    retractOverlay()
                }

                overlayView = view
                windowManager?.addView(overlayView, params)
                isOverlayAttached = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun retractOverlay() {
        if (!isOverlayAttached || overlayView == null) return
        handler.post {
            try {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayAttached = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildPersistentNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LevelUp Study Shield Active")
            .setContentText("Focus lock running. Distractions are blocked.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Study Shield Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollingRunnable)
        retractOverlay()
        super.onDestroy()
    }
}
