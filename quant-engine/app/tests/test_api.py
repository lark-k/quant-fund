from fastapi.testclient import TestClient

from app.main import app
from app.api.inference import _apply_portfolio_sell_budget
from app.core.config import get_settings
from app.core.schemas import AccountSnapshot, HoldingSnapshot
from app.strategies.rule_model import RuleQuantModel

from .conftest import make_request


client = TestClient(app)


def test_health_api_returns_up():
    response = client.get("/api/v1/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "quant-engine",
        "modelVersion": "rule-v1.39.0",
    }


def test_analyze_api_returns_quant_signal():
    request = make_request().model_dump(mode="json")

    response = client.post("/api/v1/quant/analyze", json=request)

    assert response.status_code == 200
    body = response.json()
    assert body["requestId"] == "qf-test-1001"
    assert body["fundCode"] == "025833"
    assert body["action"] in {"BUY", "SELL", "HOLD", "WATCH"}
    assert "score" in body
    assert "metrics" in body


def test_analyze_batch_api_returns_batch_counts():
    item = make_request().model_dump(mode="json")

    response = client.post(
        "/api/v1/quant/analyze-batch",
        json={"requestId": "qf-batch-test", "items": [item, item]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["requestId"] == "qf-batch-test"
    assert body["successCount"] == 2
    assert body["failedCount"] == 0
    assert len(body["results"]) == 2


def test_batch_sell_budget_prioritizes_risk_caps_total_and_defers_state():
    model = RuleQuantModel(get_settings())
    requests = []
    results = []
    for index, (amount, drawdown) in enumerate(((4000, -30), (3000, -25), (2000, -23)), start=1):
        request = make_request(
            requestId=f"budget-{index}",
            account=AccountSnapshot(accountId=1, totalAsset=10000),
            holding=HoldingSnapshot(
                holdingId=index,
                fundCode=f"00000{index}",
                fundType="INDEX",
                holdingAmount=amount,
                holdingProfitRate=-4,
                positionRate=amount / 100,
            ),
        )
        result = model.analyze(request)
        metrics = dict(result.metrics)
        metrics.update({
            "decisionReason": "extreme_risk_exit",
            "currentDrawdown60d": drawdown,
            "extremeRiskStageAfter": 1,
            "extremeRiskSellCountAfter": 1,
        })
        requests.append(request)
        results.append(result.model_copy(update={
            "action": "SELL",
            "suggestAmount": amount * 0.5,
            "suggestRatio": 50.0,
            "metrics": metrics,
        }))

    protected = _apply_portfolio_sell_budget(requests, results)

    assert sum(float(item.suggestAmount) for item in protected if item.action == "SELL") == 3000
    assert protected[0].suggestRatio == 50
    assert protected[1].suggestRatio == 33.33
    assert protected[1].metrics["portfolioSellBudgetAdjusted"] is True
    assert protected[2].action == "WATCH"
    assert protected[2].metrics["portfolioSellBudgetDeferred"] is True
    assert protected[2].metrics["extremeRiskStageAfter"] == 0


def test_analyze_api_accepts_null_trade_numbers():
    request = make_request().model_dump(mode="json")
    request["tradeRecords"] = [
        {
            "tradeType": "BUY",
            "tradeAmount": 1000,
            "tradeShare": None,
            "tradeNav": None,
            "tradeTime": "2026-06-20T15:00:00",
        }
    ]

    response = client.post("/api/v1/quant/analyze", json=request)

    assert response.status_code == 200
    assert response.json()["requestId"] == "qf-test-1001"


def test_ml_models_api_returns_registry_state():
    response = client.get("/api/v1/ml/models")

    assert response.status_code == 200
    body = response.json()
    assert body["enabled"] is False
    assert isinstance(body["models"], list)


def test_ml_predict_api_is_safe_when_disabled():
    response = client.post(
        "/api/v1/ml/predict",
        json={"requestId": "ml-test", "features": {"return20d": 2.5}},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["requestId"] == "ml-test"
    assert body["enabled"] is False
    assert body["available"] is False


def test_ml_training_sample_export_api_returns_csv_payload():
    nav_series = [point.model_dump(mode="json") for point in make_request().navSeries]
    response = client.post(
        "/api/v1/ml/training-samples/export",
        json={
            "taskName": "ml-samples",
            "strategyName": "QuantRuleEngine",
            "startDate": "2026-03-20",
            "endDate": "2026-05-10",
            "funds": [
                {
                    "fundCode": "025833",
                    "fundName": "Example Index Fund",
                    "fundType": "INDEX",
                    "navSeries": nav_series,
                }
            ],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["rowCount"] > 0
    assert body["fileName"].endswith(".csv")
    assert "return20d" in body["csvContent"]
