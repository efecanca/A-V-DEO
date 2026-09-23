"""T4/Colab icin CogVideoX-5B Image-to-Video provider."""
import gc
import os
from typing import Callable, Optional
import torch
from providers.base import VideoProvider

MODEL_ID = os.environ.get("COGVIDEO_I2V_MODEL_ID", "zai-org/CogVideoX-5b-I2V")

class CogVideoXProvider(VideoProvider):
    name = "cogvideox"

    def __init__(self):
        self._pipe = None

    def _get_pipe(self):
        if self._pipe is not None:
            return self._pipe
        if not torch.cuda.is_available():
            raise RuntimeError("CUDA GPU bulunamadi.")

        from diffusers import (
            AutoencoderKLCogVideoX,
            CogVideoXImageToVideoPipeline,
            CogVideoXTransformer3DModel,
        )
        from transformers import T5EncoderModel
        from torchao.quantization import quantize_, int8_weight_only

        # Resmi CogVideoX T4 yoluna uygun olarak sadece text encoder degil,
        # transformer ve VAE de INT8 weight-only quantize edilir.
        dtype = torch.bfloat16
        q = int8_weight_only()

        text_encoder = T5EncoderModel.from_pretrained(
            MODEL_ID, subfolder="text_encoder", torch_dtype=dtype
        )
        quantize_(text_encoder, q)

        transformer = CogVideoXTransformer3DModel.from_pretrained(
            MODEL_ID, subfolder="transformer", torch_dtype=dtype
        )
        quantize_(transformer, q)

        vae = AutoencoderKLCogVideoX.from_pretrained(
            MODEL_ID, subfolder="vae", torch_dtype=dtype
        )
        quantize_(vae, q)

        pipe = CogVideoXImageToVideoPipeline.from_pretrained(
            MODEL_ID,
            text_encoder=text_encoder,
            transformer=transformer,
            vae=vae,
            torch_dtype=dtype,
        )
        pipe.enable_sequential_cpu_offload()
        pipe.vae.enable_slicing()
        pipe.vae.enable_tiling()

        gc.collect()
        torch.cuda.empty_cache()
        self._pipe = pipe
        return pipe

    def generate_clip(
        self, prompt: str, negative_prompt: str, output_path: str,
        width: int, height: int, num_frames: int, num_inference_steps: int,
        image_path: Optional[str] = None,
        progress_callback: Optional[Callable[[int], None]] = None,
        fps: int = 8,
    ) -> str:
        if image_path is None:
            raise RuntimeError("CogVideoX T4 modu Image-to-Video icindir.")

        from PIL import Image
        from diffusers.utils import export_to_video

        pipe = self._get_pipe()
        image = Image.open(image_path).convert("RGB").resize((720, 480))
        steps = max(10, min(int(num_inference_steps), 20))

        gc.collect()
        torch.cuda.empty_cache()
        if progress_callback:
            progress_callback(5)

        def cb(pipe_obj, step_index, timestep, callback_kwargs):
            if progress_callback:
                progress_callback(min(90, 5 + int(85 * (step_index + 1) / steps)))
            return callback_kwargs

        try:
            with torch.inference_mode():
                frames = pipe(
                    image=image,
                    prompt=prompt,
                    negative_prompt=negative_prompt,
                    num_frames=49,
                    num_inference_steps=steps,
                    guidance_scale=6.0,
                    use_dynamic_cfg=True,
                    callback_on_step_end=cb,
                ).frames[0]
        except torch.cuda.OutOfMemoryError as exc:
            gc.collect()
            torch.cuda.empty_cache()
            raise RuntimeError("CogVideoX INT8 T4 bellegi yetersiz kaldi.") from exc

        export_to_video(frames, output_path, fps=8)
        del frames
        gc.collect()
        torch.cuda.empty_cache()
        if progress_callback:
            progress_callback(100)
        return output_path
