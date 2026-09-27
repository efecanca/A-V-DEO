from __future__ import annotations

import json
from typing import List, Optional, Tuple

from studio.models import Capability, ProductFingerprint, QcReport
from studio.provider_registry import ProviderRegistry


class DirectorAgent:
    name = "director"

    def build_image_brief(
        self,
        user_prompt: str,
        fingerprint: ProductFingerprint,
        *,
        is_revision: bool,
    ) -> str:
        mode = "Revise the existing approved candidate" if is_revision else "Create a new image"
        return f"""
You are producing a photorealistic luxury modest-fashion campaign image.
{mode}. The first image is the real product and is the source of truth. If a model
reference is supplied, preserve that person's identity, pose language and styling.

NON-NEGOTIABLE PRODUCT GUARDRAILS:
- Preserve the real scarf/product colors, pattern geometry, motifs, border placement,
  weave character, visible logo and readable text as faithfully as possible.
- Do not invent a replacement product. Do not mirror, redraw or simplify the pattern.
- The product must sit naturally on the model with realistic folds, scale and lighting.
- Keep product details visible enough for a customer to compare with the source.

PRODUCT FINGERPRINT (model-derived, not absolute truth):
{json.dumps(fingerprint.to_dict(), ensure_ascii=False)}

USER DIRECTION:
{user_prompt.strip()}

Return one polished editorial fashion photograph. No collage, UI, watermark or caption.
""".strip()

    def build_video_brief(self, user_prompt: str, motion: str, camera: str) -> str:
        return f"""
Animate the supplied, user-approved fashion image as the exact first frame and visual
identity reference. Preserve the model, scarf colors, motif, border, logo, clothing and
background continuity. Do not replace or redesign the scarf. Motion: {motion}. Camera:
{camera}. Create a refined luxury fashion film with physically plausible fabric motion.
Original creative direction: {user_prompt.strip()}
""".strip()


class ProductGuardianAgent:
    name = "product_guardian"

    def __init__(self, registry: ProviderRegistry) -> None:
        self.registry = registry

    def analyze(self, product_path: str, preferred_provider: Optional[str] = None) -> ProductFingerprint:
        prompt = """
Analyze only the physical product in this image. Return JSON with exactly these keys:
summary (string), dominant_colors (array of precise color descriptions or hex estimates),
motifs (array), border (string), visible_text_or_logo (array), material_notes (array),
preservation_priorities (array), confidence (number 0..1). Be conservative: use
"uncertain" when details are not visible. Do not claim pixel-perfect verification.
""".strip()
        provider_name, value, _attempts = self.registry.execute(
            Capability.VISION,
            lambda provider: provider.analyze_images(prompt, [product_path]),
            preferred=preferred_provider,
        )
        return ProductFingerprint.from_mapping(value, provider_name)


class ImageAgent:
    name = "image_agent"

    def __init__(self, registry: ProviderRegistry) -> None:
        self.registry = registry

    def generate(
        self,
        prompt: str,
        input_paths: List[str],
        output_path: str,
        aspect_ratio: str,
        preferred_provider: Optional[str],
        status_callback,
    ) -> Tuple[str, str, List[str]]:
        provider_name, result, attempts = self.registry.execute(
            Capability.IMAGE_EDIT,
            lambda provider: provider.generate_image(
                prompt, input_paths, output_path, aspect_ratio, status_callback
            ),
            preferred=preferred_provider,
        )
        return provider_name, str(result), attempts


class VisualQcAgent:
    name = "visual_qc"

    def __init__(self, registry: ProviderRegistry) -> None:
        self.registry = registry

    def compare(
        self,
        source_path: str,
        result_path: str,
        fingerprint: ProductFingerprint,
        preferred_provider: Optional[str] = None,
    ) -> QcReport:
        prompt = f"""
Compare image 1 (real product source) with image 2 (generated fashion result). Focus on
product identity, not composition. Inspect colors, motif geometry, border, logo/readable
text and obvious shape distortions. The prior fingerprint is only a hint:
{json.dumps(fingerprint.to_dict(), ensure_ascii=False)}

Return JSON with: overall_score (integer 0..100), color_match (string), motif_match
(string), border_match (string), logo_match (string), detected_changes (array),
correction_prompt (short actionable string), recommendation (accept|review|revise).
This is a probabilistic visual assessment, never claim pixel-perfect proof.
""".strip()
        provider_name, value, _attempts = self.registry.execute(
            Capability.VISION,
            lambda provider: provider.analyze_images(prompt, [source_path, result_path]),
            preferred=preferred_provider,
        )
        return QcReport.from_mapping(value, provider_name)


class VideoAgent:
    name = "video_agent"

    def __init__(self, registry: ProviderRegistry) -> None:
        self.registry = registry

    def generate(
        self,
        prompt: str,
        approved_image_path: str,
        output_path: str,
        aspect_ratio: str,
        duration_seconds: int,
        preferred_provider: Optional[str],
        status_callback,
    ) -> Tuple[str, str, List[str]]:
        provider_name, result, attempts = self.registry.execute(
            Capability.VIDEO_GENERATION,
            lambda provider: provider.generate_video(
                prompt,
                approved_image_path,
                output_path,
                aspect_ratio,
                duration_seconds,
                status_callback,
            ),
            preferred=preferred_provider,
        )
        return provider_name, str(result), attempts

