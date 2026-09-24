"""
Backend'in desteklediği modlar, süreler, oranlar, kalite/hareket/kamera
seçenekleri ve hazır stil presetleri burada tek yerde tanımlanır.

Android uygulaması bu bilgiyi APK'ya gömmek yerine GET /capabilities
üzerinden okur; böylece backend tarafında bir seçenek eklenip/çıkarıldığında
APK'yı yeniden derlemeye gerek kalmaz.
"""

import os
from typing import Optional

import torch

# ---------------------------------------------------------------------------
# Temel seçenekler
# ---------------------------------------------------------------------------

MODES = ["image_to_video", "text_to_video"]

# Hedef süreler (saniye). Gerçekte üretilen süre, modelin kararlı çalıştığı
# sahne uzunluğunun katları olacağı için hedefe YAKLAŞIK ulaşılır; gerçek
# süre job durumunda "actual_duration_seconds" alanıyla bildirilir.
TARGET_DURATIONS_SECONDS = [5, 10, 15]

ASPECT_RATIOS = {
    # etiket -> (genişlik, yükseklik) — Wan'ın 16'nın katı çözünürlük şartına uygun
    "16:9": (832, 480),
    "9:16": (480, 832),
    "1:1": (640, 640),
}

QUALITY_PRESETS = {
    # ad: (num_inference_steps, num_frames)
    "fast": (18, 25),
    "standard": (30, 33),
    "high": (45, 49),
}

MOTION_LEVELS = {
    "subtle": "very slight, barely noticeable natural motion",
    "natural": "natural, smooth and lifelike motion",
    "dynamic": "energetic, expressive and dynamic motion",
}

CAMERA_MOVES = {
    "static": "completely static, locked-off camera",
    "push_in": "slow cinematic camera push-in",
    "pull_out": "slow cinematic camera pull-out",
    "pan_left": "slow smooth camera pan from right to left",
    "pan_right": "slow smooth camera pan from left to right",
    "orbit": "slow circular orbit camera movement around the subject",
}

STYLE_PRESETS = {
    "luxury_fashion": "luxury high-fashion editorial look, elegant styling, premium atmosphere",
    "luxury_scarf_campaign": "premium modest-fashion scarf campaign, elegant adult female model wearing the supplied scarf, refined editorial lighting, restrained graceful pose, shallow depth of field, textile-first composition, slow premium commercial pacing",
    "studio": "clean professional studio setting, soft studio lighting, neutral background",
    "modern_architecture": "modern minimalist architectural background, clean geometric lines",
    "natural_daylight": "soft natural daylight, outdoor setting, warm ambient light",
    "urban": "urban city background, contemporary street style atmosphere",
    "desert_stone": "warm desert or natural stone backdrop, earthy tones",
    "minimal": "minimal plain background, no clutter, product-focused composition",
    "product_closeup": "extreme close-up on product details, shallow depth of field",
}

# ---------------------------------------------------------------------------
# Ürün koruma (kullanıcının verdiği talimatlarla birebir aynı)
# ---------------------------------------------------------------------------

PRODUCT_PROTECTION_POSITIVE = (
    "Preserve the supplied product exactly as shown in the source image. "
    "Maintain its original colors, pattern, print, border, logo, lettering, "
    "texture, proportions and distinctive details throughout the video. "
    "Do not redesign, replace or invent any product pattern or branding. "
    "Keep product identity temporally consistent across all frames."
)

PRODUCT_PROTECTION_NEGATIVE = (
    "product redesign, changed pattern, invented pattern, changed colors, "
    "changed border, changed logo, altered lettering, warped textile, "
    "texture mutation, product morphing, flicker, temporal inconsistency"
)

# ---------------------------------------------------------------------------
# GPU seviyesi tespiti (kaba, ama sağlamlık için yeterli bir sezgisel kural)
# ---------------------------------------------------------------------------


