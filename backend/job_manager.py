"""
Basit, bellek içi (in-memory) iş (job) yöneticisi.
v1 hedefi kalite değil KARARLILIK olduğu için, dış bir veritabanı yerine
thread-safe bir sözlük kullanıyoruz. Süreç yeniden başlarsa job geçmişi
kaybolur; bu, tek-GPU / Colab senaryosu için kabul edilebilir bir sınırlamadır.
"""

import threading
import time
import uuid
from typing import Optional, Dict, Any


class JobManager:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._jobs: Dict[str, Dict[str, Any]] = {}

    def create_job(self) -> str:
        job_id = uuid.uuid4().hex
        with self._lock:
            self._jobs[job_id] = {
                "status": "queued",
                "progress": 0,
                "video_url": None,
                "error": None,
                "created_at": time.time(),
            }
        return job_id

    def update_job(self, job_id: str, **fields: Any) -> None:
        with self._lock:
            if job_id in self._jobs:
                self._jobs[job_id].update(fields)

    def get_job(self, job_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            job = self._jobs.get(job_id)
            return dict(job) if job else None


job_manager = JobManager()
