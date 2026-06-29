from fastapi import APIRouter

from app.core.config import get_settings

router = APIRouter(prefix="/api/v1", tags=["health"])


@router.get("/health")
def health() -> dict[str, str]:
    settings = get_settings()
    return {
        "status": "UP",
        "service": "quant-engine",
        "modelVersion": settings.rule_model_version,
    }
