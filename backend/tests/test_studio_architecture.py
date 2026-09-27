import sys
import tempfile
import unittest
from pathlib import Path


BACKEND_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_DIR))

from job_manager import JobManager  # noqa: E402
from studio.models import Capability, ProviderDescriptor  # noqa: E402
from studio.orchestrator import StudioOrchestrator  # noqa: E402
from studio.provider_registry import ProviderError, ProviderRegistry  # noqa: E402
from studio.store import ProjectStore  # noqa: E402


class FakeProvider:
    descriptor = ProviderDescriptor(
        name="fake_primary",
        display_name="Fake Primary",
        capabilities=(
            Capability.VISION,
            Capability.IMAGE_EDIT,
            Capability.IMAGE_GENERATION,
            Capability.VIDEO_GENERATION,
        ),
        priority=1,
        cost_tier=1,
        remote=False,
    )

    def __init__(self, fail_image=False):
        self.fail_image = fail_image
        self.last_video_source = None

    def is_available(self):
        return True

    def quota_state(self):
        return "available"

    def analyze_images(self, _prompt, image_paths):
        if len(image_paths) == 1:
            return {
                "summary": "bordürlü ipek eşarp",
                "dominant_colors": ["burgundy", "cream"],
                "motifs": ["floral"],
                "border": "burgundy border",
                "visible_text_or_logo": ["FPRO"],
                "preservation_priorities": ["border", "logo"],
                "confidence": 0.8,
            }
        return {
            "overall_score": 92,
            "color_match": "high",
            "motif_match": "high",
            "border_match": "high",
            "logo_match": "review",
            "detected_changes": [],
            "correction_prompt": "",
            "recommendation": "accept",
        }

    def generate_image(self, _prompt, input_paths, output_path, _aspect, callback):
        if self.fail_image:
            raise ProviderError("planned failure")
        self.assert_source_first = input_paths[0]
        if callback:
            callback("product_applying", None, "real work")
        Path(output_path).write_bytes(b"generated-image")
        return output_path

    def generate_video(self, _prompt, approved_image_path, output_path, _aspect, _duration, callback):
        self.last_video_source = approved_image_path
        if callback:
            callback("generating", None, "remote operation")
            callback("encoding", None, "download")
        Path(output_path).write_bytes(b"generated-video")
        return output_path


class RecordingJobManager(JobManager):
    def __init__(self):
        super().__init__()
        self.stages = []

    def update_job(self, job_id, **fields):
        if fields.get("stage"):
            self.stages.append(fields["stage"])
        super().update_job(job_id, **fields)


class StudioArchitectureTests(unittest.TestCase):
    def test_provider_registry_falls_back_after_failure(self):
        failed = FakeProvider(fail_image=True)
        failed.descriptor = ProviderDescriptor(
            name="failing",
            display_name="Failing",
            capabilities=(Capability.IMAGE_EDIT,),
            priority=0,
            cost_tier=0,
            remote=True,
        )
        fallback = FakeProvider()
        registry = ProviderRegistry([failed, fallback])

        name, value, attempts = registry.execute(
            Capability.IMAGE_EDIT,
            lambda provider: provider.generate_image(
                "p", ["source"], tempfile.mktemp(), "9:16", None
            ),
        )

        self.assertEqual("fake_primary", name)
        self.assertEqual(["failing", "fake_primary"], attempts)
        self.assertTrue(Path(value).exists())
        Path(value).unlink()

    def test_project_store_hides_internal_paths_and_persists_approval(self):
        with tempfile.TemporaryDirectory() as temp:
            store = ProjectStore(Path(temp))
            project_id = store.create_project("Venedik çekimi")
            product = store.project_directory(project_id) / "product.jpg"
            product.write_bytes(b"source")
            store.set_sources(project_id, str(product))
            result = store.project_directory(project_id) / "result.png"
            result.write_bytes(b"image")
            result_id = store.add_result(
                project_id,
                media_type="image",
                path=str(result),
                prompt="prompt",
                provider="fake",
            )
            store.approve_result(result_id)

            reloaded = ProjectStore(Path(temp))
            public = reloaded.list_projects()[0]
            self.assertNotIn("product_path", public)
            self.assertEqual("/studio-media/projects/%s/result.png" % project_id, public["results"][0]["media_url"])
            self.assertTrue(reloaded.get_result(result_id)[1]["approved"])

    def test_image_requires_explicit_approval_before_video(self):
        with tempfile.TemporaryDirectory() as temp:
            provider = FakeProvider()
            registry = ProviderRegistry([provider])
            store = ProjectStore(Path(temp))
            jobs = RecordingJobManager()
            orchestrator = StudioOrchestrator(registry, store, jobs)
            orchestrator.max_auto_corrections = 0

            project_id = store.create_project("Venedik")
            product = store.project_directory(project_id) / "product.jpg"
            product.write_bytes(b"real-product")
            store.set_sources(project_id, str(product))
            image_job = jobs.create_job(mode="studio_image")
            orchestrator.generate_image(
                job_id=image_job,
                project_id=project_id,
                product_path=str(product),
                user_prompt="Venedik'te yürüyen lüks çekim",
            )

            image_state = jobs.get_job(image_job)
            image_result_id = image_state["result_id"]
            self.assertEqual("completed", image_state["status"])
            self.assertFalse(store.get_result(image_result_id)[1]["approved"])
            self.assertEqual([], [r for r in store.gallery() if r["media_type"] == "video"])
            for stage in (
                "product_analyzing",
                "scene_preparing",
                "product_applying",
                "image_enhancing",
                "quality_checking",
                "completed",
            ):
                self.assertIn(stage, jobs.stages)

            blocked_job = jobs.create_job(mode="studio_video")
            orchestrator.generate_video(
                job_id=blocked_job,
                result_id=image_result_id,
                user_prompt="film",
                aspect_ratio="9:16",
                duration_seconds=8,
                motion="walking",
                camera="tracking",
            )
            self.assertEqual("failed", jobs.get_job(blocked_job)["status"])

            store.approve_result(image_result_id)
            video_job = jobs.create_job(mode="studio_video")
            orchestrator.generate_video(
                job_id=video_job,
                result_id=image_result_id,
                user_prompt="film",
                aspect_ratio="9:16",
                duration_seconds=8,
                motion="walking",
                camera="tracking",
            )
            self.assertEqual("completed", jobs.get_job(video_job)["status"])
            self.assertEqual(store.get_result(image_result_id)[1]["path"], provider.last_video_source)


if __name__ == "__main__":
    unittest.main()

