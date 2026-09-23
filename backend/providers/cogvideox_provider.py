"""T4/Colab için CogVideoX-5B Image-to-Video provider."""

import gc
import logging
import os
import threading
from typing import Optional

import torch

from providers.base import StatusCallback, VideoProvider


MODEL_ID = os.environ.get("COGVIDEO_I2V_MODEL_ID", "zai-org/CogVideoX-5b-I2V")
logger = logging.getLogger("uvicorn.error")


class CogVideoXProvider(VideoProvider):
    name = "cogvideox"

    def __init__(self) -> None:
        self._pipe = None
        # Colab T4'te iki işin aynı anda model yüklemesi veya inference yapması
        # 16 GB VRAM'i kolayca taşırır. İşler gerçek bir GPU kuyruğunda seri çalışır.
        self._generation_lock = threading.Lock()

    @staticmethod
    def _notify(
        callback: Optional[StatusCallback],
        stage: str,
        progress: Optional[int] = None,
        detail: Optional[str] = None,
    ) -> None:
        if callback:
            callback(stage, progress, detail)

    @staticmethod
    def _clear_memory() -> None:
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()

    @staticmethod
    def _cuda_memory_summary() -> str:
        if not torch.cuda.is_available():
            return "CUDA kullanılamıyor"
        try:
            allocated = torch.cuda.memory_allocated() / (1024**3)
            reserved = torch.cuda.memory_reserved() / (1024**3)
            total = torch.cuda.get_device_properties(0).total_memory / (1024**3)
            return (
                f"allocated={allocated:.2f} GiB, reserved={reserved:.2f} GiB, "
                f"total={total:.2f} GiB"
            )
        except Exception as exc:  # Bellek tanısı asıl hatayı gölgelememeli.
            return f"CUDA bellek bilgisi okunamadı: {exc}"

    def _get_pipe(self, status_callback: Optional[StatusCallback]):
        if self._pipe is not None:
            logger.info("[CogVideoX] Model pipeline bellekte hazır; yeniden kullanılacak")
            return self._pipe
        if not torch.cuda.is_available():
            raise RuntimeError("CUDA GPU bulunamadı.")

        # Import/uyumluluk hatalarının dev model indirilmeden önce görünmesini sağla.
        logger.info("[CogVideoX] Diffusers ve TorchAO bağımlılıkları doğrulanıyor")
        from diffusers import (
            AutoencoderKLCogVideoX,
            CogVideoXImageToVideoPipeline,
            CogVideoXTransformer3DModel,
        )
        from huggingface_hub import snapshot_download
        from torchao.quantization import int8_weight_only, quantize_
        from transformers import T5EncoderModel

        properties = torch.cuda.get_device_properties(0)
        logger.info(
            "[CogVideoX] Başlatılıyor: model=%s, GPU=%s, VRAM=%.2f GiB, "
            "torch=%s, compute_capability=%s.%s",
            MODEL_ID,
            properties.name,
            properties.total_memory / (1024**3),
            torch.__version__,
            properties.major,
            properties.minor,
        )

        # from_pretrained indirme ve yüklemeyi tek, sessiz bir çağrıda karıştırıyordu.
        # Snapshot'ı önce açıkça indirerek aşamayı ve HF ilerleme çubuklarını Colab'a
        # taşıyoruz; bileşen yüklemeleri bundan sonra yalnızca yerel dosyaları okur.
        self._notify(
            status_callback,
            "model_downloading",
            detail="CogVideoX-5B-I2V model dosyaları denetleniyor ve indiriliyor",
        )
        logger.info("[CogVideoX] Hugging Face snapshot indiriliyor/denetleniyor: %s", MODEL_ID)
        model_path = snapshot_download(repo_id=MODEL_ID)
        logger.info("[CogVideoX] Model snapshot hazır: %s", model_path)

        # Resmi CogVideoX düşük bellek yoluyla uyumlu olarak text encoder,
        # transformer ve VAE üzerinde INT8 weight-only quantization uygulanır.
        dtype = torch.bfloat16

        self._notify(
            status_callback,
            "model_loading",
            detail="Metin kodlayıcı yükleniyor",
        )
        logger.info("[CogVideoX] Text encoder yükleniyor")
        text_encoder = T5EncoderModel.from_pretrained(
            model_path,
            subfolder="text_encoder",
            torch_dtype=dtype,
            local_files_only=True,
        )
        self._notify(
            status_callback,
            "quantizing",
            detail="Metin kodlayıcı INT8'e dönüştürülüyor",
        )
        logger.info("[CogVideoX] Text encoder INT8 quantization başladı")
        quantize_(text_encoder, int8_weight_only())
        logger.info("[CogVideoX] Text encoder INT8 quantization tamamlandı")

        self._notify(
            status_callback,
            "model_loading",
            detail="Video transformer yükleniyor",
        )
        logger.info("[CogVideoX] Transformer yükleniyor")
        transformer = CogVideoXTransformer3DModel.from_pretrained(
            model_path,
            subfolder="transformer",
            torch_dtype=dtype,
            local_files_only=True,
        )
        self._notify(
            status_callback,
            "quantizing",
            detail="Video transformer INT8'e dönüştürülüyor",
        )
        logger.info("[CogVideoX] Transformer INT8 quantization başladı")
        quantize_(transformer, int8_weight_only())
        logger.info("[CogVideoX] Transformer INT8 quantization tamamlandı")

        self._notify(
            status_callback,
            "model_loading",
            detail="VAE yükleniyor",
        )
        logger.info("[CogVideoX] VAE yükleniyor")
        vae = AutoencoderKLCogVideoX.from_pretrained(
            model_path,
            subfolder="vae",
            torch_dtype=dtype,
            local_files_only=True,
        )
        self._notify(
            status_callback,
            "quantizing",
            detail="VAE INT8'e dönüştürülüyor",
        )
        logger.info("[CogVideoX] VAE INT8 quantization başladı")
        quantize_(vae, int8_weight_only())
        logger.info("[CogVideoX] VAE INT8 quantization tamamlandı")

        self._notify(
            status_callback,
            "model_loading",
            detail="Pipeline kuruluyor ve CPU offload etkinleştiriliyor",
        )
        logger.info("[CogVideoX] Pipeline kuruluyor")
        pipe = CogVideoXImageToVideoPipeline.from_pretrained(
            model_path,
            text_encoder=text_encoder,
            transformer=transformer,
            vae=vae,
            torch_dtype=dtype,
            local_files_only=True,
        )
        pipe.enable_sequential_cpu_offload()
        pipe.vae.enable_slicing()
        pipe.vae.enable_tiling()

        self._clear_memory()
        self._pipe = pipe
        logger.info(
            "[CogVideoX] Pipeline hazır; sequential CPU offload etkin (%s)",
            self._cuda_memory_summary(),
        )
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
        fps: int = 8,
    ) -> str:
        del width, height  # CogVideoX-5B-I2V resmi 720x480 çözünürlüğünü kullanır.
        if image_path is None:
            raise RuntimeError("CogVideoX T4 modu yalnızca Image-to-Video içindir.")

        from diffusers.utils import export_to_video
        from PIL import Image

        current_stage = "queued"

        def report(stage: str, progress: Optional[int] = None, detail: Optional[str] = None) -> None:
            nonlocal current_stage
            current_stage = stage
            self._notify(status_callback, stage, progress, detail)

        report("queued", detail="GPU sırası bekleniyor")
        logger.info("[CogVideoX] GPU sırası bekleniyor: %s", output_path)

        with self._generation_lock:
            logger.info("[CogVideoX] GPU sırası alındı: %s", output_path)
            try:
                pipe = self._get_pipe(report)
                image = Image.open(image_path).convert("RGB").resize((720, 480))
                steps = max(10, min(int(num_inference_steps), 20))
                # CogVideoX temporal VAE için geçerli 8N+1 kare sayısına indir.
                frames_count = max(9, min(int(num_frames), 49))
                frames_count = ((frames_count - 1) // 8) * 8 + 1

                self._clear_memory()
                report(
                    "generating",
                    0,
                    f"Diffusion başladı: {steps} adım, {frames_count} kare",
                )
                logger.info(
                    "[CogVideoX] Inference başladı: steps=%s, frames=%s, fps=%s, %s",
                    steps,
                    frames_count,
                    fps,
                    self._cuda_memory_summary(),
                )

                def step_callback(pipe_obj, step_index, timestep, callback_kwargs):
                    del pipe_obj, timestep
                    progress = int(100 * (step_index + 1) / steps)
                    report(
                        "generating",
                        min(progress, 100),
                        f"Diffusion adımı {step_index + 1}/{steps}",
                    )
                    return callback_kwargs

                with torch.inference_mode():
                    generated_frames = pipe(
                        image=image,
                        prompt=prompt,
                        negative_prompt=negative_prompt,
                        num_frames=frames_count,
                        num_inference_steps=steps,
                        guidance_scale=6.0,
                        use_dynamic_cfg=True,
                        callback_on_step_end=step_callback,
                    ).frames[0]

                report("encoding", detail="Kareler MP4 videosuna kodlanıyor")
                logger.info("[CogVideoX] MP4 encoding başladı: %s", output_path)
                export_to_video(generated_frames, output_path, fps=fps)
                del generated_frames
                self._clear_memory()
                logger.info("[CogVideoX] Klip hazır: %s", output_path)
                return output_path
            except torch.cuda.OutOfMemoryError as exc:
                memory = self._cuda_memory_summary()
                logger.exception(
                    "[CogVideoX] CUDA OOM; aşama=%s, %s",
                    current_stage,
                    memory,
                )
                self._clear_memory()
                raise RuntimeError(
                    f"CogVideoX INT8 T4 belleği yetersiz kaldı (aşama: "
                    f"{current_stage}; {memory})."
                ) from exc
            except Exception:
                logger.exception(
                    "[CogVideoX] Provider hatası; aşama=%s, %s",
                    current_stage,
                    self._cuda_memory_summary(),
                )
                self._clear_memory()
                raise
