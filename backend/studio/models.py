from __future__ import annotations

from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple


class Capability(str, Enum):
    VISION = "vision"
    IMAGE_GENERATION = "image_generation"
    IMAGE_EDIT = "image_edit"
    VIDEO_GENERATION = "video_generation"


@dataclass(frozen=True)
class ProviderDescriptor:
    name: str
    display_name: str
    capabilities: Tuple[Capability, ...]
    priority: int
    cost_tier: int
    remote: bool
    notes: str = ""

    def public_dict(self, available: bool, quota_state: str) -> Dict[str, Any]:
        return {
            "name": self.name,
            "display_name": self.display_name,
            "capabilities": [item.value for item in self.capabilities],
            "available": available,
            "quota_state": quota_state,
            "cost_tier": self.cost_tier,
            "remote": self.remote,
        }


@dataclass
class ProductFingerprint:
    summary: str = ""
    dominant_colors: List[str] = field(default_factory=list)
    motifs: List[str] = field(default_factory=list)
    border: str = "belirsiz"
    visible_text_or_logo: List[str] = field(default_factory=list)
    material_notes: List[str] = field(default_factory=list)
    preservation_priorities: List[str] = field(default_factory=list)
    confidence: Optional[float] = None
    provider: str = "unknown"

    @classmethod
    def from_mapping(cls, value: Dict[str, Any], provider: str) -> "ProductFingerprint":
        def strings(key: str) -> List[str]:
            raw = value.get(key, [])
            if isinstance(raw, str):
                return [raw]
            if not isinstance(raw, list):
                return []
            return [str(item) for item in raw if item is not None]

        confidence = value.get("confidence")
        try:
            confidence_value = float(confidence) if confidence is not None else None
        except (TypeError, ValueError):
            confidence_value = None
        return cls(
            summary=str(value.get("summary", "")),
            dominant_colors=strings("dominant_colors"),
            motifs=strings("motifs"),
            border=str(value.get("border", "belirsiz")),
            visible_text_or_logo=strings("visible_text_or_logo"),
            material_notes=strings("material_notes"),
            preservation_priorities=strings("preservation_priorities"),
            confidence=confidence_value,
            provider=provider,
        )

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class QcReport:
    overall_score: Optional[int] = None
    color_match: str = "değerlendirilemedi"
    motif_match: str = "değerlendirilemedi"
    border_match: str = "değerlendirilemedi"
    logo_match: str = "değerlendirilemedi"
    detected_changes: List[str] = field(default_factory=list)
    correction_prompt: str = ""
    recommendation: str = "review"
    provider: str = "unknown"
    disclaimer: str = (
        "Bu değerlendirme görsel bir model tahminidir; kesin piksel veya ürün doğrulaması değildir."
    )

    @classmethod
    def from_mapping(cls, value: Dict[str, Any], provider: str) -> "QcReport":
        score = value.get("overall_score")
        try:
            score_value = max(0, min(int(score), 100)) if score is not None else None
        except (TypeError, ValueError):
            score_value = None
        changes = value.get("detected_changes", [])
        if isinstance(changes, str):
            changes = [changes]
        if not isinstance(changes, list):
            changes = []
        recommendation = str(value.get("recommendation", "review")).lower()
        if recommendation not in {"accept", "review", "revise"}:
            recommendation = "review"
        return cls(
            overall_score=score_value,
            color_match=str(value.get("color_match", "değerlendirilemedi")),
            motif_match=str(value.get("motif_match", "değerlendirilemedi")),
            border_match=str(value.get("border_match", "değerlendirilemedi")),
            logo_match=str(value.get("logo_match", "değerlendirilemedi")),
            detected_changes=[str(item) for item in changes if item is not None],
            correction_prompt=str(value.get("correction_prompt", "")),
            recommendation=recommendation,
            provider=provider,
        )

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

