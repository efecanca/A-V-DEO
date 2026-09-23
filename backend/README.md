# Kehribar Video - Backend (AI GPU sunucusu) — v2

FastAPI tabanlı bu backend, herhangi bir ürün fotoğrafını (yalnızca eşarp
değil) veya bir metin promptunu kısa bir videoya dönüştürür. Model,
`providers/` altında soyutlanmıştır (bkz. "Mimari" bölümü); şu an tek
sağlayıcı **Wan** ailesi (`diffusers` üzerinden), CUDA GPU'da çalışır.
Android cihazın GPU'su hiçbir zaman kullanılmaz.

## Yerel / kendi GPU sunucunuzda çalıştırma

```bash
cd backend
python3 -m venv venv
source venv/bin/activate

# 1) Önce PyTorch'u CUDA sürümünüze göre kurun (torch>=2.4.0 şart, Wan bunu gerektirir):
pip install "torch>=2.4.0" torchvision --index-url https://download.pytorch.org/whl/cu121

# 2) Sonra geri kalan bağımlılıkları kurun:
pip install -r requirements.txt

uvicorn main:app --host 0.0.0.0 --port 8000
```

İlk `/generate` isteğinde model ağırlıkları Hugging Face'den otomatik indirilir
(büyük dosyalardır, ilk indirme uzun sürebilir).

## Google Colab'da çalıştırma

`../colab/Kehribar_Video_Colab.ipynb` dosyasını Colab'a yükleyin, GPU runtime
seçin (Runtime > Change runtime type > GPU) ve hücreleri sırayla çalıştırın.
Notebook, `pyngrok` ile herkese açık bir URL üretir; bu URL'yi Android
uygulamasındaki **Ayarlar** ekranına yapıştırmanız yeterlidir.

⚠️ Colab oturumları geçicidir: oturum kapanırsa (zaman aşımı, yeniden bağlanma)
backend URL'si değişir. Bu yüzden Android tarafında sunucu adresi APK içine
gömülü değil, Ayarlar ekranından değiştirilebilir haldedir.

## Mimari (v2)

```
backend/
  capabilities.py       -> desteklenen mod/süre/oran/kalite/kamera/preset listesi + GPU seviyesi tespiti
  prompt_builder.py      -> kullanıcı promptu + ürün koruma + preset + kamera/hareket birleştirme
  ffmpeg_utils.py         -> birden fazla sahneyi (clip) tek MP4'te birleştirir
  job_manager.py           -> bellek içi iş (job) durumu, artık sahne/ürün metadatası da tutuyor
  providers/
    base.py                 -> VideoProvider soyut arayüzü
    wan_provider.py          -> Wan I2V (test edilmiş) + Wan T2V (YENİ, doğrulanmamış)
    registry.py              -> sağlayıcı kayıt defteri (yeni model eklemek buraya bir satır)
  main.py                    -> FastAPI uçları, sahne bölme/birleştirme orkestrasyonu
```

Yeni bir model/sağlayıcı eklemek isterseniz: `providers/` altına
`VideoProvider`'dan türeyen bir sınıf yazıp `registry.py`'a eklemeniz yeterli;
`main.py` ve Android tarafı değişmeden kalır.

## Uç noktalar

| Method | Yol | Açıklama |
|---|---|---|
| GET | `/capabilities` | Desteklenen mod/süre/oran/kalite/kamera/preset listesi + tespit edilen GPU seviyesi |
| POST | `/generate` | multipart, bkz. aşağıdaki alanlar -> `{job_id}` (veya toplu modda `{job_id, job_ids}`) |
| GET | `/status/{job_id}` | `{status, progress, video_url, error, scenes_completed, scenes_total, actual_duration_seconds, ...}` |
| GET | `/jobs` | Son işlerin özet listesi (bellek içi, geçici — bkz. aşağıdaki not) |
| GET | `/videos/{dosya}` | Üretilen MP4'ü servis eder |
| GET | `/health` | Basit sağlık kontrolü |

`/generate` form alanları (**hepsi opsiyoneldir**; hiçbiri gönderilmezse eski
istemciyle BİREBİR AYNI davranış korunur — tek sahne, `standard` kalite, `16:9`):

- `image` — tek dosya (ESKİ istemciler için hâlâ desteklenir)
- `images` — birden fazla dosya (çoklu ürün / reklam modu)
- `mode` — `image_to_video` (varsayılan) | `text_to_video`
- `prompt`, `negative_prompt` — serbest metin
- `duration_seconds` — `5` | `10` | `15` (verilmezse: eski davranış, tek sahne)
- `aspect_ratio` — `16:9` (varsayılan) | `9:16` | `1:1`
- `quality` — `fast` | `standard` (varsayılan) | `high`
- `motion` — `subtle` | `natural` | `dynamic`
- `camera` — `static` | `push_in` | `pull_out` | `pan_left` | `pan_right` | `orbit`
- `style_preset` — `capabilities.STYLE_PRESETS` anahtarlarından biri
- `product_protection` — `true` (varsayılan) | `false`
- `ad_mode` — `true` ise TÜM görseller TEK bir çok-sahneli reklam videosuna
  dağıtılır; `false` + birden fazla görsel verilirse HER görsel için AYRI bir
  job açılır (yanıt `{job_id, job_ids: [...]}` döner)

### Kalıcı sonuç geçmişi nerede tutuluyor?

`/jobs` yalnızca bellek içi ve geçicidir (Colab oturumu kapanınca sıfırlanır).
Android uygulamasının "Sonuçlar" ekranı bu yüzden kendi kalıcı geçmişini
**cihazda** (DataStore) tutar ve tamamlanan videoları oraya indirir; backend
yeniden başlasa bile kullanıcının geçmişi kaybolmaz. `/jobs` daha çok hata
ayıklama ve ileride bir yönetim paneli için düşünülmüştür.

