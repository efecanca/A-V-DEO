"""Provider aşamalarını job ilerlemesine dönüştüren saf yardımcılar."""

from typing import Optional


def overall_generation_progress(
    stage: str,
    progress: Optional[int],
    scene_index: int,
    scene_count: int,
) -> Optional[int]:
    """Yalnızca gerçek diffusion yüzdesini çok-sahneli toplam yüzdeye çevir."""
    if stage != "generating" or progress is None:
        return None
    if scene_count < 1:
        raise ValueError("scene_count en az 1 olmalı")

    measured = max(0, min(int(progress), 100))
    overall = int(((scene_index + measured / 100.0) / scene_count) * 100)
    return min(overall, 99)
