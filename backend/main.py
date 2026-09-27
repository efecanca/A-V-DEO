"""
FPRO AI - Video Backend (FastAPI)

v2: Artık tek bir "eşarp" kategorisine özel değildir; herhangi bir ürün
fotoğrafını (veya prompt'u, text-to-video modunda) kısa bir videoya
dönüştürür. Süre/format/kalite/hareket/kamera/stil seçenekleri ve çoklu
ürün / reklam modu destekler. Model, providers/ altında soyutlanmıştır.

Uçlar:
  GET  /capabilities     -> desteklenen mod/süre/oran/kalite/kamera/preset listesi + GPU seviyesi
  POST /generate         -> multipart, bkz. aşağıdaki alanlar => {job_id} veya {job_ids}
  GET  /status/{job_id}  -> {status, progress, video_url, error, ...}
  GET  /jobs             -> son işlerin özet listesi (Android "Sonuçlar" ekranı için yardımcı;
                             asıl kalıcı geçmiş istemci tarafında tutulur, bkz. proje README'si)
  GET  /videos/{name}    -> üretilen MP4 dosyalarını statik olarak servis eder
  GET  /health           -> basit sağlık kontrolü

/generate form alanları (hepsi opsiyoneldir, verilmezse eski istemciyle
BİREBİR AYNI davranışı korumak üzere temkinli varsayılanlar kullanılır):
  image           : tek dosya (ESKİ istemciler için - hâlâ desteklenir)
  images          : birden fazla dosya (YENİ - çoklu ürün / reklam modu)
  mode            : "image_to_video" (varsayılan) | "text_to_video"
  prompt          : serbest metin (verilmezse genel bir varsayılan kullanılır)
  negative_prompt : serbest metin
  duration_seconds: 5 | 10 | 15 (verilmezse: eski istemci davranışı - tek sahne)
  aspect_ratio    : "16:9" (varsayılan) | "9:16" | "1:1"
  quality         : "fast" | "standard" (varsayılan) | "high"
  motion          : "subtle" | "natural" | "dynamic"
  camera          : "static" | "push_in" | "pull_out" | "pan_left" | "pan_right" | "orbit"
  style_preset    : capabilities.STYLE_PRESETS anahtarlarından biri
  product_protection: "true" (varsayılan) | "false"
  ad_mode         : "true" | "false" (varsayılan) - true ise TÜM görseller TEK bir
                    çok-sahneli reklam videosuna dağıtılır; false + birden fazla
                    görsel verilirse HER görsel için AYRI bir job açılır (toplu mod).

Çalıştırma:
  uvicorn main:app --host 0.0.0.0 --port 8000
"""

import asyncio
import logging
import os
import shutil
from pathlib import Path
from typing import List, Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from starlette.concurrency import run_in_threadpool

import capabilities
from ffmpeg_utils import concat_video_clips, finalize_delivery_video
from fashion_scene import build_fashion_scene_plan
from job_manager import job_manager
from job_progress import overall_generation_progress
from prompt_builder import build_prompt
from studio.bootstrap import build_provider_registry
from studio.orchestrator import StudioOrchestrator
from studio.store import ProjectStore

logger = logging.getLogger("uvicorn.error")

BASE_DIR = Path(__file__).resolve().parent
UPLOAD_DIR = BASE_DIR / "storage" / "uploads"
OUTPUT_DIR = BASE_DIR / "storage" / "outputs"
STUDIO_DIR = BASE_DIR / "storage" / "studio"
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
STUDIO_DIR.mkdir(parents=True, exist_ok=True)

# CogVideoX-5B-I2V'nin resmi oynatma hızı 8 FPS'tir. Eski WAN_FPS değişkeni
# geriye uyumluluk için okunmaya devam eder.
FPS = int(os.environ.get("VIDEO_FPS", os.environ.get("WAN_FPS", "8")))

