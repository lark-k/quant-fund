from app.core.schemas import NavPoint
from app.features.feature_builder import build_features
from app.features.risk_features import calculate_risk_features

from .conftest import make_request


def test_feature_builder_calculates_returns_ma_and_position_ratios():
    request = make_request()

    features = build_features(request)

    assert features["navSampleSize"] == 80
    assert features["return20d"] > 0
    assert features["ma20"] > 0
    assert features["positionToSingleLimit"] == 0.48
    assert features["equityPositionToLimit"] == 0.6429


def test_risk_features_calculate_max_drawdown_from_nav_series():
    nav = [
        NavPoint(date="2026-06-01", nav=1.00),
        NavPoint(date="2026-06-02", nav=1.10),
        NavPoint(date="2026-06-03", nav=0.99),
        NavPoint(date="2026-06-04", nav=1.02),
    ]

    features = calculate_risk_features(nav)

    assert features["maxDrawdown20d"] == -10.0
    assert features["volatility5d"] > 0
