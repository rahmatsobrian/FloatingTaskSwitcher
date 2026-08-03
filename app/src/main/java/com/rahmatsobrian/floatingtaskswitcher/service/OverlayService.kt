package com.rahmatsobrian.floatingtaskswitcher.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.rahmatsobrian.floatingtaskswitcher.NOTIFICATION_CHANNEL_OVERLAY
import com.rahmatsobrian.floatingtaskswitcher.R
import com.rahmatsobrian.floatingtaskswitcher.core.permission.OperatingModeManager
import com.rahmatsobrian.floatingtaskswitcher.data.local.SettingsDataStore
import com.rahmatsobrian.floatingtaskswitcher.domain.model.RunningApp
import com.rahmatsobrian.floatingtaskswitcher.domain.model.SortMode
import com.rahmatsobrian.floatingtaskswitcher.domain.usecase.GetRecentAppsUseCase
import com.rahmatsobrian.floatingtaskswitcher.domain.usecase.SwitchToAppUseCase
import com.rahmatsobrian.floatingtaskswitcher.ui.overlay.OverlayRoot
import com.rahmatsobrian.floatingtaskswitcher.ui.overlay.OverlayUiState
import com.rahmatsobrian.floatingtaskswitcher.ui.theme.FloatingTaskSwitcherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs
import javax.inject.Inject

private const val NOTIFICATION_ID = 4201
private const val ACTION_PAUSE = "com.rahmatsobrian.floatingtaskswitcher.action.PAUSE"
private const val ACTION_RESUME = "com.rahmatsobrian.floatingtaskswitcher.action.RESUME"
private const val ACTION_EXIT = "com.rahmatsobrian.floatingtaskswitcher.action.EXIT"

@AndroidEntryPoint
class OverlayService : LifecycleService() {

    @Inject lateinit var getRecentAppsUseCase: GetRecentAppsUseCase
    @Inject lateinit var switchToAppUseCase: SwitchToAppUseCase
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var operatingModeManager: OperatingModeManager

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val serviceLifecycleOwner = ServiceLifecycleOwner()

    private var uiState by mutableStateOf(OverlayUiState())
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        serviceLifecycleOwner.performRestore()
        serviceLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)

        observeSettings()
        observeOperatingMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_EXIT -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                if (composeView == null && Settings.canDrawOverlays(this)) {
                    attachOverlay()
                }
                refreshApps()
            }
        }
        return START_STICKY
    }

    private fun observeSettings() {
        settingsDataStore.settings
            .onEach { settings ->
                uiState = uiState.copy(
                    panelStyle = settings.panelStyle,
                    opacity = settings.opacity,
                    cornerRadiusDp = settings.cornerRadiusDp,
                )
            }
            .launchIn(lifecycleScope)
    }

    private fun observeOperatingMode() {
        operatingModeManager.currentMode
            .onEach { mode -> uiState = uiState.copy(operatingMode = mode) }
            .launchIn(lifecycleScope)
    }

    private fun refreshApps() {
        lifecycleScope.launch {
            uiState = uiState.copy(isLoading = true)
            val apps = getRecentAppsUseCase(SortMode.RECENTLY_USED)
            uiState = uiState.copy(apps = apps, isLoading = false)
        }
    }

    private fun attachOverlay() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }
        layoutParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(serviceLifecycleOwner)
            setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)
            setContent {
                FloatingTaskSwitcherTheme {
                    OverlayRoot(
                        state = uiState,
                        onToggleExpanded = ::onToggleExpanded,
                        onAppClick = ::onAppClick,
                        onAppLongClick = { /* Quick actions menu: extended in a follow-up iteration */ },
                    )
                }
            }
            setOnTouchListener(dragTouchListener(params))
        }
        composeView = view
        windowManager.addView(view, params)
        serviceLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun detachOverlay() {
        composeView?.let { runCatching { windowManager.removeView(it) } }
        composeView = null
    }

    private fun onToggleExpanded() {
        uiState = uiState.copy(isExpanded = !uiState.isExpanded)
        if (uiState.isExpanded) refreshApps()
    }

    private fun onAppClick(app: RunningApp) {
        lifecycleScope.launch {
            switchToAppUseCase(app.packageName)
            uiState = uiState.copy(isExpanded = false)
        }
    }

    private fun dragTouchListener(params: WindowManager.LayoutParams): (android.view.View, MotionEvent) -> Boolean {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        return { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX)
                    val dy = (event.rawY - initialTouchY)
                    if (abs(dx) > 8 || abs(dy) > 8) isDragging = true
                    if (isDragging && !uiState.isExpanded) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(composeView, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        snapToNearestEdge(params)
                    } else {
                        view.performClick()
                        onToggleExpanded()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val bubbleWidth = composeView?.width ?: 150
        val targetX = if (params.x + bubbleWidth / 2 < screenWidth / 2) 0 else screenWidth - bubbleWidth
        val animator = android.animation.ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 220
            addUpdateListener {
                params.x = it.animatedValue as Int
                runCatching { windowManager.updateViewLayout(composeView, params) }
            }
        }
        animator.start()
    }

    private fun pause() {
        isPaused = true
        detachOverlay()
        updateNotification()
    }

    private fun resume() {
        isPaused = false
        if (Settings.canDrawOverlays(this)) attachOverlay()
        updateNotification()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val pauseResumeAction = if (isPaused) {
            NotificationCompat.Action(
                0,
                getString(R.string.notification_action_resume),
                servicePendingIntent(ACTION_RESUME),
            )
        } else {
            NotificationCompat.Action(
                0,
                getString(R.string.notification_action_pause),
                servicePendingIntent(ACTION_PAUSE),
            )
        }
        val exitAction = NotificationCompat.Action(
            0,
            getString(R.string.notification_action_exit),
            servicePendingIntent(ACTION_EXIT),
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_OVERLAY)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(R.string.notification_title_running))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(pauseResumeAction)
            .addAction(exitAction)
            .build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        detachOverlay()
        serviceLifecycleOwner.destroy()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_EXIT))
        }
    }
}
