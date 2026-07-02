from __future__ import annotations

from app.core.config import Settings
from app.core.schemas import BacktestFund, MlTrainingSampleExportRequest
from app.ml.features import FEATURE_COLUMNS, feature_row
from app.ml.predict import (
    MlSignalPredictor,
    _confidence_level,
    _direction,
    _effective_confidence,
    _model_quality_level,
    _quality_weight,
    _return_band,
    _signal_strength,
    get_ml_predictor,
)
from app.ml.registry import ModelMetadata, ModelRegistry
from app.ml.samples import export_training_samples
from app.strategies.scoring import ScoreBreakdown, adjust_total_score

from .conftest import make_nav_series


def test_feature_row_uses_stable_columns_and_numeric_defaults():
    row = feature_row({"return20d": "2.5", "canBuyMore": True, "bad": "x"})

    assert len(row) == len(FEATURE_COLUMNS)
    assert row[FEATURE_COLUMNS.index("return20d")] == 2.5
    assert row[FEATURE_COLUMNS.index("return5d")] == 0.0


def test_registry_registers_single_active_model(tmp_path):
    registry = ModelRegistry(tmp_path)
    first = ModelMetadata(
        modelId="m1",
        modelVersion="lgbm-v1",
        modelType="lightgbm",
        artifactPath="m1/model.txt",
        featureColumns=FEATURE_COLUMNS,
        labelColumn="label",
        trainingRows=100,
        validationRows=20,
        validationAuc=0.62,
        createdAt="2026-07-01T00:00:00+00:00",
        active=True,
    )
    second = ModelMetadata(
        modelId="m2",
        modelVersion="lgbm-v2",
        modelType="lightgbm",
        artifactPath="m2/model.txt",
        featureColumns=FEATURE_COLUMNS,
        labelColumn="label",
        trainingRows=120,
        validationRows=30,
        validationAuc=0.66,
        createdAt="2026-07-02T00:00:00+00:00",
        active=True,
    )

    registry.register(first)
    registry.register(second)

    models = registry.list_models()
    assert len(models) == 2
    assert registry.active_model().modelId == "m2"
    assert [model.active for model in models].count(True) == 1


def test_predictor_reports_disabled_without_model():
    prediction = MlSignalPredictor("missing", enabled=False).predict({"return20d": 1})

    assert prediction.enabled is False
    assert prediction.available is False
    assert prediction.scoreAdjustment == 0


def test_cached_predictor_respects_disabled_settings(tmp_path):
    settings = Settings(ml_enabled=False, ml_model_dir=str(tmp_path))

    prediction = get_ml_predictor(settings).predict({"return20d": 1})

    assert prediction.reason == "ML disabled"


def test_adjust_total_score_is_capped_by_score_bounds():
    score = ScoreBreakdown(
        totalScore=98,
        trendScore=80,
        opportunityScore=70,
        riskScore=60,
        positionScore=50,
        momentumScore=40,
    )

    adjusted = adjust_total_score(score, 10)

    assert adjusted.totalScore == 100
    assert adjusted.trendScore == score.trendScore


def test_ml_business_quality_translates_validation_metrics_to_medium_confidence():
    metadata = ModelMetadata(
        modelId="m1",
        modelVersion="lgbm-v1",
        modelType="lightgbm",
        artifactPath="m1/model.txt",
        featureColumns=FEATURE_COLUMNS,
        labelColumn="label",
        trainingRows=795,
        validationRows=199,
        validationAuc=0.696113,
        createdAt="2026-07-02T00:00:00+00:00",
        validationReturnMae=8.754,
        validationReturnRmse=11.196615,
        returnHorizonDays=20,
    )

    quality = _quality_weight(metadata)
    signal_strength = _signal_strength(0.68)
    confidence_score = _effective_confidence(quality, signal_strength)

    assert 0.45 <= quality < 0.72
    assert _model_quality_level(quality) == "MEDIUM"
    assert 0.0 < signal_strength <= 1.0
    assert _confidence_level(confidence_score) == "MEDIUM"
    assert _confidence_level(_effective_confidence(quality, _signal_strength(0.53))) == "LOW"
    assert _return_band(9.8, metadata) == (1.046, 18.554)
    assert _direction(0.68, 9.8) == ("BULLISH", "偏强")


def test_export_training_samples_builds_csv_with_labels():
    request = MlTrainingSampleExportRequest(
        taskName="ml-samples",
        strategyName="QuantRuleEngine",
        startDate="2026-03-20",
        endDate="2026-05-10",
        funds=[
            BacktestFund(
                fundCode="025833",
                fundName="Example Index Fund",
                fundType="INDEX",
                navSeries=make_nav_series(length=100, daily_step=0.003),
            )
        ],
    )

    response = export_training_samples(request)

    assert response.rowCount > 0
    assert response.positiveCount > 0
    assert "return20d" in response.csvContent
    assert "forwardReturn" in response.csvContent
    assert "label" in response.csvContent