app = FastAPI(title="FPRO AI Backend")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/videos", StaticFiles(directory=str(OUTPUT_DIR)), name="videos")
app.mount("/references", StaticFiles(directory=str(OUTPUT_DIR)), name="references")
app.mount("/studio-media", StaticFiles(directory=str(STUDIO_DIR)), name="studio-media")

studio_provider_registry = build_provider_registry()
project_store = ProjectStore(STUDIO_DIR)
studio_orchestrator = StudioOrchestrator(
    studio_provider_registry, project_store, job_manager
)


def _legacy_video_provider(name: Optional[str] = None):
    from providers.registry import get_provider

    return get_provider(name) if name else get_provider()


def _legacy_fashion_image_provider():
    from providers.fashion_image_provider import fashion_image_provider

    return fashion_image_provider

# asyncio yalnızca zayıf task referansları tutar. Uzun model indirme/inference
# işleri çöp toplayıcı tarafından erken bırakılmasın diye tamamlanana dek sakla.
_background_tasks: set[asyncio.Task] = set()


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/capabilities")
def get_capabilities():
    payload = capabilities.get_capabilities_payload(fps=FPS)
    try:
        provider = _legacy_video_provider()
        payload["provider"] = provider.name
        if provider.name == "cogvideox":
            payload["modes"] = ["image_to_video"]
    except Exception:
        payload["provider"] = "remote_only"
        payload["modes"] = []
    payload["studio"] = {
        "providers": studio_provider_registry.public_capabilities(),
        "image_aspect_ratios": ["9:16", "1:1", "16:9"],
        "veo_durations_seconds": [4, 6, 8],
        "stages": [
            "product_analyzing",
            "scene_preparing",
            "product_applying",
            "image_enhancing",
            "quality_checking",
            "completed",
        ],
        "requires_image_approval_before_video": True,
    }
    return payload


@app.get("/jobs")
def list_jobs():
    return {"jobs": job_manager.list_jobs()}


def _parse_bool(value: Optional[str], default: bool) -> bool:
    if value is None:
        return default
    return value.strip().lower() in ("1", "true", "yes", "on")


def _validate_image_upload(upload: UploadFile) -> None:
    if upload.content_type is None or not upload.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Yalnızca görsel dosyaları kabul edilir.")


def _save_project_upload(project_id: str, upload: UploadFile, prefix: str) -> str:
    _validate_image_upload(upload)
    extension = Path(upload.filename or "image.jpg").suffix.lower()
    if extension not in {".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif"}:
        extension = ".jpg"
    destination = project_store.project_directory(project_id) / f"{prefix}_{os.urandom(8).hex()}{extension}"
    with destination.open("wb") as handle:
        shutil.copyfileobj(upload.file, handle)
    return str(destination)


async def _run_studio_image_job(**kwargs):
    await run_in_threadpool(studio_orchestrator.generate_image, **kwargs)


async def _run_studio_video_job(**kwargs):
    await run_in_threadpool(studio_orchestrator.generate_video, **kwargs)


@app.post("/studio/generate")
async def studio_generate(
    product_image: UploadFile = File(...),
    prompt: str = Form(...),
    mannequin_reference: Optional[UploadFile] = File(default=None, alias="model_image"),
    project_name: Optional[str] = Form(default=None),
    aspect_ratio: str = Form(default="9:16"),
    preferred_provider: Optional[str] = Form(default=None),
    auto_correct: Optional[str] = Form(default="true"),
):
    """Create an image job. Video is deliberately a separate, approval-gated request."""
    if not prompt.strip():
        raise HTTPException(status_code=400, detail="Üretim açıklaması boş olamaz.")
    if aspect_ratio not in {"9:16", "1:1", "16:9"}:
        raise HTTPException(status_code=400, detail="Desteklenmeyen görsel oranı.")
    _validate_image_upload(product_image)
    if mannequin_reference is not None:
        _validate_image_upload(mannequin_reference)

    project_id = project_store.create_project(prompt, project_name)
    product_path = _save_project_upload(project_id, product_image, "product")
    model_path = (
        _save_project_upload(project_id, mannequin_reference, "model")
        if mannequin_reference is not None
        else None
    )
    project_store.set_sources(project_id, product_path, model_path)
    job_id = job_manager.create_job(
        mode="studio_image",
        quality="provider_managed",
        aspect_ratio=aspect_ratio,
        product_count=1,
        scenes_total=1,
        project_id=project_id,
        media_type="image",
    )
    task = asyncio.create_task(
        _run_studio_image_job(
            job_id=job_id,
            project_id=project_id,
            product_path=product_path,
            model_path=model_path,
            user_prompt=prompt,
            aspect_ratio=aspect_ratio,
            preferred_provider=preferred_provider,
            auto_correct=_parse_bool(auto_correct, True),
        )
    )
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)
    return {"job_id": job_id, "project_id": project_id}


