package com.rahmatsobrian.floatingtaskswitcher.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.rahmatsobrian.floatingtaskswitcher.domain.model.RunningApp

@Composable
fun OverlayRoot(
    state: OverlayUiState,
    onToggleExpanded: () -> Unit,
    onAppClick: (RunningApp) -> Unit,
    onAppLongClick: (RunningApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cornerRadius by animateDpAsState(
        targetValue = state.cornerRadiusDp.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cornerRadius",
    )

    Surface(
        modifier = modifier.alpha(state.opacity),
        shape = RoundedCornerShape(cornerRadius),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            ExpandedPanel(state = state, onAppClick = onAppClick, onAppLongClick = onAppLongClick)
        }
        if (!state.isExpanded) {
            CollapsedBubble(onClick = onToggleExpanded)
        }
    }
}

@Composable
private fun CollapsedBubble(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExpandedPanel(
    state: OverlayUiState,
    onAppClick: (RunningApp) -> Unit,
    onAppLongClick: (RunningApp) -> Unit,
) {
    Column(modifier = Modifier.padding(8.dp)) {
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.filteredApps, key = { it.packageName }) { app ->
                AppIconItem(app = app, onClick = { onAppClick(app) }, onLongClick = { onAppLongClick(app) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIconItem(app: RunningApp, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
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
                .size(44.dp)
                .clip(CircleShape)
                .background(ringColor.copy(alpha = if (app.isCurrentForeground) 0.24f else 1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap(width = 128, height = 128).asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.size(36.dp),
                )
            } else {
                Icon(imageVector = Icons.Filled.Apps, contentDescription = app.label)
            }
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.size(width = 52.dp, height = 16.dp),
        )
    }
}
