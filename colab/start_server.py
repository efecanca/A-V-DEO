"""Kehribar Video backend'ini Colab'da başlatır ve ngrok tünelini izler.

Bu dosya notebook hücresinden çalıştırılır. Hücre çalıştığı sürece FastAPI
süreci ve herkese açık tünel canlı tutulur; tünel koparsa yeni bir adres
oluşturulup ekrana yazılır.
"""

from __future__ import annotations

import getpass
import os
import socket
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional, Tuple


ROOT = Path(os.environ.get("KEHRIBAR_REPO_ROOT", "/content/A-V-DEO"))
BACKEND_DIR = ROOT / "backend"
HEALTH_HEADERS = {
    "Accept": "application/json",
    "ngrok-skip-browser-warning": "true",
}


def install_dependencies() -> None:
    packages = [
        "fastapi",
        "uvicorn[standard]",
        "python-multipart",
        "diffusers>=0.35.1",
        "transformers>=4.51.0",
        "accelerate>=0.34.0",
        "safetensors>=0.4.5",
        "sentencepiece",
        "ftfy",
        "imageio",
        "imageio-ffmpeg",
        "pyngrok",
        "huggingface_hub",
        "torchao",
        "requests",
    ]
    subprocess.run([sys.executable, "-m", "pip", "install", "-q", *packages], check=True)


def get_ngrok_token() -> str:
    token = os.environ.get("NGROK_AUTHTOKEN", "").strip()
    if not token:
        try:
            from google.colab import userdata

            token = (userdata.get("NGROK_AUTHTOKEN") or "").strip()
        except Exception:
            token = ""
    if not token:
        token = getpass.getpass("ngrok authtoken: ").strip()
    if not token:
        raise RuntimeError("ngrok token gerekli")
    return token


def find_available_port(first: int = 8000, last: int = 8010) -> int:
    for port in range(first, last + 1):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            try:
                sock.bind(("127.0.0.1", port))
            except OSError:
                continue
            return port
    raise RuntimeError(f"{first}-{last} aralığında boş backend portu bulunamadı")


def start_backend(port: int) -> subprocess.Popen:
    env = os.environ.copy()
    env["WAN_OFFLOAD_MODE"] = "sequential"
    env["COGVIDEO_I2V_MODEL_ID"] = "zai-org/CogVideoX-5b-I2V"
    return subprocess.Popen(
        [
            sys.executable,
            "-m",
            "uvicorn",
            "main:app",
            "--host",
            "0.0.0.0",
            "--port",
            str(port),
            "--log-level",
            "info",
            "--access-log",
        ],
        cwd=BACKEND_DIR,
        env=env,
    )


def wait_for_local_backend(process: subprocess.Popen, port: int) -> None:
    import requests

    for _ in range(90):
        if process.poll() is not None:
            raise RuntimeError(f"Backend erken kapandı (çıkış kodu {process.returncode})")
        try:
            response = requests.get(f"http://127.0.0.1:{port}/health", timeout=3)
            if response.ok and response.json().get("status") == "ok":
                return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError("Backend başlatılamadı")


def public_health(url: str) -> Tuple[bool, str]:
    import requests

    try:
        response = requests.get(
            f"{url.rstrip('/')}/health",
            headers=HEALTH_HEADERS,
            timeout=15,
        )
        if response.ok and response.json().get("status") == "ok":
            return True, "HTTP 200"
        ngrok_code = response.headers.get("Ngrok-Error-Code")
        detail = f"HTTP {response.status_code}"
        if ngrok_code:
            detail += f" / {ngrok_code}"
        return False, detail
    except Exception as exc:
        return False, f"{type(exc).__name__}: {exc}"


def open_verified_tunnel(port: int):
    from pyngrok import ngrok

    last_error = "bilinmiyor"
    for tunnel_attempt in range(1, 4):
        tunnel = None
        verified = False
        try:
            tunnel = ngrok.connect(port, "http")
            url = tunnel.public_url.rstrip("/")
            for _ in range(20):
                ok, detail = public_health(url)
                if ok:
                    verified = True
                    break
                last_error = detail
                time.sleep(2)
            if verified:
                return tunnel, url
        except Exception as exc:
            last_error = f"{type(exc).__name__}: {exc}"
        finally:
            if tunnel is not None and not verified:
                try:
                    ngrok.disconnect(tunnel.public_url)
                except Exception:
                    pass
        print(f"Ngrok denemesi {tunnel_attempt}/3 başarısız: {last_error}")
        ngrok.kill()
        time.sleep(3 * tunnel_attempt)
    raise RuntimeError(f"Herkese açık ngrok /health doğrulanamadı: {last_error}")


def show_server_url(url: str, reconnected: bool = False) -> None:
    title = "YENİ SUNUCU ADRESİ" if reconnected else "APK'YE YAZILACAK SUNUCU ADRESİ"
    print("\n" + "=" * 68)
    print(title + ":")
    print(url + "/")
    print("Public /health: HTTP 200 ✓")
    print("=" * 68 + "\n", flush=True)


def main() -> None:
    if subprocess.run(["nvidia-smi"], stdout=subprocess.DEVNULL).returncode != 0:
        raise RuntimeError("T4 GPU seçin.")
    if not (BACKEND_DIR / "main.py").exists():
        raise RuntimeError(f"Backend bulunamadı: {BACKEND_DIR}")

    install_dependencies()

    from pyngrok import ngrok

    ngrok.set_auth_token(get_ngrok_token())
    # Eski/çevrimdışı tünellerin adreslerinin yanlışlıkla tekrar kullanılmasını
    # önlemek için bu runtime'a ait ngrok ajanını temiz başlat.
    ngrok.kill()

    port = find_available_port()
    backend_process = start_backend(port)
    tunnel = None
    current_url: Optional[str] = None

    try:
        wait_for_local_backend(backend_process, port)
        tunnel, current_url = open_verified_tunnel(port)
        show_server_url(current_url)
        print("Bu hücreyi çalışır bırakın. Durdurursanız sunucu ve tünel kapanır.", flush=True)

        consecutive_failures = 0
        while backend_process.poll() is None:
            time.sleep(30)
            ok, detail = public_health(current_url)
            if ok:
                consecutive_failures = 0
                continue

            consecutive_failures += 1
            print(
                f"Tünel sağlık kontrolü başarısız "
                f"({consecutive_failures}/3): {detail}",
                flush=True,
            )
            if consecutive_failures < 3:
                continue

            print("Ngrok tüneli koptu; yeniden bağlanılıyor…", flush=True)
            try:
                if current_url:
                    ngrok.disconnect(current_url)
            except Exception:
                pass
            ngrok.kill()
            tunnel, current_url = open_verified_tunnel(port)
            show_server_url(current_url, reconnected=True)
            consecutive_failures = 0

        raise RuntimeError(f"Backend kapandı (çıkış kodu {backend_process.returncode})")
    except KeyboardInterrupt:
        print("Sunucu kullanıcı tarafından durduruldu.")
    finally:
        if current_url:
            try:
                ngrok.disconnect(current_url)
            except Exception:
                pass
        ngrok.kill()
        if backend_process.poll() is None:
            backend_process.terminate()
            try:
                backend_process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                backend_process.kill()


if __name__ == "__main__":
    main()