@app.post("/studio/revise")
async def studio_revise(
    project_id: str = Form(...),
    result_id: str = Form(...),
    prompt: str = Form(...),
    aspect_ratio: str = Form(default="9:16"),
    preferred_provider: Optional[str] = Form(default=None),
    auto_correct: Optional[str] = Form(default="true"),
):
    project = project_store.get_project(project_id)
    if project is None:
        raise HTTPException(status_code=404, detail={"code": "project_missing", "message": "Proje bulunamadı."})
    if not prompt.strip():
        raise HTTPException(status_code=400, detail="Revizyon açıklaması boş olamaz.")
    product_path = project.get("product_path")
    if not product_path or not Path(product_path).is_file():
        raise HTTPException(status_code=409, detail="Projenin kaynak ürün görseli bulunamadı.")
    job_id = job_manager.create_job(
        mode="studio_image_revision",
        quality="provider_managed",
        aspect_ratio=aspect_ratio,
        product_count=1,
        scenes_total=1,
        project_id=project_id,
        media_type="image",
    )
    task = asyncio.create_task(
        _run_studio_image_job(
            job_id=job_id,
            project_id=project_id,
            product_path=str(product_path),
            model_path=project.get("model_path"),
            user_prompt=prompt,
            aspect_ratio=aspect_ratio,
            preferred_provider=preferred_provider,
            revision_result_id=result_id,
            auto_correct=_parse_bool(auto_correct, True),
        )
    )
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)
    return {"job_id": job_id, "project_id": project_id}


@app.post("/studio/results/{result_id}/approve")
def approve_studio_result(
    result_id: str,
    approved: Optional[str] = Form(default="true"),
):
    try:
        result = project_store.approve_result(result_id, _parse_bool(approved, True))
    except KeyError:
        raise HTTPException(status_code=404, detail={"code": "result_missing", "message": "Görsel sonucu bulunamadı."})
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc))
    return {"result_id": result_id, "approved": result["approved"]}


@app.post("/studio/results/{result_id}/video")
async def studio_video(
    result_id: str,
    prompt: str = Form(default="Lüks moda filmi"),
    aspect_ratio: str = Form(default="9:16"),
    duration_seconds: int = Form(default=8),
    motion: str = Form(default="walking"),
    camera: str = Form(default="tracking"),
    preferred_provider: Optional[str] = Form(default=None),
):
    found = project_store.get_result(result_id)
    if found is None:
        raise HTTPException(status_code=404, detail={"code": "result_missing", "message": "Görsel sonucu bulunamadı."})
    project_id, result = found
    if result.get("media_type") != "image" or not result.get("approved"):
        raise HTTPException(status_code=409, detail="Videoya geçmeden önce mankenli görseli onaylayın.")
    if aspect_ratio not in {"9:16", "1:1", "16:9"}:
        raise HTTPException(status_code=400, detail="Desteklenmeyen video oranı.")
    job_id = job_manager.create_job(
        mode="studio_video",
        quality="provider_managed",
        aspect_ratio=aspect_ratio,
        product_count=1,
        scenes_total=1,
        project_id=project_id,
        media_type="video",
        approved_source_result_id=result_id,
    )
    task = asyncio.create_task(
        _run_studio_video_job(
            job_id=job_id,
            result_id=result_id,
            user_prompt=prompt,
            aspect_ratio=aspect_ratio,
            duration_seconds=duration_seconds,
            motion=motion,
            camera=camera,
            preferred_provider=preferred_provider,
        )
    )
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)
    return {"job_id": job_id, "project_id": project_id}


