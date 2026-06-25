package com.lk.quantfund.service;

import com.lk.quantfund.dto.strategy.RiskProfileRequest;
import com.lk.quantfund.dto.strategy.StrategyConfigRequest;
import com.lk.quantfund.enums.StrategyAction;
import com.lk.quantfund.vo.strategy.RiskProfileVO;
import com.lk.quantfund.vo.strategy.StrategyAnalysisVO;
import com.lk.quantfund.vo.strategy.StrategyConfigVO;
import com.lk.quantfund.vo.strategy.StrategySignalVO;
import java.util.List;

public interface StrategyService {

    StrategyAnalysisVO analyzeHolding(Long holdingId);

    StrategyAnalysisVO analyzeHoldingForUser(Long userId, Long holdingId);

    List<StrategyAnalysisVO> analyzeAccount(Long accountId);

    List<StrategySignalVO> listSignals(Long accountId, Long holdingId, String fundCode, StrategyAction action);

    List<StrategyConfigVO> listConfigs();

    StrategyConfigVO saveConfig(StrategyConfigRequest request);

    RiskProfileVO getRiskProfile();

    RiskProfileVO updateRiskProfile(RiskProfileRequest request);
}
