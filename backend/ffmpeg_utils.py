"""
Birden fazla kısa video sahnesini (clip) tek bir MP4'te birleştirir.

FFmpeg ikili dosyası ayrıca kurulmaz; imageio-ffmpeg paketinin indirdiği
statik ffmpeg binary'si kullanılır (requirements.txt üzerinden zaten gelir).
"""

import subprocess
import tempfile
from pathlib import Path
from typing import List, Optional

import imageio_ffmpeg


def concat_video_clips(clip_paths: List[str], output_path: str) -> str:
    """
    clip_paths sırasıyla birleştirilir (yeniden kodlama yapılmadan, "concat
    demuxer" ile) ve output_path'e yazılır. Tüm klipler aynı codec/çözünürlük/
    fps ile üretildiği için (aynı sağlayıcı, aynı ayarlar) stream-copy
    güvenlidir ve hızlıdır.
    """
    if len(clip_paths) == 1:
        # Tek sahne varsa birleştirmeye gerek yok, doğrudan kopyala.
        Path(output_path).write_bytes(Path(clip_paths[0]).read_bytes())
        return output_path

    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()

    with tempfile.NamedTemporaryFile(mode="w", suffix=".txt", delete=False) as list_file:
        for clip_path in clip_paths:
            # ffmpeg concat demuxer format: her satır "file '<mutlak yol>'"
            list_file.write(f"file '{Path(clip_path).resolve()}'\n")
        list_file_path = list_file.name

    try:
        cmd = [
            ffmpeg_exe,
            "-y",
            "-f", "concat",
            "-safe", "0",
            "-i", list_file_path,
            "-c", "copy",
            output_path,
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            # Stream-copy başarısız olursa (örn. hafif codec parametre
            # farkları), yeniden kodlayarak tekrar dene - daha yavaş ama sağlam.
            cmd_reencode = [
                ffmpeg_exe,
                "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", list_file_path,
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                output_path,
            ]
            result2 = subprocess.run(cmd_reencode, capture_output=True, text=True)
            if result2.returncode != 0:
                raise RuntimeError(
                    f"FFmpeg birleştirme başarısız oldu: {result2.stderr[-800:]}"
                )
    finally:
        Path(list_file_path).unlink(missing_ok=True)

    return output_path


def finalize_delivery_video(
    input_path: str,
    output_path: Optional[str] = None,
    aspect_ratio: str = "9:16",
    fps: int = 30,
) -> str:
    """Üretim klibini telefon/Reels teslim formatına dönüştür.

    AI'nın düşük FPS üretimini tekrar inference yapmadan 30 FPS zaman tabanına
    çevirir. 9:16 teslimatta görüntü kırpılıp 1080x1920'e ölçeklenir; siyah
    kenar veya yatay 720x480 çıktı bırakılmaz.
    """
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    src = Path(input_path)
    dst = Path(output_path) if output_path else src.with_name(src.stem + "_delivery.mp4")
    if aspect_ratio == "9:16":
        vf = (
            "crop='if(gt(iw/ih,9/16),ih*9/16,iw)':"
            "'if(gt(iw/ih,9/16),ih,iw*16/9)',"
            "scale=1080:1920:flags=lanczos,fps=30"
        )
    elif aspect_ratio == "1:1":
        vf = "crop='min(iw,ih)':'min(iw,ih)',scale=1080:1080:flags=lanczos,fps=30"
    else:
        vf = "crop='if(gt(iw/ih,16/9),ih*16/9,iw)':'if(gt(iw/ih,16/9),ih,iw*9/16)',scale=1920:1080:flags=lanczos,fps=30"

    cmd = [
        ffmpeg_exe, "-y", "-i", str(src),
        "-vf", vf,
        "-c:v", "libx264", "-preset", "medium", "-crf", "18",
        "-pix_fmt", "yuv420p", "-movflags", "+faststart",
        "-an", str(dst),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"FFmpeg teslim videosu başarısız: {result.stderr[-1000:]}")
    return str(dst)