@app.get("/projects")
def list_projects():
    return {"projects": project_store.list_projects()}


@app.get("/projects/{project_id}")
def get_project(project_id: str):
    project = project_store.get_project(project_id)
    if project is None:
        raise HTTPException(status_code=404, detail={"code": "project_missing", "message": "Proje bulunamadı."})
    return project_store.public_project(project)


@app.get("/gallery")
def studio_gallery():
    return {"items": project_store.gallery()}


async def _run_reference_job(job_id: str, source_path: str, output_path: str, prompt: Optional[str]):
    plan = build_fashion_scene_plan(prompt)
    try:
        job_manager.update_job(job_id, status="processing", stage="reference_model_loading",
                               stage_detail="Mankenli görsel modeli hazırlanıyor", progress=None)
        def cb(stage, progress, detail):
            job_manager.update_job(job_id, status="processing", stage=stage,
                                   stage_detail=detail, progress=progress)
            logger.info("[REFERENCE %s] aşama=%s ilerleme=%s ayrıntı=%s",
                        job_id, stage, progress if progress is not None else "ölçülemiyor", detail or "-")
        await run_in_threadpool(
            _legacy_fashion_image_provider().generate_reference, source_path, plan.prompt,
            plan.negative_prompt, output_path, cb
        )
        job_manager.update_job(
            job_id, status="completed", stage="reference_completed",
            stage_detail="Mankenli görsel hazır", progress=100,
            reference_url=f"/references/{Path(output_path).name}"
        )
    except Exception as exc:
        logger.exception("[REFERENCE %s] üretim başarısız", job_id)
        job_manager.update_job(job_id, status="failed", stage="failed",
                               stage_detail=str(exc), progress=None,
                               error=f"Mankenli görsel üretilemedi: {exc}")
    finally:
        try:
            os.remove(source_path)
        except OSError:
            pass


@app.post("/prepare-reference")
async def prepare_reference(
    image: UploadFile = File(...),
    prompt: Optional[str] = Form(default=None),
):
    """İsteği hemen kabul eder; uzun model yükleme/inference arka planda job olarak sürer."""
    if image.content_type is None or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Yalnızca görsel dosyaları kabul edilir.")
    job_id = job_manager.create_job(
        mode="reference_image", quality="standard", aspect_ratio="9:16",
        product_count=1, scenes_total=1
    )
    ext = os.path.splitext(image.filename or "product.jpg")[1] or ".jpg"
    source_path = UPLOAD_DIR / f"reference_{job_id}{ext}"
    output_path = OUTPUT_DIR / f"reference_{job_id}.jpg"
    with open(source_path, "wb") as out:
        shutil.copyfileobj(image.file, out)
    task = asyncio.create_task(_run_reference_job(job_id, str(source_path), str(output_path), prompt))
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)
    return {"job_id": job_id}


