from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class FundProfile:
    effectiveType: str
    managementStyle: str
    vehicleType: str
    note: str = ""
    benchmarkTolerance: float = 0.5
    stableAnnualReturn: float = 12.0
    stableDrawdownFloor: float = -18.0
    stableBenchmarkGap: float = 8.0
    maxAnnualTrades: float = 6.0
    defensiveAnnualReturn: float | None = None
    defensiveDrawdownFloor: float | None = None
    defensiveBenchmarkGap: float | None = None


FUND_PROFILE_BY_CODE: dict[str, FundProfile] = {
    "021528": FundProfile("ACTIVE_EQUITY", "ACTIVE", "MUTUAL_FUND", "财通成长优选混合C", stableBenchmarkGap=18.0),
    "012922": FundProfile(
        "ACTIVE_QDII",
        "ACTIVE",
        "QDII",
        "易方达全球成长精选/优选，QDII 载体但主动管理",
        stableAnnualReturn=18.0,
        stableDrawdownFloor=-16.0,
        stableBenchmarkGap=20.0,
        maxAnnualTrades=5.5,
    ),
    "016874": FundProfile("ACTIVE_EQUITY", "ACTIVE", "MUTUAL_FUND", "广发远见智选混合C", stableBenchmarkGap=18.0),
    "025833": FundProfile(
        "INDEX",
        "INDEX",
        "INDEX_FUND",
        "天弘中证电网/电网设备指数C",
        stableBenchmarkGap=12.0,
        maxAnnualTrades=4.0,
        defensiveAnnualReturn=5.0,
        defensiveDrawdownFloor=-10.0,
        defensiveBenchmarkGap=18.0,
    ),
    "013403": FundProfile(
        "ETF_QDII",
        "PASSIVE",
        "ETF_QDII",
        "华夏恒生科技ETF发起式联接(QDII)C",
        stableAnnualReturn=15.0,
        stableDrawdownFloor=-14.0,
        stableBenchmarkGap=6.0,
        maxAnnualTrades=4.5,
    ),
}

FUND_PROFILE_BY_NAME_KEYWORD: tuple[tuple[str, FundProfile], ...] = (
    ("财通成长优选", FUND_PROFILE_BY_CODE["021528"]),
    ("易方达全球成长", FUND_PROFILE_BY_CODE["012922"]),
    ("广发远见智选", FUND_PROFILE_BY_CODE["016874"]),
    ("天弘中证电网", FUND_PROFILE_BY_CODE["025833"]),
    ("天弘电网设备", FUND_PROFILE_BY_CODE["025833"]),
    ("华夏恒生科技ETF", FUND_PROFILE_BY_CODE["013403"]),
)


def resolve_fund_profile(fund_code: str | None, fund_name: str | None, fund_type: str | None) -> FundProfile:
    code = (fund_code or "").strip()
    if code in FUND_PROFILE_BY_CODE:
        return FUND_PROFILE_BY_CODE[code]

    name = fund_name or ""
    for keyword, profile in FUND_PROFILE_BY_NAME_KEYWORD:
        if keyword in name:
            return profile

    return _infer_profile(fund_name, fund_type)


def resolve_effective_fund_type(fund_code: str | None, fund_name: str | None, fund_type: str | None) -> str:
    return resolve_fund_profile(fund_code, fund_name, fund_type).effectiveType


def normalize_fund_type(fund_type: str | None) -> str:
    return (fund_type or "UNKNOWN").strip().upper()


def is_active_fund_type(fund_type: str | None) -> bool:
    normalized = normalize_fund_type(fund_type)
    return normalized in {"ACTIVE_EQUITY", "ACTIVE_QDII", "EQUITY", "MIXED", "STOCK", "HYBRID"}


def is_qdii_or_overseas_type(fund_type: str | None) -> bool:
    normalized = normalize_fund_type(fund_type)
    return "QDII" in normalized or "OVERSEAS" in normalized or "GLOBAL" in normalized


def is_passive_qdii_type(fund_type: str | None) -> bool:
    normalized = normalize_fund_type(fund_type)
    return normalized in {"ETF_QDII", "INDEX_QDII"}


def is_index_fund_type(fund_type: str | None) -> bool:
    normalized = normalize_fund_type(fund_type)
    return normalized in {"INDEX", "INDEX_QDII", "ETF", "ETF_QDII", "INDEX_FUND", "INDEX_ENHANCED"}


def _infer_profile(fund_name: str | None, fund_type: str | None) -> FundProfile:
    text = f"{fund_name or ''} {fund_type or ''}".upper()
    original = normalize_fund_type(fund_type)
    is_qdii = any(token in text for token in ("QDII", "海外", "全球", "恒生", "纳斯达克", "标普"))
    is_etf = "ETF" in text
    is_index = any(token in text for token in ("INDEX", "指数", "中证", "沪深", "恒生科技", "ETF_LINK", "INDEX_ENHANCED"))
    is_active = original in {"ACTIVE_EQUITY", "ACTIVE_QDII", "EQUITY", "MIXED", "STOCK", "HYBRID"} or any(
        token in text for token in ("主动", "混合", "股票", "成长", "智选", "优选", "精选")
    )

    if is_active and is_qdii and not is_etf:
        return FundProfile("ACTIVE_QDII", "ACTIVE", "QDII")
    if is_etf and is_qdii:
        return FundProfile("ETF_QDII", "PASSIVE", "ETF_QDII")
    if is_index and is_qdii:
        return FundProfile("INDEX_QDII", "INDEX", "QDII")
    if is_active:
        return FundProfile("ACTIVE_EQUITY", "ACTIVE", "MUTUAL_FUND")
    if is_etf:
        return FundProfile("ETF", "PASSIVE", "ETF")
    if is_index:
        return FundProfile("INDEX", "INDEX", "INDEX_FUND")
    return FundProfile(original, "UNKNOWN", original)


def _normalize_type(fund_type: str | None) -> str:
    return normalize_fund_type(fund_type)
