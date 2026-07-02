from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class ModelMetadata:
    modelId: str
    modelVersion: str
    modelType: str
    artifactPath: str
    featureColumns: list[str]
    labelColumn: str
    trainingRows: int
    validationRows: int
    validationAuc: float | None
    createdAt: str
    returnArtifactPath: str | None = None
    returnTargetColumn: str = "forwardReturn"
    validationReturnRmse: float | None = None
    validationReturnMae: float | None = None
    returnHorizonDays: int | None = None
    active: bool = False
    notes: str = ""

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "ModelMetadata":
        return cls(
            modelId=str(data["modelId"]),
            modelVersion=str(data.get("modelVersion", data["modelId"])),
            modelType=str(data.get("modelType", "lightgbm")),
            artifactPath=str(data["artifactPath"]),
            returnArtifactPath=_optional_str(data.get("returnArtifactPath")),
            featureColumns=[str(item) for item in data.get("featureColumns", [])],
            labelColumn=str(data.get("labelColumn", "label")),
            returnTargetColumn=str(data.get("returnTargetColumn", "forwardReturn")),
            trainingRows=int(data.get("trainingRows", 0)),
            validationRows=int(data.get("validationRows", 0)),
            validationAuc=_optional_float(data.get("validationAuc")),
            validationReturnRmse=_optional_float(data.get("validationReturnRmse")),
            validationReturnMae=_optional_float(data.get("validationReturnMae")),
            returnHorizonDays=_optional_int(data.get("returnHorizonDays")),
            createdAt=str(data.get("createdAt", "")),
            active=bool(data.get("active", False)),
            notes=str(data.get("notes", "")),
        )


class ModelRegistry:
    def __init__(self, model_dir: str | Path):
        self.model_dir = Path(model_dir)
        self.registry_path = self.model_dir / "registry.json"

    def list_models(self) -> list[ModelMetadata]:
        if not self.registry_path.exists():
            return []
        with self.registry_path.open("r", encoding="utf-8") as file:
            payload = json.load(file)
        return [ModelMetadata.from_dict(item) for item in payload.get("models", [])]

    def active_model(self, model_id: str | None = None) -> ModelMetadata | None:
        models = self.list_models()
        if model_id:
            return next((model for model in models if model.modelId == model_id), None)
        active = [model for model in models if model.active]
        if active:
            return sorted(active, key=lambda item: item.createdAt)[-1]
        return None

    def register(self, metadata: ModelMetadata, activate: bool = True) -> ModelMetadata:
        self.model_dir.mkdir(parents=True, exist_ok=True)
        models = [model for model in self.list_models() if model.modelId != metadata.modelId]
        if activate:
            models = [
                ModelMetadata(**{**asdict(model), "active": False})
                for model in models
            ]
            metadata = ModelMetadata(**{**asdict(metadata), "active": True})
        models.append(metadata)
        with self.registry_path.open("w", encoding="utf-8") as file:
            json.dump({"models": [asdict(model) for model in models]}, file, ensure_ascii=False, indent=2)
        return metadata

    def resolve_artifact(self, metadata: ModelMetadata, artifact_path: str | None = None) -> Path:
        path = Path(artifact_path or metadata.artifactPath)
        if path.is_absolute():
            return path
        return self.model_dir / path


def utc_timestamp() -> str:
    return datetime.now(UTC).replace(microsecond=0).isoformat()


def _optional_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _optional_int(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _optional_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value)
    return text if text else None
