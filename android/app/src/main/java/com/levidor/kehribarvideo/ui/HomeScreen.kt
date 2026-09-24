package com.levidor.kehribarvideo.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.levidor.kehribarvideo.R
import com.levidor.kehribarvideo.data.generationStageLabel
import com.levidor.kehribarvideo.util.FileUtils
import com.levidor.kehribarvideo.viewmodel.GenerationPhase
import com.levidor.kehribarvideo.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenAdMode: () -> Unit,
    onOpenResults: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScopeCompat()

    LaunchedEffect(Unit) {
        viewModel.configureAsNormalMode()
        viewModel.loadCapabilities()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.addProducts(listOf(uri))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenAdMode) {
                        Icon(Icons.Filled.Campaign, contentDescription = "Reklam Videosu")
                    }
                    IconButton(onClick = onOpenResults) {
                        Icon(Icons.Filled.History, contentDescription = "Sonuçlar")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
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

            val isTextToVideo = state.options.mode == "text_to_video"

            // ---- Ürün fotoğrafı seçim alanı (yalnızca image_to_video modunda) ----
            if (!isTextToVideo) {
                val selectedUri = state.products.firstOrNull()?.uri
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedUri != null) {
                        AsyncImage(
                            model = selectedUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.AddAPhoto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(stringResource(R.string.pick_photo), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.pick_photo_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            GenerationOptionsPanel(
                options = state.options,
                capabilities = state.capabilities,
                showModeToggle = true,
                onChange = { newOptions -> viewModel.updateOptions { newOptions } }
            )

            val isBusy = state.phase == GenerationPhase.UPLOADING ||
                state.phase == GenerationPhase.QUEUED ||
                state.phase == GenerationPhase.PROCESSING

            val canSubmit = !isBusy && (isTextToVideo || state.products.isNotEmpty())

            if (!isTextToVideo) {
                Button(
                    onClick = { viewModel.prepareReferenceImage() },
                    enabled = !isBusy && !state.isPreparingReference && state.products.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (state.isPreparingReference) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  Mankenli görsel hazırlanıyor…")
                    } else {
                        Text("Mankenli Görsel Oluştur")
                    }
                }

                state.referenceImageUrl?.let { referenceUrl ->
                    Text("Mankenli önizleme", style = MaterialTheme.typography.titleMedium)
                    AsyncImage(
                        model = referenceUrl,
                        contentDescription = "Mankenli eşarp önizlemesi",
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        "Eşarp, desen ve bağlama doğruysa aşağıdan videoya geçebilirsin.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Button(
                onClick = { viewModel.generateVideo() },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.generate_button))
                }
            }

            if (isBusy) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val label = generationStageLabel(state.stage, state.progress)
                    Text(label, style = MaterialTheme.typography.bodyMedium)
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
                            try {
                                FileUtils.saveVideoToGallery(context, file)
                            } catch (_: Exception) { }
                        }
                    }) {
                        Text(stringResource(R.string.save_button))
                    }
                    OutlinedButton(onClick = {
                        val intent = FileUtils.buildShareIntent(context, file)
                        context.startActivity(Intent.createChooser(intent, "Videoyu paylaş"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  " + stringResource(R.string.share_button))
                    }
                }
                OutlinedButton(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.new_video_button))
                }
            }
        }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
