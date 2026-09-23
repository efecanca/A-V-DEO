# FPRO AI (v2)

Herhangi bir ürün fotoğrafını (eşarp, çanta, tekstil, ileride başka moda
ürünleri) uzak bir GPU backend'i (Colab T4 üzerinde CogVideoX-5B-I2V +
TorchAO INT8) ile kısa bir tanıtım videosuna dönüştüren Android uygulaması +
backend. v2 ile artık: Fotoğraftan/Metinden Video modu, ayarlanabilir
süre/format/kalite/hareket/kamera, hazır stil presetleri, çoklu ürün +
15 saniyelik reklam modu, ve kalıcı bir "Sonuçlar" geçmişi ekleniyor.

```
KehribarVideo/
├── android/    -> Android Studio projesi (Kotlin + Jetpack Compose)
├── backend/    -> FastAPI + CogVideoX-5B-I2V INT8 (CUDA GPU üzerinde çalışır)
├── colab/      -> Google Colab GPU kurulum notebook'u
└── .github/workflows/build-apk.yml -> GitHub Actions ile otomatik APK derleme
```

## APK'yı GitHub Actions'tan alma

`main` dalına her gönderimde GitHub Actions Android birim testlerini çalıştırır,
Debug APK'yı derler ve indirilebilir bir artifact olarak yayınlar:

1. GitHub'da **Actions → Build Debug APK** sayfasını açın.
2. En yeni yeşil çalıştırmayı açıp "fpro-ai-debug-apk" adlı artifact'i
   indirin — debug APK içindedir.
3. İsterseniz Android Studio'da `android/` klasörünü açıp **Run** ile de
   derleyebilirsiniz.

## Hızlı başlangıç

1. **Backend'i başlatın** (Colab'da `colab/Kehribar_Video_Colab.ipynb`'i
   çalıştırın veya kendi CUDA GPU sunucunuzda `backend/README.md`'yi izleyin).
   Size bir URL verecek (örn. `https://xxxx.ngrok-free.app/`).
2. **APK'yı kurun** (yukarıdaki GitHub Actions ya da Android Studio yoluyla).
3. Uygulamayı açın, sağ üstteki **Ayarlar (⚙)** ikonuna dokunup backend
   URL'sini yapıştırın ve **Bağlantıyı Test Et / Yeniden Bağlan** düğmesine
   basın. `Bağlı ✓` görülmeden üretime başlamayın.
4. Ana ekranda ürün fotoğrafı seçin (veya "Metinden Video" moduna geçip
   yalnızca prompt yazın), süre/format/kalite/hareket/kamera/stil
   seçeneklerini ayarlayın, **Video Oluştur**'a basın. Model indirme/yükleme,
   INT8 quantization, üretim ve kodlama aşamaları görünür; gerçek diffusion
   yüzdesi yalnızca hesaplanabildiğinde gösterilir. Bitince video uygulama içinde oynar, **Kaydet** ile galeriye,
   **Paylaş** ile istediğiniz uygulamaya gönderebilirsiniz.
5. Sağ üstteki 📣 ikonu **Reklam Videosu** modunu açar: birden fazla ürün
   fotoğrafı seçip ya TEK bir çok-sahneli reklam videosunda ya da her ürün
   için AYRI bir videoda (toplu mod) birleştirebilirsiniz.
6. Sağ üstteki 🕘 ikonu **Sonuçlar** ekranını açar: cihazda kalıcı olarak
   saklanan geçmiş üretimlerinizi (durum, süre, format) gösterir; backend
   yeniden başlasa bile bu geçmiş kaybolmaz.

## Neden sunucu adresi APK içine gömülü değil?

Colab oturumları geçici olduğu için GPU backend URL'si sık değişir.
Adres, Ayarlar ekranından DataStore'a kaydedilir; URL değiştiğinde
uygulamayı yeniden derlemenize gerek kalmaz. Uygulama, yanlışlıkla
`/health` ile birlikte yapıştırılan adresi de backend kök adresine çevirir.

Colab notebook'u adresi ekrana yazmadan önce hem yerel hem de herkese açık
`/health` uçlarını doğrular. Çalıştırma hücresini açık bırakın; hücre
durdurulursa ngrok tüneli de kapanır. Tünel kopup yeniden kurulursa notebook
**YENİ SUNUCU ADRESİ** yazdırır ve bu adresin Ayarlar'a yeniden girilmesi gerekir.

## v2'de test edilmiş / edilmemiş olanlar (lütfen okuyun)

- **Backend durum mantığı** ölçülemeyen aşamalara sahte yüzde vermemek ve gerçek
  diffusion yüzdesini çoklu sahnelere yaymak için birim testleriyle doğrulanır.
- Varsayılan Colab provider'ı yalnızca **CogVideoX-5B-I2V** modunu ilan eder;
  desteklenmeyen Text-to-Video seçeneği Android'e sunulmaz.
- **Android tarafı** GitHub Actions ile otomatik test edilip Debug APK olarak
  derlenir. Gerçek telefonda kamera/galeri seçimi ve uzun üretim sırasında
  mobil ağ geçişleri ayrıca denenmelidir.
- `ALLOWED_COMBINATIONS` GPU/kalite/süre tablosu gerçek ölçüm değil, temkinli
  bir tahmindir; gerçek donanımda test ettikten sonra ayarlamanız önerilir.

## Sınırlamalar (kararlılık önceliği)

- Video kısa (~2 sn / 33 kare) ve 480p'dir; amaç kaliteden önce sağlam/öngörülebilir
  bir uçtan uca akış kurmaktır.
- **CogVideoX-5B-I2V**, text encoder + transformer + VAE için INT8 weight-only
  quantization ve sequential CPU offload kullanır. İlk model indirmesi,
  quantization ve T4 inference uzun sürebilir; OOM/uyumluluk hataları Colab
  hücresinde traceback ve job'ın `failed` ayrıntısı olarak görünür.
- İş kuyruğu backend'de bellek içi tutulur (basit ve öngörülebilir);
  backend süreci yeniden başlarsa devam eden işler kaybolur.
- `usesCleartextTraffic="true"` ve tüm alan adlarına açık network security
  config, ngrok/Colab gibi değişken http(s) adresleriyle çalışabilmek için
  bilerek gevşek bırakıldı; kalıcı bir sunucuya geçince sıkılaştırmanız önerilir.

Ayrıntılar için `android/` ve `backend/` klasörlerindeki kod yorumlarına
ve `backend/README.md`'ye bakabilirsiniz.
