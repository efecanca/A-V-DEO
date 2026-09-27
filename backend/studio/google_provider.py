from __future__ import annotations

import base64
import json
import mimetypes
import os
import time
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

import requests

from secret_loader import load_secret
from studio.models import Capability, ProviderDescriptor
from studio.provider_registry import ProviderError


StatusCallback = Callable[[str, Optional[int], Optional[str]], None]
API_BASE = "https://generativelanguage.googleapis.com/v1beta"


def _mime_type(path: str) -> str:
    guessed, _ = mimetypes.guess_type(path)
    return guessed if guessed and guessed.startswith("image/") else "image/jpeg"


def _image_data(path: str) -> str:
    return base64.b64encode(Path(path).read_bytes()).decode("ascii")


def _clean_json_text(raw: str) -> str:
    value = raw.strip()
    if value.startswith("```"):
        value = value.split("\n", 1)[1] if "\n" in value else value[3:]
        if value.endswith("```"):
            value = value[:-3]
        if value.lstrip().startswith("json"):
            value = value.lstrip()[4:].lstrip()
    return value.strip()


def _find_nested(value: Any, keys: set[str]) -> Any:
    if isinstance(value, dict):
        for key, child in value.items():
            if key in keys:
                return child
        for child in value.values():
            found = _find_nested(child, keys)
            if found is not None:
                return found
    elif isinstance(value, list):
        for child in value:
            found = _find_nested(child, keys)
            if found is not None:
                return found
    return None


class _GoogleRestClient:
    def __init__(
        self,
        api_key: Optional[str] = None,
        session: Optional[requests.Session] = None,
        sleep_fn: Callable[[float], None] = time.sleep,
    ) -> None:
        self.api_key = (api_key if api_key is not None else load_secret("GEMINI_API_KEY")).strip()
        self.session = session or requests.Session()
        self.sleep_fn = sleep_fn
        self._quota_state = "available" if self.api_key else "missing_secret"

    def available(self) -> bool:
        return bool(self.api_key)

    def quota_state(self) -> str:
        return self._quota_state

    def request_json(
        self,
        method: str,
        url: str,
        *,
        payload: Optional[Dict[str, Any]] = None,
        timeout: int = 120,
    ) -> Dict[str, Any]:
        if not self.api_key:
            raise ProviderError(
                "GEMINI_API_KEY backend ortamında bulunamadı.", retryable=False
            )
        try:
            response = self.session.request(
                method,
                url,
                headers={
                    "x-goog-api-key": self.api_key,
                    "Content-Type": "application/json",
                },
                json=payload,
                timeout=timeout,
            )
        except requests.RequestException as exc:
            raise ProviderError(f"Google API bağlantısı başarısız: {exc}") from exc

        if response.status_code == 429:
            self._quota_state = "limited"
        if not response.ok:
            try:
                detail = response.json().get("error", {}).get("message", "")
            except Exception:
                detail = ""
            safe_detail = str(detail)[:500] or "Ayrıntı verilmedi"
            raise ProviderError(
                f"Google API HTTP {response.status_code}: {safe_detail}",
                status_code=response.status_code,
                retryable=response.status_code in {408, 409, 429, 500, 502, 503, 504},
            )
        try:
            return response.json()
        except ValueError as exc:
            raise ProviderError("Google API geçerli JSON döndürmedi.") from exc

    def download(self, url: str, output_path: str) -> None:
        try:
            with self.session.get(
                url,
                headers={"x-goog-api-key": self.api_key},
                timeout=180,
                stream=True,
            ) as response:
                if not response.ok:
                    raise ProviderError(
                        f"Veo video indirme HTTP {response.status_code}",
                        status_code=response.status_code,
                    )
                destination = Path(output_path)
                destination.parent.mkdir(parents=True, exist_ok=True)
                with destination.open("wb") as handle:
                    for chunk in response.iter_content(chunk_size=1024 * 1024):
                        if chunk:
                            handle.write(chunk)
        except requests.RequestException as exc:
            raise ProviderError(f"Veo videosu indirilemedi: {exc}") from exc


