from datetime import date, timedelta

from app.core.config import Settings
from app.core.schemas import HoldingSnapshot, MarketContext, NavPoint
from app.strategies.rule_model import RuleQuantModel

from .conftest import make_request


def test_rule_model_returns_complete_signal_shape():
    response = RuleQuantModel(Settings()).analyze(make_request())

    assert response.fundCode == "025833"
    assert response.action in {"BUY", "SELL", "HOLD", "WATCH"}
    assert response.modelVersion == "rule-v1.39.0"
    assert response.score.totalScore >= 0
    assert "return20d" in response.metrics
    assert response.reasons
    assert response.risks


def test_rule_model_allows_buy_after_1457_when_scores_are_high():
    request = make_request(market=MarketContext(tradingDay=True, trading=True, now="2026-06-28 14:58:00"))

    response = RuleQuantModel(Settings()).analyze(request)

    assert response.action == "BUY"
    assert not any("14:57" in reason for reason in response.reasons)


def test_rule_model_allows_qdii_intraday_buy():
    holding = HoldingSnapshot(
        fundCode="968000",
        fundName="QDII Fund",
        fundType="QDII",
        holdingAmount=1200,
        positionRate=5,
    )
    request = make_request(holding=holding)

    response = RuleQuantModel(Settings()).analyze(request)

    assert response.action == "BUY"
    assert not any("QDII" in reason for reason in response.reasons)


def test_rule_model_reports_signal_risk_separately_from_investor_profile():
    request = make_request(holding=HoldingSnapshot(
        fundCode="013403",
        fundType="ETF",
        holdingAmount=4000,
        holdingProfitRate=-20,
        positionRate=40,
    ))

    response = RuleQuantModel(Settings()).analyze(request)

    assert response.action == "SELL"
    assert response.riskLevel == "HIGH"
    assert response.metrics["signalRiskLevel"] == "HIGH"
    assert response.metrics["investorRiskLevel"] == "MEDIUM"


def test_intraday_estimate_can_change_live_advice_from_hold_to_buy():
    official = _nav_series(100, -0.0004)
    unchanged = [
        *official,
        _estimated_point(official[-1].nav, 0),
    ]
    rising = [
        *official,
        _estimated_point(official[-1].nav * 1.08, 8),
    ]
    model = RuleQuantModel(Settings(ml_enabled=False))

    unchanged_response = model.analyze(make_request(navSeries=unchanged))
    rising_response = model.analyze(make_request(navSeries=rising))

    assert unchanged_response.action == "HOLD"
    assert rising_response.action == "BUY"
    assert rising_response.metrics["intradayEstimateUsed"] is True
    assert rising_response.metrics["decisionReason"] == "strong_trend_buy"


def test_intraday_estimate_can_trigger_live_extreme_risk_sell():
    official = _nav_series(100, 0)
    falling = [
        *official,
        _estimated_point(official[-1].nav * 0.77, -23),
    ]
    model = RuleQuantModel(Settings(ml_enabled=False))

    response = model.analyze(make_request(navSeries=falling))

    assert response.action == "SELL"
    assert response.metrics["intradayEstimateUsed"] is True
    assert response.metrics["currentDrawdown60d"] == -23
    assert response.metrics["decisionReason"] == "extreme_risk_exit"


def _nav_series(length: int, daily_step: float) -> list[NavPoint]:
    start = date(2026, 1, 1)
    value = 1.0
    points: list[NavPoint] = []
    for index in range(length):
        value *= 1 + daily_step
        points.append(NavPoint(
            date=(start + timedelta(days=index)).isoformat(),
            nav=round(value, 6),
            dailyGrowthRate=round(daily_step * 100, 4),
        ))
    return points


def _estimated_point(nav: float, growth_rate: float) -> NavPoint:
    return NavPoint(
        date="2026-06-28",
        nav=round(nav, 6),
        dailyGrowthRate=growth_rate,
        estimated=True,
        observedAt="2026-06-28 14:50:00",
        navSource="TEST_ESTIMATE",
    )
