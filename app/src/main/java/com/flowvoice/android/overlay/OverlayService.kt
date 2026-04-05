package com.flowvoice.android.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.flowvoice.android.R
import com.flowvoice.android.accessibility.FlowVoiceAccessibilityService
import com.flowvoice.android.api.ApiClient
import com.flowvoice.android.audio.AudioRecorder
import com.flowvoice.android.settings.SettingsRepository
import com.flowvoice.android.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

class OverlayService : LifecycleService() {

    companion object {
        @Volatile var instance: OverlayService? = null
            private set

        private const val NOTIF_CHANNEL_ID = "flowvoice_overlay"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "com.flowvoice.android.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioRecorder = AudioRecorder()
    private var recordingJob: Job? = null
    @Volatile private var isRecording = false

    // Touch drag state
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var initialParamX = 0
    private var initialParamY = 0
    private var hasMoved = false
    private val recordingStartRunnable = Runnable { triggerStartRecording() }

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannel()
        startForegroundWithNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = OverlayView(this)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        windowManager.addView(overlayView, layoutParams)
        overlayView.visibility = View.GONE

        overlayView.setOnTouchListener { _, event -> handleTouch(event) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        cancelRecording()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    // --- Overlay visibility ---

    fun show() {
        mainHandler.post { overlayView.visibility = View.VISIBLE }
    }

    fun hide() {
        mainHandler.post {
            if (!isRecording) {
                overlayView.visibility = View.GONE
            }
        }
    }

    // --- Touch handling ---

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.rawX
                touchStartY = event.rawY
                initialParamX = layoutParams.x
                initialParamY = layoutParams.y
                hasMoved = false
                // Delay recording start to distinguish drag from press
                mainHandler.postDelayed(recordingStartRunnable, 150)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchStartX
                val dy = event.rawY - touchStartY
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > 10f) {
                    hasMoved = true
                    mainHandler.removeCallbacks(recordingStartRunnable)
                }
                layoutParams.x = initialParamX + dx.toInt()
                layoutParams.y = initialParamY + dy.toInt()
                windowManager.updateViewLayout(overlayView, layoutParams)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(recordingStartRunnable)
                if (isRecording) {
                    triggerStopAndTranscribe()
                }
            }
        }
        return true
    }

    // --- Recording lifecycle ---

    private fun triggerStartRecording() {
        if (isRecording) return
        isRecording = true
        overlayView.setState(OverlayView.State.RECORDING)

        recordingJob = lifecycleScope.launch(Dispatchers.IO) {
            audioRecorder.start() // blocks until stop() is called
        }
    }

    private fun cancelRecording() {
        isRecording = false
        audioRecorder.stop()
        recordingJob?.cancel()
        recordingJob = null
    }

    private fun triggerStopAndTranscribe() {
        if (!isRecording) return
        isRecording = false

        lifecycleScope.launch(Dispatchers.IO) {
            val wavBytes = audioRecorder.stop()
            recordingJob?.cancel()

            withContext(Dispatchers.Main) {
                overlayView.setState(OverlayView.State.LOADING)
            }

            try {
                val settings = SettingsRepository(applicationContext).load()
                val response = ApiClient.transcribe(
                    host = settings.host,
                    port = settings.port,
                    audioBytes = wavBytes,
                    language = settings.language,
                    preprocess = settings.preprocess
                )

                withContext(Dispatchers.Main) {
                    val injected = FlowVoiceAccessibilityService.instance?.injectText(response.transcript) ?: false
                    if (!injected) {
                        // Fallback: copy to clipboard and notify user
                        copyToClipboard(response.transcript)
                        Toast.makeText(applicationContext, R.string.msg_text_pasted_clipboard, Toast.LENGTH_SHORT).show()
                    }
                    overlayView.setState(OverlayView.State.SUCCESS)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.msg_connection_failed, e.message ?: "Unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                    overlayView.setState(OverlayView.State.ERROR)
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("FlowVoice", text)
        clipboard.setPrimaryClip(clip)
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }
}
