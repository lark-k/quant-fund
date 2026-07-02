from __future__ import annotations

from app.core.schemas import RiskProfile, ScoreBreakdown


def clamp(value: float, lower: float = 0, upper: float = 100) -> float:
    return max(lower, min(upper, value))


def calculate_scores(features: dict, risk_profile: RiskProfile) -> ScoreBreakdown:
    trend_score = _trend_score(features)
    opportunity_score = _opportunity_score(features)
    risk_score = _risk_score(features, risk_profile)
    position_score = _position_score(features)
    momentum_score = _momentum_score(features)
    total = (
        trend_score * 0.35
        + opportunity_score * 0.25
        + risk_score * 0.15
        + position_score * 0.15
        + momentum_score * 0.10
    )
    return ScoreBreakdown(
        totalScore=round(clamp(total), 2),
        trendScore=round(trend_score, 2),
        opportunityScore=round(opportunity_score, 2),
        riskScore=round(risk_score, 2),
        positionScore=round(position_score, 2),
        momentumScore=round(momentum_score, 2),
    )


def adjust_total_score(score: ScoreBreakdown, adjustment: float) -> ScoreBreakdown:
    return ScoreBreakdown(
        totalScore=round(clamp(score.totalScore + adjustment), 2),
        trendScore=score.trendScore,
        opportunityScore=score.opportunityScore,
        riskScore=score.riskScore,
        positionScore=score.positionScore,
        momentumScore=score.momentumScore,
    )


def _trend_score(features: dict) -> float:
    score = 50
    score += float(features.get("return5d", 0)) * 1.2
    score += float(features.get("return20d", 0)) * 1.1
    score += float(features.get("return60d", 0)) * 0.35
    score += float(features.get("ma20Deviation", 0)) * 0.7
    score += float(features.get("trendSlope20d", 0)) * 6
    score -= max(float(features.get("consecutiveDownDays", 0)) - 2, 0) * 5
    return clamp(score)


def _opportunity_score(features: dict) -> float:
    score = 50
    score -= float(features.get("ma20Deviation", 0)) * 1.0
    score -= float(features.get("return5d", 0)) * 0.6
    score += max(float(features.get("return20d", 0)), 0) * 0.4
    score += max(float(features.get("return60d", 0)), 0) * 0.2
    score -= max(abs(float(features.get("maxDrawdown20d", 0))) - 10, 0) * 1.2
    return clamp(score)


def _risk_score(features: dict, risk_profile: RiskProfile) -> float:
    volatility20 = float(features.get("volatility20d", 0))
    drawdown60 = abs(float(features.get("maxDrawdown60d", 0)))
    loss_ratio = float(features.get("lossDayRatio20d", 0))
    score = 78 - volatility20 * 0.45 - drawdown60 * 1.2 - max(loss_ratio - 50, 0) * 0.35
    if risk_profile.riskLevel == "LOW":
        score -= 6
    elif risk_profile.riskLevel == "HIGH":
        score += 4
    return clamp(score)


def _position_score(features: dict) -> float:
    position_ratio = float(features.get("positionToSingleLimit", 0))
    profit_buffer = float(features.get("profitBuffer", 0))
    loss_pressure = float(features.get("lossPressure", 0))
    score = 92 - position_ratio * 35 + min(profit_buffer, 20) * 0.4 - loss_pressure * 1.2
    if features.get("shouldReduceByPosition"):
        score -= 25
    return clamp(score)


def _momentum_score(features: dict) -> float:
    score = 50
    score += float(features.get("themeRate", 0)) * 2.5
    score += float(features.get("estimateGrowthRate", 0)) * 2.0
    score += min(float(features.get("consecutiveUpDays", 0)), 4) * 3
    score -= min(float(features.get("consecutiveDownDays", 0)), 4) * 4
    return clamp(score)