@app.post("/generate")
async def generate(
    image: Optional[UploadFile] = File(default=None),
    images: List[UploadFile] = File(default=[]),
    mode: str = Form(default="image_to_video"),
    prompt: Optional[str] = Form(default=None),
    negative_prompt: Optional[str] = Form(default=None),
    duration_seconds: Optional[int] = Form(default=None),
    aspect_ratio: Optional[str] = Form(default=None),
    quality: Optional[str] = Form(default=None),
    motion: Optional[str] = Form(default=None),
    camera: Optional[str] = Form(default=None),
    style_preset: Optional[str] = Form(default=None),
    product_protection: Optional[str] = Form(default=None),
    ad_mode: Optional[str] = Form(default=None),
):
    # --- Görselleri topla (eski "image" tekil alanı hâlâ destekleniyor) ---
    uploaded_files: List[UploadFile] = [f for f in images if f is not None]
    if image is not None:
        uploaded_files = [image] + uploaded_files

    if mode == "text_to_video":
        uploaded_files = []  # T2V'de görsele ihtiyaç yok
    elif not uploaded_files:
        raise HTTPException(status_code=400, detail="En az bir ürün fotoğrafı gerekli.")
    else:
        for f in uploaded_files:
            if f.content_type is None or not f.content_type.startswith("image/"):
                raise HTTPException(status_code=400, detail="Yalnızca görsel dosyaları kabul edilir.")

    is_ad_mode = _parse_bool(ad_mode, default=False)
    use_product_protection = _parse_bool(product_protection, default=True)
    if mode == "text_to_video":
        use_product_protection = False  # koruyacak bir kaynak görsel yok

    quality = quality if quality in capabilities.QUALITY_PRESETS else "standard"
    aspect_ratio_key = aspect_ratio if aspect_ratio in capabilities.ASPECT_RATIOS else (
        "9:16" if is_ad_mode and aspect_ratio is None else "16:9"
    )

    legacy_single_scene = duration_seconds is None and not is_ad_mode
    target_duration = duration_seconds or (15 if is_ad_mode else 5)

    num_products_for_check = len(uploaded_files) if is_ad_mode else 1
    ok, reason, suggestion = capabilities.check_feasible(quality, target_duration, num_products_for_check)
    if not ok:
        raise HTTPException(status_code=400, detail={"message": reason, "suggestion": suggestion})

    final_prompt, final_negative = build_prompt(
        prompt, negative_prompt, motion, camera, style_preset, use_product_protection
    )

    common_kwargs = dict(
        mode=mode,
        quality=quality,
        aspect_ratio=aspect_ratio_key,
        motion=motion,
        camera=camera,
        style_preset=style_preset,
        product_protection=use_product_protection,
        prompt=final_prompt,
        negative_prompt=final_negative,
        legacy_single_scene=legacy_single_scene,
        target_duration_seconds=target_duration,
    )

    # --- Reklam modu: TÜM görseller TEK bir çok-sahneli videoya dağıtılır ---
    if is_ad_mode:
        job_id = await _create_and_launch_job(uploaded_files, **common_kwargs)
        return {"job_id": job_id}

    # --- Tek görsel (ya da text-to-video): normal tek job akışı ---
    if len(uploaded_files) <= 1:
        job_id = await _create_and_launch_job(uploaded_files, **common_kwargs)
        return {"job_id": job_id}

    # --- Toplu mod: birden fazla görsel, AD_MODE kapalı -> her biri ayrı job ---
    job_ids = []
    for f in uploaded_files:
        jid = await _create_and_launch_job([f], **common_kwargs)
        job_ids.append(jid)
    return {"job_id": job_ids[0], "job_ids": job_ids}


