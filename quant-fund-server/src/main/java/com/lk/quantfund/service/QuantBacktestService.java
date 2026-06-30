package com.lk.quantfund.service;

import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.BatchResponse;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.NavRefreshResponse;
import com.lk.quantfund.dto.backtest.QuantBacktestPayloads.RunRequest;

public interface QuantBacktestService {

    BatchResponse run(RunRequest request);

    NavRefreshResponse refreshNavCache(RunRequest request);
}
