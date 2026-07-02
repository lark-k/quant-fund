from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any

import numpy as np

from app.core.config import Settings
from app.ml.features import FEATURE_COLUMNS, feature_row
from app.ml.registry import ModelMetadata, ModelRegistry
from app.strategies.scoring import clamp

try:  # pragma: no cover - exercised only when optional dependency is installed
    import lightgbm as lgb
except ImportError:  # pragma: no cover - default local test environment may omit it
    lgb = None


@dataclass(frozen=True)
class MlPrediction:
    enabled: bool
    available: bool
    probability: float | None = None
    expectedReturn: float | None = None
    expectedReturnLower: float | None = None
    expectedReturnUpper: float | None = None
    returnHorizonDays: int | None = None
    returnModelAvailable: bool = False
    scoreAdjustment: float = 0.0
    rawScoreAdjustment: float = 0.0
    qualityWeight: float = 0.0
    modelQualityLevel: str = "LOW"
    signalStrength: float = 0.0
    confidenceScore: float = 0.0
    confidenceLevel: str = "LOW"
    direction: str = "UNKNOWN"
    directionText: str = "模型不可用"
    validationAuc: float | None = None
    validationReturnMae: float | None = None
    validationReturnRmse: float | None = None
    modelId: str | None = None
    modelVersion: str | None = None
    reason: str = ""


class MlSignalPredictor:
    def __init__(
        self,
        model_dir: str | Path,
        model_id: str | None = None,
        enabled: bool = False,
        score_adjustment_cap: float = 5.0,
    ):
        self.enabled = enabled
        self.score_adjustment_cap = max(0.0, float(score_adjustment_cap))
        self.registry = ModelRegistry(model_dir)
        self.metadata = self.registry.active_model(model_id) if enabled else None
        self.booster = self._load_booster(self.metadata, "artifactPath")
        self.return_booster = self._load_booster(self.metadata, "returnArtifactPath")

    def predict(self, features: dict[str, Any]) -> MlPrediction:
        if not self.enabled:
            return MlPrediction(enabled=False, available=False, reason="ML disabled")
        if lgb is None:
            return MlPrediction(enabled=True, available=False, reason="lightgbm is not installed")
        if self.metadata is None or self.booster is None:
            return MlPrediction(enabled=True, available=False, reason="No active ML model")

        columns = self.metadata.featureColumns or FEATURE_COLUMNS
        matrix = np.array([feature_row(features, columns)], dtype=float)
        probability = float(np.asarray(self.booster.predict(matrix))[0])
        probability = clamp(probability, 0.0, 1.0)
        expected_return = None
        if self.return_booster is not None:
            expected_return = float(np.asarray(self.return_booster.predict(matrix))[0])
        raw_adjustment = (probability - 0.5) * 2 * self.score_adjustment_cap
        quality_weight = _quality_weight(self.metadata)
        signal_strength = _signal_strength(probability)
        confidence_score = _effective_confidence(quality_weight, signal_strength)
        adjustment = raw_adjustment * quality_weight * signal_strength
        lower, upper = _return_band(expected_return, self.metadata)
        direction, direction_text = _direction(probability, expected_return)
        return MlPrediction(
            enabled=True,
            available=True,
            probability=round(probability, 6),
            expectedReturn=None if expected_return is None else round(expected_return, 4),
            expectedReturnLower=lower,
            expectedReturnUpper=upper,
            returnHorizonDays=self.metadata.returnHorizonDays,
            returnModelAvailable=self.return_booster is not None,
            scoreAdjustment=round(adjustment, 4),
            rawScoreAdjustment=round(raw_adjustment, 4),
            qualityWeight=round(quality_weight, 4),
            modelQualityLevel=_model_quality_level(quality_weight),
            signalStrength=round(signal_strength, 4),
            confidenceScore=round(confidence_score, 4),
            confidenceLevel=_confidence_level(confidence_score),
            direction=direction,
            directionText=direction_text,
            validationAuc=self.metadata.validationAuc,
            validationReturnMae=self.metadata.validationReturnMae,
            validationReturnRmse=self.metadata.validationReturnRmse,
            modelId=self.metadata.modelId,
            modelVersion=self.metadata.modelVersion,
            reason="OK",
        )

    def _load_booster(self, metadata: ModelMetadata | None, artifact_field: str):
        if not self.enabled or metadata is None or lgb is None:
            return None
        artifact_path = getattr(metadata, artifact_field, None)
        if not artifact_path:
            return None
        artifact = self.registry.resolve_artifact(metadata, artifact_path)
        if not artifact.exists():
            return None
        return lgb.Booster(model_file=str(artifact))


