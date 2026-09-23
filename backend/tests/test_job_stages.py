import sys
import unittest
from pathlib import Path


BACKEND_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_DIR))

from job_manager import JobManager  # noqa: E402
from job_progress import overall_generation_progress  # noqa: E402


class JobStageTests(unittest.TestCase):
    def test_new_job_has_queued_stage_without_fake_percentage(self):
        manager = JobManager()
        job_id = manager.create_job(scenes_total=1, mode="image_to_video")

        job = manager.get_job(job_id)
        self.assertEqual("queued", job["status"])
        self.assertEqual("queued", job["stage"])
        self.assertIsNone(job["progress"])

    def test_unmeasurable_stages_never_get_percentage(self):
        for stage in ("model_downloading", "model_loading", "quantizing", "encoding"):
            with self.subTest(stage=stage):
                self.assertIsNone(overall_generation_progress(stage, 75, 0, 1))

    def test_real_diffusion_progress_is_mapped_across_scenes(self):
        self.assertEqual(20, overall_generation_progress("generating", 40, 0, 2))
        self.assertEqual(70, overall_generation_progress("generating", 40, 1, 2))
        self.assertEqual(99, overall_generation_progress("generating", 100, 0, 1))

    def test_failed_job_keeps_error_and_nullable_progress(self):
        manager = JobManager()
        job_id = manager.create_job(scenes_total=1)
        manager.update_job(
            job_id,
            status="failed",
            stage="failed",
            stage_detail="CUDA out of memory during quantizing",
            progress=None,
            error="Video üretimi başarısız oldu: CUDA out of memory",
        )

        job = manager.get_job(job_id)
        self.assertEqual("failed", job["stage"])
        self.assertIsNone(job["progress"])
        self.assertIn("CUDA out of memory", job["error"])


if __name__ == "__main__":
    unittest.main()
