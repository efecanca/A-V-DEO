package com.levidor.kehribarvideo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.levidor.kehribarvideo.data.ApiClient
import com.levidor.kehribarvideo.data.CapabilitiesResponse
import com.levidor.kehribarvideo.data.GalleryItemResponse
import com.levidor.kehribarvideo.data.ProjectResponse
import com.levidor.kehribarvideo.data.QcReportResponse
import com.levidor.kehribarvideo.data.ServerConnection
import com.levidor.kehribarvideo.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class StudioPhase { IDLE, UPLOADING, RUNNING, COMPLETED, FAILED }

data class StudioChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val resultId: String? = null,
    val qc: QcReportResponse? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class StudioVideoOptions(
    val aspectRatio: String = "9:16",
    val durationSeconds: Int = 8,
    val motion: String = "walking",
    val camera: String = "tracking"
)

data class StudioUiState(
    val productUri: Uri? = null,
    val modelUri: Uri? = null,
    val promptText: String = "",
    val lastUserPrompt: String = "",
    val messages: List<StudioChatMessage> = listOf(
        StudioChatMessage(
            role = "assistant",
            text = "Merhaba 👋 Ürün fotoğrafını ekle ve hayalindeki moda çekimini anlat. " +
                "Görseli sen onaylamadan videoya geçmem."
        )
    ),
    val phase: StudioPhase = StudioPhase.IDLE,
    val stage: String? = null,
    val stageDetail: String? = null,
    val progress: Int? = null,
    val activeProjectId: String? = null,
    val activeResultId: String? = null,
    val capabilities: CapabilitiesResponse? = null,
    val projects: List<ProjectResponse> = emptyList(),
    val gallery: List<GalleryItemResponse> = emptyList(),
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val isBusy: Boolean
        get() = phase == StudioPhase.UPLOADING || phase == StudioPhase.RUNNING
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState

    init {
        refreshLibrary()
        loadCapabilities()
    }

    fun setProduct(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            productUri = uri,
            errorMessage = null,
            infoMessage = "Ürün referansı eklendi."
        )
    }

    fun setModelReference(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            modelUri = uri,
            errorMessage = null,
            infoMessage = "Manken referansı eklendi."
        )
    }

    fun updatePrompt(value: String) {
        _uiState.value = _uiState.value.copy(promptText = value)
    }

    fun chooseResultForEdit(resultId: String) {
        _uiState.value = _uiState.value.copy(
            activeResultId = resultId,
            promptText = "",
            infoMessage = "Revizyonunu mesaj kutusuna yaz; bu görsel temel alınacak."
        )
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun newProject() {
        val greeting = _uiState.value.messages.first()
        _uiState.value = StudioUiState(
            capabilities = _uiState.value.capabilities,
            projects = _uiState.value.projects,
            gallery = _uiState.value.gallery,
            messages = listOf(greeting)
        )
    }

    fun loadCapabilities() {
        viewModelScope.launch {
            try {
                val baseUrl = settings.serverUrlFlow.first()
                val response = ApiClient.getService(baseUrl).getCapabilities()
                _uiState.value = _uiState.value.copy(capabilities = response)
            } catch (_: Exception) {
                // Studio remains usable; request errors are shown when the user sends.
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            try {
                val baseUrl = settings.serverUrlFlow.first()
                val service = ApiClient.getService(baseUrl)
                val projects = service.getProjects().projects.map { it.withAbsoluteUrls(baseUrl) }
                val gallery = service.getGallery().items.map { item ->
                    item.copy(media_url = absoluteUrl(baseUrl, item.media_url))
                }
                _uiState.value = _uiState.value.copy(projects = projects, gallery = gallery)
            } catch (_: Exception) {
                // Empty/offline states are intentionally handled by the UI.
            }
        }
    }

    fun openProject(projectId: String) {
        viewModelScope.launch {
            try {
                val baseUrl = settings.serverUrlFlow.first()
                val project = ApiClient.getService(baseUrl).getProject(projectId).withAbsoluteUrls(baseUrl)
                val results = project.results.associateBy { it.id }
                val chat = mutableListOf<StudioChatMessage>()
                project.messages.sortedBy { it.created_at ?: 0.0 }.forEach { message ->
                    val result = message.result_id?.let(results::get)
                    chat += StudioChatMessage(
                        id = message.id,
                        role = message.role,
                        text = message.text,
                        mediaUrl = result?.media_url,
                        mediaType = result?.media_type,
                        resultId = result?.id,
                        qc = result?.qc,
                        createdAtMillis = ((message.created_at ?: 0.0) * 1000).toLong()
                    )
                }
                if (chat.isEmpty()) {
                    chat += StudioChatMessage(role = "assistant", text = "Bu proje için sohbet kaydı bulunamadı.")
                }
                val latestImage = project.results.lastOrNull { it.media_type == "image" }
                _uiState.value = _uiState.value.copy(
                    activeProjectId = project.id,
                    activeResultId = latestImage?.id,
                    messages = chat,
                    promptText = "",
                    phase = StudioPhase.IDLE,
                    stage = null,
                    stageDetail = null,
                    progress = null,
                    errorMessage = null,
                    infoMessage = "${project.name} açıldı. Sohbetten revizyon isteyebilirsin."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Proje açılamadı: ${e.message ?: "bağlantı hatası"}"
                )
            }
        }
    }

    fun sendPrompt() {
        val prompt = _uiState.value.promptText.trim()
        if (prompt.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Önce çekim isteğini yaz.")
            return
        }
        startImageRequest(prompt, _uiState.value.activeResultId)
    }

    fun regenerate(resultId: String) {
        val prompt = _uiState.value.lastUserPrompt.ifBlank {
            "Aynı yaratıcı yönü koruyarak yeni bir alternatif üret. Ürünü değiştirme."
        }
        startImageRequest(prompt, resultId)
    }

    private fun startImageRequest(prompt: String, revisionResultId: String?) {
        val snapshot = _uiState.value
        if (snapshot.isBusy) return
        if (revisionResultId == null && snapshot.productUri == null) {
            _uiState.value = snapshot.copy(errorMessage = "Önce Eşarp / Ürün Ekle menüsünden ürün fotoğrafını seç.")
            return
        }

        _uiState.value = snapshot.copy(
            promptText = "",
            lastUserPrompt = prompt,
            messages = snapshot.messages + StudioChatMessage(role = "user", text = prompt),
            phase = StudioPhase.UPLOADING,
            stage = "uploading",
            stageDetail = "Referanslar güvenli biçimde sunucuya gönderiliyor",
            progress = null,
            errorMessage = null,
            infoMessage = null
        )

        viewModelScope.launch {
            try {
                val baseUrl = settings.serverUrlFlow.first()
                val service = ApiClient.getService(baseUrl)
                val response = if (revisionResultId != null && snapshot.activeProjectId != null) {
                    fun text(value: String): RequestBody =
                        value.toRequestBody("text/plain".toMediaTypeOrNull())
                    service.reviseStudioImage(
                        mapOf(
                            "project_id" to text(snapshot.activeProjectId),
                            "result_id" to text(revisionResultId),
                            "prompt" to text(prompt),
                            "aspect_ratio" to text("9:16"),
                            "auto_correct" to text("true")
                        )
                    )
                } else {
                    createInitialStudioJob(service, snapshot, prompt)
                }
                _uiState.value = _uiState.value.copy(
                    activeProjectId = response.project_id,
                    phase = StudioPhase.RUNNING,
                    stage = "queued",
                    stageDetail = "AI ajanları hazırlanıyor",
                    progress = null
                )
                pollJob(baseUrl, response.job_id)
            } catch (e: retrofit2.HttpException) {
                fail(
                    ServerConnection.studioFailureMessage(
                        e.code(), e.response()?.errorBody()?.string()
                    )
                )
            } catch (e: java.net.UnknownHostException) {
                fail("Sunucuya ulaşılamıyor. Ayarlar'daki bağlantıyı kontrol et.")
            } catch (e: java.net.ConnectException) {
                fail("Sunucu bağlantısı kurulamadı. Colab/Kaggle hücresi çalışıyor mu?")
            } catch (e: java.net.SocketTimeoutException) {
                fail("Sunucu isteği zamanında kabul etmedi. Bağlantıyı kontrol edip tekrar dene.")
            } catch (e: Exception) {
                fail("Stüdyo isteği başlatılamadı: ${e.message ?: "bilinmeyen hata"}")
            }
        }
    }

    private suspend fun createInitialStudioJob(
        service: com.levidor.kehribarvideo.data.ApiService,
        snapshot: StudioUiState,
        prompt: String
    ): com.levidor.kehribarvideo.data.StudioGenerateResponse = withContext(Dispatchers.IO) {
        val temporaryFiles = mutableListOf<File>()
        try {
            val product = createImagePart(snapshot.productUri!!, "product_image", temporaryFiles)
            val model = snapshot.modelUri?.let { createImagePart(it, "model_image", temporaryFiles) }
            fun text(value: String): RequestBody =
                value.toRequestBody("text/plain".toMediaTypeOrNull())
            service.generateStudioImage(
                product,
                model,
                mapOf(
                    "prompt" to text(prompt),
                    "aspect_ratio" to text("9:16"),
                    "auto_correct" to text("true")
                )
            )
        } finally {
            temporaryFiles.forEach { it.delete() }
        }
    }

    private fun createImagePart(
        uri: Uri,
        field: String,
        temporaryFiles: MutableList<File>
    ): MultipartBody.Part {
        val context = getApplication<Application>()
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val suffix = when {
            mime.contains("png") -> ".png"
            mime.contains("webp") -> ".webp"
            else -> ".jpg"
        }
        val file = File.createTempFile("fpro_${field}_", suffix, context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: throw java.io.IOException("Seçilen görsel okunamadı.")
        temporaryFiles += file
        val body = file.asRequestBody(mime.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(field, file.name, body)
    }

    fun generateVideo(resultId: String, options: StudioVideoOptions) {
        if (_uiState.value.isBusy) return
        val prompt = _uiState.value.lastUserPrompt.ifBlank { "Lüks moda filmi" }
        _uiState.value = _uiState.value.copy(
            phase = StudioPhase.RUNNING,
            stage = "scene_preparing",
            stageDetail = "Görsel onayı kaydediliyor",
            progress = null,
            errorMessage = null,
            infoMessage = null,
            messages = _uiState.value.messages + StudioChatMessage(
                role = "user",
                text = "Onayladığım görseli ${options.aspectRatio} formatında videoya dönüştür."
            )
        )
        viewModelScope.launch {
            try {
                val baseUrl = settings.serverUrlFlow.first()
                val service = ApiClient.getService(baseUrl)
                service.approveStudioResult(resultId)
                val response = service.generateStudioVideo(
                    resultId,
                    mapOf(
                        "prompt" to prompt,
                        "aspect_ratio" to options.aspectRatio,
                        "duration_seconds" to options.durationSeconds.toString(),
                        "motion" to options.motion,
                        "camera" to options.camera
                    )
                )
                pollJob(baseUrl, response.job_id)
            } catch (e: retrofit2.HttpException) {
                fail(ServerConnection.studioFailureMessage(e.code(), e.response()?.errorBody()?.string()))
            } catch (e: Exception) {
                fail("Video başlatılamadı: ${e.message ?: "bağlantı hatası"}")
            }
        }
    }

    private suspend fun pollJob(initialBaseUrl: String, jobId: String) {
        var baseUrl = initialBaseUrl
        var service = ApiClient.getService(baseUrl)
        var connectionFailures = 0
        while (true) {
            delay(if (connectionFailures == 0) 2500 else 5000)
            val configured = settings.serverUrlFlow.first()
            if (configured != baseUrl) {
                baseUrl = configured
                service = ApiClient.getService(baseUrl)
                connectionFailures = 0
            }
            val status = try {
                service.getStatus(jobId)
            } catch (e: retrofit2.HttpException) {
                val body = e.response()?.errorBody()?.string()
                if (ServerConnection.isMissingJob(e.code(), body)) {
                    fail(
                        "Bu üretim işi sunucu yeniden başladığı için kayboldu. " +
                            "Ürün ve proje kayıtların duruyor; işlemi yeniden başlatabilirsin."
                    )
                    return
                }
                connectionFailures += 1
                _uiState.value = _uiState.value.copy(
                    infoMessage = "Bağlantı geçici olarak kesildi; üretim sunucuda sürüyor olabilir. Yeniden deneniyor…"
                )
                continue
            } catch (_: Exception) {
                connectionFailures += 1
                _uiState.value = _uiState.value.copy(
                    infoMessage = "Sunucuyla bağlantı yenileniyor; işlem kesilmedi."
                )
                continue
            }
            connectionFailures = 0
            _uiState.value = _uiState.value.copy(
                phase = if (status.status == "completed") StudioPhase.COMPLETED else StudioPhase.RUNNING,
                stage = status.stage,
                stageDetail = status.stage_detail,
                progress = status.progress,
                infoMessage = null
            )
            when (status.status) {
                "completed" -> {
                    val mediaType = status.media_type ?: if (status.video_url != null) "video" else "image"
                    val mediaPath = if (mediaType == "video") status.video_url else status.image_url
                    val mediaUrl = absoluteUrl(baseUrl, mediaPath)
                    val resultId = status.result_id
                    _uiState.value = _uiState.value.copy(
                        phase = StudioPhase.COMPLETED,
                        stage = "completed",
                        stageDetail = status.stage_detail,
                        progress = 100,
                        activeProjectId = status.project_id ?: _uiState.value.activeProjectId,
                        activeResultId = if (mediaType == "image") resultId else _uiState.value.activeResultId,
                        messages = _uiState.value.messages + StudioChatMessage(
                            role = "assistant",
                            text = if (mediaType == "video") {
                                "Onayladığın görselden video hazır."
                            } else {
                                "Mankenli görsel hazır. İncele, revizyon iste veya onaylayıp videoya geç."
                            },
                            mediaUrl = mediaUrl,
                            mediaType = mediaType,
                            resultId = resultId,
                            qc = status.qc
                        )
                    )
                    refreshLibrary()
                    return
                }
                "failed" -> {
                    fail(status.error ?: status.stage_detail ?: "Üretim başarısız oldu.")
                    refreshLibrary()
                    return
                }
            }
        }
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(
            phase = StudioPhase.FAILED,
            stage = "failed",
            stageDetail = null,
            progress = null,
            errorMessage = message
        )
    }

    private fun ProjectResponse.withAbsoluteUrls(baseUrl: String): ProjectResponse = copy(
        product_url = absoluteUrl(baseUrl, product_url),
        model_url = absoluteUrl(baseUrl, model_url),
        thumbnail_url = absoluteUrl(baseUrl, thumbnail_url),
        results = results.map { it.copy(media_url = absoluteUrl(baseUrl, it.media_url)) }
    )

    private fun absoluteUrl(baseUrl: String, path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http://") || path.startsWith("https://") -> path
        else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}
