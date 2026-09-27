import base64
import sys
import tempfile
import unittest
from pathlib import Path


BACKEND_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_DIR))

from studio.google_provider import GeminiImageProvider, GoogleVeoProvider  # noqa: E402


class FakeGoogleClient:
    def __init__(self):
        self.calls = []
        self.downloads = []
        self.sleep_fn = lambda _seconds: None

    def available(self):
        return True

    def quota_state(self):
        return "available"

    def request_json(self, method, url, *, payload=None, timeout=120):
        self.calls.append((method, url, payload, timeout))
        if url.endswith("/interactions"):
            return {
                "output_image": {
                    "data": base64.b64encode(b"generated-image").decode("ascii")
                }
            }
        if url.endswith(":predictLongRunning"):
            return {"name": "operations/video-123"}
        if url.endswith("/operations/video-123"):
            return {
                "done": True,
                "response": {
                    "generateVideoResponse": {
                        "generatedSamples": [
                            {"video": {"uri": "https://files.example/video.mp4"}}
                        ]
                    }
                },
            }
        raise AssertionError(f"Unexpected request: {method} {url}")

    def download(self, url, output_path):
        self.downloads.append((url, output_path))
        Path(output_path).write_bytes(b"generated-video")


class GoogleProviderContractTests(unittest.TestCase):
    def test_gemini_interactions_uses_product_image_and_official_output_shape(self):
        client = FakeGoogleClient()
        provider = GeminiImageProvider(client)
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "product.png"
            output = Path(temp) / "result.png"
            source.write_bytes(b"real-product")

            provider.generate_image("fashion brief", [str(source)], str(output), "9:16")

            method, url, payload, _timeout = client.calls[0]
            self.assertEqual("POST", method)
            self.assertTrue(url.endswith("/v1beta/interactions"))
            self.assertEqual("gemini-3.1-flash-image", payload["model"])
            self.assertEqual("9:16", payload["response_format"]["aspect_ratio"])
            self.assertEqual(
                base64.b64encode(b"real-product").decode("ascii"),
                payload["input"][1]["data"],
            )
            self.assertEqual(b"generated-image", output.read_bytes())

    def test_veo_animates_the_exact_approved_result_as_initial_image(self):
        client = FakeGoogleClient()
        provider = GoogleVeoProvider(client)
        with tempfile.TemporaryDirectory() as temp:
            approved = Path(temp) / "approved.png"
            output = Path(temp) / "video.mp4"
            approved.write_bytes(b"user-approved-result")

            provider.generate_video(
                "luxury fashion film",
                str(approved),
                str(output),
                "9:16",
                8,
            )

            method, url, payload, _timeout = client.calls[0]
            self.assertEqual("POST", method)
            self.assertTrue(url.endswith("/models/veo-3.1-generate-preview:predictLongRunning"))
            self.assertEqual("9:16", payload["parameters"]["aspectRatio"])
            self.assertEqual("8", payload["parameters"]["durationSeconds"])
            self.assertEqual(
                base64.b64encode(b"user-approved-result").decode("ascii"),
                payload["instances"][0]["image"]["inlineData"]["data"],
            )
            self.assertEqual(
                [("https://files.example/video.mp4", str(output))], client.downloads
            )
            self.assertEqual(b"generated-video", output.read_bytes())


if __name__ == "__main__":
    unittest.main()
