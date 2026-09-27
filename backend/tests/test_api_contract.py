import sys
import unittest
from pathlib import Path


BACKEND_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_DIR))


try:
    from fastapi.testclient import TestClient
    import main
except ImportError:
    TestClient = None
    main = None


@unittest.skipIf(TestClient is None, "FastAPI test dependencies are not installed")
class ApiContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(main.app)

    def test_health_and_studio_capabilities_remain_available_without_local_gpu(self):
        self.assertEqual({"status": "ok"}, self.client.get("/health").json())
        response = self.client.get("/capabilities")
        self.assertEqual(200, response.status_code)
        studio = response.json()["studio"]
        self.assertTrue(studio["requires_image_approval_before_video"])
        names = {provider["name"] for provider in studio["providers"]}
        self.assertIn("gemini_image", names)
        self.assertIn("google_veo", names)
        self.assertIn("qwen_image_edit", names)
        self.assertIn("cogvideox_int8", names)

    def test_missing_runtime_job_returns_friendly_machine_readable_reason(self):
        response = self.client.get("/status/not-a-real-job")
        self.assertEqual(404, response.status_code)
        self.assertEqual("job_lost", response.json()["detail"]["code"])
        self.assertIn("sunucuda bulunmuyor", response.json()["detail"]["message"])


if __name__ == "__main__":
    unittest.main()

