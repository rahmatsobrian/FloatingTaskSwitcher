package com.rahmatsobrian.floatingtaskswitcher.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rahmatsobrian.floatingtaskswitcher.BuildConfig
import com.rahmatsobrian.floatingtaskswitcher.R
import com.rahmatsobrian.floatingtaskswitcher.ui.components.AppTopBar

private data class AboutFeature(val icon: ImageVector, val title: String, val description: String)

private val features = listOf(
    AboutFeature(
        icon = Icons.Filled.Bolt,
        title = "Root / Shizuku / Standard otomatis",
        description = "Memilih mode terbaik yang tersedia di perangkat tanpa perlu diatur manual.",
    ),
    AboutFeature(
        icon = Icons.Filled.Shield,
        title = "Tanpa API tersembunyi",
        description = "Seluruh fitur dibangun di atas API resmi Android - tidak ada exploit atau hooking framework.",
    ),
    AboutFeature(
        icon = Icons.Filled.PrivacyTip,
        title = "Tidak ada tracking",
        description = "Tidak mengumpulkan data pengguna, tidak ada analytics, tidak ada iklan. Semua data tersimpan lokal di perangkat.",
    ),
    AboutFeature(
        icon = Icons.Filled.Lock,
        title = "Root tidak pernah diminta otomatis",
        description = "Prompt izin root hanya muncul setelah kamu menekan tombol di Permission Manager.",
    ),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { AppTopBar(title = "Tentang Aplikasi") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                    Text(
                        text = "Floating Task Switcher",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "Versi ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    text = "Multitasking assistant berbentuk floating overlay - alternatif yang lebih cepat daripada membuka Recent Apps bawaan Android.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(features) { feature ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = feature.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(text = feature.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = feature.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Dibuat oleh RahmatSobrian",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
