from app.core.schemas import AccountSnapshot, HoldingSnapshot, MarketContext, ScoreBreakdown
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


def buy_score() -> ScoreBreakdown:
    return ScoreBreakdown(
        totalScore=76,
        trendScore=70,
        opportunityScore=60,
        riskScore=35,
        positionScore=75,
        momentumScore=55,
    )


def test_high_score_maps_to_buy_before_1457_when_risk_room_exists():
    request = make_request()
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert action == "BUY"
    assert amount > 0
    assert ratio == 8
    assert blockers == []


def test_equity_fund_can_buy_with_good_trend_even_when_risk_score_is_low():
    holding = HoldingSnapshot(
        fundCode="021528",
        fundType="ACTIVE_EQUITY",
        holdingAmount=1200,
        positionRate=10,
        holdingProfitRate=6,
    )
    request = make_request(holding=holding)
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, buy_score(), features)

    assert action == "BUY"
    assert amount == 300
    assert ratio == 3
    assert blockers == []


def test_low_risk_score_alone_does_not_force_sell_when_trend_is_strong():
    request = make_request()
    features = build_features(request)
    score = ScoreBreakdown(
        totalScore=68,
        trendScore=95,
        opportunityScore=75,
        riskScore=10,
        positionScore=80,
        momentumScore=45,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "HOLD"
    assert amount == 0
    assert ratio == 0
    assert blockers == []


def test_1457_or_later_blocks_buy():
    request = make_request(market=MarketContext(tradingDay=True, trading=True, now="2026-06-28 14:57:00"))
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert action == "WATCH"
    assert amount == 0
    assert ratio == 0
    assert len(blockers) == 1
    assert "BUY" in blockers[0]


def test_position_over_limit_does_not_buy():
    holding = HoldingSnapshot(
        fundCode="025833",
        fundType="INDEX",
        holdingAmount=1200,
        positionRate=30,
        holdingProfitRate=5,
    )
    request = make_request(holding=holding)
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert action == "SELL"
    assert amount == 120
    assert ratio == 10
    assert len(blockers) == 1


def test_market_breakdown_maps_to_sell():
    holding = HoldingSnapshot(
        fundCode="013403",
        fundType="ETF",
        holdingAmount=1200,
        positionRate=12,
        holdingProfitRate=-14,
    )
    request = make_request(holding=holding)
    features = build_features(request)
    features["lossPressure"] = 14
    features["maxDrawdown60d"] = -16
    score = ScoreBreakdown(
        totalScore=36,
        trendScore=35,
        opportunityScore=45,
        riskScore=25,
        positionScore=70,
        momentumScore=40,
    )

    action, amount, ratio, blockers = map_action(request, score, features)

    assert action == "SELL"
    assert amount == 120
    assert ratio == 10
    assert blockers == []


def test_equity_position_over_limit_does_not_force_sell_or_block_buy():
    account = AccountSnapshot(accountId=1, totalAsset=10000, equityPositionRate=95)
    request = make_request(account=account)
    features = build_features(request)

    action, amount, ratio, blockers = map_action(request, high_score(), features)

    assert features["shouldReduceByPosition"] is False
    assert action == "BUY"
    assert amount > 0
    assert ratio == 8
    assert blockers == []


def test_qdii_intraday_blocks_buy_from_a_share_movement():
    holding = HoldingSnapshot(fundCode="968000", fundType="QDII", positionRate=8, currentEstimateGrowthRate=1.2)
    request = make_request(holding=holding)
    features = build_features(request)

    action, _, _, blockers = map_action(request, high_score(), features)

    assert action == "WATCH"
    assert len(blockers) == 1
