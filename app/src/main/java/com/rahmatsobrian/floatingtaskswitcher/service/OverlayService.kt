package com.rahmatsobrian.floatingtaskswitcher.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.rahmatsobrian.floatingtaskswitcher.NOTIFICATION_CHANNEL_OVERLAY
import com.rahmatsobrian.floatingtaskswitcher.R
import com.rahmatsobrian.floatingtaskswitcher.core.permission.OperatingModeManager
import com.rahmatsobrian.floatingtaskswitcher.data.local.DarkModeOption
import com.rahmatsobrian.floatingtaskswitcher.data.local.PanelStyle
import com.rahmatsobrian.floatingtaskswitcher.data.local.SettingsDataStore
import com.rahmatsobrian.floatingtaskswitcher.domain.model.RunningApp
import com.rahmatsobrian.floatingtaskswitcher.domain.model.SortMode
import com.rahmatsobrian.floatingtaskswitcher.domain.usecase.GetRecentAppsUseCase
import com.rahmatsobrian.floatingtaskswitcher.domain.usecase.SwitchToAppUseCase
import com.rahmatsobrian.floatingtaskswitcher.ui.overlay.OverlayRoot
import com.rahmatsobrian.floatingtaskswitcher.ui.overlay.OverlayUiState
import com.rahmatsobrian.floatingtaskswitcher.ui.theme.FloatingTaskSwitcherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val NOTIFICATION_ID = 4201
private const val ACTION_PAUSE = "com.rahmatsobrian.floatingtaskswitcher.action.PAUSE"
private const val ACTION_RESUME = "com.rahmatsobrian.floatingtaskswitcher.action.RESUME"
private const val ACTION_EXIT = "com.rahmatsobrian.floatingtaskswitcher.action.EXIT"
private const val AUTO_HIDE_PEEK_IDLE_MS = 3_000L
private const val AUTO_HIDE_COLLAPSE_IDLE_MS = 5_000L

@AndroidEntryPoint
class OverlayService : LifecycleService() {

    @Inject lateinit var getRecentAppsUseCase: GetRecentAppsUseCase
    @Inject lateinit var switchToAppUseCase: SwitchToAppUseCase
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var operatingModeManager: OperatingModeManager

    private lateinit var windowManager: WindowManager

    /** The actual View added to the WindowManager (a small FrameLayout wrapping the ComposeView
     *  below). ComposeView itself is `final` in this library version and can't be subclassed, so
     *  outside-touch detection lives on this wrapper instead. */
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val serviceLifecycleOwner = ServiceLifecycleOwner()

