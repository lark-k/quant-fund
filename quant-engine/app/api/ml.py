from __future__ import annotations

from typing import Any

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.core.config import get_settings
from app.core.schemas import MlTrainingSampleExportRequest
from app.ml.predict import get_ml_predictor
from app.ml.registry import ModelRegistry
from app.ml.samples import export_training_samples

router = APIRouter(prefix="/api/v1/ml", tags=["ml"])


class MlPredictRequest(BaseModel):
    requestId: str = "ml-predict"
    features: dict[str, Any] = Field(default_factory=dict)


@router.get("/models")
def list_models():
    settings = get_settings()
    registry = ModelRegistry(settings.ml_model_dir)
    return {
        "enabled": settings.ml_enabled,
        "modelDir": settings.ml_model_dir,
        "activeModelId": settings.ml_model_id,
        "models": [model.__dict__ for model in registry.list_models()],
    }


@router.post("/predict")
def predict(request: MlPredictRequest):
    prediction = get_ml_predictor(get_settings()).predict(request.features)
    return {
        "requestId": request.requestId,
        **prediction.__dict__,
    }


@router.post("/training-samples/export")
def export_samples(request: MlTrainingSampleExportRequest):
    return export_training_samples(request)
