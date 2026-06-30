from fastapi import APIRouter, HTTPException

from app.backtest.engine import run_batch_backtest, run_grid_backtest, run_single_backtest
from app.core.config import get_settings
from app.core.schemas import BacktestBatchRunRequest, BacktestGridRunRequest, BacktestRunRequest

router = APIRouter(prefix="/api/v1/backtest", tags=["backtest"])


@router.post("/run")
def run(request: BacktestRunRequest):
    return run_single_backtest(request, get_settings())


@router.post("/run-batch")
def run_batch(request: BacktestBatchRunRequest):
    settings = get_settings()
    if len(request.funds) > settings.max_batch_funds:
        raise HTTPException(status_code=400, detail=f"funds size exceeds max_batch_funds={settings.max_batch_funds}")
    return run_batch_backtest(request, settings)


@router.post("/run-grid")
def run_grid(request: BacktestGridRunRequest):
    settings = get_settings()
    try:
        return run_grid_backtest(request, settings)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
