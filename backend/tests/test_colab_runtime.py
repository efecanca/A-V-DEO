import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class ColabRuntimeContractTests(unittest.TestCase):
    def test_runtime_pins_compatible_torchao_stack_and_keeps_logs_visible(self):
        source = (ROOT / "colab" / "start_server.py").read_text(encoding="utf-8")
        for package in (
            '"diffusers==0.40.0"',
            '"transformers==5.17.0"',
            '"accelerate==1.15.0"',
            '"torchao==0.18.0"',
        ):
            self.assertIn(package, source)
        self.assertNotIn('"pip", "install", "-q"', source)
        self.assertIn("stdout=subprocess.PIPE", source)
        self.assertIn("stderr=subprocess.STDOUT", source)

    def test_cogvideox_applies_int8_during_component_loading(self):
        source = (ROOT / "backend" / "providers" / "cogvideox_provider.py").read_text(
            encoding="utf-8"
        )
        self.assertGreaterEqual(source.count("quantization_config="), 2)
        self.assertIn("Int8WeightOnlyConfig", source)
        self.assertIn("except torch.cuda.OutOfMemoryError", source)
        self.assertIn("logger.exception", source)

    def test_gemini_secret_is_backend_only_and_loaded_from_kaggle(self):
        android_sources = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (ROOT / "android" / "app" / "src").rglob("*.kt")
        )
        self.assertNotIn("GEMINI_API_KEY", android_sources)
        kaggle = (ROOT / "kaggle" / "FPRO_AI_Kaggle.py").read_text(encoding="utf-8")
        self.assertIn('get_secret("GEMINI_API_KEY")', kaggle)


if __name__ == "__main__":
    unittest.main()