async def _create_and_launch_job(
    files: List[UploadFile],
    mode: str,
    quality: str,
    aspect_ratio: str,
    motion: Optional[str],
    camera: Optional[str],
    style_preset: Optional[str],
    product_protection: bool,
    prompt: str,
    negative_prompt: str,
    legacy_single_scene: bool,
    target_duration_seconds: int,
) -> str:
    width, height = capabilities.ASPECT_RATIOS[aspect_ratio]
    steps, frames = capabilities.QUALITY_PRESETS[quality]
    scene_seconds = frames / float(FPS)

    if legacy_single_scene:
        num_scenes = 1
    else:
        num_scenes = max(1, round(target_duration_seconds / scene_seconds))

    job_id = job_manager.create_job(
        mode=mode,
        quality=quality,
        aspect_ratio=aspect_ratio,
        product_count=len(files),
        scenes_total=num_scenes,
    )

    # Görselleri diske kaydet (varsa)
    saved_image_paths: List[str] = []
    for idx, f in enumerate(files):
        ext = os.path.splitext(f.filename or "upload.jpg")[1] or ".jpg"
        dest = UPLOAD_DIR / f"{job_id}_{idx}{ext}"
        with open(dest, "wb") as out:
            shutil.copyfileobj(f.file, out)
        saved_image_paths.append(str(dest))

    output_path = OUTPUT_DIR / f"{job_id}.mp4"

    task = asyncio.create_task(
        _run_job(
            job_id=job_id,
            image_paths=saved_image_paths,
            prompt=prompt,
            negative_prompt=negative_prompt,
            width=width,
            height=height,
            num_inference_steps=steps,
            frames_per_scene=frames,
            num_scenes=num_scenes,
            scene_seconds=scene_seconds,
            output_path=str(output_path),
        )
    )
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)

    return job_id


async def _run_job(
    job_id: str,
    image_paths: List[str],
    prompt: str,
    negative_prompt: str,
    width: int,
    height: int,
    num_inference_steps: int,
    frames_per_scene: int,
    num_scenes: int,
    scene_seconds: float,
    output_path: str,
):
    provider = _legacy_video_provider()
    logger.info(
        "[JOB %s] oluşturuldu: provider=%s, sahne=%s, çözünürlük=%sx%s, adım=%s",
        job_id,
        provider.name,
        num_scenes,
        width,
        height,
        num_inference_steps,
    )

    scene_paths: List[str] = []
    try:
        for scene_index in range(num_scenes):
            image_path = None
            if image_paths:
                image_path = image_paths[scene_index % len(image_paths)]

            def status_cb(
                stage: str,
                progress: Optional[int],
                detail: Optional[str],
                _scene_index=scene_index,
            ) -> None:
                # Model indirme/yükleme/quantization/encoding sürelerinden güvenilir
                # bir yüzde çıkarılamaz. Yalnızca provider'ın gerçek diffusion adımı
                # verdiği "generating" aşamasını toplam iş yüzdesine dönüştürüyoruz.
                overall_progress = overall_generation_progress(
                    stage,
                    progress,
                    _scene_index,
                    num_scenes,
                )

                status = "queued" if stage == "queued" else "processing"
                job_manager.update_job(
                    job_id,
                    status=status,
                    stage=stage,
                    stage_detail=detail,
                    progress=overall_progress,
                )
                logger.info(
                    "[JOB %s] aşama=%s ilerleme=%s ayrıntı=%s",
                    job_id,
                    stage,
                    overall_progress if overall_progress is not None else "ölçülemiyor",
                    detail or "-",
                )

            scene_output = str(Path(output_path).with_name(f"{job_id}_scene_{scene_index}.mp4"))

            # Lüks eşarp konseptinde düz ürün fotoğrafını doğrudan video modeline
            # vermek yerine önce fotogerçekçi, mankenli moda referans karesi üret.
            video_image_path = image_path
            if image_path and "premium modest-fashion scarf campaign" in prompt:
                reference_path = str(
                    Path(output_path).with_name(f"{job_id}_reference_{scene_index}.jpg")
                )
                scene_plan = build_fashion_scene_plan(prompt)

                def reference_cb(stage, progress, detail):
                    job_manager.update_job(
                        job_id,
                        status="processing",
                        stage=stage,
                        stage_detail=detail,
                        progress=None,
                    )
                    logger.info(
                        "[JOB %s] aşama=%s ilerleme=%s ayrıntı=%s",
                        job_id, stage, progress if progress is not None else "ölçülemiyor", detail or "-"
                    )

                await run_in_threadpool(
                    _legacy_fashion_image_provider().generate_reference,
                    image_path,
                    scene_plan.prompt,
                    scene_plan.negative_prompt,
                    reference_path,
                    reference_cb,
                )
                video_image_path = reference_path

            await run_in_threadpool(
                provider.generate_clip,
                prompt,
                negative_prompt,
                scene_output,
                width,
                height,
                frames_per_scene,
                num_inference_steps,
                video_image_path,
                status_cb,
                FPS,
            )
            if video_image_path and video_image_path != image_path:
                try:
                    os.remove(video_image_path)
                except OSError:
                    pass
            scene_paths.append(scene_output)
            job_manager.update_job(job_id, scenes_completed=scene_index + 1)

        job_manager.update_job(
            job_id,
            status="processing",
            stage="encoding",
            stage_detail="Video sahneleri birleştiriliyor",
            progress=None,
        )
        logger.info("[JOB %s] video sahneleri birleştiriliyor", job_id)
        await run_in_threadpool(concat_video_clips, scene_paths, output_path)

        # Son kullanıcıya AI modelinin ham 8 FPS / çalışma çözünürlüğünü değil,
        # standart dikey Reels teslim videosunu ver.
        job = job_manager.get_job(job_id) or {}
        delivery_aspect = job.get("aspect_ratio", "9:16")
        if delivery_aspect == "9:16":
            delivery_path = str(Path(output_path).with_name(f"{job_id}_delivery.mp4"))
            job_manager.update_job(
                job_id,
                status="processing",
                stage="encoding",
                stage_detail="1080×1920 / 30 FPS teslim videosu hazırlanıyor",
                progress=None,
            )
            await run_in_threadpool(
                finalize_delivery_video, output_path, delivery_path, delivery_aspect, 30
            )
            os.replace(delivery_path, output_path)

        video_name = os.path.basename(output_path)
        job_manager.update_job(
            job_id,
            status="completed",
            stage="completed",
            stage_detail="Video hazır",
            progress=100,
            video_url=f"/videos/{video_name}",
            actual_duration_seconds=round(num_scenes * scene_seconds, 2),
        )
        logger.info("[JOB %s] tamamlandı: %s", job_id, output_path)
    except Exception as exc:
        logger.exception("[JOB %s] video üretimi başarısız oldu", job_id)
        job_manager.update_job(
            job_id,
            status="failed",
            stage="failed",
            stage_detail=str(exc),
            progress=None,
            error=f"Video üretimi başarısız oldu: {exc}",
        )
    finally:
        # Ara sahne dosyalarını ve yüklenen orijinal görselleri temizle
        for p in scene_paths:
            try:
                if p != output_path:
                    os.remove(p)
            except OSError:
                pass
        for p in image_paths:
            try:
                os.remove(p)
            except OSError:
                pass


