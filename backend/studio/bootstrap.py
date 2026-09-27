from __future__ import annotations

from studio.google_provider import GeminiImageProvider, GoogleVeoProvider, _GoogleRestClient
from studio.local_providers import (
    CogVideoFallbackProvider,
    LocalProductInspector,
    QwenImageFallbackProvider,
)
from studio.provider_registry import ProviderRegistry


def build_provider_registry() -> ProviderRegistry:
    google_client = _GoogleRestClient()
    return ProviderRegistry(
        [
            GeminiImageProvider(google_client),
            GoogleVeoProvider(google_client),
            QwenImageFallbackProvider(),
            CogVideoFallbackProvider(),
            LocalProductInspector(),
        ]
    )

