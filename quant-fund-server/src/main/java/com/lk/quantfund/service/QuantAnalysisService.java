package com.lk.quantfund.service;

import com.lk.quantfund.enums.StrategyAction;
import com.lk.quantfund.vo.quant.QuantEngineHealthVO;
import com.lk.quantfund.vo.quant.QuantSignalVO;
import java.util.List;

public interface QuantAnalysisService {

    QuantSignalVO analyzeHolding(Long holdingId);

    QuantSignalVO analyzeHoldingForUser(Long userId, Long holdingId);

    List<QuantSignalVO> analyzeAccount(Long accountId);

    List<QuantSignalVO> latestSignals(Long accountId, Long holdingId, String fundCode, StrategyAction action);

    QuantEngineHealthVO health();
}
