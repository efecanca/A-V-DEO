"""T4/Colab icin CogVideoX-5B Image-to-Video provider."""
import os
from typing import Callable, Optional
import torch
from providers.base import VideoProvider
MODEL_ID=os.environ.get("COGVIDEO_I2V_MODEL_ID","zai-org/CogVideoX-5b-I2V")

class CogVideoXProvider(VideoProvider):
    name="cogvideox"
    def __init__(self): self._pipe=None
    def _get_pipe(self):
        if self._pipe is not None: return self._pipe
        if not torch.cuda.is_available(): raise RuntimeError("CUDA GPU bulunamadi.")
        from diffusers import CogVideoXImageToVideoPipeline
        pipe=CogVideoXImageToVideoPipeline.from_pretrained(MODEL_ID,torch_dtype=torch.bfloat16)
        pipe.enable_sequential_cpu_offload()
        pipe.vae.enable_slicing()
        pipe.vae.enable_tiling()
        self._pipe=pipe
        return pipe
    def generate_clip(self,prompt:str,negative_prompt:str,output_path:str,width:int,height:int,
        num_frames:int,num_inference_steps:int,image_path:Optional[str]=None,
        progress_callback:Optional[Callable[[int],None]]=None,fps:int=8)->str:
        if image_path is None: raise RuntimeError("CogVideoX T4 modu Image-to-Video icindir.")
        from PIL import Image
        from diffusers.utils import export_to_video
        pipe=self._get_pipe()
        image=Image.open(image_path).convert("RGB").resize((720,480))
        steps=max(10,min(int(num_inference_steps),30))
        if progress_callback: progress_callback(5)
        def cb(pipe_obj,step_index,timestep,callback_kwargs):
            if progress_callback: progress_callback(min(90,5+int(85*(step_index+1)/steps)))
            return callback_kwargs
        try:
            frames=pipe(image=image,prompt=prompt,negative_prompt=negative_prompt,num_frames=49,
                num_inference_steps=steps,guidance_scale=6.0,use_dynamic_cfg=True,
                callback_on_step_end=cb).frames[0]
        except torch.cuda.OutOfMemoryError as exc:
            torch.cuda.empty_cache()
            raise RuntimeError("CogVideoX icin GPU bellegi yetersiz kaldi.") from exc
        export_to_video(frames,output_path,fps=8)
        if progress_callback: progress_callback(100)
        return output_path
