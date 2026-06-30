from fastapi.testclient import TestClient

from app.main import app

from .conftest import make_request


client = TestClient(app)


def test_health_api_returns_up():
    response = client.get("/api/v1/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "quant-engine",
        "modelVersion": "rule-v1.19.0",
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
