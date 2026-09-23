# Kehribar Video

Kullanıcının galeriden seçtiği bir eşarp/model fotoğrafını, uzak bir GPU
backend'i (Wan Image-to-Video modeli) üzerinden kısa bir moda videosuna
dönüştüren Android uygulaması + backend.

```
KehribarVideo/
├── android/    -> Android Studio projesi (Kotlin + Jetpack Compose)
├── backend/    -> FastAPI + Wan I2V (CUDA GPU üzerinde çalışır)
├── colab/      -> Google Colab GPU kurulum notebook'u
└── .github/workflows/build-apk.yml -> GitHub Actions ile otomatik APK derleme
```

## Önemli: APK bu teslimatta yok — neden ve nasıl alınır

Bu projeyi hazırladığım ortamın internet erişimi yalnızca birkaç paket
kayıt sunucusuyla (PyPI, npm, GitHub vb.) sınırlı; Android SDK ve Google'ın
Maven deposuna (Jetpack/Compose kütüphaneleri buradan iniyor) erişimim yok.
Bu yüzden APK'yı burada gerçekten derleyip test edemedim — sizi "derledim"
diye yanıltmak yerine bunu açıkça söylemek istedim.

Kaynak kodun kendisi eksiksiz ve placeholder içermiyor. Zaten GitHub Actions
kullandığınızı bildiğim için en pratik yol bu:

1. `android/` klasörünün içeriğini bir GitHub reposuna (repo kökü değil,
   `android/` klasörünün içi `android/` alt klasörü olarak) push edin —
   `.github/workflows/build-apk.yml` zaten push'ta otomatik tetiklenir.
2. Actions sekmesinde iş bitince "kehribar-video-debug-apk" adlı artifact'i
   indirin — debug APK içindedir.
3. Android Studio'nuz varsa `android/` klasörünü doğrudan açıp
   "Run" ile de derleyebilirsiniz (Gradle wrapper jar'ı ilk açılışta
   Android Studio tarafından otomatik indirilir).

## Hızlı başlangıç

1. **Backend'i başlatın** (Colab'da `colab/Kehribar_Video_Colab.ipynb`'i
   çalıştırın veya kendi CUDA GPU sunucunuzda `backend/README.md`'yi izleyin).
   Size bir URL verecek (örn. `https://xxxx.ngrok-free.app/`).
2. **APK'yı kurun** (yukarıdaki GitHub Actions ya da Android Studio yoluyla).
3. Uygulamayı açın, sağ üstteki **Ayarlar (⚙)** ikonuna dokunup backend
   URL'sini yapıştırıp kaydedin.
4. Ana ekranda fotoğraf seçin, prompt'u isterseniz düzenleyin,
   **Video Oluştur**'a basın. İlerleme yüzdesi görünür; bitince video
   uygulama içinde oynar, **Kaydet** ile galeriye, **Paylaş** ile
   istediğiniz uygulamaya gönderebilirsiniz.

## Neden sunucu adresi APK içine gömülü değil?

Colab oturumları geçici olduğu için GPU backend URL'si sık değişir.
Adres, Ayarlar ekranından DataStore'a kaydedilir; URL değiştiğinde
uygulamayı yeniden derlemenize gerek kalmaz.

## Sınırlamalar (v1 — kararlılık önceliği)

- Video kısa (~2 sn / 33 kare) ve 480p'dir; amaç kaliteden önce sağlam/öngörülebilir
  bir uçtan uca akış kurmaktır.
- **Wan2.1-I2V-14B modeli, ücretsiz Colab T4'e (16GB) ancak "sequential CPU
  offload" ile sığar; bu mod OOM'u önler ama tek bir video onlarca dakika
  sürebilir.** Daha hızlı sonuç için Colab Pro'da L4/A100 gibi ≥24GB VRAM'li
  bir GPU seçip `WAN_OFFLOAD_MODE=model` yapabilirsiniz (ayrıntı:
  `backend/README.md`).
- İş kuyruğu backend'de bellek içi tutulur (basit ve öngörülebilir);
  backend süreci yeniden başlarsa devam eden işler kaybolur.
- `usesCleartextTraffic="true"` ve tüm alan adlarına açık network security
  config, ngrok/Colab gibi değişken http(s) adresleriyle çalışabilmek için
  bilerek gevşek bırakıldı; kalıcı bir sunucuya geçince sıkılaştırmanız önerilir.

Ayrıntılar için `android/` ve `backend/` klasörlerindeki kod yorumlarına
ve `backend/README.md`'ye bakabilirsiniz.
