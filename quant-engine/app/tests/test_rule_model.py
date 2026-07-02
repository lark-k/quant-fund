from app.core.config import Settings
from app.core.schemas import HoldingSnapshot, MarketContext
from app.strategies.rule_model import RuleQuantModel

from .conftest import make_request


def test_rule_model_returns_complete_signal_shape():
    response = RuleQuantModel(Settings()).analyze(make_request())

    assert response.fundCode == "025833"
    assert response.action in {"BUY", "SELL", "HOLD", "WATCH"}
    assert response.modelVersion == "rule-v1.21.0"
    assert response.score.totalScore >= 0
    assert "return20d" in response.metrics
    assert response.reasons
    assert response.risks


def test_rule_model_enforces_no_buy_after_1457_even_when_scores_are_high():
    request = make_request(market=MarketContext(tradingDay=True, trading=True, now="2026-06-28 14:58:00"))

    response = RuleQuantModel(Settings()).analyze(request)

    assert response.action != "BUY"
    assert any("14:57" in reason for reason in response.reasons)


def test_rule_model_allows_qdii_intraday_buy():
    holding = HoldingSnapshot(fundCode="968000", fundName="QDII Fund", fundType="QDII", positionRate=5)
    request = make_request(holding=holding)

    response = RuleQuantModel(Settings()).analyze(request)

    assert response.action == "BUY"
    assert not any("QDII" in reason for reason in response.reasons)