class GeminiImageProvider:
    descriptor = ProviderDescriptor(
        name="gemini_image",
        display_name="Gemini Image",
        capabilities=(Capability.VISION, Capability.IMAGE_GENERATION, Capability.IMAGE_EDIT),
        priority=10,
        cost_tier=2,
        remote=True,
        notes="Resmî Gemini API; ürün analizi, görsel üretme ve düzenleme.",
    )

    def __init__(self, client: Optional[_GoogleRestClient] = None) -> None:
        self.client = client or _GoogleRestClient()
        self.image_model = os.environ.get("FPRO_GEMINI_IMAGE_MODEL", "gemini-3.1-flash-image")
        self.vision_model = os.environ.get("FPRO_GEMINI_VISION_MODEL", "gemini-2.5-flash")
        self.image_size = os.environ.get("FPRO_GEMINI_IMAGE_SIZE", "2K")

    def is_available(self) -> bool:
        return self.client.available()

    def quota_state(self) -> str:
        return self.client.quota_state()

    def analyze_images(self, prompt: str, image_paths: List[str]) -> Dict[str, Any]:
        parts: List[Dict[str, Any]] = [{"text": prompt}]
        for path in image_paths:
            parts.append(
                {
                    "inlineData": {
                        "mimeType": _mime_type(path),
                        "data": _image_data(path),
                    }
                }
            )
        payload = {
            "contents": [{"role": "user", "parts": parts}],
            "generationConfig": {"responseMimeType": "application/json"},
        }
        response = self.client.request_json(
            "POST",
            f"{API_BASE}/models/{self.vision_model}:generateContent",
            payload=payload,
        )
        try:
            raw = response["candidates"][0]["content"]["parts"][0]["text"]
            decoded = json.loads(_clean_json_text(raw))
        except (KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError) as exc:
            raise ProviderError("Gemini vision yapılandırılmış değerlendirme döndürmedi.") from exc
        if not isinstance(decoded, dict):
            raise ProviderError("Gemini vision JSON nesnesi döndürmedi.")
        return decoded

    def generate_image(
        self,
        prompt: str,
        input_paths: List[str],
        output_path: str,
        aspect_ratio: str,
        status_callback: Optional[StatusCallback] = None,
    ) -> str:
        if status_callback:
            status_callback("product_applying", None, "Kaynak ürün referansları Gemini Image'a gönderiliyor")
        inputs: List[Dict[str, Any]] = [{"type": "text", "text": prompt}]
        for path in input_paths:
            inputs.append(
                {
                    "type": "image",
                    "data": _image_data(path),
                    "mime_type": _mime_type(path),
                }
            )
        payload = {
            "model": self.image_model,
            "input": inputs,
            "response_format": {
                "type": "image",
                "mime_type": "image/png",
                "aspect_ratio": aspect_ratio,
                "image_size": self.image_size,
            },
        }
        response = self.client.request_json(
            "POST", f"{API_BASE}/interactions", payload=payload, timeout=300
        )
        if status_callback:
            status_callback("image_enhancing", None, "Gemini Image çıktısı alınıyor")

        output_image = response.get("output_image") or response.get("outputImage")
        if not isinstance(output_image, dict):
            output_image = _find_nested(response, {"output_image", "outputImage"})
        encoded = output_image.get("data") if isinstance(output_image, dict) else None
        if not encoded:
            raise ProviderError("Gemini Image yanıtında görsel verisi bulunamadı.")
        try:
            image_bytes = base64.b64decode(encoded)
        except (TypeError, ValueError) as exc:
            raise ProviderError("Gemini Image görsel verisi çözülemedi.") from exc
        destination = Path(output_path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(image_bytes)
        return str(destination)


class GoogleVeoProvider:
    descriptor = ProviderDescriptor(
        name="google_veo",
        display_name="Google Veo",
        capabilities=(Capability.VIDEO_GENERATION,),
        priority=10,
        cost_tier=3,
        remote=True,
        notes="Resmî Gemini API Veo image-to-video uzun işlem uçları.",
    )

    def __init__(self, client: Optional[_GoogleRestClient] = None) -> None:
        self.client = client or _GoogleRestClient()
        self.video_model = os.environ.get("FPRO_VEO_MODEL", "veo-3.1-generate-preview")
        self.poll_seconds = max(2, int(os.environ.get("FPRO_VEO_POLL_SECONDS", "10")))
        self.max_wait_seconds = max(60, int(os.environ.get("FPRO_VEO_MAX_WAIT_SECONDS", "900")))

    def is_available(self) -> bool:
        return self.client.available()

    def quota_state(self) -> str:
        return self.client.quota_state()

    def generate_video(
        self,
        prompt: str,
        approved_image_path: str,
        output_path: str,
        aspect_ratio: str,
        duration_seconds: int,
        status_callback: Optional[StatusCallback] = None,
    ) -> str:
        if aspect_ratio not in {"9:16", "16:9"}:
            raise ProviderError(
                "Veo 3.1 yalnızca 9:16 ve 16:9 oranlarını destekliyor.",
                retryable=True,
            )
        duration = duration_seconds if duration_seconds in {4, 6, 8} else 8
        if status_callback:
            status_callback(
                "generating",
                None,
                "Onaylanan mankenli görsel Veo başlangıç karesi olarak gönderiliyor",
            )
        payload = {
            "instances": [
                {
                    "prompt": prompt,
                    "image": {
                        "inlineData": {
                            "mimeType": _mime_type(approved_image_path),
                            "data": _image_data(approved_image_path),
                        }
                    },
                }
            ],
            "parameters": {
                "numberOfVideos": 1,
                "aspectRatio": aspect_ratio,
                "durationSeconds": str(duration),
                "resolution": "720p",
                "personGeneration": "allow_adult",
            },
        }
        operation = self.client.request_json(
            "POST",
            f"{API_BASE}/models/{self.video_model}:predictLongRunning",
            payload=payload,
            timeout=180,
        )
        operation_name = operation.get("name")
        if not operation_name:
            raise ProviderError("Veo uzun işlem kimliği döndürmedi.")

        started = time.monotonic()
        while True:
            elapsed = int(time.monotonic() - started)
            if elapsed > self.max_wait_seconds:
                raise ProviderError("Veo işlemi izin verilen bekleme süresini aştı.")
            self.client.sleep_fn(self.poll_seconds)
            status = self.client.request_json(
                "GET", f"{API_BASE}/{str(operation_name).lstrip('/')}", timeout=60
            )
            if status_callback:
                status_callback(
                    "generating",
                    None,
                    f"Veo işlemi sürüyor ({elapsed // 60} dk {elapsed % 60} sn)",
                )
            if not status.get("done", False):
                continue
            if status.get("error"):
                message = str(status["error"].get("message", "Veo işlemi başarısız"))
                raise ProviderError(message)
            video_uri = _find_nested(status.get("response", status), {"uri"})
            if not isinstance(video_uri, str) or not video_uri.startswith("http"):
                raise ProviderError("Veo tamamlandı ancak video indirme adresi bulunamadı.")
            if status_callback:
                status_callback("encoding", None, "Veo çıktısı güvenli biçimde indiriliyor")
            self.client.download(video_uri, output_path)
            return output_path