def get_ml_predictor(settings: Settings) -> MlSignalPredictor:
    return _cached_predictor(
        settings.ml_model_dir,
        settings.ml_model_id,
        settings.ml_enabled,
        settings.ml_score_adjustment_cap,
    )


@lru_cache(maxsize=8)
def _cached_predictor(
    model_dir: str,
    model_id: str | None,
    enabled: bool,
    score_adjustment_cap: float,
) -> MlSignalPredictor:
    return MlSignalPredictor(model_dir, model_id, enabled, score_adjustment_cap)


def _quality_weight(metadata: ModelMetadata | None) -> float:
    if metadata is None or metadata.validationAuc is None:
        return 0.0
    auc_score = clamp((metadata.validationAuc - 0.55) / 0.25, 0.0, 1.0)
    sample_score = clamp((metadata.validationRows or 0) / 500, 0.0, 1.0)
    mae = metadata.validationReturnMae
    return_score = 0.6 if mae is None else clamp((12.0 - mae) / 8.0, 0.2, 1.0)
    return clamp(auc_score * 0.60 + sample_score * 0.25 + return_score * 0.15, 0.0, 1.0)


def _signal_strength(probability: float) -> float:
    return clamp(abs(probability - 0.5) / 0.2, 0.0, 1.0)


def _effective_confidence(quality_weight: float, signal_strength: float) -> float:
    return clamp(quality_weight * signal_strength, 0.0, 1.0)


def _confidence_level(confidence_score: float) -> str:
    if confidence_score >= 0.60:
        return "HIGH"
    if confidence_score >= 0.30:
        return "MEDIUM"
    return "LOW"


def _model_quality_level(quality_weight: float) -> str:
    if quality_weight >= 0.72:
        return "HIGH"
    if quality_weight >= 0.45:
        return "MEDIUM"
    return "LOW"


def _direction(probability: float, expected_return: float | None) -> tuple[str, str]:
    if probability >= 0.62 and (expected_return is None or expected_return >= 0):
        return "BULLISH", "偏强"
    if probability <= 0.42 or (expected_return is not None and expected_return <= -5):
        return "BEARISH", "偏弱"
    return "NEUTRAL", "中性"


def _return_band(expected_return: float | None, metadata: ModelMetadata | None) -> tuple[float | None, float | None]:
    if expected_return is None:
        return None, None
    mae = metadata.validationReturnMae if metadata and metadata.validationReturnMae is not None else None
    if mae is None:
        return None, None
    return round(expected_return - mae, 4), round(expected_return + mae, 4)


def main() -> None:
    parser = argparse.ArgumentParser(description="Predict QuantFund ML probability and forward return from a feature JSON file.")
    parser.add_argument("--features", required=True, help="JSON file containing a single feature object.")
    parser.add_argument("--model-dir", default="models")
    parser.add_argument("--model-id", default=None)
    parser.add_argument("--cap", type=float, default=5.0)
    args = parser.parse_args()

    with Path(args.features).open("r", encoding="utf-8") as file:
        features = json.load(file)
    predictor = MlSignalPredictor(args.model_dir, args.model_id, enabled=True, score_adjustment_cap=args.cap)
    print(json.dumps(predictor.predict(features).__dict__, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