    private var uiState by mutableStateOf(OverlayUiState())
    private var currentDarkModeOption by mutableStateOf(DarkModeOption.SYSTEM)
    private var currentDynamicColorEnabled by mutableStateOf(true)
    private var isPaused = false
    private var isSnappedToLeftEdge = true
    private var lastInteractionAtMillis = System.currentTimeMillis()
    private var autoHideEnabled = false
    private var gamingModeEnabled = true

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        serviceLifecycleOwner.performRestore()
        serviceLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)

        observeSettings()
        observeOperatingMode()
        observeForegroundAppForGamingMode()
        startAutoHideWatcher()
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
                if (overlayView == null && Settings.canDrawOverlays(this)) {
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
                autoHideEnabled = settings.autoHideEnabled
                gamingModeEnabled = settings.gamingModeEnabled
                currentDarkModeOption = settings.darkModeOption
                currentDynamicColorEnabled = settings.dynamicColorEnabled
                val expandByDefault = settings.panelStyle == PanelStyle.EXPAND_PANEL && !uiState.isExpanded
                uiState = uiState.copy(
                    panelStyle = settings.panelStyle,
                    opacity = settings.opacity,
                    cornerRadiusDp = settings.cornerRadiusDp,
                    isExpanded = if (expandByDefault) true else uiState.isExpanded,
                )
                if (expandByDefault) refreshApps()
            }
            .launchIn(lifecycleScope)
    }

    private fun observeOperatingMode() {
        operatingModeManager.currentMode
            .onEach { mode -> uiState = uiState.copy(operatingMode = mode) }
            .launchIn(lifecycleScope)
    }

    /**
     * Gaming Mode: force the panel back to a collapsed bubble while a game is foregrounded.
     * Skipped for Expand Panel style, which is meant to stay open as a persistent dock.
     */
    private fun observeForegroundAppForGamingMode() {
        TaskAccessibilityService.foregroundPackage
            .onEach { packageName ->
                if (packageName == null || !gamingModeEnabled) return@onEach
                if (uiState.panelStyle == PanelStyle.EXPAND_PANEL) return@onEach
                if (isGamePackage(packageName) && uiState.isExpanded) {
                    uiState = uiState.copy(isExpanded = false)
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun isGamePackage(packageName: String): Boolean {
        val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            info.category == ApplicationInfo.CATEGORY_GAME
        } else {
            (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        }
    }

    /**
     * Auto Hide has two effects, matching how the person described it:
     *  - collapsed & idle for a while -> fade to low opacity ("peek"), full opacity returns on touch
     *  - expanded & idle for a while  -> auto-collapse back to the bubble
     * Expand Panel style is exempt from the auto-collapse half, since it is meant to stay open.
     */
    private fun startAutoHideWatcher() {
        lifecycleScope.launch {
            while (isActive) {
                delay(1_000)
                if (!autoHideEnabled) continue
                val idleFor = System.currentTimeMillis() - lastInteractionAtMillis
                when {
                    uiState.isExpanded && uiState.panelStyle != PanelStyle.EXPAND_PANEL &&
                        idleFor >= AUTO_HIDE_COLLAPSE_IDLE_MS -> {
                        uiState = uiState.copy(isExpanded = false)
                        lastInteractionAtMillis = System.currentTimeMillis()
                    }
                    !uiState.isExpanded -> {
                        val shouldPeek = idleFor >= AUTO_HIDE_PEEK_IDLE_MS
                        if (shouldPeek != uiState.isPeeking) {
                            uiState = uiState.copy(isPeeking = shouldPeek)
                        }
                    }
                }
            }
        }
    }

    private fun onInteraction() {
        lastInteractionAtMillis = System.currentTimeMillis()
        if (uiState.isPeeking) {
            uiState = uiState.copy(isPeeking = false)
        }
    }

    /** Tap outside the overlay window: collapse the panel, unless it's the "always open" style. */
    private fun onOutsideTouch() {
        if (uiState.isExpanded && uiState.panelStyle != PanelStyle.EXPAND_PANEL) {
            onCollapse()
        }
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

        // FLAG_LAYOUT_NO_LIMITS is deliberately NOT set: without it, the window is confined to
        // the screen's decor area, which is what keeps the bubble/panel from being drawn under
        // the status bar or the navigation bar.
        // FLAG_NOT_TOUCH_MODAL lets touches outside our bounds pass through to the app below
        // instead of being silently swallowed, and is required for FLAG_WATCH_OUTSIDE_TOUCH to
        // deliver an ACTION_OUTSIDE event so we can auto-collapse an expanded panel on an
        // outside tap.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }
        layoutParams = params

        val composeContent = ComposeView(this).apply {
            setViewTreeLifecycleOwner(serviceLifecycleOwner)
            setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)
            setContent {
                FloatingTaskSwitcherTheme(
                    darkModeOption = currentDarkModeOption,
                    dynamicColorEnabled = currentDynamicColorEnabled,
                ) {
                    OverlayRoot(
                        state = uiState,
                        onToggleExpanded = ::onToggleExpanded,
                        onCollapse = ::onCollapse,
                        onAppClick = ::onAppClick,
                        onAppLongClick = { /* Quick actions menu: extended in a follow-up iteration */ },
                        onBubbleTap = ::onBubbleTap,
                        onDrag = ::onBubbleDrag,
                        onDragEnd = ::onBubbleDragEnd,
                        onInteraction = ::onInteraction,
                    )
                }
            }
        }

        val container = OutsideTouchContainer(this, onOutsideTouch = ::onOutsideTouch).apply {
            addView(
                composeContent,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT),
            )
            // WRAP_CONTENT means the window resizes whenever the panel expands/collapses or
            // switches layout style. Re-clamp on every size change so a bigger panel can't end
            // up partially off-screen after growing from a bubble pinned near an edge.
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)
                if (sizeChanged) {
                    clampToScreen(params)
                    runCatching { windowManager.updateViewLayout(this, params) }
                }
            }
        }
        overlayView = container
        val attached = runCatching { windowManager.addView(container, params) }
        if (attached.isFailure) {
            android.util.Log.e(
                "FloatingTaskSwitcher",
                "Failed to attach overlay window",
                attached.exceptionOrNull(),
            )
            overlayView = null
            lifecycleScope.launch { settingsDataStore.updateFloatingServiceEnabled(false) }
            stopSelf()
            return
        }
        serviceLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun detachOverlay() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    private fun onToggleExpanded() {
        uiState = uiState.copy(isExpanded = !uiState.isExpanded)
        if (uiState.isExpanded) refreshApps()
    }

    private fun onCollapse() {
        uiState = uiState.copy(isExpanded = false)
    }

    /**
     * Tapping the collapsed bubble normally toggles the panel open. In Quick Switch style the
     * whole point is to skip the panel: a tap instantly switches to the most recently used app.
     */
    private fun onBubbleTap() {
        if (uiState.panelStyle == PanelStyle.MINI_BUBBLE) {
            val mostRecent = uiState.apps.firstOrNull()
            if (mostRecent != null) {
                onAppClick(mostRecent)
            } else {
                refreshApps()
            }
        } else {
            onToggleExpanded()
        }
    }

    private fun onAppClick(app: RunningApp) {
        lifecycleScope.launch {
            switchToAppUseCase(app.packageName)
            uiState = uiState.copy(isExpanded = false)
        }
    }

    private fun onBubbleDrag(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        params.x += dx.toInt()
        params.y += dy.toInt()
        clampToScreen(params)
        runCatching { windowManager.updateViewLayout(overlayView, params) }
    }

    private fun onBubbleDragEnd() {
        layoutParams?.let { snapToNearestEdge(it) }
    }

    /** Keeps the window's x/y fully within the current display bounds. */
    private fun clampToScreen(params: WindowManager.LayoutParams) {
        val displayMetrics = resources.displayMetrics
        val viewWidth = overlayView?.width?.takeIf { it > 0 } ?: 150
        val viewHeight = overlayView?.height?.takeIf { it > 0 } ?: 150
        val maxX = (displayMetrics.widthPixels - viewWidth).coerceAtLeast(0)
        val maxY = (displayMetrics.heightPixels - viewHeight).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams) {
        clampToScreen(params)
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val bubbleWidth = overlayView?.width ?: 150
        val snapLeft = params.x + bubbleWidth / 2 < screenWidth / 2
        isSnappedToLeftEdge = snapLeft
        val targetX = (if (snapLeft) 0 else screenWidth - bubbleWidth).coerceAtLeast(0)
        val animator = android.animation.ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 220
            addUpdateListener {
                params.x = it.animatedValue as Int
                runCatching { windowManager.updateViewLayout(overlayView, params) }
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

/**
 * A plain View.OnTouchListener never sees MotionEvent.ACTION_OUTSIDE - it is only ever
 * delivered through dispatchTouchEvent on the window's root view. ComposeView is `final` in
 * this library version and can't be subclassed to override that method directly, so this tiny
 * FrameLayout wrapper hosts the ComposeView as a child instead and catches the event itself.
 */
private class OutsideTouchContainer(
    context: Context,
    private val onOutsideTouch: () -> Unit,
) : FrameLayout(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch()
            return true
        }
        return super.dispatchTouchEvent(event)
    }
}
