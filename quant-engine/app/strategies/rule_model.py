from __future__ import annotations

from app.core.config import Settings
from app.core.schemas import QuantAnalyzeRequest, QuantSignalResponse
from app.features.feature_builder import build_features
from app.strategies.action_mapper import ACTION_TEXT, map_action
from app.strategies.scoring import calculate_scores


class RuleQuantModel:
    def __init__(self, settings: Settings):
        self.settings = settings

    def analyze(self, request: QuantAnalyzeRequest) -> QuantSignalResponse:
        features = build_features(request)
        score = calculate_scores(features, request.riskProfile)
        action, suggest_amount, suggest_ratio, blockers = map_action(request, score, features)
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
            riskLevel=request.riskProfile.riskLevel,
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
