from __future__ import annotations

import math
import os
from pathlib import Path
from typing import Any, Dict, List, Optional

from studio.models import Capability, ProviderDescriptor
from studio.provider_registry import ProviderError


class LocalProductInspector:
    """Cheap fallback: measurable color/layout hints, never semantic certainty."""

    descriptor = ProviderDescriptor(
        name="local_product_probe",
        display_name="Yerel Ürün Ön Analizi",
        capabilities=(Capability.VISION,),
        priority=90,
        cost_tier=0,
        remote=False,
        notes="Yalnızca ölçülebilir renk ve boyut ipuçları; semantik QC değildir.",
    )

    def is_available(self) -> bool:
        try:
            import PIL  # noqa: F401

            return True
        except ImportError:
            return False

    def quota_state(self) -> str:
        return "not_applicable"

    @staticmethod
    def _profile(path: str) -> Dict[str, Any]:
        from PIL import Image, ImageStat

        image = Image.open(path).convert("RGB")
        thumbnail = image.copy()
        thumbnail.thumbnail((96, 96))
        quantized = thumbnail.quantize(colors=5).convert("RGB")
        colors = quantized.getcolors(maxcolors=96 * 96) or []
        top = sorted(colors, key=lambda item: item[0], reverse=True)[:5]
        hex_colors = ["#%02X%02X%02X" % rgb for _, rgb in top]
        mean = tuple(float(v) for v in ImageStat.Stat(thumbnail).mean[:3])
        return {
            "width": image.width,
            "height": image.height,
            "dominant_colors": hex_colors,
            "mean": mean,
        }

    def analyze_images(self, prompt: str, image_paths: List[str]) -> Dict[str, Any]:
        del prompt
        if not image_paths:
            raise ProviderError("Yerel analiz için görsel gerekli.", retryable=False)
        source = self._profile(image_paths[0])
        if len(image_paths) == 1:
            return {
                "summary": (
                    f"{source['width']}×{source['height']} kaynak ürün; semantik özellikler "
                    "Gemini vision olmadan doğrulanamadı."
                ),
                "dominant_colors": source["dominant_colors"],
                "motifs": ["semantik motif analizi için vision provider gerekli"],
                "border": "yerel ön analizde güvenilir biçimde belirlenemedi",
                "visible_text_or_logo": [],
                "material_notes": [],
                "preservation_priorities": [
                    "kaynak renk paletini koru",
                    "desen, bordür ve görünür yazıyı değiştirme",
                ],
                "confidence": 0.2,
            }

        result = self._profile(image_paths[1])
        distance = math.sqrt(
            sum((source["mean"][i] - result["mean"][i]) ** 2 for i in range(3))
        )
        color_score = max(0, min(100, int(100 - distance / 2.5)))
        return {
            "overall_score": color_score,
            "color_match": f"Ortalama renk benzerliği yaklaşık %{color_score}",
            "motif_match": "yerel ön analizde semantik olarak değerlendirilemedi",
            "border_match": "yerel ön analizde semantik olarak değerlendirilemedi",
            "logo_match": "yerel ön analizde semantik olarak değerlendirilemedi",
            "detected_changes": [
                "Gemini vision yok; sonuç yalnızca kaba renk istatistiğine dayanıyor"
            ],
            "correction_prompt": "Kaynak ürünün renk, motif, bordür ve görünür yazılarını daha sıkı koru.",
            "recommendation": "review",
        }


class QwenImageFallbackProvider:
    descriptor = ProviderDescriptor(
        name="qwen_image_edit",
        display_name="Qwen Image Edit (Fallback)",
        capabilities=(Capability.IMAGE_GENERATION, Capability.IMAGE_EDIT),
        priority=60,
        cost_tier=1,
        remote=False,
        notes="Lisansı ve model erişimi runtime yöneticisi tarafından doğrulanmalıdır.",
    )

    def is_available(self) -> bool:
        if os.environ.get("FPRO_ENABLE_QWEN_FALLBACK", "true").lower() not in {"1", "true", "yes"}:
            return False
        try:
            import torch

            return torch.cuda.is_available() or os.environ.get(
                "FPRO_ALLOW_CPU_IMAGE_FALLBACK", "false"
            ).lower() in {"1", "true", "yes"}
        except ImportError:
            return False

    def quota_state(self) -> str:
        return "not_applicable"

    def generate_image(
        self,
        prompt: str,
        input_paths: List[str],
        output_path: str,
        aspect_ratio: str,
        status_callback=None,
    ) -> str:
        del aspect_ratio
        if not input_paths:
            raise ProviderError("Qwen image-edit için kaynak görsel gerekli.", retryable=False)
        try:
            from providers.fashion_image_provider import fashion_image_provider

            return fashion_image_provider.generate_reference(
                input_paths[0],
                prompt,
                "ürün desenini, rengini, bordürünü veya logosunu değiştirme",
                output_path,
                status_callback,
            )
        except Exception as exc:
            raise ProviderError(f"Qwen fallback başarısız: {exc}") from exc


class CogVideoFallbackProvider:
    descriptor = ProviderDescriptor(
        name="cogvideox_int8",
        display_name="CogVideoX INT8 / T4 (Fallback)",
        capabilities=(Capability.VIDEO_GENERATION,),
        priority=60,
        cost_tier=1,
        remote=False,
        notes="Onaylı görseli yerel CogVideoX-5B-I2V pipeline'ına verir.",
    )

    def is_available(self) -> bool:
        if os.environ.get("FPRO_ENABLE_COGVIDEO_FALLBACK", "true").lower() not in {"1", "true", "yes"}:
            return False
        try:
            import torch

            return torch.cuda.is_available()
        except ImportError:
            return False

    def quota_state(self) -> str:
        return "not_applicable"

    def generate_video(
        self,
        prompt: str,
        approved_image_path: str,
        output_path: str,
        aspect_ratio: str,
        duration_seconds: int,
        status_callback=None,
    ) -> str:
        try:
            from providers.registry import get_provider

            portrait = aspect_ratio == "9:16"
            square = aspect_ratio == "1:1"
            width, height = (480, 720) if portrait else ((512, 512) if square else (720, 480))
            fps = int(os.environ.get("VIDEO_FPS", "8"))
            frames = max(9, min(duration_seconds * fps + 1, 49))
            return get_provider("cogvideox").generate_clip(
                prompt,
                "ürün kimliği değişmesi, desen kayması, logo bozulması, deformasyon",
                output_path,
                width,
                height,
                frames,
                20,
                approved_image_path,
                status_callback,
                fps,
            )
        except Exception as exc:
            raise ProviderError(f"CogVideoX fallback başarısız: {exc}") from exc

