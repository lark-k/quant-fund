package com.lk.quantfund.service;

import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;

public interface FundUniverseService {

    FundScreenerTaskResultVO syncUniverse();

    FundScreenerTaskResultVO rebuildUniverse();
}
