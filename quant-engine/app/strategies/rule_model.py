from __future__ import annotations

from app.core.config import Settings
from app.core.schemas import QuantAnalyzeRequest, QuantSignalResponse
from app.features.feature_builder import build_features
from app.ml.predict import get_ml_predictor
from app.strategies.action_mapper import ACTION_TEXT, map_action
from app.strategies.scoring import adjust_total_score, calculate_scores


class RuleQuantModel:
    def __init__(self, settings: Settings):
        self.settings = settings

    def analyze(self, request: QuantAnalyzeRequest) -> QuantSignalResponse:
        features = build_features(request)
        score = calculate_scores(features, request.riskProfile)
        ml_prediction = get_ml_predictor(self.settings).predict(features)
        features["mlEnabled"] = ml_prediction.enabled
        features["mlAvailable"] = ml_prediction.available
        features["mlProbability"] = ml_prediction.probability
        features["mlExpectedReturn"] = ml_prediction.expectedReturn
        features["mlExpectedReturnLower"] = ml_prediction.expectedReturnLower
        features["mlExpectedReturnUpper"] = ml_prediction.expectedReturnUpper
        features["mlReturnHorizonDays"] = ml_prediction.returnHorizonDays
        features["mlReturnModelAvailable"] = ml_prediction.returnModelAvailable
        features["mlScoreAdjustment"] = ml_prediction.scoreAdjustment
        features["mlRawScoreAdjustment"] = ml_prediction.rawScoreAdjustment
        features["mlQualityWeight"] = ml_prediction.qualityWeight
        features["mlModelQualityLevel"] = ml_prediction.modelQualityLevel
        features["mlSignalStrength"] = ml_prediction.signalStrength
        features["mlConfidenceScore"] = ml_prediction.confidenceScore
        features["mlConfidenceLevel"] = ml_prediction.confidenceLevel
        features["mlDirection"] = ml_prediction.direction
        features["mlDirectionText"] = ml_prediction.directionText
        features["mlValidationAuc"] = ml_prediction.validationAuc
        features["mlValidationReturnMae"] = ml_prediction.validationReturnMae
        features["mlValidationReturnRmse"] = ml_prediction.validationReturnRmse
        features["mlModelId"] = ml_prediction.modelId
        features["mlModelVersion"] = ml_prediction.modelVersion
        if ml_prediction.available and ml_prediction.scoreAdjustment:
            score = adjust_total_score(score, ml_prediction.scoreAdjustment)
        action, suggest_amount, suggest_ratio, blockers = map_action(request, score, features)
        signal_risk_level = self._signal_risk_level(action, features, score)
        features["investorRiskLevel"] = request.riskProfile.riskLevel
        features["signalRiskLevel"] = signal_risk_level
        reasons = self._build_reasons(request, features, score, blockers)
        risks = self._build_risks(request, features, blockers)
        confidence = self._confidence(score, features, blockers)

        deadline = request.market.deadline.isoformat(sep=" ") if request.market.deadline else None
        return QuantSignalResponse(
            requestId=request.requestId,
            fundCode=request.holding.fundCode,
            holdingId=request.holding.holdingId,
            action=action,
            actionText=ACTION_TEXT[action],
            suggestAmount=suggest_amount,
            suggestRatio=suggest_ratio,
            confidence=confidence,
            riskLevel=signal_risk_level,
            score=score,
            metrics=features,
            reasons=reasons,
            risks=risks,
            modelVersion=self.settings.rule_model_version,
            deadline=deadline,
        )

    def _build_reasons(
        self,
        request: QuantAnalyzeRequest,
        features: dict,
        score,
        blockers: list[str],
    ) -> list[str]:
        reasons = [
            f"综合评分 {score.totalScore:.2f}，趋势/机会/风险/仓位/动量分别为 "
            f"{score.trendScore:.2f}/{score.opportunityScore:.2f}/{score.riskScore:.2f}/"
            f"{score.positionScore:.2f}/{score.momentumScore:.2f}",
            f"近 20 日收益 {float(features.get('return20d', 0)):.2f}%，"
            f"20 日均线偏离 {float(features.get('ma20Deviation', 0)):.2f}%",
            f"当前持仓收益率 {request.holding.holdingProfitRate:.2f}%，"
            f"单基金仓位 {request.holding.positionRate:.2f}%",
        ]
        if blockers:
            reasons.extend(blockers)
        if features.get("mlAvailable"):
            expected_return = features.get("mlExpectedReturn")
            horizon_days = features.get("mlReturnHorizonDays")
            reasons.append(
                "ML 仅作规则辅助调分："
                f"本次调分 {float(features.get('mlScoreAdjustment') or 0):.2f}，"
                f"偏强概率 {float(features.get('mlProbability') or 0):.2%}，"
                f"信号强度 {float(features.get('mlSignalStrength') or 0):.2%}，"
                f"本次可信度 {features.get('mlConfidenceLevel')}"
            )
            if expected_return is not None:
                lower = features.get("mlExpectedReturnLower")
                upper = features.get("mlExpectedReturnUpper")
                band = ""
                if lower is not None and upper is not None:
                    band = f"，参考区间 {float(lower):.2f}% 至 {float(upper):.2f}%"
                reasons.append(
                    "LGBM 方向判断 "
                    f"{features.get('mlDirectionText')}，预测收益仅作参考 {float(expected_return):.2f}%"
                    + (f"，周期 {int(horizon_days)} 个净值样本" if horizon_days else "")
                    + band
                )
        if int(features.get("navSampleSize", 0)) < 20:
            reasons.append("历史净值样本不足 20 条，模型置信度已下调")
        return reasons

    def _build_risks(self, request: QuantAnalyzeRequest, features: dict, blockers: list[str]) -> list[str]:
        risks = [
            f"20 日年化波动率 {float(features.get('volatility20d', 0)):.2f}%，"
            f"60 日最大回撤 {float(features.get('maxDrawdown60d', 0)):.2f}%",
            "建议以 15:00 前平台净值、交易规则和个人风险配置为准",
        ]
        if request.holding.positionRate >= request.riskProfile.maxSingleFundPositionRate * 0.9:
            risks.append("单基金仓位接近风险配置上限，继续加仓空间有限")
        risks.extend(blockers)
        return list(dict.fromkeys(risks))

    def _confidence(self, score, features: dict, blockers: list[str]) -> float:
        sample_size = int(features.get("navSampleSize", 0))
        sample_factor = min(sample_size / 60, 1)
        score_factor = abs(score.totalScore - 50) / 50
        confidence = 0.45 + sample_factor * 0.25 + score_factor * 0.25
        if blockers:
            confidence -= 0.08
        return round(max(0.1, min(0.95, confidence)), 2)

    def _signal_risk_level(self, action: str, features: dict, score) -> str:
        reason = str(features.get("decisionReason") or "")
        if reason in {"extreme_risk_exit", "risk_exit", "extreme_risk_recovery_watch"}:
            return "HIGH"
        if action == "SELL" or score.riskScore < 30 or _abs_metric(features, "currentDrawdown60d") >= 15:
            return "MEDIUM"
        return "LOW"


def _abs_metric(features: dict, key: str) -> float:
    return abs(float(features.get(key, 0) or 0))
