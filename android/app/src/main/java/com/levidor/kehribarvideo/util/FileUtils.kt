package com.levidor.kehribarvideo.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    /**
     * Uzak video_url'i indirip cache klasörüne kaydeder ve yerel Uri döner.
     * ExoPlayer'a yerel dosya vermek, oynatma sırasında ağ dalgalanmalarına
     * karşı daha kararlıdır.
     */
    suspend fun downloadVideoToCache(context: Context, videoUrl: String, jobId: String): File =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(videoUrl)
                .header("ngrok-skip-browser-warning", "true")
                .build()
            val dir = File(context.cacheDir, "videos").apply { mkdirs() }
            val outFile = File(dir, "kehribar_$jobId.mp4")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw java.io.IOException("Video indirilemedi: HTTP ${response.code}")
                }
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw java.io.IOException("Video verisi boş döndü")
            }
            outFile
        }

    /**
     * Videoyu cihazın Galeri (Movies) klasörüne kalıcı olarak kaydeder.
     */
    suspend fun saveVideoToGallery(context: Context, sourceFile: File): Uri =
        withContext(Dispatchers.IO) {
            val fileName = "KehribarVideo_${System.currentTimeMillis()}.mp4"
            val resolver = context.contentResolver

            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/KehribarVideo")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val itemUri = resolver.insert(collection, values)
                ?: throw java.io.IOException("MediaStore kaydı oluşturulamadı")

            resolver.openOutputStream(itemUri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw java.io.IOException("Video yazılamadı")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }

            itemUri
        }

    /**
     * Videoyu paylaşmak için bir Intent.ACTION_SEND hazırlar (FileProvider üzerinden).
     */
    fun buildShareIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