@app.get("/status/{job_id}")
def status(job_id: str):
    job = job_manager.get_job(job_id)
    if job is None:
        raise HTTPException(
            status_code=404,
            detail={
                "code": "job_lost",
                "message": (
                    "Bu üretim işi artık sunucuda bulunmuyor. Runtime yeniden "
                    "başlatılmış veya geçici iş belleği temizlenmiş olabilir."
                ),
            },
        )
    return {
        "status": job["status"],
        "stage": job.get("stage"),
        "stage_detail": job.get("stage_detail"),
        "progress": job.get("progress"),
        "video_url": job.get("video_url"),
        "reference_url": job.get("reference_url"),
        "image_url": job.get("image_url"),
        "error": job.get("error"),
        "scenes_completed": job.get("scenes_completed", 0),
        "scenes_total": job.get("scenes_total", 1),
        "actual_duration_seconds": job.get("actual_duration_seconds"),
        "mode": job.get("mode"),
        "quality": job.get("quality"),
        "aspect_ratio": job.get("aspect_ratio"),
        "product_count": job.get("product_count", 1),
        "project_id": job.get("project_id"),
        "result_id": job.get("result_id"),
        "media_type": job.get("media_type"),
        "provider": job.get("provider"),
        "provider_attempts": job.get("provider_attempts"),
        "qc": job.get("qc"),
        "updated_at": job.get("updated_at"),
    }
