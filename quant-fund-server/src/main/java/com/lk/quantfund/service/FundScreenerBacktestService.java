package com.lk.quantfund.service;

import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import com.lk.quantfund.vo.screener.FundScreenerValidationVO;

public interface FundScreenerBacktestService {

    FundScreenerTaskResultVO runIncremental();

    FundScreenerValidationVO getValidation();

    default FundScreenerTaskResultVO backtest() {
        return runIncremental();
    }
}
