package com.levidor.kehribarvideo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.levidor.kehribarvideo.R
import com.levidor.kehribarvideo.data.ApiClient
import com.levidor.kehribarvideo.data.ServerConnection
import com.levidor.kehribarvideo.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf("Henüz test edilmedi") }
    var testing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        url = repository.serverUrlFlow.first()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResourceCompat(R.string.settings_title)) },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResourceCompat(R.string.server_url_label))
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    saved = false
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://xxxx.ngrok-free.app/") }
            )
            Text(
                "Colab veya başka bir GPU sunucusu her yeniden başlatıldığında " +
                    "adres değişebilir. Yeni adresi buraya yapıştırıp kaydetmeniz yeterli, " +
                    "uygulamayı yeniden derlemenize gerek yoktur."
            )
            Button(onClick = {
                scope.launch {
                    saved = false
                    try {
                        url = repository.setServerUrl(url)
                        saved = true
                        connectionState = "Adres kaydedildi; bağlantıyı test edin."
                    } catch (e: IllegalArgumentException) {
                        connectionState = e.message ?: "Sunucu adresi geçersiz."
                    }
                }
            }) {
                Text("Kaydet")
            }
            if (saved) {
                Text("Kaydedildi ✓")
            }
            Button(
                enabled = !testing,
                onClick = {
                    scope.launch {
                        testing = true
                        saved = false
                        connectionState = "Bağlantı test ediliyor…"
                        try {
                            val normalized = repository.setServerUrl(url)
                            url = normalized
                            saved = true

                            val response = ApiClient.getService(normalized).health()
                            connectionState = when {
                                response.isSuccessful &&
                                    response.body()?.status.equals("ok", ignoreCase = true) ->
                                    "Bağlı ✓"
                                response.isSuccessful ->
                                    "Sunucuya ulaşıldı fakat /health geçerli yanıt vermedi."
                                else -> ServerConnection.failureMessage(
                                    statusCode = response.code(),
                                    ngrokErrorCode = response.headers()["Ngrok-Error-Code"],
                                    errorBody = response.errorBody()?.string()
                                )
                            }
                        } catch (e: IllegalArgumentException) {
                            connectionState = e.message ?: "Sunucu adresi geçersiz."
                        } catch (_: UnknownHostException) {
                            connectionState = "Sunucu adı bulunamadı. Adresi kontrol edin."
                        } catch (_: ConnectException) {
                            connectionState = "Sunucuya bağlanılamadı. Colab çalışıyor mu?"
                        } catch (_: SocketTimeoutException) {
                            connectionState = "Sunucu zamanında yanıt vermedi. Tekrar deneyin."
                        } catch (e: Exception) {
                            connectionState = "Bağlantı kurulamadı: ${e.message ?: "bilinmeyen hata"}"
                        } finally {
                            testing = false
                        }
                    }
                }
            ) {
                Text(if (testing) "Test ediliyor…" else "Bağlantıyı Test Et / Yeniden Bağlan")
            }
            Text("Sunucu durumu: " + connectionState)
        }
    }
}

// stringResource, önizleme/derleme uyumluluğu için basit sarmalayıcı
@Composable
private fun stringResourceCompat(id: Int): String = androidx.compose.ui.res.stringResource(id)
