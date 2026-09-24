"""FPRO AI: ürün fotoğrafından fotogerçekçi moda sahnesi hazırlama katmanı.

Bu katman video modelinden bağımsızdır. Amaç düz ürün/eşarp görselini önce
9:16, mankenli, fotogerçekçi bir referans kareye dönüştürmek; video sağlayıcısı
daha sonra bu kareyi hareketlendirir.
"""

from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class FashionScenePlan:
    prompt: str
    negative_prompt: str
    aspect_ratio: str = "9:16"


def build_fashion_scene_plan(user_prompt: Optional[str]) -> FashionScenePlan:
    request = (user_prompt or "elegant premium fashion campaign setting").strip()
    prompt = (
        "Use the uploaded scarf/product photograph as the strict product reference. "
        "Create a photorealistic professional fashion campaign photograph in vertical 9:16. "
        "Place the exact supplied scarf naturally tied on the head of an adult female fashion model. "
        "The scarf must behave like real silk around the head and neck with physically plausible folds. "
        "Preserve the source product identity: exact colors, motifs, ornamental border, logo/lettering, "
        "relative motif placement and distinctive design details. Do not redesign or substitute the scarf. "
        "Create natural human skin with pores and fine texture, realistic eyes, hair strands, hands, "
        "fabric microtexture, physically plausible shadows and reflections. Use real professional fashion "
        "photography language: natural lens rendering, restrained depth of field, believable exposure, "
        "editorial composition, no synthetic CGI look. "
        "Scene and direction requested by the user: " + request + ". "
        "The result is the hero reference frame for a luxury fashion video, so keep the model, scarf and "
        "scene coherent and suitable for later image-to-video animation."
    )
    negative = (
        "CGI, 3D render, illustration, plastic skin, wax skin, over-smoothed face, uncanny face, "
        "AI artifacts, malformed hands, extra fingers, duplicate person, impossible anatomy, "
        "fake fabric, painted textile, changed scarf colors, changed motif, invented motif, "
        "missing border, altered logo, gibberish lettering, warped pattern, product redesign, "
        "floating cloth, impossible folds, oversaturated HDR, excessive beauty filter"
    )
    return FashionScenePlan(prompt=prompt, negative_prompt=negative)
