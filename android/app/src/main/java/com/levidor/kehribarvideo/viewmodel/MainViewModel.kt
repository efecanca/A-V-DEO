package com.levidor.kehribarvideo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.levidor.kehribarvideo.data.ApiClient
import com.levidor.kehribarvideo.data.CapabilitiesResponse
import com.levidor.kehribarvideo.data.JobHistoryRepository
import com.levidor.kehribarvideo.data.JobRecord
import com.levidor.kehribarvideo.data.ProductItem
import com.levidor.kehribarvideo.data.SettingsRepository
import com.levidor.kehribarvideo.util.FileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

enum class GenerationPhase {
    IDLE, UPLOADING, QUEUED, PROCESSING, COMPLETED, FAILED
}

data class GenerationOptions(
    val mode: String = "image_to_video",
    val durationSeconds: Int = 5,
    val aspectRatio: String = "16:9",
    val quality: String = "standard",
    val motion: String = "natural",
    val camera: String = "static",
    val stylePreset: String? = null,
    val productProtection: Boolean = true,
    val customPrompt: String = "",
    val customNegativePrompt: String = ""
)

data class MainUiState(
    val products: List<ProductItem> = emptyList(),
    val options: GenerationOptions = GenerationOptions(),
    val capabilities: CapabilitiesResponse? = null,
    val phase: GenerationPhase = GenerationPhase.IDLE,
    val progress: Int = 0,
    val localVideoFile: File? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isAdScreen: Boolean = false,
    val combineIntoSingleVideo: Boolean = true
)