### Sahne bölme ve süre

Model, tek seferde yalnızca kısa ve kararlı klipler üretebildiği için
(`capabilities.QUALITY_PRESETS`teki kare sayısına bağlı, `standard` kalitede
~2 sn), `duration_seconds` daha uzun istendiğinde backend bu süreyi kaç
sahneye böleceğini hesaplar, her sahneyi ayrı ayrı üretir ve sonunda FFmpeg
(concat demuxer) ile tek MP4'te birleştirir. Gerçekte üretilen süre hedefe
YAKLAŞIKTIR; `GET /status/{job_id}` içindeki `actual_duration_seconds` gerçek
süreyi bildirir. Reklam modunda birden fazla ürün verildiyse sahneler ürünler
arasında sırayla (round-robin) dağıtılır.

## GPU belleği ve hız — önemli

`Wan-AI/Wan2.1-I2V-14B-480P`, bf16'da tek başına ~30GB+ VRAM ister. Ücretsiz
Colab T4 (16GB) buna asla çıplak şekilde sığmaz; bu yüzden varsayılan ayar
`WAN_OFFLOAD_MODE=sequential`'dır (katman katman CPU↔GPU offload). Bu ayar
OOM riskini büyük ölçüde azaltır ama üretim süresini ciddi şekilde uzatır —
T4'te tek bir kısa sahne bile **dakikalar** sürebilir, 15 saniyelik bir reklam
videosu (birden fazla sahne) çok daha uzun sürer. Bu, "kalite yerine
kararlılık" hedefiyle bilinçli bir seçimdir.

**Otomatik kombinasyon koruması:** `capabilities.py` içindeki
`ALLOWED_COMBINATIONS` tablosu, tespit edilen GPU belleğine (`torch.cuda
.get_device_properties`) göre hangi (kalite, süre) kombinasyonuna izin
verildiğini belirler. Örn. T4'te `high` kalite + 15 sn gibi OOM riski yüksek
bir kombinasyon `/generate` isteğinde **400 hatası + önerilen alternatif**
ile reddedilir; iş hiç başlatılmaz, dakikalarca beklenip sonra OOM ile
başarısız olmaz. **Bu tablo gerçek ölçümle değil temkinli bir tahminle
oluşturulmuştur** — gerçek donanımınızda test ettikten sonra
`capabilities.ALLOWED_COMBINATIONS`'ı genişletmeniz/daraltmanız önerilir.
`GPU_TIER_OVERRIDE=low|mid|high` ortam değişkeniyle tespiti manuel geçebilirsiniz.

Daha hızlı üretim isterseniz (Colab Pro / L4 / A100 gibi ≥24GB VRAM'li bir GPU):

```bash
export WAN_OFFLOAD_MODE=model   # sequential'dan daha hızlı, daha fazla VRAM ister
```

≥40GB VRAM'li bir kartta (A100 40GB/80GB vb.) offload'u tamamen kapatabilirsiniz:

```bash
export WAN_OFFLOAD_MODE=none
```

Bellek yetersizliği (CUDA OOM) yine de oluşursa backend, işi genel bir hata
yerine "GPU belleği yetersiz kaldı" mesajıyla `failed` olarak işaretler.

## Modeli değiştirme

```bash
export WAN_I2V_MODEL_ID="Wan-AI/Wan2.1-I2V-14B-720P"   # Image-to-Video
export WAN_T2V_MODEL_ID="Wan-AI/Wan2.1-T2V-14B"        # Text-to-Video (varsayılan: 1.3B)
```

## v2'de test edilmiş / edilmemiş olanlar (önemli, lütfen okuyun)

- **Image-to-Video (tek ürün, tek sahne):** önceki sürümden beri var olan,
  gerçek GitHub Actions build'iyle en az bir kez derlenmiş yol. Bu güncellemede
  yalnızca parametreleri (genişlik/yükseklik/kare/adım) sabit değişkenlerden
  fonksiyon parametresine taşıdık; üretim mantığı DEĞİŞMEDİ.
- **Backend'in tüm yeni akışları (capabilities, çoklu sahne + FFmpeg birleştirme,
  reklam modu, toplu mod, geri uyumlu eski istemci çağrısı, feasibility guard):**
  gerçek GPU olmadan, `torch`/`diffusers` sahte (stub) modüllerle uçtan uca
  test edildi — HTTP akışı, job durumu geçişleri, sahne bölme matematiği ve
  FFmpeg çağrısı doğru çalışıyor. **Gerçek Wan modeliyle, gerçek bir GPU'da
  henüz doğrulanmadı.**
- **Text-to-Video (Wan T2V):** tamamen YENİ kod, hiç çalıştırılmadı. `WanPipeline`
  ile aynı bellek optimizasyonlarını kullanır ama gerçek bir üretim denemesi yapılmadı.
- **`ALLOWED_COMBINATIONS` tablosu:** gerçek ölçüm değil, temkinli tahmin.
- **Android tarafı:** Gradle/Android SDK bu ortamda mevcut olmadığı için
  gerçek derleme burada yapılamadı (aynı sınırlama önceki turlarda da
  belirtilmişti); GitHub Actions'a push edip sonucu kontrol etmeniz gerekiyor.

## v1'den kalan tasarım kararları (hâlâ geçerli)

- İş kuyruğu bellek içi (in-memory) tutulur; basit ve tahmin edilebilirdir.
- Varsayılan prompt/negatif prompt artık "ürün" ifadesiyle genelleştirildi
  (yalnızca eşarp değil); Android tarafında ek metin girilebilir.
