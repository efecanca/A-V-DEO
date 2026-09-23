"""
Video üretim sağlayıcıları için ortak arayüz.

Yeni bir model/sağlayıcı eklemek (Wan'ın başka bir sürümü, ya da tamamen
başka açık kaynak bir video modeli) bu sınıftan türeyip registry.py'a
kaydetmek kadar basit olmalıdır; main.py ve Android tarafı değişmeden kalır.
"""

from abc import ABC, abstractmethod
from typing import Callable, Optional


# Aşama adı, varsa gerçek yüzde ve insan-okur teknik ayrıntı.
# Yüzde yalnızca ölçülebilen işlerde (ör. diffusion adımları) gönderilir;
# model indirme/yükleme/quantization süreleri için None kalır.
StatusCallback = Callable[[str, Optional[int], Optional[str]], None]


class VideoProvider(ABC):
    name: str = "base"

    @abstractmethod
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
    ) -> str:
        """
        Tek bir video sahnesi (clip) üretir ve output_path'e MP4 olarak yazar.

        image_path verilirse Image-to-Video, None ise Text-to-Video modunda
        çalışılır (sağlayıcı bu modu desteklemiyorsa NotImplementedError fırlatmalı).
        status_callback(stage, progress, detail) gerçek çalışma aşamasını bildirir.
        progress yalnızca hesaplanabildiğinde 0-100 aralığında olmalıdır.
        """
        raise NotImplementedError