/**
 * Hem normal üretim ekranını hem de "Reklam Videosu" modunu besler.
 * [isAdScreen] yalnızca UI'ın hangi ekranda olduğunu belirtir (çoklu seçim
 * limiti gibi); asıl "tek video mu, ayrı video mu" kararı
 * [combineIntoSingleVideo] ile verilir ve backend'e ad_mode olarak gönderilir.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    val jobHistoryRepository = JobHistoryRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun configureAsAdMode() {
        _uiState.value = _uiState.value.copy(
            isAdScreen = true,
            options = _uiState.value.options.copy(durationSeconds = 15, aspectRatio = "9:16")

        )
    }

    /** Ana ekrana her dönüşte reklam moduna özgü bayrakların sızmasını önler. */
    fun configureAsNormalMode() {
        _uiState.value = _uiState.value.copy(isAdScreen = false)
    }

    fun loadCapabilities() {
        viewModelScope.launch {
            try {
                val baseUrl = settingsRepository.serverUrlFlow.first()
                val caps = ApiClient.getService(baseUrl).getCapabilities()
                _uiState.value = _uiState.value.copy(capabilities = caps)
            } catch (_: Exception) {
                // Sunucuya henüz ulaşılamıyor olabilir (ör. Colab başlamadan önce);
                // UI, LocalCapabilityDefaults ile devam eder, bu sessizce yoksayılır.
            }
        }
    }

    fun setCombineIntoSingleVideo(value: Boolean) {
        _uiState.value = _uiState.value.copy(combineIntoSingleVideo = value)
    }

    /**
     * Sonuçlar ekranı açıldığında / yenile denildiğinde, hâlâ "queued" veya
     * "processing" durumunda görünen geçmiş kayıtları TEK SEFERLİK sorgular.
     * Sürekli arka plan polling'i yalnızca üretimi bu oturumda BAŞLATAN
     * [pollAndTrack] tarafından yapılır; bu fonksiyon, uygulama kapatılıp
     * yeniden açıldığında yarım kalmış görünen işleri güncellemek içindir.
     */
    fun refreshPendingJobs() {
        viewModelScope.launch {
            val baseUrl = settingsRepository.serverUrlFlow.first()
            val service = ApiClient.getService(baseUrl)
            val context = getApplication<android.app.Application>()
            val pending = jobHistoryRepository.historyFlow.first()
                .filter { it.status == "queued" || it.status == "processing" }

            pending.forEach { record ->
                try {
                    val status = service.getStatus(record.jobId)
                    when (status.status) {
                        "completed" -> {
                            val videoUrl = status.video_url
                            if (videoUrl != null) {
                                val absoluteUrl = if (videoUrl.startsWith("http")) videoUrl else baseUrl.trimEnd('/') + videoUrl
                                val localFile = FileUtils.downloadVideoToCache(context, absoluteUrl, record.jobId)
                                jobHistoryRepository.addOrUpdate(
                                    record.copy(status = "completed", progress = 100, localVideoPath = localFile.absolutePath)
                                )
                            }
                        }
                        "failed" -> jobHistoryRepository.addOrUpdate(
                            record.copy(status = "failed", errorMessage = status.error)
                        )
                        else -> jobHistoryRepository.addOrUpdate(
                            record.copy(status = status.status, progress = status.progress ?: record.progress)
                        )
                    }
                } catch (_: Exception) {
                    // Sunucuya şu an ulaşılamıyor olabilir; bir sonraki yenilemede tekrar denenir.
                }
            }
        }
    }

    fun addProducts(uris: List<Uri>) {
        val current = _uiState.value.products
        val isAdScreen = _uiState.value.isAdScreen
        val finalList = if (isAdScreen) {
            (current + uris.map { ProductItem(uri = it) }).take(10)
        } else {
            // Tekli üretim modunda yeni seçim öncekinin yerine geçer.
            uris.lastOrNull()?.let { listOf(ProductItem(uri = it)) } ?: current
        }
        _uiState.value = _uiState.value.copy(
            products = finalList,
            phase = GenerationPhase.IDLE,
            localVideoFile = null,
            errorMessage = null
        )
    }

    fun removeProduct(id: String) {
        _uiState.value = _uiState.value.copy(
            products = _uiState.value.products.filterNot { it.id == id }
        )
    }

    fun moveProduct(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.products.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _uiState.value = _uiState.value.copy(products = list)
    }

    fun updateOptions(transform: (GenerationOptions) -> GenerationOptions) {
        _uiState.value = _uiState.value.copy(options = transform(_uiState.value.options))
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun generateVideo() {
        val state = _uiState.value
        val opts = state.options

        if (opts.mode != "text_to_video" && state.products.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Lütfen en az bir ürün fotoğrafı seçin.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = GenerationPhase.UPLOADING,
                progress = 0,
                errorMessage = null,
                infoMessage = null,
                localVideoFile = null
            )
            try {
                val baseUrl = settingsRepository.serverUrlFlow.first()
                val service = ApiClient.getService(baseUrl)
                val context = getApplication<android.app.Application>()

                val imageParts = state.products.mapIndexed { index, product ->
                    val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$index.jpg")
                    context.contentResolver.openInputStream(product.uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    } ?: throw java.io.IOException("Fotoğraf okunamadı (ürün ${index + 1}).")
                    val body = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("images", tempFile.name, body)
                }

                fun text(v: String): RequestBody = v.toRequestBody("text/plain".toMediaTypeOrNull())

                val fields = mutableMapOf<String, RequestBody>(
                    "mode" to text(opts.mode),
                    "duration_seconds" to text(opts.durationSeconds.toString()),
                    "aspect_ratio" to text(opts.aspectRatio),
                    "quality" to text(opts.quality),
                    "motion" to text(opts.motion),
                    "camera" to text(opts.camera),
                    "product_protection" to text(opts.productProtection.toString()),
                    "ad_mode" to text((state.isAdScreen && state.combineIntoSingleVideo).toString())
                )
                opts.stylePreset?.let { fields["style_preset"] = text(it) }
                if (opts.customPrompt.isNotBlank()) fields["prompt"] = text(opts.customPrompt)
                if (opts.customNegativePrompt.isNotBlank()) fields["negative_prompt"] = text(opts.customNegativePrompt)

                val response = service.generateVideo(imageParts, fields)
                val jobIds = response.job_ids ?: listOf(response.job_id)

                jobIds.forEachIndexed { idx, jobId ->
                    jobHistoryRepository.addOrUpdate(
                        JobRecord(
                            jobId = jobId,
                            createdAtMillis = System.currentTimeMillis(),
                            productLabel = if (state.isAdScreen && state.combineIntoSingleVideo)
                                "Reklam (${state.products.size} ürün)" else "Ürün ${idx + 1}",
                            mode = opts.mode,
                            durationSeconds = opts.durationSeconds,
                            aspectRatio = opts.aspectRatio,
                            status = "queued",
                            progress = 0
                        )
                    )
                    val isPrimary = idx == 0
                    launch { pollAndTrack(baseUrl, jobId, isPrimary) }
                }

                if (jobIds.size > 1) {
                    _uiState.value = _uiState.value.copy(
                        phase = GenerationPhase.QUEUED,
                        infoMessage = "${jobIds.size} video sıraya alındı. İlerlemeyi Sonuçlar sekmesinden takip edebilirsiniz."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(phase = GenerationPhase.QUEUED)
                }
            } catch (e: java.net.UnknownHostException) {
                fail("Sunucuya ulaşılamıyor. Ayarlar'dan sunucu adresini kontrol edin.")
            } catch (e: java.net.ConnectException) {
                fail("Sunucuya bağlanılamadı. Backend çalışıyor mu ve adres doğru mu?")
            } catch (e: java.net.SocketTimeoutException) {
                fail("Sunucudan zamanında yanıt alınamadı. Lütfen tekrar deneyin.")
            } catch (e: retrofit2.HttpException) {
                fail("Sunucu isteği reddetti (HTTP ${e.code()}). Seçtiğiniz kalite/süre kombinasyonu bu GPU için uygun olmayabilir.")
            } catch (e: Exception) {
                fail("Beklenmeyen bir hata oluştu: ${e.message ?: "bilinmiyor"}")
            }
        }
    }

    /**
     * Bir job_id'yi tamamlanana/başarısız olana kadar periyodik olarak sorgular.
     * Her zaman JobHistoryRepository'ye yazar; [isPrimary] true ise (ilk/tek iş)
     * ayrıca bu ekranın kendi inline oynatıcı durumunu da günceller.
     */
    private suspend fun pollAndTrack(baseUrl: String, jobId: String, isPrimary: Boolean) {
        val service = ApiClient.getService(baseUrl)
        val context = getApplication<android.app.Application>()

        if (isPrimary) {
            _uiState.value = _uiState.value.copy(phase = GenerationPhase.PROCESSING)
        }

        var consecutiveConnectionFailures = 0
        while (true) {
            delay(if (consecutiveConnectionFailures == 0) 2500 else 5000)
            val status = try {
                service.getStatus(jobId)
            } catch (_: Exception) {
                consecutiveConnectionFailures += 1
                if (consecutiveConnectionFailures >= 6) {
                    // Sunucudaki iş hâlâ devam ediyor olabilir. Geçici bir mobil
                    // ağ/ngrok kesintisini kalıcı üretim hatası diye geçmişe yazma.
                    if (isPrimary) {
                        fail(
                            "Sunucuyla bağlantı kesildi. İş sunucuda devam ediyor olabilir; " +
                                "Ayarlar'dan bağlantıyı test edip Sonuçlar ekranından yenileyin."
                        )
                    }
                    return
                }
                if (isPrimary) {
                    _uiState.value = _uiState.value.copy(
                        infoMessage = "Bağlantı geçici olarak kesildi; yeniden deneniyor " +
                            "($consecutiveConnectionFailures/6)…"
                    )
                }
                continue
            }

            if (consecutiveConnectionFailures > 0 && isPrimary) {
                _uiState.value = _uiState.value.copy(infoMessage = null)
            }
            consecutiveConnectionFailures = 0

            when (status.status) {
                "queued", "processing" -> {
                    val pct = status.progress ?: 0
                    updateHistoryStatus(jobId, status.status, pct)
                    if (isPrimary) {
                        _uiState.value = _uiState.value.copy(
                            phase = if (status.status == "queued") GenerationPhase.QUEUED else GenerationPhase.PROCESSING,
                            progress = pct
                        )
                    }
                }
                "completed" -> {
                    val videoUrl = status.video_url
                    if (videoUrl == null) {
                        updateHistoryStatus(jobId, "failed", 0, errorMessage = "Sunucu video adresi göndermedi.")
                        if (isPrimary) fail("Sunucu tamamlandı dedi ama video adresi göndermedi.")
                        return
                    }
                    try {
                        val absoluteUrl = if (videoUrl.startsWith("http")) videoUrl else baseUrl.trimEnd('/') + videoUrl
                        val localFile = FileUtils.downloadVideoToCache(context, absoluteUrl, jobId)
                        updateHistoryStatus(jobId, "completed", 100, localVideoPath = localFile.absolutePath)
                        if (isPrimary) {
                            _uiState.value = _uiState.value.copy(
                                phase = GenerationPhase.COMPLETED,
                                progress = 100,
                                localVideoFile = localFile
                            )
                        }
                    } catch (e: Exception) {
                        val msg = "Video indirilemedi: ${e.message ?: "bilinmeyen hata"}"
                        updateHistoryStatus(jobId, "failed", 0, errorMessage = msg)
                        if (isPrimary) fail(msg)
                    }
                    return
                }
                "failed" -> {
                    val msg = status.error ?: "Video üretimi başarısız oldu."
                    updateHistoryStatus(jobId, "failed", 0, errorMessage = msg)
                    if (isPrimary) fail(msg)
                    return
                }
            }
        }
    }

    private suspend fun updateHistoryStatus(
        jobId: String,
        status: String,
        progress: Int,
        localVideoPath: String? = null,
        errorMessage: String? = null
    ) {
        val current = jobHistoryRepository.historyFlow.first().find { it.jobId == jobId } ?: return
        jobHistoryRepository.addOrUpdate(
            current.copy(
                status = status,
                progress = progress,
                localVideoPath = localVideoPath ?: current.localVideoPath,
                errorMessage = errorMessage
            )
        )
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(
            phase = GenerationPhase.FAILED,
            errorMessage = message
        )
    }

    fun reset() {
        val current = _uiState.value
        _uiState.value = MainUiState(
            options = current.options,
            isAdScreen = current.isAdScreen,
            combineIntoSingleVideo = current.combineIntoSingleVideo,
            capabilities = current.capabilities
        )
    }
}
