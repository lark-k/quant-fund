from datetime import date, datetime
from zoneinfo import ZoneInfo
from typing import Literal
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, ConfigDict, Field, model_validator
from app.technical.trend import live_analysis
from app.technical.backtest import replay, compare_rules

router = APIRouter(prefix='/api/v1/nav-technical', tags=['nav-technical'])


class Nav(BaseModel):
    model_config = ConfigDict(allow_inf_nan=False)
    date: date
    nav: float = Field(gt=0)
    dailyGrowthRate: float | None = None
    sourceName: str | None = None


class ExecutionContext(BaseModel):
    model_config = ConfigDict(allow_inf_nan=False, extra='forbid')
    cashBalance: float
    holdingShares: float = Field(ge=0)
    holdingAmount: float = Field(ge=0)
    pendingTrades: int = Field(ge=0)
    lastTradeDate: date | None = None
    snapshotVersion: int = Field(ge=0)
    snapshotAt: datetime


class AnalysisRequest(BaseModel):
    rows: list[Nav] = Field(max_length=4000)
    fundType: str
    evaluatedAt: datetime
    trading: bool
    hasHolding: bool = True
    tradingDates: list[str] | None = Field(default=None, max_length=4000)
    execution: ExecutionContext | None = None


class ReplayRequest(BaseModel):
    model_config = ConfigDict(allow_inf_nan=False, extra='forbid')
    rows: list[Nav] = Field(max_length=4000)
    tradingDates: list[str] = Field(max_length=4000)
    fundCode: str = Field(pattern=r'^\d{6}$')
    fundType: str
    startDate: date
    endDate: date
    initialCash: float = Field(default=10000, ge=100, le=100000000)
    initialPositionPercent: float = Field(default=0, ge=0, le=100)
    ruleVersion: Literal['NAV-TA v1', 'NAV-TA v2-balanced', 'NAV-TA v3.1-trend'] = 'NAV-TA v1'
    buyPercent: float = Field(default=25, gt=0, le=100)
    sellPercent: float = Field(default=50, gt=0, le=100)
    buyFee: float = Field(default=0.15, ge=0, le=5)
    shortSellFee: float = Field(default=1.5, ge=0, le=5)
    mediumSellFee: float = Field(default=0.5, ge=0, le=5)
    sellFee: float = Field(default=0, ge=0, le=5)
    disclosureDelay: int = Field(default=1, ge=1, le=5)
    confirmDelay: int = Field(default=1, ge=1, le=10)
    settlementDelay: int = Field(default=3, ge=1, le=20)

    @model_validator(mode='after')
    def validate_range(self):
        if self.startDate >= self.endDate or (self.endDate-self.startDate).days > 1827:
            raise ValueError('回测区间须为 2 天至 5 年')
        if self.endDate > datetime.now(ZoneInfo('Asia/Shanghai')).date():
            raise ValueError('回测截止日不能在未来')
        if 'QDII' in self.fundType.upper() and self.disclosureDelay < 2:
            raise ValueError('QDII 披露延迟至少设为 2 个净值观察日')
        return self


@router.post('/analyze')
def analyze_endpoint(request: AnalysisRequest):
    return live_analysis([r.model_dump(mode='json') for r in request.rows], request.fundType, request.evaluatedAt, request.trading, request.hasHolding, request.tradingDates,
                         request.execution.model_dump(mode='json') if request.execution else None)


@router.post('/backtest')
def replay_endpoint(request: ReplayRequest):
    try:
        result = replay(request)
        result['comparison'] = compare_rules(request, result)
        return result
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
