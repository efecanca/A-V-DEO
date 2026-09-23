"""
Kehribar Video - AI Backend (FastAPI)

Android uygulamasından gelen fotoğrafı alır, Wan Image-to-Video modeliyle
kısa bir moda videosuna dönüştürür ve iş (job) durumunu polling ile raporlar.

Uçlar:
  POST /generate         -> multipart: image, prompt, negative_prompt  => {job_id}
  GET  /status/{job_id}  -> {status, progress, video_url, error}
  GET  /videos/{name}    -> üretilen MP4 dosyalarını statik olarak servis eder
  GET  /health           -> basit sağlık kontrolü

Çalıştırma:
  uvicorn main:app --host 0.0.0.0 --port 8000
"""

import os
import shutil
import traceback
import uuid
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from starlette.concurrency import run_in_threadpool

from job_manager import job_manager
from model_wan import generate_video

BASE_DIR = Path(__file__).resolve().parent
UPLOAD_DIR = BASE_DIR / "storage" / "uploads"
OUTPUT_DIR = BASE_DIR / "storage" / "outputs"
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="Kehribar Video Backend")

# Android uygulaması farklı bir origin/port'tan çağırabileceği için CORS açık.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/videos", StaticFiles(directory=str(OUTPUT_DIR)), name="videos")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/generate")
async def generate(
    image: UploadFile = File(...),
    prompt: str = Form(...),
    negative_prompt: str = Form(default=""),
):
    if image.content_type is None or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Yalnızca görsel dosyaları kabul edilir.")

    job_id = job_manager.create_job()

    ext = os.path.splitext(image.filename or "upload.jpg")[1] or ".jpg"
    image_path = UPLOAD_DIR / f"{job_id}{ext}"

    with open(image_path, "wb") as f:
        shutil.copyfileobj(image.file, f)

    output_path = OUTPUT_DIR / f"{job_id}.mp4"

    # Ağır işi arka planda thread pool'da çalıştır; event loop'u bloklamaz,
    # bu sayede /status sorguları video üretimi sırasında da hızlı yanıt verir.
    import asyncio
    asyncio.create_task(_run_job(job_id, str(image_path), prompt, negative_prompt, str(output_path)))

    return {"job_id": job_id}


async def _run_job(job_id: str, image_path: str, prompt: str, negative_prompt: str, output_path: str):
    job_manager.update_job(job_id, status="processing", progress=0)

    def progress_cb(pct: int):
        job_manager.update_job(job_id, status="processing", progress=pct)

    try:
        await run_in_threadpool(
            generate_video,
            image_path,
            prompt,
            negative_prompt,
            output_path,
            progress_cb,
        )
        video_name = os.path.basename(output_path)
        job_manager.update_job(
            job_id,
            status="completed",
            progress=100,
            video_url=f"/videos/{video_name}",
        )
    except Exception as exc:
        traceback.print_exc()
        job_manager.update_job(
            job_id,
            status="failed",
            error=f"Video üretimi başarısız oldu: {exc}",
        )
    finally:
        # Yüklenen orijinal fotoğrafı temizle (disk alanı için)
        try:
            os.remove(image_path)
        except OSError:
            pass


@app.get("/status/{job_id}")
def status(job_id: str):
    job = job_manager.get_job(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="job_id bulunamadı.")
    return {
        "status": job["status"],
        "progress": job.get("progress", 0),
        "video_url": job.get("video_url"),
        "error": job.get("error"),
    }
