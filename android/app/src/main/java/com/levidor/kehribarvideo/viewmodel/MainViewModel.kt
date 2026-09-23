package com.levidor.kehribarvideo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.levidor.kehribarvideo.data.ApiClient
import com.levidor.kehribarvideo.data.DefaultPrompts
import com.levidor.kehribarvideo.data.SettingsRepository
import com.levidor.kehribarvideo.util.FileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

enum class GenerationPhase {
    IDLE, UPLOADING, QUEUED, PROCESSING, COMPLETED, FAILED
}

data class MainUiState(
    val selectedImageUri: Uri? = null,
    val prompt: String = DefaultPrompts.POSITIVE,
    val negativePrompt: String = DefaultPrompts.NEGATIVE,
    val phase: GenerationPhase = GenerationPhase.IDLE,
    val progress: Int = 0,
    val jobId: String? = null,
    val localVideoFile: File? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun onImageSelected(uri: Uri?) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            phase = GenerationPhase.IDLE,
            errorMessage = null,
            localVideoFile = null
        )
    }

    fun onPromptChanged(text: String) {
        _uiState.value = _uiState.value.copy(prompt = text)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun generateVideo() {
        val state = _uiState.value
        val imageUri = state.selectedImageUri
        if (imageUri == null) {
            _uiState.value = state.copy(errorMessage = "Lütfen önce bir fotoğraf seçin.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = GenerationPhase.UPLOADING,
                progress = 0,
                errorMessage = null,
                localVideoFile = null
            )
            try {
                val baseUrl = settingsRepository.serverUrlFlow.first()
                val service = ApiClient.getService(baseUrl)

                val context = getApplication<android.app.Application>()
                val tempImageFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    FileOutputStream(tempImageFile).use { output -> input.copyTo(output) }
                } ?: throw java.io.IOException("Seçilen fotoğraf okunamadı.")

                val imageBody = tempImageFile.asRequestBody("image/*".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", tempImageFile.name, imageBody)
                val promptBody = state.prompt.toRequestBody("text/plain".toMediaTypeOrNull())
                val negativeBody = state.negativePrompt.toRequestBody("text/plain".toMediaTypeOrNull())

                val generateResponse = service.generateVideo(imagePart, promptBody, negativeBody)
                _uiState.value = _uiState.value.copy(
                    phase = GenerationPhase.QUEUED,
                    jobId = generateResponse.job_id
                )

                pollStatus(baseUrl, generateResponse.job_id)
            } catch (e: java.net.UnknownHostException) {
                fail("Sunucuya ulaşılamıyor. Ayarlar'dan sunucu adresini kontrol edin.")
            } catch (e: java.net.ConnectException) {
                fail("Sunucuya bağlanılamadı. Backend çalışıyor mu ve adres doğru mu?")
            } catch (e: java.net.SocketTimeoutException) {
                fail("Sunucudan zamanında yanıt alınamadı. Lütfen tekrar deneyin.")
            } catch (e: retrofit2.HttpException) {
                fail("Sunucu hatası (HTTP ${e.code()}). Lütfen daha sonra tekrar deneyin.")
            } catch (e: Exception) {
                fail("Beklenmeyen bir hata oluştu: ${e.message ?: "bilinmiyor"}")
            }
        }
    }

    private suspend fun pollStatus(baseUrl: String, jobId: String) {
        val service = ApiClient.getService(baseUrl)
        val context = getApplication<android.app.Application>()

        while (true) {
            delay(2000)
            val status = try {
                service.getStatus(jobId)
            } catch (e: Exception) {
                fail("Durum sorgulanırken bağlantı hatası oluştu.")
                return
            }

            when (status.status) {
                "queued" -> _uiState.value = _uiState.value.copy(
                    phase = GenerationPhase.QUEUED,
                    progress = status.progress ?: 0
                )
                "processing" -> _uiState.value = _uiState.value.copy(
                    phase = GenerationPhase.PROCESSING,
                    progress = status.progress ?: _uiState.value.progress
                )
                "completed" -> {
                    val videoUrl = status.video_url
                    if (videoUrl == null) {
                        fail("Sunucu tamamlandı dedi ama video adresi göndermedi.")
                        return
                    }
                    try {
                        val absoluteUrl = if (videoUrl.startsWith("http")) videoUrl else baseUrl.trimEnd('/') + videoUrl
                        val localFile = FileUtils.downloadVideoToCache(context, absoluteUrl, jobId)
                        _uiState.value = _uiState.value.copy(
                            phase = GenerationPhase.COMPLETED,
                            progress = 100,
                            localVideoFile = localFile
                        )
                    } catch (e: Exception) {
                        fail("Video indirilemedi: ${e.message ?: "bilinmeyen hata"}")
                    }
                    return
                }
                "failed" -> {
                    fail(status.error ?: "Video üretimi başarısız oldu.")
                    return
                }
                else -> {
                    // bilinmeyen durum, aynı fazda beklemeye devam et
                }
            }
        }
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(
            phase = GenerationPhase.FAILED,
            errorMessage = message
        )
    }

    fun reset() {
        _uiState.value = MainUiState()
    }
}
