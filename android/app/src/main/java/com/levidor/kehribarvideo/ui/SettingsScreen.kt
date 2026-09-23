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
import com.levidor.kehribarvideo.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                    repository.setServerUrl(url.trim())
                    saved = true
                }
            }) {
                Text("Kaydet")
            }
            if (saved) {
                Text("Kaydedildi ✓")
            }
            Button(enabled = !testing, onClick = { scope.launch { testing = true; connectionState = "Bağlantı test ediliyor…"; try { repository.setServerUrl(url.trim()); val normalized = if (url.trim().endsWith("/")) url.trim() else url.trim() + "/"; val response = ApiClient.getService(normalized).health(); connectionState = if (response.isSuccessful) "Bağlı ✓" else "Bağlantı yok (HTTP " + response.code() + ")" } catch (e: Exception) { connectionState = "Bağlantı yok" } finally { testing = false } } }) { Text(if (testing) "Test ediliyor…" else "Bağlantıyı Test Et / Yeniden Bağlan") }
            Text("Sunucu durumu: " + connectionState)
        }
    }
}

// stringResource, önizleme/derleme uyumluluğu için basit sarmalayıcı
@Composable
private fun stringResourceCompat(id: Int): String = androidx.compose.ui.res.stringResource(id)
