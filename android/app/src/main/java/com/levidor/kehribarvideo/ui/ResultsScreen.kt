package com.levidor.kehribarvideo.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.levidor.kehribarvideo.data.JobRecord
import com.levidor.kehribarvideo.data.generationStageLabel
import com.levidor.kehribarvideo.data.optionLabel
import com.levidor.kehribarvideo.util.FileUtils
import com.levidor.kehribarvideo.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val history by viewModel.jobHistoryRepository.historyFlow.collectAsState(initial = emptyList())
    var expandedJobId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshPendingJobs() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sonuçlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshPendingJobs() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Yenile")
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Henüz üretilmiş bir video yok.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history, key = { it.jobId }) { record ->
                JobHistoryCard(
                    record = record,
                    expanded = expandedJobId == record.jobId,
                    onToggleExpand = {
                        expandedJobId = if (expandedJobId == record.jobId) null else record.jobId
                    }
                )
            }
        }
    }
}

@Composable
private fun JobHistoryCard(record: JobRecord, expanded: Boolean, onToggleExpand: () -> Unit) {
    val context = LocalContext.current
    val dateStr = remember(record.createdAtMillis) {
        SimpleDateFormat("d MMM, HH:mm", Locale("tr")).format(Date(record.createdAtMillis))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .clickable(enabled = record.status == "completed") { onToggleExpand() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusIcon(record.status)
            Column(Modifier.weight(1f)) {
                Text(record.productLabel, style = MaterialTheme.typography.titleMedium)
                Text(
                    "$dateStr · ${optionLabel(record.mode)} · ${record.durationSeconds}sn · ${record.aspectRatio}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (record.status == "processing" || record.status == "queued") {
                val stage = record.stage ?: if (record.status == "queued") "queued" else "generating"
                Text(
                    generationStageLabel(stage, record.progress),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (record.status == "processing" || record.status == "queued") {
            record.stageDetail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        if (record.status == "failed" && record.errorMessage != null) {
            Text(
                record.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (expanded && record.status == "completed" && record.localVideoPath != null) {
            val file = File(record.localVideoPath)
            if (file.exists()) {
                VideoPlayerView(uri = Uri.fromFile(file))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        val intent = FileUtils.buildShareIntent(context, file)
                        context.startActivity(Intent.createChooser(intent, "Videoyu paylaş"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("  Paylaş")
                    }
                }
            } else {
                Text(
                    "Video dosyası cihazda bulunamadı (önbellek temizlenmiş olabilir).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(status: String) {
    when (status) {
        "completed" -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        "failed" -> Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        "processing" -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else -> Icon(Icons.Filled.HourglassEmpty, contentDescription = null)
    }
}
