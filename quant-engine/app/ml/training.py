from __future__ import annotations

import argparse
import json
from pathlib import Path
from uuid import uuid4

import numpy as np
import pandas as pd

from app.ml.features import FEATURE_COLUMNS
from app.ml.registry import ModelMetadata, ModelRegistry, utc_timestamp

try:  # pragma: no cover - exercised only when optional dependency is installed
    import lightgbm as lgb
except ImportError:  # pragma: no cover - default local test environment may omit it
    lgb = None


def train_lgbm_from_csv(
    input_csv: str | Path,
    model_dir: str | Path = "models",
    label_column: str = "label",
    return_target_column: str = "forwardReturn",
    model_version: str = "lgbm-v1.0.0",
    validation_fraction: float = 0.2,
    num_boost_round: int = 120,
    n_jobs: int = 8,
    activate: bool = True,
) -> ModelMetadata:
    if lgb is None:
        raise RuntimeError("lightgbm is not installed. Run `pip install lightgbm` before training.")

    frame = pd.read_csv(input_csv)
    missing = [column for column in [*FEATURE_COLUMNS, label_column, return_target_column] if column not in frame.columns]
    if missing:
        raise ValueError(f"Training CSV missing columns: {', '.join(missing)}")

    if "date" in frame.columns:
        frame = frame.sort_values("date")
    frame = frame.replace([np.inf, -np.inf], np.nan).fillna(0)
    labels = frame[label_column].astype(int)
    if labels.nunique() < 2:
        raise ValueError("Training labels must contain both positive and negative samples.")
    return_target = frame[return_target_column].astype(float)

    split = _split_index(len(frame), validation_fraction)
    train_frame = frame.iloc[:split]
    valid_frame = frame.iloc[split:]
    train_set = lgb.Dataset(
        train_frame[FEATURE_COLUMNS],
        label=train_frame[label_column].astype(int),
        feature_name=FEATURE_COLUMNS,
    )
    valid_set = None
    if len(valid_frame) > 0 and valid_frame[label_column].nunique() > 1:
        valid_set = lgb.Dataset(
            valid_frame[FEATURE_COLUMNS],
            label=valid_frame[label_column].astype(int),
            feature_name=FEATURE_COLUMNS,
            reference=train_set,
        )

    params = {
        "objective": "binary",
        "metric": "auc",
        "learning_rate": 0.05,
        "num_leaves": 15,
        "min_data_in_leaf": 20,
        "feature_fraction": 0.9,
        "bagging_fraction": 0.9,
        "bagging_freq": 1,
        "verbosity": -1,
        "num_threads": max(1, int(n_jobs)),
    }
    booster = lgb.train(
        params,
        train_set,
        num_boost_round=max(10, int(num_boost_round)),
        valid_sets=[valid_set] if valid_set is not None else None,
    )
    return_train_set = lgb.Dataset(
        train_frame[FEATURE_COLUMNS],
        label=train_frame[return_target_column].astype(float),
        feature_name=FEATURE_COLUMNS,
    )
    return_valid_set = None
    if len(valid_frame) > 0:
        return_valid_set = lgb.Dataset(
            valid_frame[FEATURE_COLUMNS],
            label=valid_frame[return_target_column].astype(float),
            feature_name=FEATURE_COLUMNS,
            reference=return_train_set,
        )
    return_params = {
        "objective": "regression",
        "metric": "l2",
        "learning_rate": 0.05,
        "num_leaves": 15,
        "min_data_in_leaf": 20,
        "feature_fraction": 0.9,
        "bagging_fraction": 0.9,
        "bagging_freq": 1,
        "verbosity": -1,
        "num_threads": max(1, int(n_jobs)),
    }
    return_booster = lgb.train(
        return_params,
        return_train_set,
        num_boost_round=max(10, int(num_boost_round)),
        valid_sets=[return_valid_set] if return_valid_set is not None else None,
    )

    registry = ModelRegistry(model_dir)
    model_id = f"lgbm-{uuid4().hex[:12]}"
    model_path = Path(model_id) / "model.txt"
    return_model_path = Path(model_id) / "return_model.txt"
    artifact = registry.model_dir / model_path
    artifact.parent.mkdir(parents=True, exist_ok=True)
    booster.save_model(str(artifact))
    return_artifact = registry.model_dir / return_model_path
    return_booster.save_model(str(return_artifact))

    validation_auc = _auc(valid_frame[label_column].astype(int).to_numpy(), booster.predict(valid_frame[FEATURE_COLUMNS])) if len(valid_frame) > 0 else None
    return_metrics = _regression_metrics(
        valid_frame[return_target_column].astype(float).to_numpy(),
        return_booster.predict(valid_frame[FEATURE_COLUMNS]),
    ) if len(valid_frame) > 0 else {"rmse": None, "mae": None}
    metadata = ModelMetadata(
        modelId=model_id,
        modelVersion=model_version,
        modelType="lightgbm",
        artifactPath=str(model_path).replace("\\", "/"),
        returnArtifactPath=str(return_model_path).replace("\\", "/"),
        featureColumns=FEATURE_COLUMNS,
        labelColumn=label_column,
        returnTargetColumn=return_target_column,
        trainingRows=len(train_frame),
        validationRows=len(valid_frame),
        validationAuc=validation_auc,
        validationReturnRmse=return_metrics["rmse"],
        validationReturnMae=return_metrics["mae"],
        returnHorizonDays=_infer_return_horizon(frame),
        createdAt=utc_timestamp(),
        active=activate,
        notes="Trained binary probability and forward-return regression models from cached/exported QuantFund feature data. No external fund data requests are made.",
    )
    registry.register(metadata, activate=activate)
    with (artifact.parent / "metadata.json").open("w", encoding="utf-8") as file:
        json.dump(metadata.__dict__, file, ensure_ascii=False, indent=2)
    return metadata


