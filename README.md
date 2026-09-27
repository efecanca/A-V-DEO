# FPRO AI — Profesyonel Multi-Agent Moda Stüdyosu

FPRO AI, gerçek eşarp/ürün fotoğrafını koruma önceliğiyle mankenli moda
görseline, kullanıcı onayından sonra da videoya dönüştüren Android + FastAPI
uygulamasıdır. Telefon uygulaması sohbet tabanlı bir stüdyo, Projeler, Galeri
ve Ayarlar sekmeleri sunar. Backend; Director, Product Guardian, Image Agent,
Visual QC ve Video Agent rollerini capability tabanlı provider katmanı üzerinden
yürütür.

Varsayılan uzak provider'lar **Gemini Image** ve **Google Veo**'dur. Qwen
Image Edit ile **CogVideoX-5B-I2V + TorchAO INT8/T4** silinmemiştir; secret,
kota veya provider hatasında uygun fallback olarak korunur.

```
KehribarVideo/
├── android/    -> Android Studio projesi (Kotlin + Jetpack Compose)
├── backend/    -> FastAPI + agent/provider orkestrasyonu + proje deposu
├── colab/      -> Google Colab GPU kurulum notebook'u
└── .github/workflows/build-apk.yml -> GitHub Actions ile otomatik APK derleme
```

## APK'yı GitHub Actions'tan alma

`main` dalına her gönderimde GitHub Actions Android birim testlerini çalıştırır,
Debug APK'yı derler ve indirilebilir bir artifact olarak yayınlar:

1. GitHub'da **Actions → Build Debug APK** sayfasını açın.
2. En yeni yeşil çalıştırmayı açıp "fpro-ai-debug-apk" adlı artifact'i
   indirin ve ZIP'i açın — kurulacak dosya içerideki `app-debug.apk`'dır.
   Artifact ZIP'inin uzantısını `.apk` yapmak Android'de "paket ayrıştırılamadı"
   hatasına yol açar.
3. İsterseniz Android Studio'da `android/` klasörünü açıp **Run** ile de
   derleyebilirsiniz.

GitHub'ın geçici runner'ında üretilen Debug APK'ların imza anahtarı koşular
arasında değişebilir. Telefonda daha eski bir Actions Debug APK'sı kuruluysa
yeni dosya onun üzerine kurulamayabilir; bu durumda eski debug uygulamasını bir
kez kaldırmak gerekir. Kalıcı kullanıcı verisini koruyan mağaza güncellemeleri
için aynı güvenli release signing key'iyle imzalanmış Release APK/AAB kullanın.

## Hızlı başlangıç

1. Colab veya Kaggle Secrets içine `NGROK_AUTHTOKEN` ve Gemini/Veo kullanmak
   için `GEMINI_API_KEY` ekleyin. Anahtarlar APK'ya veya repoya yazılmaz.
2. **Backend'i başlatın** (Colab'da `colab/Kehribar_Video_Colab.ipynb`'i
   çalıştırın veya kendi CUDA GPU sunucunuzda `backend/README.md`'yi izleyin).
   Size bir URL verecek (örn. `https://xxxx.ngrok-free.app/`).
3. **APK'yı kurun** (yukarıdaki GitHub Actions ya da Android Studio yoluyla).
4. **Ayarlar → Gelişmiş / Tanılama** altında backend URL'sini kaydedin ve
   **Bağlantıyı Kontrol Et** ile `Bağlı ve hazır ✓` sonucunu doğrulayın.
5. **Stüdyo** sekmesinde `+` ile gerçek ürün fotoğrafını (isteğe bağlı manken
   referansını) ekleyip çekimi sohbet diliyle anlatın. Uygulama yalnız gerçek
   backend aşamasını ve varsa provider'ın ölçtüğü yüzdeyi gösterir.
6. Mankenli görseli inceleyin; sohbetten revizyon isteyin veya sonuç kartındaki
   **Videoya Dönüştür** ile açıkça onaylayın. Video daima bu onaylı görseli
   başlangıç karesi olarak kullanır.
