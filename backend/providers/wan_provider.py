"""
Wan ailesi (Image-to-Video + Text-to-Video) için VideoProvider uygulaması.

ÖNEMLİ: Image-to-Video kısmı, önceki tek-mod model_wan.py'deki DAHA ÖNCE
test edilip GitHub Actions üzerinden derlenmiş akışla AYNI mantığı kullanır
(sequential CPU offload, VAE tiling, torch>=2.4 kontrolü, OOM yakalama).
Text-to-Video kısmı bu güncellemeyle YENİ eklenmiştir ve gerçek bir GPU'da
henüz doğrulanmamıştır (bkz. proje README'sindeki "test edilmesi gerekenler").
"""

import os
from typing import Optional

import torch

from providers.base import StatusCallback, VideoProvider

I2V_MODEL_ID = os.environ.get("WAN_I2V_MODEL_ID", "Wan-AI/Wan2.1-I2V-14B-480P")
# T2V için varsayılan olarak bilinçli şekilde KÜÇÜK (1.3B) modeli seçtik:
# resmi Wan repo'suna göre bu model ~8GB VRAM ile çalışır, ücretsiz T4'te
# I2V-14B'den çok daha güvenlidir.
T2V_MODEL_ID = os.environ.get("WAN_T2V_MODEL_ID", "Wan-AI/Wan2.1-T2V-1.3B")

OFFLOAD_MODE = os.environ.get("WAN_OFFLOAD_MODE", "sequential")
GUIDANCE_SCALE = float(os.environ.get("WAN_GUIDANCE_SCALE", "5.0"))


def _check_torch_version() -> None:
    torch_major_minor = tuple(int(x) for x in torch.__version__.split("+")[0].split(".")[:2])
    if torch_major_minor < (2, 4):
        raise RuntimeError(
            f"PyTorch {torch.__version__} bulundu, ancak Wan modelleri için "
            "torch>=2.4.0 gereklidir. requirements.txt'deki kurulum notuna bakın."
        )


def _apply_memory_optimizations(pipe) -> None:
    """Sequential/model offload + VAE tiling + attention slicing (destekleniyorsa)."""
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
        pipe.enable_sequential_cpu_offload()
    elif OFFLOAD_MODE == "model":
        pipe.enable_model_cpu_offload()
    else:
        pipe.to("cuda")


class WanProvider(VideoProvider):
    name = "wan"

    def __init__(self) -> None:
        self._i2v_pipeline = None
        self._t2v_pipeline = None

    # -- Image-to-Video ----------------------------------------------------
    def _get_i2v_pipeline(self, status_callback: Optional[StatusCallback] = None):
        if self._i2v_pipeline is not None:
            return self._i2v_pipeline

        if not torch.cuda.is_available():
            raise RuntimeError(
                "CUDA GPU bulunamadı. Bu backend yalnızca GPU üzerinde çalışacak "
                "şekilde tasarlanmıştır (Colab GPU runtime veya kendi CUDA sunucunuz)."
            )
        _check_torch_version()

        from diffusers import WanImageToVideoPipeline, AutoencoderKLWan

        if status_callback:
            status_callback("model_loading", None, "Wan I2V modeli yükleniyor")
        dtype = torch.bfloat16
        vae = AutoencoderKLWan.from_pretrained(I2V_MODEL_ID, subfolder="vae", torch_dtype=torch.float32)
        pipe = WanImageToVideoPipeline.from_pretrained(I2V_MODEL_ID, vae=vae, torch_dtype=dtype)
        _apply_memory_optimizations(pipe)

        self._i2v_pipeline = pipe
        return pipe

    # -- Text-to-Video (YENİ, doğrulanmamış) --------------------------------
    def _get_t2v_pipeline(self, status_callback: Optional[StatusCallback] = None):
        if self._t2v_pipeline is not None:
            return self._t2v_pipeline

        if not torch.cuda.is_available():
            raise RuntimeError(
                "CUDA GPU bulunamadı. Bu backend yalnızca GPU üzerinde çalışacak "
                "şekilde tasarlanmıştır (Colab GPU runtime veya kendi CUDA sunucunuz)."
            )
        _check_torch_version()

        from diffusers import WanPipeline, AutoencoderKLWan

        if status_callback:
            status_callback("model_loading", None, "Wan T2V modeli yükleniyor")
        dtype = torch.bfloat16
        vae = AutoencoderKLWan.from_pretrained(T2V_MODEL_ID, subfolder="vae", torch_dtype=torch.float32)
        pipe = WanPipeline.from_pretrained(T2V_MODEL_ID, vae=vae, torch_dtype=dtype)
        _apply_memory_optimizations(pipe)

        self._t2v_pipeline = pipe
        return pipe

    def generate_clip(
        self,
        prompt: str,
        negative_prompt: str,
        output_path: str,
        width: int,
        height: int,
        num_frames: int,
        num_inference_steps: int,
        image_path: Optional[str] = None,
        status_callback: Optional[StatusCallback] = None,
        fps: int = 16,
    ) -> str:
        from PIL import Image
        import imageio

        def _step_callback(pipe, step_index, timestep, callback_kwargs):
            if status_callback:
                pct = int(100 * (step_index + 1) / num_inference_steps)
                status_callback(
                    "generating",
                    min(pct, 100),
                    f"Diffusion adımı {step_index + 1}/{num_inference_steps}",
                )
            return callback_kwargs

        if status_callback:
            status_callback("queued", None, "GPU sırası bekleniyor")

        try:
            if image_path is not None:
                pipeline = self._get_i2v_pipeline(status_callback)
                image = Image.open(image_path).convert("RGB").resize((width, height))
                if status_callback:
                    status_callback("generating", 0, "Wan I2V inference başladı")
                result = pipeline(
                    image=image,
                    prompt=prompt,
                    negative_prompt=negative_prompt,
                    height=height,
                    width=width,
                    num_frames=num_frames,
                    num_inference_steps=num_inference_steps,
                    guidance_scale=GUIDANCE_SCALE,
                    callback_on_step_end=_step_callback,
                )
            else:
                pipeline = self._get_t2v_pipeline(status_callback)
                if status_callback:
                    status_callback("generating", 0, "Wan T2V inference başladı")
                result = pipeline(
                    prompt=prompt,
                    negative_prompt=negative_prompt,
                    height=height,
                    width=width,
                    num_frames=num_frames,
                    num_inference_steps=num_inference_steps,
                    guidance_scale=GUIDANCE_SCALE,
                    callback_on_step_end=_step_callback,
                )
        except torch.cuda.OutOfMemoryError as exc:
            torch.cuda.empty_cache()
            raise RuntimeError(
                "GPU belleği yetersiz kaldı (CUDA OOM). Kaliteyi/süreyi düşürün "
                "veya WAN_OFFLOAD_MODE=sequential kullandığınızdan emin olun."
            ) from exc

        frames = result.frames[0]

        if status_callback:
            status_callback("encoding", None, "Kareler MP4 videosuna kodlanıyor")

        imageio.mimsave(output_path, frames, fps=fps, codec="libx264", quality=8)

        return output_path
