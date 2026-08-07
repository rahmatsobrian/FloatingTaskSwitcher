package com.rahmatsobrian.floatingtaskswitcher.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.rahmatsobrian.floatingtaskswitcher.data.local.PanelStyle
import com.rahmatsobrian.floatingtaskswitcher.domain.model.RunningApp

/**
 * Root composable for the overlay window.
 *
 * The collapsed bubble handles drag and tap-to-expand together in one gesture detector (see
 * below) because a plain View.OnTouchListener never reliably gets the gesture - the child
 * AndroidComposeView consumes ACTION_DOWN first for its own pointer input, so an outer listener
 * silently loses it. Once expanded, dragging is intentionally NOT attached to the whole panel
 * surface anymore: applying it there made small finger jitter while tapping an app icon or the
 * "Kecilkan" button get misread as a drag, which both swallowed those taps and could shift the
 * window mid-touch. Dragging while expanded is done through a dedicated handle in the header
 * instead (see [ExpandedPanel]), so the rest of the panel is free for normal clicks.
 */
@Composable
fun OverlayRoot(
    state: OverlayUiState,
    onToggleExpanded: () -> Unit,
    onCollapse: () -> Unit,
    onAppClick: (RunningApp) -> Unit,
    onAppLongClick: (RunningApp) -> Unit,
    onBubbleTap: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cornerRadius by animateDpAsState(
        targetValue = state.cornerRadiusDp.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "cornerRadius",
    )
    val effectiveAlpha by animateFloatAsState(
        targetValue = if (state.isPeeking) 0.35f else state.opacity,
        label = "overlayAlpha",
    )

    Surface(
        modifier = modifier
            .alpha(effectiveAlpha)
            .then(
                if (!state.isExpanded) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            onInteraction()
                            var dragged = false
                            var totalDx = 0f
                            var totalDy = 0f
                            val slop = viewConfiguration.touchSlop
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!dragged) onBubbleTap()
                                    if (dragged) onDragEnd()
                                    break
                                }
                                val delta = change.positionChange()
                                totalDx += delta.x
                                totalDy += delta.y
                                if (!dragged && (kotlin.math.abs(totalDx) > slop || kotlin.math.abs(totalDy) > slop)) {
                                    dragged = true
                                }
                                if (dragged) {
                                    change.consume()
                                    onDrag(delta.x, delta.y)
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(cornerRadius),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = expandIn(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                expandFrom = Alignment.TopStart,
            ) + fadeIn(animationSpec = tween(durationMillis = 220)),
            exit = shrinkOut(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.TopStart,
            ) + fadeOut(animationSpec = tween(durationMillis = 160)),
        ) {
            ExpandedPanel(
                state = state,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onCollapse = onCollapse,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onInteraction = onInteraction,
            )
        }
        if (!state.isExpanded) {
            CollapsedBubble()
        }
    }
}

@Composable
private fun CollapsedBubble() {
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedPanel(
    state: OverlayUiState,
    onAppClick: (RunningApp) -> Unit,
    onAppLongClick: (RunningApp) -> Unit,
    onCollapse: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onInteraction: () -> Unit,
) {
    // Every child below uses fillMaxWidth()/fillMaxSize() against THIS fixed width rather than
    // each sizing itself independently. That matters because the floating window itself is
    // WRAP_CONTENT: without an explicit width here, fillMaxWidth() on the header would resolve
    // against the window's own loose incoming constraint (effectively close to the full screen
    // width) instead of the icon list's actual width - which is exactly what made Vertical Dock
    // balloon out sideways and left Grid with dead space past its 4th column.
    val contentWidth = when (state.panelStyle) {
        PanelStyle.VERTICAL_DOCK -> 100.dp
        PanelStyle.GRID -> 260.dp
        PanelStyle.COMPACT -> 220.dp
        PanelStyle.HORIZONTAL_DOCK, PanelStyle.EXPAND_PANEL, PanelStyle.MINI_BUBBLE -> 300.dp
    }
    Column(modifier = Modifier.padding(8.dp).width(contentWidth)) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Dedicated drag handle: dragging the whole panel is only recognized on this left
            // strip (icon + "Recent Apps" label + generous padding for an easy-to-hit target),
            // not over the app icons or the Kecilkan button, so a slightly shaky tap on those
            // never gets misread as "the user is dragging the panel".
            Row(
                modifier = Modifier
                    .padding(vertical = 6.dp, horizontal = 2.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            onInteraction()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    onDragEnd()
                                    break
                                }
                                val delta = change.positionChange()
                                if (delta.x != 0f || delta.y != 0f) {
                                    change.consume()
                                    onDrag(delta.x, delta.y)
                                }
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.DragIndicator,
                    contentDescription = "Geser panel",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Recent Apps",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (state.panelStyle == PanelStyle.EXPAND_PANEL) {
                Text(
                    text = "Selalu terbuka",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(onClick = onCollapse)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "Kecilkan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        if (state.isLoading && state.apps.isEmpty()) {
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            return@Column
        }
        if (state.filteredApps.isEmpty()) {
            Text(
                text = "Tidak ada aplikasi terbaru",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
            return@Column
        }

        when (state.panelStyle) {
            PanelStyle.VERTICAL_DOCK -> LazyColumn(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.filteredApps, key = { it.packageName }) { app ->
                    AppIconItem(app = app, iconSize = 44.dp, showLabel = true, onClick = { onAppClick(app) }, onLongClick = { onAppLongClick(app) })
                }
            }
            PanelStyle.GRID -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(state.filteredApps, key = { it.packageName }) { app ->
                    AppIconItem(app = app, iconSize = 40.dp, showLabel = true, onClick = { onAppClick(app) }, onLongClick = { onAppLongClick(app) })
                }
            }
            PanelStyle.COMPACT -> LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.filteredApps, key = { it.packageName }) { app ->
                    AppIconItem(app = app, iconSize = 32.dp, showLabel = false, onClick = { onAppClick(app) }, onLongClick = { onAppLongClick(app) })
                }
            }
            // MINI_BUBBLE never reaches an expanded panel (see OverlayService: tapping the
            // bubble in this style switches directly to the most-recent app instead of
            // expanding). EXPAND_PANEL and HORIZONTAL_DOCK share this roomier row layout.
            PanelStyle.HORIZONTAL_DOCK, PanelStyle.EXPAND_PANEL, PanelStyle.MINI_BUBBLE ->
                LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.filteredApps, key = { it.packageName }) { app ->
                        AppIconItem(app = app, iconSize = 44.dp, showLabel = true, onClick = { onAppClick(app) }, onLongClick = { onAppLongClick(app) })
                    }
                }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIconItem(
    app: RunningApp,
    iconSize: Dp,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = app.icon
        val ringColor = if (app.isCurrentForeground) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(ringColor.copy(alpha = if (app.isCurrentForeground) 0.24f else 1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap(width = 128, height = 128).asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.size(iconSize * 0.8f),
                )
            } else {
                Icon(imageVector = Icons.Filled.Apps, contentDescription = app.label)
            }
        }
        if (showLabel) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.size(width = 52.dp, height = 16.dp),
            )
        }
    }
}
