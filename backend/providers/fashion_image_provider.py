"""FPRO AI photorealistic fashion reference-frame provider.

Default engine: Qwen-Image-Edit family through diffusers. The implementation
is lazy-loaded so the video backend can boot before the image model is needed.
The model id can be changed without an APK update:
    FPRO_IMAGE_MODEL_ID=<huggingface model id>
"""

import os
import threading
from pathlib import Path
from typing import Callable, Optional

import torch
from PIL import Image

StatusCallback = Callable[[str, Optional[int], Optional[str]], None]


class FashionImageProvider:
    name = "qwen_image_edit"

    def __init__(self) -> None:
        self.model_id = os.environ.get("FPRO_IMAGE_MODEL_ID", "Qwen/Qwen-Image-Edit")
        self._pipe = None
        self._lock = threading.Lock()

    def _dtype(self):
        if not torch.cuda.is_available():
            return torch.float32
        major, _ = torch.cuda.get_device_capability(0)
        return torch.bfloat16 if major >= 8 else torch.float16

    def _get_pipe(self, cb: Optional[StatusCallback] = None):
        with self._lock:
            if self._pipe is not None:
                return self._pipe
            if cb:
                cb("reference_model_loading", None, f"Görsel AI yükleniyor: {self.model_id}")
            try:
                from diffusers import QwenImageEditPipeline
            except ImportError as exc:
                raise RuntimeError(
                    "QwenImageEditPipeline bu diffusers sürümünde bulunamadı. "
                    "Görsel üretim bağımlılıklarını güncelleyin."
                ) from exc

            pipe = QwenImageEditPipeline.from_pretrained(
                self.model_id,
                torch_dtype=self._dtype(),
                low_cpu_mem_usage=True,
            )
            # T4 gibi 16 GB kartlarda VRAM'i koru. CUDA varsa model parçaları
            # gerektiğinde GPU'ya taşınır; video modeliyle aynı süreçte yaşayabilir.
            if torch.cuda.is_available():
                pipe.enable_model_cpu_offload()
            else:
                pipe.to("cpu")
            self._pipe = pipe
            return pipe

    def generate_reference(
        self,
        source_path: str,
        prompt: str,
        negative_prompt: str,
        output_path: str,
        status_callback: Optional[StatusCallback] = None,
    ) -> str:
        pipe = self._get_pipe(status_callback)
        if status_callback:
            status_callback("reference_generating", 0, "Ürün mankene uygulanıyor ve moda sahnesi kuruluyor")

        source = Image.open(source_path).convert("RGB")
        kwargs = dict(
            image=source,
            prompt=prompt,
            negative_prompt=negative_prompt,
            num_inference_steps=28,
            guidance_scale=4.0,
        )
        try:
            result = pipe(**kwargs)
        except torch.cuda.OutOfMemoryError as exc:
            if torch.cuda.is_available():
                torch.cuda.empty_cache()
            raise RuntimeError(
                "Fotogerçekçi referans kare için GPU belleği yetmedi. "
                "FPRO_IMAGE_MODEL_ID ile daha hafif bir image-edit modeli seçilebilir."
            ) from exc

        image = result.images[0]
        # Video zincirinin hedefi dikey reklamdır. Edit modelinin kendi doğal
        # çözünürlüğünü bozmayıp 9:16 merkez kırpma uygula.
        w, h = image.size
        target_ratio = 9 / 16
        if w / h > target_ratio:
            new_w = max(1, int(h * target_ratio))
            left = (w - new_w) // 2
            image = image.crop((left, 0, left + new_w, h))
        elif w / h < target_ratio:
            new_h = max(1, int(w / target_ratio))
            top = (h - new_h) // 2
            image = image.crop((0, top, w, top + new_h))

        Path(output_path).parent.mkdir(parents=True, exist_ok=True)
        image.save(output_path, quality=96)
        if status_callback:
            status_callback("reference_generating", 100, "Fotogerçekçi mankenli referans kare hazır")
        return output_path


fashion_image_provider = FashionImageProvider()
