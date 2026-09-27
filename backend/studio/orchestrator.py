from __future__ import annotations

import logging
import os
import uuid
from pathlib import Path
from typing import Any, Optional

from studio.agents import (
    DirectorAgent,
    ImageAgent,
    ProductGuardianAgent,
    VideoAgent,
    VisualQcAgent,
)
from studio.models import QcReport
from studio.provider_registry import ProviderRegistry
from studio.store import ProjectStore


logger = logging.getLogger("uvicorn.error")


class StudioOrchestrator:
    """Coordinates real agent/provider work and writes every observable stage to the job."""

    def __init__(self, registry: ProviderRegistry, store: ProjectStore, job_manager: Any) -> None:
        self.registry = registry
        self.store = store
        self.jobs = job_manager
        self.director = DirectorAgent()
        self.guardian = ProductGuardianAgent(registry)
        self.image_agent = ImageAgent(registry)
        self.qc_agent = VisualQcAgent(registry)
        self.video_agent = VideoAgent(registry)
        self.max_auto_corrections = max(
            0, min(int(os.environ.get("FPRO_MAX_AUTO_CORRECTIONS", "1")), 2)
        )
        self.qc_revise_threshold = max(
            0, min(int(os.environ.get("FPRO_QC_REVISE_THRESHOLD", "74")), 100)
        )

    def _stage(
        self,
        job_id: str,
        stage: str,
        detail: str,
        progress: Optional[int] = None,
        **extra: Any,
    ) -> None:
        self.jobs.update_job(
            job_id,
            status="processing",
            stage=stage,
            stage_detail=detail,
            progress=progress,
            **extra,
        )
        logger.info(
            "[STUDIO %s] aşama=%s ilerleme=%s ayrıntı=%s",
            job_id,
            stage,
            progress if progress is not None else "ölçülemiyor",
            detail,
        )

    def _image_provider_callback(self, job_id: str):
        stage_map = {
            "reference_model_loading": "scene_preparing",
            "reference_generating": "product_applying",
            "product_applying": "product_applying",
            "image_enhancing": "image_enhancing",
        }

        def callback(stage: str, _progress: Optional[int], detail: Optional[str]) -> None:
            mapped = stage_map.get(stage, "image_enhancing")
            self._stage(job_id, mapped, detail or "Görsel provider işlemi sürüyor", None)

        return callback

    def generate_image(
        self,
        *,
        job_id: str,
        project_id: str,
        product_path: str,
        user_prompt: str,
        model_path: Optional[str] = None,
        aspect_ratio: str = "9:16",
        preferred_provider: Optional[str] = None,
        revision_result_id: Optional[str] = None,
        auto_correct: bool = True,
    ) -> None:
        self.store.update_project(project_id, status="processing")
        self.store.add_message(project_id, "user", user_prompt)
        try:
            self._stage(
                job_id,
                "product_analyzing",
                "Ürünün renk, motif, bordür ve görünür yazıları analiz ediliyor",
            )
            # Registry priority makes Gemini the default without coupling the
            # orchestration layer to one vendor. A compatible user preference
            # is honoured; otherwise the registry selects the best vision provider.
            fingerprint = self.guardian.analyze(
                product_path, preferred_provider=preferred_provider
            )
            self.store.update_project(project_id, fingerprint=fingerprint.to_dict())

            self._stage(
                job_id,
                "scene_preparing",
                "Yönetmen ajanı sahne ve ürün koruma talimatlarını hazırlıyor",
            )
            revision_path: Optional[str] = None
            if revision_result_id:
                found = self.store.get_result(revision_result_id)
                if found is None or found[0] != project_id:
                    raise ValueError("Revizyon için seçilen görsel bu projede bulunamadı.")
                if found[1].get("media_type") != "image":
                    raise ValueError("Yalnızca görsel sonuçlar revize edilebilir.")
                revision_path = str(found[1]["path"])

            brief = self.director.build_image_brief(
                user_prompt, fingerprint, is_revision=revision_path is not None
            )
            inputs = [product_path]
            if revision_path:
                inputs.append(revision_path)
            if model_path:
                inputs.append(model_path)

            self._stage(
                job_id,
                "product_applying",
                "Gerçek ürün referansı manken ve sahneye uygulanıyor",
            )
            output_path = str(
                self.store.project_directory(project_id)
                / f"image_{uuid.uuid4().hex}.png"
            )
            provider, final_path, attempts = self.image_agent.generate(
                brief,
                inputs,
                output_path,
                aspect_ratio,
                preferred_provider,
                self._image_provider_callback(job_id),
            )

            self._stage(
                job_id,
                "image_enhancing",
                "Moda görselinin ayrıntıları ve doğal ışığı sonlandırılıyor",
                provider=provider,
                provider_attempts=attempts,
            )
            self._stage(
                job_id,
                "quality_checking",
                "Visual QC gerçek ürün ile oluşturulan görseli karşılaştırıyor",
            )
            qc = self.qc_agent.compare(
                product_path,
                final_path,
                fingerprint,
                preferred_provider=preferred_provider,
            )

            correction_count = 0
            should_correct = (
                auto_correct
                and self.max_auto_corrections > 0
                and qc.correction_prompt.strip()
                and (
                    qc.recommendation == "revise"
                    or (
                        qc.overall_score is not None
                        and qc.overall_score < self.qc_revise_threshold
                    )
                )
            )
            while should_correct and correction_count < self.max_auto_corrections:
                correction_count += 1
                self._stage(
                    job_id,
                    "image_enhancing",
                    f"Ürün koruma geri bildirimi uygulanıyor ({correction_count}/{self.max_auto_corrections})",
                )
                corrected_path = str(
                    self.store.project_directory(project_id)
                    / f"image_{uuid.uuid4().hex}_corrected.png"
                )
                correction_brief = (
                    brief
                    + "\n\nVISUAL QC CORRECTION (model assessment, apply conservatively):\n"
                    + qc.correction_prompt
                )
                correction_inputs = [product_path, final_path]
                if model_path:
                    correction_inputs.append(model_path)
                provider, final_path, correction_attempts = self.image_agent.generate(
                    correction_brief,
                    correction_inputs,
                    corrected_path,
                    aspect_ratio,
                    preferred_provider,
                    self._image_provider_callback(job_id),
                )
                attempts.extend(correction_attempts)
                self._stage(
                    job_id,
                    "quality_checking",
                    "Düzeltilen görsel ürün referansıyla yeniden karşılaştırılıyor",
                )
                qc = self.qc_agent.compare(
                    product_path,
                    final_path,
                    fingerprint,
                    preferred_provider=preferred_provider,
                )
                should_correct = False

            result_id = self.store.add_result(
                project_id,
                media_type="image",
                path=final_path,
                prompt=user_prompt,
                provider=provider,
                qc=qc.to_dict(),
                parent_result_id=revision_result_id,
                auto_corrections=correction_count,
            )
            public = self.store.public_result(
                project_id, self.store.get_result(result_id)[1]
            )
            self.store.add_message(
                project_id,
                "assistant",
                "Mankenli görsel hazır. Onaylayabilir veya sohbetten revizyon isteyebilirsin.",
                result_id=result_id,
            )
            self.jobs.update_job(
                job_id,
                status="completed",
                stage="completed",
                stage_detail="Mankenli görsel hazır",
                progress=100,
                project_id=project_id,
                result_id=result_id,
                media_type="image",
                image_url=public["media_url"],
                provider=provider,
                provider_attempts=attempts,
                qc=qc.to_dict(),
            )
            logger.info("[STUDIO %s] görsel tamamlandı: result=%s", job_id, result_id)
        except Exception as exc:
            logger.exception("[STUDIO %s] görsel ajan zinciri başarısız", job_id)
            self.store.update_project(project_id, status="failed", last_error=str(exc))
            self.jobs.update_job(
                job_id,
                status="failed",
                stage="failed",
                stage_detail=str(exc),
                progress=None,
                error=f"Görsel üretimi başarısız oldu: {exc}",
                project_id=project_id,
                media_type="image",
            )

    def generate_video(
        self,
        *,
        job_id: str,
        result_id: str,
        user_prompt: str,
        aspect_ratio: str,
        duration_seconds: int,
        motion: str,
        camera: str,
        preferred_provider: Optional[str] = None,
    ) -> None:
        found = self.store.get_result(result_id)
        if found is None:
            self.jobs.update_job(
                job_id,
                status="failed",
                stage="failed",
                stage_detail="Onaylanan görsel bulunamadı",
                error="Video üretimi için seçilen görsel bulunamadı.",
            )
            return
        project_id, image_result = found
        if image_result.get("media_type") != "image" or not image_result.get("approved"):
            self.jobs.update_job(
                job_id,
                status="failed",
                stage="failed",
                stage_detail="Görsel henüz kullanıcı tarafından onaylanmadı",
                error="Videoya geçmeden önce mankenli görseli onaylayın.",
            )
            return

        self.store.update_project(project_id, status="processing")
        try:
            self._stage(
                job_id,
                "scene_preparing",
                "Onaylanan görsel video başlangıç karesi olarak hazırlanıyor",
            )
            prompt = self.director.build_video_brief(user_prompt, motion, camera)
            output_path = str(
                self.store.project_directory(project_id)
                / f"video_{uuid.uuid4().hex}.mp4"
            )

            def callback(stage: str, progress: Optional[int], detail: Optional[str]) -> None:
                mapped = stage if stage in {"generating", "encoding"} else "generating"
                measured = progress if mapped == "generating" else None
                self._stage(job_id, mapped, detail or "Video provider işlemi sürüyor", measured)

            provider, final_path, attempts = self.video_agent.generate(
                prompt,
                str(image_result["path"]),
                output_path,
                aspect_ratio,
                duration_seconds,
                preferred_provider,
                callback,
            )
            self._stage(job_id, "encoding", "Video dosyası teslim için hazırlanıyor", None)
            video_result_id = self.store.add_result(
                project_id,
                media_type="video",
                path=final_path,
                prompt=user_prompt,
                provider=provider,
                parent_result_id=result_id,
            )
            public = self.store.public_result(
                project_id, self.store.get_result(video_result_id)[1]
            )
            self.store.add_message(
                project_id,
                "assistant",
                "Onaylanan görselden video hazırlandı.",
                result_id=video_result_id,
            )
            self.jobs.update_job(
                job_id,
                status="completed",
                stage="completed",
                stage_detail="Video hazır",
                progress=100,
                project_id=project_id,
                result_id=video_result_id,
                media_type="video",
                video_url=public["media_url"],
                provider=provider,
                provider_attempts=attempts,
            )
        except Exception as exc:
            logger.exception("[STUDIO %s] video ajan zinciri başarısız", job_id)
            self.store.update_project(project_id, status="failed", last_error=str(exc))
            self.jobs.update_job(
                job_id,
                status="failed",
                stage="failed",
                stage_detail=str(exc),
                progress=None,
                error=f"Video üretimi başarısız oldu: {exc}",
                project_id=project_id,
                media_type="video",
            )