def detect_gpu_tier() -> str:
    """
    'low'    -> ~16GB civarı (ör. Colab ücretsiz T4)
    'mid'    -> ~24GB civarı (ör. L4)
    'high'   -> ~40GB+ (ör. A100)
    'unknown'-> CUDA yok / tespit edilemedi (güvenlik için 'low' gibi davranılır)
    """
    override = os.environ.get("GPU_TIER_OVERRIDE")
    if override in ("low", "mid", "high"):
        return override

    if not torch.cuda.is_available():
        return "unknown"

    try:
        total_gb = torch.cuda.get_device_properties(0).total_memory / (1024 ** 3)
    except Exception:
        return "unknown"

    if total_gb < 20:
        return "low"
    if total_gb < 30:
        return "mid"
    return "high"


# Hangi (kalite, hedef süre) kombinasyonuna hangi GPU seviyesinde izin verildiği.
# Bunlar gerçek ölçümle değil temkinli bir tahminle belirlenmiştir; gerçek
# donanımda test ettikten sonra bu tabloyu genişletmeniz/daraltmanız önerilir.
ALLOWED_COMBINATIONS = {
    "low": {"fast": {5, 10, 15}, "standard": {5, 10}, "high": {5}},
    "mid": {"fast": {5, 10, 15}, "standard": {5, 10, 15}, "high": {5, 10}},
    "high": {"fast": {5, 10, 15}, "standard": {5, 10, 15}, "high": {5, 10, 15}},
    # GPU tespit edilemediyse en temkinli (low) tabloyla aynı davran
    "unknown": {"fast": {5, 10, 15}, "standard": {5, 10}, "high": {5}},
}


def check_feasible(quality: str, target_duration_seconds: int, num_products: int = 1):
    """
    (ok, reason, suggestion) döner. ok=False ise reason kullanıcıya
    gösterilecek Türkçe açıklama, suggestion önerilen alternatif kombinasyondur.
    """
    tier = detect_gpu_tier()
    allowed = ALLOWED_COMBINATIONS.get(tier, ALLOWED_COMBINATIONS["unknown"])
    allowed_durations = allowed.get(quality, set())

    if target_duration_seconds not in allowed_durations:
        best = max(allowed_durations) if allowed_durations else 5
        return (
            False,
            f"Tespit edilen GPU seviyesi '{tier}' için '{quality}' kalitede "
            f"{target_duration_seconds} sn video güvenli değil (OOM riski yüksek).",
            {"quality": quality, "target_duration_seconds": best},
        )

    # Çoklu ürün (reklam modu) ek yük getirir; ürün sayısı arttıkça toplam
    # sahne sayısı da arttığından yalnızca "high" kalitede ekstra bir sınır koy.
    if quality == "high" and num_products > 4 and tier in ("low", "unknown"):
        return (
            False,
            f"'{tier}' GPU seviyesinde 'high' kalitede {num_products} ürünlü "
            "reklam videosu önerilmez. Kaliteyi düşürün veya ürün sayısını azaltın.",
            {"quality": "standard", "target_duration_seconds": target_duration_seconds},
        )

    return True, None, None


def scene_seconds_for_quality(quality: str, fps: int) -> float:
    _, frames = QUALITY_PRESETS.get(quality, QUALITY_PRESETS["standard"])
    return frames / float(fps)


def get_capabilities_payload(fps: int = 16) -> dict:
    tier = detect_gpu_tier()
    return {
        "provider": "wan",
        "modes": MODES,
        "target_durations_seconds": TARGET_DURATIONS_SECONDS,
        "aspect_ratios": list(ASPECT_RATIOS.keys()),
        "quality_presets": list(QUALITY_PRESETS.keys()),
        "motion_levels": list(MOTION_LEVELS.keys()),
        "camera_moves": list(CAMERA_MOVES.keys()),
        "style_presets": list(STYLE_PRESETS.keys()),
        "product_protection_default": True,
        "gpu_tier": tier,
        "allowed_combinations": ALLOWED_COMBINATIONS.get(tier, ALLOWED_COMBINATIONS["unknown"]),
        "scene_seconds_by_quality": {
            q: round(scene_seconds_for_quality(q, fps), 2) for q in QUALITY_PRESETS
        },
        "notes": (
            "Süreler yaklaşıktır: video, modelin kararlı ürettiği kısa sahnelerin "
            "art arda eklenmesiyle oluşturulur (bkz. scene_seconds_by_quality). "
            "allowed_combinations, tespit edilen GPU belleğine göre hangi kalite/süre "
            "kombinasyonunun güvenli sayıldığını gösterir."
        ),
    }
