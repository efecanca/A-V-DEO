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
    "flickering, duplicate person, extra limbs, text, watermark, distorted textile"
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
    parts = [ (user_prompt or DEFAULT_BASE_PROMPT).strip() ]

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
