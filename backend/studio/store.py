from __future__ import annotations

import copy
import json
import re
import threading
import time
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


def _now() -> float:
    return time.time()


class ProjectStore:
    """Small JSON-backed project catalog suitable for a single Colab worker."""

    def __init__(self, root_dir: Path) -> None:
        self.root_dir = Path(root_dir)
        self.projects_dir = self.root_dir / "projects"
        self.index_path = self.root_dir / "index.json"
        self.root_dir.mkdir(parents=True, exist_ok=True)
        self.projects_dir.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        self._projects: Dict[str, Dict[str, Any]] = self._load()

    def _load(self) -> Dict[str, Dict[str, Any]]:
        if not self.index_path.exists():
            return {}
        try:
            value = json.loads(self.index_path.read_text(encoding="utf-8"))
            return value if isinstance(value, dict) else {}
        except (OSError, ValueError):
            return {}

    def _save_locked(self) -> None:
        temp = self.index_path.with_suffix(".tmp")
        temp.write_text(
            json.dumps(self._projects, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temp.replace(self.index_path)

    def create_project(self, prompt: str, name: Optional[str] = None) -> str:
        project_id = uuid.uuid4().hex
        safe_name = (name or prompt.strip() or "Yeni Moda Projesi")[:48]
        timestamp = _now()
        with self._lock:
            self._projects[project_id] = {
                "id": project_id,
                "name": safe_name,
                "created_at": timestamp,
                "updated_at": timestamp,
                "status": "draft",
                "product_path": None,
                "model_path": None,
                "fingerprint": None,
                "messages": [],
                "results": [],
            }
            self.project_directory(project_id).mkdir(parents=True, exist_ok=True)
            self._save_locked()
        return project_id

    def project_directory(self, project_id: str) -> Path:
        if not re.fullmatch(r"[a-f0-9]{32}", project_id):
            raise KeyError("Geçersiz proje kimliği")
        return self.projects_dir / project_id

    def set_sources(
        self,
        project_id: str,
        product_path: str,
        model_path: Optional[str] = None,
    ) -> None:
        with self._lock:
            project = self._require(project_id)
            project["product_path"] = product_path
            if model_path:
                project["model_path"] = model_path
            project["updated_at"] = _now()
            self._save_locked()

    def update_project(self, project_id: str, **fields: Any) -> None:
        allowed = {"name", "status", "fingerprint", "last_error"}
        with self._lock:
            project = self._require(project_id)
            for key, value in fields.items():
                if key in allowed:
                    project[key] = value
            project["updated_at"] = _now()
            self._save_locked()

    def add_message(
        self,
        project_id: str,
        role: str,
        text: str,
        *,
        result_id: Optional[str] = None,
    ) -> str:
        message_id = uuid.uuid4().hex
        with self._lock:
            project = self._require(project_id)
            project["messages"].append(
                {
                    "id": message_id,
                    "role": role,
                    "text": text,
                    "result_id": result_id,
                    "created_at": _now(),
                }
            )
            project["updated_at"] = _now()
            self._save_locked()
        return message_id

    def add_result(
        self,
        project_id: str,
        *,
        media_type: str,
        path: str,
        prompt: str,
        provider: str,
        qc: Optional[Dict[str, Any]] = None,
        parent_result_id: Optional[str] = None,
        auto_corrections: int = 0,
    ) -> str:
        result_id = uuid.uuid4().hex
        timestamp = _now()
        with self._lock:
            project = self._require(project_id)
            project["results"].append(
                {
                    "id": result_id,
                    "media_type": media_type,
                    "path": path,
                    "prompt": prompt,
                    "provider": provider,
                    "qc": qc,
                    "approved": False,
                    "parent_result_id": parent_result_id,
                    "auto_corrections": auto_corrections,
                    "created_at": timestamp,
                }
            )
            project["status"] = "video_ready" if media_type == "video" else "ready"
            project["updated_at"] = timestamp
            self._save_locked()
        return result_id

    def approve_result(self, result_id: str, approved: bool = True) -> Dict[str, Any]:
        with self._lock:
            _project_id, result = self._find_result_locked(result_id)
            if result["media_type"] != "image":
                raise ValueError("Yalnızca görsel sonuçlar video için onaylanabilir.")
            result["approved"] = bool(approved)
            result["approved_at"] = _now() if approved else None
            self._save_locked()
            return copy.deepcopy(result)

    def get_project(self, project_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            project = self._projects.get(project_id)
            return copy.deepcopy(project) if project else None

    def get_result(self, result_id: str) -> Optional[Tuple[str, Dict[str, Any]]]:
        with self._lock:
            try:
                project_id, result = self._find_result_locked(result_id)
            except KeyError:
                return None
            return project_id, copy.deepcopy(result)

    def list_projects(self) -> List[Dict[str, Any]]:
        with self._lock:
            values = sorted(
                self._projects.values(), key=lambda item: item.get("updated_at", 0), reverse=True
            )
            return [self.public_project(copy.deepcopy(item)) for item in values]

    def gallery(self) -> List[Dict[str, Any]]:
        items: List[Dict[str, Any]] = []
        with self._lock:
            for project in self._projects.values():
                for result in project.get("results", []):
                    public = self._public_result(project["id"], copy.deepcopy(result))
                    public["project_id"] = project["id"]
                    public["project_name"] = project["name"]
                    items.append(public)
        return sorted(items, key=lambda item: item.get("created_at", 0), reverse=True)

    def public_project(self, project: Dict[str, Any]) -> Dict[str, Any]:
        project_id = project["id"]
        product_path = project.pop("product_path", None)
        model_path = project.pop("model_path", None)
        project.pop("last_error", None)
        project["product_url"] = self._media_url(product_path) if product_path else None
        project["model_url"] = self._media_url(model_path) if model_path else None
        project["results"] = [
            self._public_result(project_id, result) for result in project.get("results", [])
        ]
        project["thumbnail_url"] = next(
            (
                result["media_url"]
                for result in reversed(project["results"])
                if result.get("media_type") == "image"
            ),
            project.get("product_url"),
        )
        return project

    def public_result(self, project_id: str, result: Dict[str, Any]) -> Dict[str, Any]:
        return self._public_result(project_id, copy.deepcopy(result))

    def _public_result(self, project_id: str, result: Dict[str, Any]) -> Dict[str, Any]:
        del project_id
        path = result.pop("path", None)
        result["media_url"] = self._media_url(path) if path else None
        return result

    def _media_url(self, raw_path: str) -> str:
        path = Path(raw_path).resolve()
        root = self.root_dir.resolve()
        relative = path.relative_to(root)
        return "/studio-media/" + relative.as_posix()

    def _require(self, project_id: str) -> Dict[str, Any]:
        project = self._projects.get(project_id)
        if project is None:
            raise KeyError("Proje bulunamadı")
        return project

    def _find_result_locked(self, result_id: str) -> Tuple[str, Dict[str, Any]]:
        for project_id, project in self._projects.items():
            for result in project.get("results", []):
                if result.get("id") == result_id:
                    return project_id, result
        raise KeyError("Sonuç bulunamadı")

