# Kehribar Video - Backend (AI GPU sunucusu)

FastAPI tabanlı bu backend, Android uygulamasından gelen fotoğrafı alır ve
açık kaynak **Wan** ailesinden bir Image-to-Video modeliyle (`diffusers`
kütüphanesi üzerinden) kısa bir video üretir. Sadece CUDA GPU üzerinde
çalışır; Android cihazın GPU'su kullanılmaz.

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

## Uç noktalar

| Method | Yol | Açıklama |
|---|---|---|
| POST | `/generate` | multipart: `image`, `prompt`, `negative_prompt` -> `{job_id}` |
| GET | `/status/{job_id}` | `{status, progress, video_url, error}` |
| GET | `/videos/{dosya}` | Üretilen MP4'ü servis eder |
| GET | `/health` | Basit sağlık kontrolü |

## GPU belleği ve hız — önemli

`Wan-AI/Wan2.1-I2V-14B-480P`, bf16'da tek başına ~30GB+ VRAM ister. Ücretsiz
Colab T4 (16GB) buna asla çıplak şekilde sığmaz; bu yüzden varsayılan ayar
`WAN_OFFLOAD_MODE=sequential`'dır (katman katman CPU↔GPU offload). Bu ayar
OOM riskini büyük ölçüde azaltır ama üretim süresini ciddi şekilde uzatır —
T4'te tek bir kısa video **onlarca dakika** sürebilir. Bu, "ilk sürümde
kalite yerine kararlılık" hedefiyle bilinçli bir seçimdir; hız değil,
tamamlanma garantisi önceliklidir.

Daha hızlı üretim isterseniz (Colab Pro / L4 / A100 gibi ≥24GB VRAM'li bir GPU):

```bash
export WAN_OFFLOAD_MODE=model   # sequential'dan daha hızlı, daha fazla VRAM ister
```

≥40GB VRAM'li bir kartta (A100 40GB/80GB vb.) offload'u tamamen kapatabilirsiniz:

```bash
export WAN_OFFLOAD_MODE=none
```

Bellek yetersizliği (CUDA OOM) durumunda backend, işi genel bir hata yerine
"GPU belleği yetersiz kaldı" mesajıyla `failed` olarak işaretler; bu durumda
`WAN_NUM_FRAMES` değerini (varsayılan 33) düşürmek genelde yeterlidir.

## Modeli değiştirme

`model_wan.py` içindeki `WAN_MODEL_ID` ortam değişkenini değiştirerek farklı
bir Wan varyantı (örn. daha büyük/kaliteli bir sürüm) kullanabilirsiniz:

```bash
export WAN_MODEL_ID="Wan-AI/Wan2.1-I2V-14B-720P"
```

## v1 tasarım kararları (kararlılık önceliği)

- **Sürüm notu:** `diffusers`, Wan desteğini ancak Mart 2025'te ekledi;
  `requirements.txt` bu yüzden `diffusers>=0.35.1` ve `torch>=2.4.0` gerektirir
  (daha eski sürümlerde `WanImageToVideoPipeline` mevcut değildir ve backend
  import aşamasında çöker).
- İş kuyruğu bellek içi (in-memory) tutulur; basit ve tahmin edilebilirdir.
  Süreç yeniden başlarsa devam eden işler kaybolur (Colab senaryosunda zaten
  oturum sıfırlanabildiği için bu kabul edilebilir bir sınırlamadır).
- Video kısa tutulur (varsayılan ~49 kare / 16 fps) ve çözünürlük 480p'dir;
  bu, GPU belleği ve üretim süresi açısından en kararlı ayardır.
- Varsayılan prompt/negatif prompt, eşarbın deseni/rengi/logosu/bordürünü
  koruyacak şekilde ayarlanmıştır; Android tarafında bu metin düzenlenebilir.
