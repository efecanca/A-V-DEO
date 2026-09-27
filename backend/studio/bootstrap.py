from __future__ import annotations

import os

from studio.google_provider import GeminiImageProvider, GoogleVeoProvider, _GoogleRestClient
from studio.local_providers import (
    CogVideoFallbackProvider,
    LocalProductInspector,
    QwenImageFallbackProvider,
)
from studio.provider_registry import ProviderRegistry


def build_provider_registry() -> ProviderRegistry:
    google_client = _GoogleRestClient()

    # Gemini anahtarı mevcutsa ağır Qwen image fallback'ini varsayılan olarak
    # registry'ye bile ekleme. Böylece Kaggle/Colab görsel üretiminde Qwen
    # checkpoint indirmeye/yüklemeye başlayıp sistem RAM'ini tüketemez.
    # İstenirse FPRO_ENABLE_LOCAL_IMAGE_FALLBACK=1 ile tekrar açılabilir.
    providers = [
        GeminiImageProvider(google_client),
        GoogleVeoProvider(google_client),
    ]

    enable_local_image = os.environ.get("FPRO_ENABLE_LOCAL_IMAGE_FALLBACK", "").strip().lower()
    if not google_client.available() or enable_local_image in {"1", "true", "yes", "on"}:
        providers.append(QwenImageFallbackProvider())

    providers.extend(
        [
            CogVideoFallbackProvider(),
            LocalProductInspector(),
        ]
    )
    return ProviderRegistry(providers)
