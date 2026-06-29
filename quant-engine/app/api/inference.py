from fastapi import APIRouter, HTTPException

from app.core.config import get_settings
from app.core.schemas import (
    QuantAnalyzeRequest,
    QuantBatchAnalyzeRequest,
    QuantBatchAnalyzeResponse,
)
from app.strategies.rule_model import RuleQuantModel

router = APIRouter(prefix="/api/v1/quant", tags=["quant"])


@router.post("/analyze")
def analyze(request: QuantAnalyzeRequest):
    model = RuleQuantModel(get_settings())
    return model.analyze(request)


@router.post("/analyze-batch", response_model=QuantBatchAnalyzeResponse)
def analyze_batch(request: QuantBatchAnalyzeRequest):
    settings = get_settings()
    if len(request.items) > settings.max_batch_funds:
        raise HTTPException(
            status_code=400,
            detail=f"items size exceeds max_batch_funds={settings.max_batch_funds}",
        )

    model = RuleQuantModel(settings)
    results = []
    errors = []
    for index, item in enumerate(request.items):
        try:
            results.append(model.analyze(item))
        except Exception as exc:  # pragma: no cover - defensive batch isolation
            errors.append(
                {
                    "index": index,
                    "requestId": getattr(item, "requestId", None),
                    "message": str(exc),
                }
            )
    return QuantBatchAnalyzeResponse(
        requestId=request.requestId,
        results=results,
        successCount=len(results),
        failedCount=len(errors),
        errors=errors,
    )
