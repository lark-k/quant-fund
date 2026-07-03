from app.strategies.fund_profile import (
    is_active_fund_type,
    is_index_fund_type,
    is_passive_qdii_type,
    resolve_effective_fund_type,
    resolve_fund_profile,
)


def test_known_fund_profiles_resolve_by_code():
    assert resolve_effective_fund_type("021528", "财通成长优选混合C", "MIXED") == "ACTIVE_EQUITY"
    assert resolve_effective_fund_type("012922", "易方达全球成长精选混合(QDII)人民币C", "QDII") == "ACTIVE_QDII"
    assert resolve_effective_fund_type("016874", "广发远见智选混合C", "MIXED") == "ACTIVE_EQUITY"
    assert resolve_effective_fund_type("025833", "天弘中证电网设备指数C", "INDEX") == "INDEX"
    assert resolve_effective_fund_type("013403", "华夏恒生科技ETF发起式联接(QDII)C", "QDII") == "ETF_QDII"


def test_known_fund_profiles_resolve_by_name_keyword_when_code_is_new():
    profile = resolve_fund_profile("999999", "易方达全球成长优选混合(QDII)人民币C", "QDII")

    assert profile.effectiveType == "ACTIVE_QDII"
    assert profile.managementStyle == "ACTIVE"
    assert profile.vehicleType == "QDII"


def test_unknown_qdii_etf_is_not_treated_as_active_qdii():
    profile = resolve_fund_profile("888888", "某某恒生科技ETF联接(QDII)C", "QDII")

    assert profile.effectiveType == "ETF_QDII"
    assert profile.managementStyle == "PASSIVE"


def test_profile_behavior_knobs_are_maintained_by_fund_type():
    active_qdii = resolve_fund_profile("012922", "", "QDII")
    index = resolve_fund_profile("025833", "", "INDEX")
    etf_qdii = resolve_fund_profile("013403", "", "QDII")

    assert active_qdii.stableBenchmarkGap > etf_qdii.stableBenchmarkGap
    assert index.defensiveAnnualReturn == 5.0
    assert etf_qdii.defensiveAnnualReturn is None


def test_type_predicates_use_effective_profile_types():
    assert is_active_fund_type("ACTIVE_QDII")
    assert is_passive_qdii_type("ETF_QDII")
    assert is_index_fund_type("INDEX")
