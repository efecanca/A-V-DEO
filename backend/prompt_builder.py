"""
Kullanıcının serbest metin promptunu; ürün koruma talimatı, hazır stil
preseti, hareket yoğunluğu ve kamera hareketi ile birleştirip backend'in
modele göndereceği nihai prompt/negative_prompt çiftini üretir.

Bu birleştirme backend tarafında yapılır ki Android istemcisi her zaman
kısa/basit bir metin göndersin; koruma talimatının tam metni istemci
tarafında tekrar edilmesin (tek doğruluk kaynağı burasıdır).
"""

from typing import Optional

from capabilities import (
    CAMERA_MOVES,
    MOTION_LEVELS,
    PRODUCT_PROTECTION_NEGATIVE,
    PRODUCT_PROTECTION_POSITIVE,
    STYLE_PRESETS,
)

DEFAULT_BASE_PROMPT = (
    "Animate the supplied product photograph with subtle natural motion. "
    "Preserve clothing, person and background exactly as shown."
)

DEFAULT_BASE_NEGATIVE = (
    "flickering, duplicate person, extra limbs, text, watermark, distorted textile, "
    "abstract texture, psychedelic colors, melting face, morphing face, disappearing subject, "
    "camera shake, fast motion, scarf deformation, pattern drift, logo mutation"
)

LUXURY_SCARF_CAMPAIGN_PROMPT = (
    "Create a premium modest-fashion scarf commercial from the supplied reference photograph. "
    "Keep the same adult model, scarf placement, styling and scene identity from the first frame "
    "through the final frame. The model remains composed and elegant with only micro movements: "
    "a subtle blink, tiny natural breathing, and a very small graceful head adjustment. "
    "The scarf is the hero product and must stay sharply recognizable. Preserve every original "
    "color, motif, border, logo, lettering, weave and fold; never invent or transform the textile. "
    "Use refined soft editorial lighting, realistic skin and fabric, shallow depth of field and "
    "a restrained luxury campaign aesthetic. Motion must remain temporally stable and photorealistic."
)


def build_prompt(
    user_prompt: Optional[str],
    user_negative_prompt: Optional[str],
    motion: Optional[str],
    camera: Optional[str],
    style_preset: Optional[str],
    product_protection: bool = True,
) -> tuple[str, str]:
    """
    (final_prompt, final_negative_prompt) döner.
    """
    if style_preset == "luxury_scarf_campaign":
        base_prompt = LUXURY_SCARF_CAMPAIGN_PROMPT
        # Kullanıcının ek açıklaması ana kampanya talimatını ezmesin; sonuna eklenir.
        if user_prompt and user_prompt.strip():
            base_prompt += " Additional direction: " + user_prompt.strip()
        parts = [base_prompt]
    else:
        parts = [(user_prompt or DEFAULT_BASE_PROMPT).strip()]

    if motion and motion in MOTION_LEVELS:
        parts.append(MOTION_LEVELS[motion])

    if camera and camera in CAMERA_MOVES:
        parts.append(CAMERA_MOVES[camera])

    if style_preset and style_preset in STYLE_PRESETS:
        parts.append(STYLE_PRESETS[style_preset])

    if product_protection:
        parts.append(PRODUCT_PROTECTION_POSITIVE)

    final_prompt = ". ".join(p.rstrip(".") for p in parts if p) + "."

    neg_parts = [(user_negative_prompt or DEFAULT_BASE_NEGATIVE).strip()]
    if product_protection:
        neg_parts.append(PRODUCT_PROTECTION_NEGATIVE)

    final_negative = ", ".join(p.strip(", ") for p in neg_parts if p)

    return final_prompt, final_negative
