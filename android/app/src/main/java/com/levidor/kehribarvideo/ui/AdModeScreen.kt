package com.levidor.kehribarvideo.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.levidor.kehribarvideo.data.generationStageLabel
import com.levidor.kehribarvideo.util.FileUtils
import com.levidor.kehribarvideo.viewmodel.GenerationPhase
import com.levidor.kehribarvideo.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdModeScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScopeCompat()

    // Bu ekrana her girişte "reklam modu" ayarlarını (10'a kadar ürün, 15sn/9:16
    // varsayılanı) uygula. DisposableEffect ile yalnızca bu ekran açıkken geçerli.
    DisposableEffect(Unit) {
        viewModel.configureAsAdMode()
        viewModel.loadCapabilities()
        onDispose { }
    }

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addProducts(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reklam Videosu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            Text(
                "Birden fazla ürün fotoğrafı seçin; aşağıdaki anahtar açıkken hepsi " +
                    "TEK bir reklam videosuna sahneler halinde dağıtılır, kapalıyken " +
                    "her ürün için AYRI bir video üretilir.",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tek video halinde birleştir", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = state.combineIntoSingleVideo,
                    onCheckedChange = { viewModel.setCombineIntoSingleVideo(it) }
                )
            }

            // ---- Ürün küçük önizlemeleri ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.products.forEachIndexed { index, product ->
                    Box(modifier = Modifier.size(96.dp)) {
                        AsyncImage(
                            model = product.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { viewModel.removeProduct(product.id) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Kaldır", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Row(modifier = Modifier.align(Alignment.BottomCenter)) {
                            IconButton(
                                onClick = { viewModel.moveProduct(index, (index - 1).coerceAtLeast(0)) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "Sola taşı", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            IconButton(
                                onClick = { viewModel.moveProduct(index, (index + 1).coerceAtMost(state.products.size - 1)) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.ArrowForwardIos, contentDescription = "Sağa taşı", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp))
                        .clickable { multiPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Ürün ekle", tint = MaterialTheme.colorScheme.primary)
                }
            }

            GenerationOptionsPanel(
                options = state.options,
                capabilities = state.capabilities,
                showModeToggle = false,
                onChange = { newOptions -> viewModel.updateOptions { newOptions } }
            )

            val isBusy = state.phase == GenerationPhase.UPLOADING ||
                state.phase == GenerationPhase.QUEUED ||
                state.phase == GenerationPhase.PROCESSING

            Button(
                onClick = { viewModel.generateVideo() },
                enabled = !isBusy && state.products.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text("Reklam Videosu Oluştur")
                }
            }

            if (isBusy) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        generationStageLabel(state.stage, state.progress),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.stageDetail?.takeIf { it.isNotBlank() }?.let { detail ->
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    val realProgress = state.progress
                    if (realProgress != null) {
                        LinearProgressIndicator(
                            progress = { realProgress.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            state.infoMessage?.let { message ->
                Snackbar(action = {
                    OutlinedButton(onClick = { viewModel.clearError() }) { Text("Tamam") }
                }) { Text(message) }
            }

            state.errorMessage?.let { message ->
                Snackbar(action = {
                    OutlinedButton(onClick = { viewModel.clearError() }) { Text("Kapat") }
                }) { Text(message) }
            }

            if (state.phase == GenerationPhase.COMPLETED && state.localVideoFile != null) {
                val file = state.localVideoFile!!
                Text("Video hazır 🎬", style = MaterialTheme.typography.titleMedium)
                VideoPlayerView(uri = Uri.fromFile(file))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        scope.launch {
                            try { FileUtils.saveVideoToGallery(context, file) } catch (_: Exception) { }
                        }
                    }) { Text("Kaydet") }
                    OutlinedButton(onClick = {
                        val intent = FileUtils.buildShareIntent(context, file)
                        context.startActivity(Intent.createChooser(intent, "Videoyu paylaş"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Paylaş")
                    }
                }
                OutlinedButton(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Yeni Reklam Videosu")
                }
            }
        }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