def _split_index(size: int, validation_fraction: float) -> int:
    if size < 10:
        return size
    fraction = min(max(validation_fraction, 0.0), 0.5)
    return max(1, min(size - 1, int(size * (1 - fraction))))


def _auc(y_true: np.ndarray, y_score: np.ndarray) -> float | None:
    positives = y_score[y_true == 1]
    negatives = y_score[y_true == 0]
    if len(positives) == 0 or len(negatives) == 0:
        return None
    comparisons = (positives[:, None] > negatives[None, :]).mean()
    ties = (positives[:, None] == negatives[None, :]).mean() * 0.5
    return round(float(comparisons + ties), 6)


def _regression_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> dict[str, float | None]:
    if len(y_true) == 0:
        return {"rmse": None, "mae": None}
    errors = np.asarray(y_pred, dtype=float) - np.asarray(y_true, dtype=float)
    return {
        "rmse": round(float(np.sqrt(np.mean(errors ** 2))), 6),
        "mae": round(float(np.mean(np.abs(errors))), 6),
    }


def _infer_return_horizon(frame: pd.DataFrame) -> int | None:
    if "horizonDays" not in frame.columns or frame["horizonDays"].empty:
        return 20
    values = frame["horizonDays"].dropna().astype(int)
    if values.empty:
        return 20
    return int(values.mode().iloc[0])


def main() -> None:
    parser = argparse.ArgumentParser(description="Train a LightGBM helper model for QuantFund.")
    parser.add_argument("--input", required=True, help="CSV containing feature columns and a binary label column.")
    parser.add_argument("--model-dir", default="models")
    parser.add_argument("--label-column", default="label")
    parser.add_argument("--return-target-column", default="forwardReturn")
    parser.add_argument("--model-version", default="lgbm-v1.0.0")
    parser.add_argument("--validation-fraction", type=float, default=0.2)
    parser.add_argument("--rounds", type=int, default=120)
    parser.add_argument("--n-jobs", type=int, default=8)
    parser.add_argument("--no-activate", action="store_true")
    args = parser.parse_args()

    metadata = train_lgbm_from_csv(
        input_csv=args.input,
        model_dir=args.model_dir,
        label_column=args.label_column,
        return_target_column=args.return_target_column,
        model_version=args.model_version,
        validation_fraction=args.validation_fraction,
        num_boost_round=args.rounds,
        n_jobs=args.n_jobs,
        activate=not args.no_activate,
    )
    print(json.dumps(metadata.__dict__, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
