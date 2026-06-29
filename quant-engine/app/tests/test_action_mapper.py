from app.core.schemas import HoldingSnapshot, MarketContext, ScoreBreakdown
from app.features.feature_builder import build_features
from app.strategies.action_mapper import map_action

from .conftest import make_request


def high_score() -> ScoreBreakdown:
    return ScoreBreakdown(
        totalScore=86,
        trendScore=90,
        opportunityScore=82,
        riskScore=80,
        positionScore=78,
        momentumScore=84,
    )


def test_high_score_maps_to_buy_before_1457_when_risk_room_exists():
    request = make_request()
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert action == "BUY"
    assert amount > 0
    assert ratio == 5
    assert blockers == []


def test_1457_or_later_blocks_buy():
    request = make_request(market=MarketContext(tradingDay=True, trading=True, now="2026-06-28 14:57:00"))
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert action == "WATCH"
    assert amount == 0
    assert ratio == 0
    assert "14:57 后不得生成 BUY 建议" in blockers


def test_position_over_limit_does_not_buy():
    holding = HoldingSnapshot(fundCode="025833", fundType="INDEX", positionRate=30, holdingProfitRate=5)
    request = make_request(holding=holding)
    features = build_features(request)

    action, _, _, blockers = map_action(request, high_score(), features)

    assert action == "SELL"
    assert "单基金仓位已达到或超过风险配置上限" in blockers


def test_qdii_intraday_blocks_buy_from_a_share_movement():
    holding = HoldingSnapshot(fundCode="968000", fundType="QDII", positionRate=8, currentEstimateGrowthRate=1.2)
    request = make_request(holding=holding)
    features = build_features(request)

    action, _, _, blockers = map_action(request, high_score(), features)

    assert action == "WATCH"
    assert "QDII/海外基金不得使用 A 股盘中波动生成当日买入建议" in blockers
