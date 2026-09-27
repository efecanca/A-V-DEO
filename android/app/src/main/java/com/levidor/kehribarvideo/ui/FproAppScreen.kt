package com.levidor.kehribarvideo.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.levidor.kehribarvideo.data.ApiClient
import com.levidor.kehribarvideo.data.GalleryItemResponse
import com.levidor.kehribarvideo.data.ProjectResponse
import com.levidor.kehribarvideo.data.ServerConnection
import com.levidor.kehribarvideo.data.SettingsRepository
import com.levidor.kehribarvideo.data.generationStageLabel
import com.levidor.kehribarvideo.ui.theme.Champagne
import com.levidor.kehribarvideo.ui.theme.Emerald
import com.levidor.kehribarvideo.ui.theme.Obsidian
import com.levidor.kehribarvideo.util.FileUtils
import com.levidor.kehribarvideo.viewmodel.StudioChatMessage
import com.levidor.kehribarvideo.viewmodel.StudioUiState
import com.levidor.kehribarvideo.viewmodel.StudioVideoOptions
import com.levidor.kehribarvideo.viewmodel.StudioViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FproDestination(val label: String, val icon: ImageVector) {
    STUDIO("Stüdyo", Icons.Filled.AutoAwesome),
    PROJECTS("Projeler", Icons.Filled.FolderOpen),
    GALLERY("Galeri", Icons.Filled.GridView),
    SETTINGS("Ayarlar", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FproAppScreen(viewModel: StudioViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var destination by rememberSaveable { mutableStateOf(FproDestination.STUDIO) }
    var attachmentSheet by remember { mutableStateOf(false) }
    var videoResultId by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val productPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::setProduct) }
    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::setModelReference) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let(viewModel::setProduct) }

    fun openCamera() {
        val directory = File(context.cacheDir, "videos").apply { mkdirs() }
        val file = File(directory, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val message = state.errorMessage ?: state.infoMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FproTopBar(
                showNewProject = destination == FproDestination.STUDIO,
                onNewProject = viewModel::newProject
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                FproDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = {
                            destination = item
                            if (item == FproDestination.PROJECTS || item == FproDestination.GALLERY) {
                                viewModel.refreshLibrary()
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Obsidian,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (destination) {
            FproDestination.STUDIO -> StudioScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onOpenAttachments = { attachmentSheet = true },
                onPromptChange = viewModel::updatePrompt,
                onSend = viewModel::sendPrompt,
                onEdit = viewModel::chooseResultForEdit,
                onRegenerate = viewModel::regenerate,
                onVideo = { videoResultId = it }
            )
            FproDestination.PROJECTS -> ProjectsScreen(
                projects = state.projects,
                modifier = Modifier.padding(padding),
                onNewProject = {
                    viewModel.newProject()
                    destination = FproDestination.STUDIO
                },
                onOpenProject = {
                    viewModel.openProject(it)
                    destination = FproDestination.STUDIO
                }
            )
            FproDestination.GALLERY -> GalleryScreen(
                gallery = state.gallery,
                modifier = Modifier.padding(padding)
            )
            FproDestination.SETTINGS -> FproSettingsScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onConnectionRestored = {
                    viewModel.loadCapabilities()
                    viewModel.refreshLibrary()
                }
            )
        }
    }

    if (attachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { attachmentSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null
        ) {
            AttachmentSheet(
                onClose = { attachmentSheet = false },
                onProduct = {
                    attachmentSheet = false
                    productPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onModel = {
                    attachmentSheet = false
                    modelPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onCamera = {
                    attachmentSheet = false
                    openCamera()
                },
                onGallery = {
                    attachmentSheet = false
                    productPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
    }

    videoResultId?.let { resultId ->
        ModalBottomSheet(
            onDismissRequest = { videoResultId = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            VideoOptionsSheet(
                onCancel = { videoResultId = null },
                onCreate = { options ->
                    videoResultId = null
                    viewModel.generateVideo(resultId, options)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FproTopBar(showNewProject: Boolean, onNewProject: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FPRO", style = MaterialTheme.typography.headlineMedium)
                Text(" AI", style = MaterialTheme.typography.headlineMedium, color = Champagne)
            }
        },
        actions = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                contentColor = Obsidian
            ) {
                Text(
                    "PRO",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (showNewProject) {
                IconButton(onClick = onNewProject) {
                    Icon(Icons.Filled.Add, contentDescription = "Yeni proje")
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
private fun StudioScreen(
    state: StudioUiState,
    modifier: Modifier,
    onOpenAttachments: () -> Unit,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onEdit: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onVideo: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.stage) {
        val extra = if (state.isBusy) 1 else 0
        val target = (state.messages.size + extra - 1).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.messages.size <= 1 && state.activeProjectId == null) {
                item { StudioWelcomeCard() }
                item { InspirationStrip(onPromptChange) }
            }
            if (state.productUri != null || state.modelUri != null) {
                item { ReferenceStrip(state.productUri, state.modelUri) }
            }
            items(state.messages, key = { it.id }) { message ->
                ChatMessageCard(
                    message = message,
                    onEdit = onEdit,
                    onRegenerate = onRegenerate,
                    onVideo = onVideo
                )
            }
            if (state.isBusy) {
                item { RealStageCard(state.stage, state.stageDetail, state.progress) }
            }
        }

        StudioComposer(
            value = state.promptText,
            busy = state.isBusy,
            hasProduct = state.productUri != null || state.activeProjectId != null,
            onValueChange = onPromptChange,
            onAdd = onOpenAttachments,
            onSend = onSend
        )
    }
}

@Composable
private fun StudioWelcomeCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = Champagne.copy(alpha = 0.14f)) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Champagne,
                    modifier = Modifier.padding(10.dp).size(24.dp)
                )
            }
            Text("Bugün hangi çekimi yapalım?", style = MaterialTheme.typography.titleLarge)
            Text(
                "Gerçek eşarbını yükle, sahneyi anlat. FPRO ajanları ürünü analiz eder, " +
                    "mankenli görseli hazırlar ve onayını bekler.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = Emerald, modifier = Modifier.size(18.dp))
                Text("Ürün koruma modu açık", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun InspirationStrip(onPromptChange: (String) -> Unit) {
    val ideas = listOf(
        "Venedik" to "Venedik'te yürüyen lüks moda çekimi oluştur.",
        "Kyoto" to "Arka planı Kyoto sokakları yap, eşarbı değiştirme.",
        "Stüdyo" to "Yumuşak ışıklı premium stüdyo kampanyası oluştur.",
        "Editoryal" to "Modern mimaride güçlü bir moda editoryali oluştur."
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Çekim fikirleri", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ideas) { idea ->
                Card(
                    modifier = Modifier.width(140.dp).clickable { onPromptChange(idea.second) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Champagne)
                        Text(idea.first, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceStrip(productUri: Uri?, modelUri: Uri?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Referanslar", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            productUri?.let { ReferenceThumbnail(it, "Ürün") }
            modelUri?.let { ReferenceThumbnail(it, "Manken") }
        }
    }
}

@Composable
private fun ReferenceThumbnail(uri: Uri, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = uri,
            contentDescription = label,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
                .border(1.dp, Champagne.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ChatMessageCard(
    message: StudioChatMessage,
    onEdit: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onVideo: (String) -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = if (isUser) Champagne.copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface,
            contentColor = if (isUser) Obsidian else MaterialTheme.colorScheme.onSurface,
            shape = if (isUser) {
                RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
            } else {
                RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
            },
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = if (isUser) Modifier.fillMaxWidth(0.88f) else Modifier.fillMaxWidth()
        ) {
            Text(message.text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
        }
        if (message.mediaUrl != null && message.resultId != null) {
            ResultCard(
                message = message,
                onEdit = { onEdit(message.resultId) },
                onRegenerate = { onRegenerate(message.resultId) },
                onVideo = { onVideo(message.resultId) }
            )
        }
    }
}

@Composable
private fun ResultCard(
    message: StudioChatMessage,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onVideo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (message.mediaType == "video") {
                VideoPlayerView(Uri.parse(message.mediaUrl))
            } else {
                AsyncImage(
                    model = message.mediaUrl,
                    contentDescription = "Oluşturulan moda görseli",
                    modifier = Modifier.fillMaxWidth().aspectRatio(9f / 14f).heightIn(max = 560.dp),
                    contentScale = ContentScale.Crop
                )
            }
            message.qc?.overall_score?.let { score ->
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = Emerald, modifier = Modifier.size(18.dp))
                    Text("AI ürün benzerliği: %$score · yaklaşık", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (message.mediaType == "image") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { ResultAction("Düzenle", Icons.Filled.Edit, onEdit) }
                    item { ResultAction("Tekrar Oluştur", Icons.Filled.Refresh, onRegenerate) }
                    item { ResultAction("Videoya Dönüştür", Icons.Filled.MovieCreation, onVideo, primary = true) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val file = FileUtils.downloadMediaToCache(
                                    context, message.mediaUrl!!, message.resultId!!, message.mediaType ?: "image"
                                )
                                if (message.mediaType == "video") {
                                    FileUtils.saveVideoToGallery(context, file)
                                } else {
                                    FileUtils.saveImageToGallery(context, file)
                                }
                                Toast.makeText(context, "Galeriye kaydedildi", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "Kaydedilemedi", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Kaydet")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val file = FileUtils.downloadMediaToCache(
                                    context, message.mediaUrl!!, message.resultId!!, message.mediaType ?: "image"
                                )
                                val intent = FileUtils.buildMediaShareIntent(
                                    context, file, message.mediaType ?: "image"
                                )
                                context.startActivity(Intent.createChooser(intent, "FPRO AI ile paylaş"))
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "Paylaşılamadı", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Paylaş")
                }
            }
        }
    }
}

@Composable
private fun ResultAction(label: String, icon: ImageVector, onClick: () -> Unit, primary: Boolean = false) {
    if (primary) {
        Button(onClick = onClick) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  $label")
        }
    } else {
        FilledTonalButton(onClick = onClick) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  $label")
        }
    }
}

@Composable
private fun RealStageCard(stage: String?, detail: String?, progress: Int?) {
    val imageStages = listOf(
        "product_analyzing" to "Ürün analiz ediliyor",
        "scene_preparing" to "Manken sahnesi hazırlanıyor",
        "product_applying" to "Eşarp uygulanıyor",
        "image_enhancing" to "Görsel iyileştiriliyor",
        "quality_checking" to "Kalite kontrolü",
        "completed" to "Hazır"
    )
    val videoStages = listOf(
        "scene_preparing" to "Onaylı görsel hazırlanıyor",
        "generating" to "Video oluşturuluyor",
        "encoding" to "Video sonlandırılıyor",
        "completed" to "Hazır"
    )
    val stages = if (stage in setOf("generating", "encoding")) videoStages else imageStages
    val currentIndex = stages.indexOfFirst { it.first == stage }.coerceAtLeast(0)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Champagne.copy(alpha = 0.32f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Champagne)
                Text("AI ajanları çalışıyor", style = MaterialTheme.typography.titleSmall)
            }
            stages.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        index < currentIndex -> Icon(Icons.Filled.Check, null, tint = Emerald, modifier = Modifier.size(18.dp))
                        index == currentIndex -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Champagne
                        )
                        else -> Box(
                            Modifier.size(10.dp).background(MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                    Text(
                        item.second,
                        color = if (index <= currentIndex) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            progress?.let {
                LinearProgressIndicator(
                    progress = { it.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    color = Champagne
                )
                Text("%${it.coerceIn(0, 100)}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun StudioComposer(
    value: String,
    busy: Boolean,
    hasProduct: Boolean,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onAdd,
                enabled = !busy,
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Görsel ekle")
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
                enabled = !busy,
                placeholder = {
                    Text(if (hasProduct) "Revizyonunu veya sahneyi yaz…" else "Bir mesaj yaz veya görsel ekle…")
                },
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Champagne,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            IconButton(
                onClick = onSend,
                enabled = !busy && value.isNotBlank(),
                modifier = Modifier.background(
                    if (!busy && value.isNotBlank()) Champagne else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Gönder",
                    tint = if (!busy && value.isNotBlank()) Obsidian else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AttachmentSheet(
    onClose: () -> Unit,
    onProduct: () -> Unit,
    onModel: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    val options = listOf(
        Triple("Eşarp / Ürün Ekle", "Gerçek ürün fotoğrafını yükle", Icons.Filled.AddAPhoto),
        Triple("Manken Referansı", "İsteğe bağlı manken fotoğrafı", Icons.Filled.Image),
        Triple("Fotoğraf Çek", "Kamerayla yeni fotoğraf", Icons.Filled.PhotoCamera),
        Triple("Galeriden Seç", "Telefondan ürün görseli seç", Icons.Filled.Collections)
    )
    val actions = listOf(onProduct, onModel, onCamera, onGallery)
    Column(
        Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Görsel Ekle", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Kapat") }
        }
        options.forEachIndexed { index, option ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = actions[index]),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Champagne.copy(alpha = 0.16f)) {
                        Icon(option.third, null, tint = Champagne, modifier = Modifier.padding(11.dp).size(22.dp))
                    }
                    Column {
                        Text(option.first, style = MaterialTheme.typography.titleSmall)
                        Text(option.second, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoOptionsSheet(onCancel: () -> Unit, onCreate: (StudioVideoOptions) -> Unit) {
    var options by remember { mutableStateOf(StudioVideoOptions()) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Video Oluştur", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Kapat") }
        }
        OptionSection("En boy oranı", listOf("9:16", "1:1", "16:9"), options.aspectRatio) {
            options = options.copy(aspectRatio = it)
        }
        OptionSection("Süre", listOf("4 sn", "6 sn", "8 sn"), "${options.durationSeconds} sn") {
            options = options.copy(durationSeconds = it.substringBefore(" ").toInt())
        }
        OptionSection("Hareket", listOf("Yürüme", "Dönüş", "Yakın Plan"), motionLabel(options.motion)) {
            options = options.copy(
                motion = when (it) { "Dönüş" -> "turning"; "Yakın Plan" -> "close_up"; else -> "walking" }
            )
        }
        OptionSection("Kamera", listOf("Sabit", "Takip", "Sinematik"), cameraLabel(options.camera)) {
            options = options.copy(
                camera = when (it) { "Sabit" -> "static"; "Sinematik" -> "cinematic"; else -> "tracking" }
            )
        }
        if (options.aspectRatio == "1:1") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = Champagne, modifier = Modifier.size(18.dp))
                Text("1:1, destekleyen uygun fallback provider ile üretilecektir.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = { onCreate(options) },
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Icon(Icons.Filled.MovieCreation, contentDescription = null)
            Text("  Onaylı Görselden Video Oluştur")
        }
    }
}

private fun motionLabel(value: String) = when (value) {
    "turning" -> "Dönüş"
    "close_up" -> "Yakın Plan"
    else -> "Yürüme"
}

private fun cameraLabel(value: String) = when (value) {
    "static" -> "Sabit"
    "cinematic" -> "Sinematik"
    else -> "Takip"
}

@Composable
private fun OptionSection(title: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(values) { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(value) },
                    leadingIcon = if (value == selected) {
                        { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun ProjectsScreen(
    projects: List<ProjectResponse>,
    modifier: Modifier,
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Projelerim", style = MaterialTheme.typography.headlineMedium)
                Text("Moda çalışmaların tek yerde", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onNewProject, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Text("  Yeni")
            }
        }
        if (projects.isEmpty()) {
            PremiumEmptyState(
                icon = Icons.Filled.FolderOpen,
                title = "İlk projen burada görünecek",
                message = "Stüdyo'da bir ürün ekleyip çekimini başlat."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(154.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(project) { onOpenProject(project.id) }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectResponse, onClick: () -> Unit) {
    val date = remember(project.updated_at) {
        SimpleDateFormat("d MMM, HH:mm", Locale("tr")).format(Date((project.updated_at * 1000).toLong()))
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.82f)) {
                AsyncImage(
                    model = project.thumbnail_url ?: project.product_url,
                    contentDescription = project.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = CircleShape,
                    color = Obsidian.copy(alpha = 0.72f)
                ) {
                    val hasVideo = project.results.any { it.media_type == "video" }
                    Icon(
                        if (hasVideo) Icons.Filled.PlayArrow else Icons.Filled.Image,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp).size(16.dp)
                    )
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(projectStatusLabel(project.status), color = Champagne, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun projectStatusLabel(status: String) = when (status) {
    "ready" -> "Görsel hazır"
    "video_ready" -> "Video hazır"
    "processing" -> "Üretiliyor"
    "failed" -> "Dikkat gerekli"
    else -> "Taslak"
}

@Composable
private fun GalleryScreen(gallery: List<GalleryItemResponse>, modifier: Modifier) {
    var filter by rememberSaveable { mutableStateOf("Tümü") }
    val filtered = gallery.filter {
        filter == "Tümü" || (filter == "Görseller" && it.media_type == "image") ||
            (filter == "Videolar" && it.media_type == "video")
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Galeri", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
        LazyRow(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("Tümü", "Görseller", "Videolar")) { item ->
                FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item) })
            }
        }
        if (filtered.isEmpty()) {
            PremiumEmptyState(
                icon = Icons.Filled.GridView,
                title = "Galeri henüz boş",
                message = "Tamamlanan görseller ve videolar burada yer alacak."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(145.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filtered, key = { it.id }) { item -> GalleryCard(item) }
            }
        }
    }
}

@Composable
private fun GalleryCard(item: GalleryItemResponse) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.78f)) {
            AsyncImage(
                model = item.media_url,
                contentDescription = item.project_name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                shape = CircleShape,
                color = Obsidian.copy(alpha = 0.7f)
            ) {
                Icon(
                    if (item.media_type == "video") Icons.Filled.PlayArrow else Icons.Filled.Image,
                    null,
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp).size(15.dp)
                )
            }
        }
        Text(
            item.project_name,
            modifier = Modifier.padding(10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun PremiumEmptyState(icon: ImageVector, title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = Champagne.copy(alpha = 0.14f)) {
            Icon(icon, null, tint = Champagne, modifier = Modifier.padding(18.dp).size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 18.dp))
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun FproSettingsScreen(
    state: StudioUiState,
    modifier: Modifier,
    onConnectionRestored: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var connectionState by remember { mutableStateOf("Kontrol edilmedi") }
    var testing by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { url = repository.serverUrlFlow.first() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Ayarlar", style = MaterialTheme.typography.headlineMedium)
            Text("Stüdyo deneyimi ve güvenli bağlantı", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Emerald.copy(alpha = 0.14f)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Emerald, modifier = Modifier.padding(10.dp))
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Yaratıcı Bulut", style = MaterialTheme.typography.titleMedium)
                            Text(connectionState, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(
                        enabled = !testing,
                        onClick = {
                            scope.launch {
                                testing = true
                                connectionState = "Bağlantı kontrol ediliyor…"
                                try {
                                    val normalized = repository.setServerUrl(url)
                                    url = normalized
                                    val response = ApiClient.getService(normalized).health()
                                    connectionState = if (response.isSuccessful && response.body()?.status == "ok") {
                                        onConnectionRestored()
                                        "Bağlı ve hazır ✓"
                                    } else {
                                        ServerConnection.failureMessage(
                                            response.code(),
                                            response.headers()["Ngrok-Error-Code"],
                                            response.errorBody()?.string()
                                        )
                                    }
                                } catch (e: Exception) {
                                    connectionState = "Bağlantı kurulamadı: ${e.message ?: "adres kontrol edilmeli"}"
                                } finally {
                                    testing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (testing) "Kontrol ediliyor…" else "Bağlantıyı Kontrol Et")
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Görünüm", style = MaterialTheme.typography.titleMedium)
                    Text("Koyu ve açık tema telefonunun sistem ayarını otomatik izler.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            TextButton(onClick = { advanced = !advanced }) {
                Icon(if (advanced) Icons.Filled.Close else Icons.Filled.MoreVert, null)
                Text("  Gelişmiş / Tanılama")
            }
            AnimatedVisibility(advanced) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Backend adresi") },
                        placeholder = { Text("https://…ngrok-free.app/") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    url = repository.setServerUrl(url)
                                    connectionState = "Adres kaydedildi; bağlantıyı kontrol et."
                                } catch (e: Exception) {
                                    connectionState = e.message ?: "Adres kaydedilemedi"
                                }
                            }
                        }
                    ) { Text("Adresi Kaydet") }
                    Text("Provider durumu", style = MaterialTheme.typography.titleSmall)
                    state.capabilities?.studio?.providers?.forEach { provider ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(provider.display_name, modifier = Modifier.weight(1f))
                            Text(
                                if (provider.available) "Hazır" else "Yapılandırılmadı",
                                color = if (provider.available) Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Text(
                        "API anahtarları yalnız backend secret ortamında tutulur; uygulama paketine eklenmez.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