7. Sonuçlar **Projeler** ve **Galeri** içinde görsel/video türüyle listelenir;
   kartlardan kaydedilebilir ve paylaşılabilir.

## Yeni Studio API akışı

`POST /studio/generate` → `GET /status/{job_id}` → isteğe bağlı
`POST /studio/revise` → `POST /studio/results/{result_id}/approve` →
`POST /studio/results/{result_id}/video` zinciri kullanılır. Görsel aşamaları:
`product_analyzing`, `scene_preparing`, `product_applying`,
`image_enhancing`, `quality_checking`, `completed/failed`.

Visual QC bir model tahminidir; uygulama bunu kesin piksel doğrulaması olarak
sunmaz. Otomatik düzeltme sayısı `FPRO_MAX_AUTO_CORRECTIONS` ile en fazla ikiye
sınırlandırılır (varsayılan 1).

## Neden sunucu adresi APK içine gömülü değil?

Colab oturumları geçici olduğu için GPU backend URL'si sık değişir.
Adres, Ayarlar ekranından DataStore'a kaydedilir; URL değiştiğinde
uygulamayı yeniden derlemenize gerek kalmaz. Uygulama, yanlışlıkla
`/health` ile birlikte yapıştırılan adresi de backend kök adresine çevirir.

Colab notebook'u adresi ekrana yazmadan önce hem yerel hem de herkese açık
`/health` uçlarını doğrular. Çalıştırma hücresini açık bırakın; hücre
durdurulursa ngrok tüneli de kapanır. Tünel kopup yeniden kurulursa notebook
**YENİ SUNUCU ADRESİ** yazdırır ve bu adresin Ayarlar'a yeniden girilmesi gerekir.

## Test edilmiş / çalışma zamanında doğrulanacak noktalar

- **Backend durum mantığı** ölçülemeyen aşamalara sahte yüzde vermemek ve gerçek
  diffusion yüzdesini çoklu sahnelere yaymak için birim testleriyle doğrulanır.
- Agent/provider seçimi, proje kalıcılığı, açık onay kapısı ve onaylı görselin
  video provider'ına verilmesi backend birim testleriyle doğrulanır.
- **Android tarafı** GitHub Actions ile otomatik test edilip Debug APK olarak
  derlenir. Gerçek telefonda kamera/galeri seçimi ve uzun üretim sırasında
  mobil ağ geçişleri ayrıca denenmelidir.
- Gerçek Gemini/Veo üretimi secret, etkin faturalandırma/kota ve provider
  erişimi gerektirir; CI canlı ve ücretli üretim çağrısı yapmaz.

## Sınırlamalar (kararlılık önceliği)

- Yerel CogVideoX fallback kısa (en fazla 49 kare) ve 480p çalışma alanındadır;
  Veo yolu ise seçilen 4/6/8 saniye ve 720p ayarını kullanır.
- **CogVideoX-5B-I2V**, en büyük iki bileşen olan text encoder + transformer
  için yükleme sırasında INT8 weight-only quantization; VAE için GPU'ya
  uygun FP16/BF16 ve tüm pipeline için sequential CPU offload kullanır. İlk model indirmesi,
  quantization ve T4 inference uzun sürebilir; OOM/uyumluluk hataları Colab
  hücresinde traceback ve job'ın `failed` ayrıntısı olarak görünür.
- İş kuyruğu backend'de bellek içi tutulur (basit ve öngörülebilir);
  backend süreci yeniden başlarsa devam eden işler kaybolur.
- `usesCleartextTraffic="true"` ve tüm alan adlarına açık network security
  config, ngrok/Colab gibi değişken http(s) adresleriyle çalışabilmek için
  bilerek gevşek bırakıldı; kalıcı bir sunucuya geçince sıkılaştırmanız önerilir.

Ayrıntılar için `android/` ve `backend/` klasörlerindeki kod yorumlarına
ve `backend/README.md`'ye bakabilirsiniz.
