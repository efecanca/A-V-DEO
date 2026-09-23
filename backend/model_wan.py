"""
Wan Image-to-Video model sarmalayıcısı.

Bu modül, açık kaynak Wan ailesinden bir Image-to-Video modelini
(örn. "Wan-AI/Wan2.1-I2V-14B-480P") diffusers kütüphanesinin
WanImageToVideoPipeline sınıfı üzerinden CUDA GPU'da çalıştırır.

Notlar:
- Model ağırlıkları büyüktür (onlarca GB). İlk çalıştırmada Hugging Face'den
  otomatik indirilir ve `~/.cache/huggingface` altında saklanır.
- Bu dosya, backend süreci başlarken BİR KEZ yüklenip GPU belleğinde tutulacak
  şekilde tasarlanmıştır (bkz. main.py -> lifespan / get_pipeline()).
- Telefon/istemci tarafında hiçbir model çalışmaz; bu dosya yalnızca
  backend (CUDA GPU sunucusu / Colab) üzerinde çalışır.
"""

import os
import time
from typing import Callable, Optional

import torch

MODEL_ID = os.environ.get("WAN_MODEL_ID", "Wan-AI/Wan2.1-I2V-14B-480P")
NUM_FRAMES = int(os.environ.get("WAN_NUM_FRAMES", "33"))   # T4'te kararlılık için 49'dan 33'e düşürüldü
NUM_INFERENCE_STEPS = int(os.environ.get("WAN_STEPS", "30"))
GUIDANCE_SCALE = float(os.environ.get("WAN_GUIDANCE_SCALE", "5.0"))
HEIGHT = int(os.environ.get("WAN_HEIGHT", "480"))
WIDTH = int(os.environ.get("WAN_WIDTH", "832"))
FPS = int(os.environ.get("WAN_FPS", "16"))

# Wan2.1-I2V-14B, bf16'da ~30GB+ VRAM ister; ücretsiz Colab T4 (16GB) buna
# HİÇBİR ZAMAN çıplak .to("cuda") ile sığmaz. Bu yüzden offload zorunludur ve
# .to("cuda") ile enable_*_cpu_offload() ASLA birlikte çağrılmaz:
# .to("cuda") tüm ağırlıkları önce GPU'ya taşır, offload çağrısı devreye
# girdiğinde bellek zaten dolmuş olur (asıl hata buradaydı).
#   "sequential" -> katman katman offload, en düşük VRAM, en yavaş (T4/16GB, varsayılan)
#   "model"      -> blok bazlı offload, orta VRAM (~24GB+, L4/A100 gibi), daha hızlı
#   "none"       -> offload yok, tüm model GPU'da kalır (>=40GB VRAM gerektirir)
OFFLOAD_MODE = os.environ.get("WAN_OFFLOAD_MODE", "sequential")

_pipeline = None


def get_pipeline():
    """Wan I2V pipeline'ını tembel (lazy) şekilde yükler."""
    global _pipeline
    if _pipeline is not None:
        return _pipeline

    if not torch.cuda.is_available():
        raise RuntimeError(
            "CUDA GPU bulunamadı. Bu backend yalnızca GPU üzerinde çalışacak "
            "şekilde tasarlanmıştır (Colab GPU runtime veya kendi CUDA sunucunuz)."
        )

    torch_major_minor = tuple(int(x) for x in torch.__version__.split("+")[0].split(".")[:2])
    if torch_major_minor < (2, 4):
        raise RuntimeError(
            f"PyTorch {torch.__version__} bulundu, ancak Wan modelleri için "
            "torch>=2.4.0 gereklidir. requirements.txt'deki kurulum notuna bakın."
        )

    from diffusers import WanImageToVideoPipeline, AutoencoderKLWan

    dtype = torch.bfloat16

    vae = AutoencoderKLWan.from_pretrained(MODEL_ID, subfolder="vae", torch_dtype=torch.float32)
    pipe = WanImageToVideoPipeline.from_pretrained(MODEL_ID, vae=vae, torch_dtype=dtype)

    # VAE bellek tasarrufu (destekleniyorsa) - kare sayısı arttıkça VAE decode
    # belleği de büyür, tiling bunu sınırlar.
    if hasattr(pipe, "vae") and hasattr(pipe.vae, "enable_tiling"):
        try:
            pipe.vae.enable_tiling()
        except Exception:
            pass

    if hasattr(pipe, "enable_attention_slicing"):
        try:
            pipe.enable_attention_slicing()
        except Exception:
            pass

    if OFFLOAD_MODE == "sequential":
        # Ağırlıklar CPU'da kalır, her katman ihtiyaç anında GPU'ya alınır.
        # T4 (16GB) gibi düşük VRAM'li kartlarda OOM riskini en aza indirir,
        # ama üretim süresi belirgin şekilde uzar (bir video onlarca dakika sürebilir).
        pipe.enable_sequential_cpu_offload()
    elif OFFLOAD_MODE == "model":
        # Bloklar bazında offload; sequential'dan daha hızlı ama daha fazla
        # VRAM ister (pratikte ~24GB+ önerilir, ör. Colab L4/A100).
        pipe.enable_model_cpu_offload()
    else:
        # Offload yok: tüm modeli doğrudan GPU'ya yükle (>=40GB VRAM gerekir).
        pipe.to("cuda")

    _pipeline = pipe
    return _pipeline


def generate_video(
    image_path: str,
    prompt: str,
    negative_prompt: str,
    output_path: str,
    progress_callback: Optional[Callable[[int], None]] = None,
) -> str:
    """
    Verilen görselden Wan I2V ile kısa bir video üretir ve output_path'e MP4 olarak yazar.
    progress_callback(0-100) her adımda çağrılır, /status endpoint'i bunu döner.
    """
    from PIL import Image
    import imageio

    pipeline = get_pipeline()

    image = Image.open(image_path).convert("RGB")
    # Kaynak görselin en-boy oranını koruyarak modelin beklediği çözünürlüğe yaklaştır
    image = image.resize((WIDTH, HEIGHT))

    if progress_callback:
        progress_callback(5)

    def _step_callback(pipe, step_index, timestep, callback_kwargs):
        if progress_callback:
            pct = 5 + int(85 * (step_index + 1) / NUM_INFERENCE_STEPS)
            progress_callback(min(pct, 90))
        return callback_kwargs

    try:
        result = pipeline(
            image=image,
            prompt=prompt,
            negative_prompt=negative_prompt,
            height=HEIGHT,
            width=WIDTH,
            num_frames=NUM_FRAMES,
            num_inference_steps=NUM_INFERENCE_STEPS,
            guidance_scale=GUIDANCE_SCALE,
            callback_on_step_end=_step_callback,
        )
    except torch.cuda.OutOfMemoryError as exc:
        torch.cuda.empty_cache()
        raise RuntimeError(
            "GPU belleği yetersiz kaldı (CUDA OOM). Çözüm için: WAN_NUM_FRAMES "
            "değerini düşürün, WAN_OFFLOAD_MODE=sequential kullandığınızdan emin "
            "olun ya da daha büyük VRAM'li bir GPU (L4/A100) deneyin."
        ) from exc

    frames = result.frames[0]

    if progress_callback:
        progress_callback(92)

    imageio.mimsave(output_path, frames, fps=FPS, codec="libx264", quality=8)

    if progress_callback:
        progress_callback(100)

    return output_path
