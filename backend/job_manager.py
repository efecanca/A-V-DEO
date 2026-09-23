"""
Basit, bellek içi (in-memory) iş (job) yöneticisi.
v1 hedefi kalite değil KARARLILIK olduğu için, dış bir veritabanı yerine
thread-safe bir sözlük kullanıyoruz. Süreç yeniden başlarsa job geçmişi
kaybolur; Android tarafı kendi geçmişini yerel olarak da sakladığı için
(bkz. JobHistoryRepository) bu sınırlama kullanıcı deneyimini bozmaz.
"""

import threading
import time
import uuid
from typing import Any, Dict, List, Optional


class JobManager:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._jobs: Dict[str, Dict[str, Any]] = {}
        # Bellek içi sözlüğün sınırsız büyümesini engellemek için basit bir üst sınır.
        self._max_jobs = 200

    def create_job(self, **metadata: Any) -> str:
        job_id = uuid.uuid4().hex
        with self._lock:
            if len(self._jobs) >= self._max_jobs:
                oldest_id = min(self._jobs, key=lambda k: self._jobs[k]["created_at"])
                del self._jobs[oldest_id]

            job = {
                "status": "queued",
                "stage": "queued",
                "stage_detail": "GPU sırası bekleniyor",
                "progress": None,
                "video_url": None,
                "error": None,
                "created_at": time.time(),
                "updated_at": time.time(),
                "scenes_completed": 0,
                "scenes_total": metadata.get("scenes_total", 1),
                "actual_duration_seconds": None,
            }
            job.update(metadata)
            self._jobs[job_id] = job
        return job_id

    def update_job(self, job_id: str, **fields: Any) -> None:
        with self._lock:
            if job_id in self._jobs:
                fields["updated_at"] = time.time()
                self._jobs[job_id].update(fields)

    def get_job(self, job_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            job = self._jobs.get(job_id)
            return dict(job) if job else None

    def list_jobs(self, limit: int = 50) -> List[Dict[str, Any]]:
        with self._lock:
            items = sorted(self._jobs.items(), key=lambda kv: kv[1]["created_at"], reverse=True)
            return [{"job_id": job_id, **data} for job_id, data in items[:limit]]


job_manager = JobManager()
