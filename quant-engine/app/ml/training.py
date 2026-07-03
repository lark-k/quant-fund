from __future__ import annotations

import argparse
import json
from pathlib import Path
from uuid import uuid4

import numpy as np
import pandas as pd

from app.ml.features import FEATURE_COLUMNS, PROFILE_FEATURE_COLUMNS, fund_profile_features
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
    classification_target: str = "label",
    classification_return_threshold: float = 0.0,
    classification_learning_rate: float = 0.05,
    classification_num_leaves: int = 15,
    classification_min_data_in_leaf: int = 20,
    classification_feature_fraction: float = 0.9,
    classification_bagging_fraction: float = 0.9,
    classification_sample_weight: str = "none",
    classification_recency_weight_multiplier: float = 1.0,
    model_version: str = "lgbm-v1.0.0",
    validation_fraction: float = 0.2,
    num_boost_round: int = 120,
    return_num_boost_round: int | None = None,
    n_jobs: int = 8,
    activate: bool = True,
    min_activation_auc: float = 0.55,
    max_activation_return_mae: float = 12.0,
    return_objective: str = "regression",
    return_learning_rate: float = 0.05,
    return_num_leaves: int = 15,
    return_min_data_in_leaf: int = 20,
    return_feature_fraction: float = 0.9,
    return_bagging_fraction: float = 0.9,
    return_sample_weight: str = "none",
    return_recency_weight_multiplier: float = 1.0,
    random_seed: int = 17,
    feature_set: str = "all",
) -> ModelMetadata:
    if lgb is None:
        raise RuntimeError("lightgbm is not installed. Run `pip install lightgbm` before training.")

    frame = pd.read_csv(input_csv, dtype={"fundCode": "string"})
    frame = _ensure_profile_feature_columns(frame)
    feature_columns = _feature_columns_for_set(feature_set)
    required_columns = [*feature_columns, return_target_column]
    if classification_target == "label":
        required_columns.append(label_column)
    missing = [column for column in required_columns if column not in frame.columns]
    if missing:
        raise ValueError(f"Training CSV missing columns: {', '.join(missing)}")

    if "date" in frame.columns:
        frame = frame.sort_values("date")
    frame = frame.replace([np.inf, -np.inf], np.nan).fillna(0)
    labels = _classification_labels(frame, label_column, return_target_column, classification_target, classification_return_threshold)
    if labels.nunique() < 2:
        raise ValueError("Training labels must contain both positive and negative samples.")
    class_label_column = "__classificationLabel"
    frame[class_label_column] = labels.astype(int)

    split = _split_index(len(frame), validation_fraction)
    train_frame = frame.iloc[:split]
    valid_frame = frame.iloc[split:]
    train_set = lgb.Dataset(
        train_frame[feature_columns],
        label=train_frame[class_label_column].astype(int),
        weight=_sample_weights(
            train_frame,
            classification_sample_weight,
            classification_recency_weight_multiplier,
            train_frame[class_label_column].astype(int),
        ),
        feature_name=feature_columns,
    )
    valid_set = None
    if len(valid_frame) > 0 and valid_frame[class_label_column].nunique() > 1:
        valid_set = lgb.Dataset(
            valid_frame[feature_columns],
            label=valid_frame[class_label_column].astype(int),
            feature_name=feature_columns,
            reference=train_set,
        )

    params = {
        "objective": "binary",
        "metric": "auc",
        "learning_rate": classification_learning_rate,
        "num_leaves": max(2, int(classification_num_leaves)),
        "min_data_in_leaf": max(1, int(classification_min_data_in_leaf)),
        "feature_fraction": classification_feature_fraction,
        "bagging_fraction": classification_bagging_fraction,
        "bagging_freq": 1,
        "verbosity": -1,
        "num_threads": max(1, int(n_jobs)),
        "seed": int(random_seed),
        "feature_fraction_seed": int(random_seed),
        "bagging_seed": int(random_seed),
    }
    booster = lgb.train(
        params,
        train_set,
        num_boost_round=max(10, int(num_boost_round)),
        valid_sets=[valid_set] if valid_set is not None else None,
    )
    return_train_set = lgb.Dataset(
        train_frame[feature_columns],
        label=train_frame[return_target_column].astype(float),
        weight=_sample_weights(train_frame, return_sample_weight, return_recency_weight_multiplier),
        feature_name=feature_columns,
    )
    return_valid_set = None
    if len(valid_frame) > 0:
        return_valid_set = lgb.Dataset(
            valid_frame[feature_columns],
            label=valid_frame[return_target_column].astype(float),
            feature_name=feature_columns,
            reference=return_train_set,
        )
    return_params = {
        "objective": return_objective,
        "metric": "l1" if return_objective in {"huber", "fair", "regression_l1"} else "l2",
        "learning_rate": return_learning_rate,
        "num_leaves": max(2, int(return_num_leaves)),
        "min_data_in_leaf": max(1, int(return_min_data_in_leaf)),
        "feature_fraction": return_feature_fraction,
        "bagging_fraction": return_bagging_fraction,
        "bagging_freq": 1,
        "verbosity": -1,
        "num_threads": max(1, int(n_jobs)),
        "seed": int(random_seed),
        "feature_fraction_seed": int(random_seed),
        "bagging_seed": int(random_seed),
    }
    return_booster = lgb.train(
        return_params,
        return_train_set,
        num_boost_round=max(10, int(return_num_boost_round or num_boost_round)),
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

    validation_auc = _auc(valid_frame[class_label_column].astype(int).to_numpy(), booster.predict(valid_frame[feature_columns])) if len(valid_frame) > 0 else None
    return_metrics = _regression_metrics(
        valid_frame[return_target_column].astype(float).to_numpy(),
        return_booster.predict(valid_frame[feature_columns]),
    ) if len(valid_frame) > 0 else {"rmse": None, "mae": None}
    should_activate = _activation_allowed(
        activate,
        validation_auc,
        len(valid_frame),
        return_metrics["mae"],
        min_activation_auc,
        max_activation_return_mae,
    )
    metadata_label_column = _classification_label_column(label_column, return_target_column, classification_target, classification_return_threshold)
    notes = (
        "Trained binary probability and forward-return regression models from cached/exported QuantFund feature data. "
        f"Classification target={metadata_label_column}; classificationWeight={classification_sample_weight}; "
        f"returnObjective={return_objective}; returnWeight={return_sample_weight}; featureSet={feature_set}. "
        "No external fund data requests are made."
    )
    if activate and not should_activate:
        notes += (
            f" Activation skipped because validation quality did not meet gates: "
            f"AUC>={min_activation_auc}, validationRows>0, returnMae<={max_activation_return_mae}."
        )
    metadata = ModelMetadata(
        modelId=model_id,
        modelVersion=model_version,
        modelType="lightgbm",
        artifactPath=str(model_path).replace("\\", "/"),
        returnArtifactPath=str(return_model_path).replace("\\", "/"),
        featureColumns=feature_columns,
        labelColumn=metadata_label_column,
        returnTargetColumn=return_target_column,
        trainingRows=len(train_frame),
        validationRows=len(valid_frame),
        validationAuc=validation_auc,
        validationReturnRmse=return_metrics["rmse"],
        validationReturnMae=return_metrics["mae"],
        returnHorizonDays=_infer_return_horizon(frame),
        createdAt=utc_timestamp(),
        active=should_activate,
        notes=notes,
    )
    registry.register(metadata, activate=should_activate)
    with (artifact.parent / "metadata.json").open("w", encoding="utf-8") as file:
        json.dump(metadata.__dict__, file, ensure_ascii=False, indent=2)
    return metadata


def _split_index(size: int, validation_fraction: float) -> int:
    if size < 10:
        return size
    fraction = min(max(validation_fraction, 0.0), 0.5)
    return max(1, min(size - 1, int(size * (1 - fraction))))


def _ensure_profile_feature_columns(frame: pd.DataFrame) -> pd.DataFrame:
    if all(column in frame.columns for column in PROFILE_FEATURE_COLUMNS):
        return frame
    if not {"fundCode", "fundName", "fundType"}.issubset(frame.columns):
        return frame
    profile_features = frame.apply(
        lambda row: fund_profile_features(row.get("fundCode"), row.get("fundName"), row.get("fundType")),
        axis=1,
        result_type="expand",
    )
    for column in PROFILE_FEATURE_COLUMNS:
        if column not in frame.columns:
            frame[column] = profile_features[column]
    return frame


def _classification_labels(
    frame: pd.DataFrame,
    label_column: str,
    return_target_column: str,
    classification_target: str,
    threshold: float,
) -> pd.Series:
    if classification_target == "label":
        return frame[label_column].astype(int)
    if classification_target == "forward-return-threshold":
        return (frame[return_target_column].astype(float) > float(threshold)).astype(int)
    raise ValueError(f"Unsupported classification_target: {classification_target}")


def _classification_label_column(
    label_column: str,
    return_target_column: str,
    classification_target: str,
    threshold: float,
) -> str:
    if classification_target == "label":
        return label_column
    if classification_target == "forward-return-threshold":
        return f"{return_target_column}>{float(threshold):g}"
    raise ValueError(f"Unsupported classification_target: {classification_target}")


def _sample_weights(
    frame: pd.DataFrame,
    scheme: str,
    recency_multiplier: float = 1.0,
    labels: pd.Series | None = None,
) -> pd.Series | None:
    normalized = (scheme or "none").strip().lower().replace("_", "-")
    if normalized == "none":
        return None
    weights = pd.Series(np.ones(len(frame), dtype=float), index=frame.index)
    if normalized == "fund-balanced":
        if "fundCode" not in frame.columns:
            raise ValueError("fund-balanced sample weighting requires fundCode column.")
        counts = frame.groupby("fundCode")["fundCode"].transform("count")
        weights *= len(frame) / (counts * max(frame["fundCode"].nunique(), 1))
        return weights.astype(float)
    if normalized in {"balanced", "balanced-exp-recent"}:
        if labels is None:
            raise ValueError("Balanced sample weighting requires classification labels.")
        counts = labels.value_counts()
        if len(counts) > 1:
            class_weight = {label: len(labels) / (len(counts) * count) for label, count in counts.items()}
            weights *= labels.map(class_weight).astype(float)
    if normalized in {"exp-recent", "balanced-exp-recent"}:
        multiplier = max(float(recency_multiplier), 1.0)
        if len(frame) > 1 and multiplier > 1:
            weights *= np.exp(np.linspace(0, np.log(multiplier), len(frame)))
    if normalized not in {"balanced", "fund-balanced", "linear-recent", "exp-recent", "balanced-exp-recent"}:
        raise ValueError(f"Unsupported sample_weight scheme: {scheme}")
    if normalized == "linear-recent" and len(frame) > 1:
        weights *= np.linspace(1 / max(float(recency_multiplier), 1.0), max(float(recency_multiplier), 1.0), len(frame))
    return weights.astype(float)


def _feature_columns_for_set(feature_set: str) -> list[str]:
    normalized = (feature_set or "all").strip().lower().replace("_", "-")
    legacy_columns = [
        "return5d",
        "return20d",
        "return60d",
        "ma20Deviation",
        "trendSlope20d",
        "volatility20d",
        "maxDrawdown20d",
        "maxDrawdown60d",
        "lossDayRatio20d",
        "consecutiveUpDays",
        "consecutiveDownDays",
        "positionToSingleLimit",
        "profitBuffer",
        "lossPressure",
        "themeRate",
        "estimateGrowthRate",
        "navSampleSize",
        "holdingProfitRate",
        "holdingDays",
    ]
    long_cycle_columns = [
        "return120d",
        "ma60Deviation",
        "ma120Deviation",
        "volatility60d",
        "maxDrawdown120d",
    ]
    tracking_columns = [column for column in FEATURE_COLUMNS if column.startswith("tracking")]
    broad_market_columns = [column for column in FEATURE_COLUMNS if column.startswith("market")]
    profile_columns = [column for column in FEATURE_COLUMNS if column in PROFILE_FEATURE_COLUMNS]
    feature_sets = {
        "all": FEATURE_COLUMNS,
        "legacy": legacy_columns,
        "legacy-profile": [*legacy_columns, *profile_columns],
        "legacy-profile-long": [*legacy_columns, *profile_columns, *long_cycle_columns],
        "legacy-profile-tracking": [*legacy_columns, *profile_columns, *tracking_columns],
        "legacy-profile-broad": [*legacy_columns, *profile_columns, *broad_market_columns],
        "legacy-profile-long-broad": [*legacy_columns, *profile_columns, *long_cycle_columns, *broad_market_columns],
        "base-no-profile": [column for column in FEATURE_COLUMNS if column not in PROFILE_FEATURE_COLUMNS],
    }
    if normalized not in feature_sets:
        raise ValueError(f"Unsupported feature_set: {feature_set}")
    return list(feature_sets[normalized])


def _activation_allowed(
    requested: bool,
    validation_auc: float | None,
    validation_rows: int,
    validation_return_mae: float | None,
    min_activation_auc: float,
    max_activation_return_mae: float,
) -> bool:
    if not requested or validation_rows <= 0 or validation_auc is None:
        return False
    if validation_auc < min_activation_auc:
        return False
    if validation_return_mae is not None and validation_return_mae > max_activation_return_mae:
        return False
    return True


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
    parser.add_argument("--classification-target", choices=["label", "forward-return-threshold"], default="label")
    parser.add_argument("--classification-return-threshold", type=float, default=0.0)
    parser.add_argument("--classification-learning-rate", type=float, default=0.05)
    parser.add_argument("--classification-num-leaves", type=int, default=15)
    parser.add_argument("--classification-min-data-in-leaf", type=int, default=20)
    parser.add_argument("--classification-feature-fraction", type=float, default=0.9)
    parser.add_argument("--classification-bagging-fraction", type=float, default=0.9)
    parser.add_argument("--classification-sample-weight", choices=["none", "balanced", "linear-recent", "exp-recent", "fund-balanced", "balanced-exp-recent"], default="none")
    parser.add_argument("--classification-recency-weight-multiplier", type=float, default=1.0)
    parser.add_argument("--model-version", default="lgbm-v1.0.0")
    parser.add_argument("--validation-fraction", type=float, default=0.2)
    parser.add_argument("--rounds", type=int, default=120)
    parser.add_argument("--return-rounds", type=int, default=None)
    parser.add_argument("--n-jobs", type=int, default=8)
    parser.add_argument("--no-activate", action="store_true")
    parser.add_argument("--min-activation-auc", type=float, default=0.55)
    parser.add_argument("--max-activation-return-mae", type=float, default=12.0)
    parser.add_argument("--return-objective", choices=["regression", "regression_l1", "huber", "fair"], default="regression")
    parser.add_argument("--return-learning-rate", type=float, default=0.05)
    parser.add_argument("--return-num-leaves", type=int, default=15)
    parser.add_argument("--return-min-data-in-leaf", type=int, default=20)
    parser.add_argument("--return-feature-fraction", type=float, default=0.9)
    parser.add_argument("--return-bagging-fraction", type=float, default=0.9)
    parser.add_argument("--return-sample-weight", choices=["none", "linear-recent", "exp-recent", "fund-balanced"], default="none")
    parser.add_argument("--return-recency-weight-multiplier", type=float, default=1.0)
    parser.add_argument("--random-seed", type=int, default=17)
    parser.add_argument(
        "--feature-set",
        choices=[
            "all",
            "legacy",
            "legacy-profile",
            "legacy-profile-long",
            "legacy-profile-tracking",
            "legacy-profile-broad",
            "legacy-profile-long-broad",
            "base-no-profile",
        ],
        default="all",
    )
    args = parser.parse_args()

    metadata = train_lgbm_from_csv(
        input_csv=args.input,
        model_dir=args.model_dir,
        label_column=args.label_column,
        return_target_column=args.return_target_column,
        classification_target=args.classification_target,
        classification_return_threshold=args.classification_return_threshold,
        classification_learning_rate=args.classification_learning_rate,
        classification_num_leaves=args.classification_num_leaves,
        classification_min_data_in_leaf=args.classification_min_data_in_leaf,
        classification_feature_fraction=args.classification_feature_fraction,
        classification_bagging_fraction=args.classification_bagging_fraction,
        classification_sample_weight=args.classification_sample_weight,
        classification_recency_weight_multiplier=args.classification_recency_weight_multiplier,
        model_version=args.model_version,
        validation_fraction=args.validation_fraction,
        num_boost_round=args.rounds,
        return_num_boost_round=args.return_rounds,
        n_jobs=args.n_jobs,
        activate=not args.no_activate,
        min_activation_auc=args.min_activation_auc,
        max_activation_return_mae=args.max_activation_return_mae,
        return_objective=args.return_objective,
        return_learning_rate=args.return_learning_rate,
        return_num_leaves=args.return_num_leaves,
        return_min_data_in_leaf=args.return_min_data_in_leaf,
        return_feature_fraction=args.return_feature_fraction,
        return_bagging_fraction=args.return_bagging_fraction,
        return_sample_weight=args.return_sample_weight,
        return_recency_weight_multiplier=args.return_recency_weight_multiplier,
        random_seed=args.random_seed,
        feature_set=args.feature_set,
    )
    print(json.dumps(metadata.__dict__, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
