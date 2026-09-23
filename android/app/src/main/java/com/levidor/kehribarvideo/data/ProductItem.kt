package com.levidor.kehribarvideo.data

import android.net.Uri
import java.util.UUID

/**
 * Üretim ekranında seçilen tek bir ürün fotoğrafı. Çoklu seçimde (toplu mod /
 * reklam modu) sıralama ve kaldırma işlemleri için kararlı bir [id] taşır
 * (Uri tek başına sıralama/kaldırma için güvenilir bir anahtar değildir).
 */
data class ProductItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri
)
