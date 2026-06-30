from fastapi import FastAPI

from app.api.backtest import router as backtest_router
from app.api.health import router as health_router
from app.api.inference import router as inference_router


app = FastAPI(
    title="QuantFund Quant Engine",
    version="0.1.0",
    description="Rule-based intraday quant decision engine for QuantFund.",
)

app.include_router(health_router)
app.include_router(inference_router)
app.include_router(backtest_router)
