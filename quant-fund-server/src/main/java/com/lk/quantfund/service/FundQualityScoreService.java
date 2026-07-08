package com.lk.quantfund.service;

import com.lk.quantfund.common.PageResponse;
import com.lk.quantfund.dto.screener.FundScreenerQueryRequest;
import com.lk.quantfund.vo.screener.FundScreenerExplainVO;
import com.lk.quantfund.vo.screener.FundScreenerRankItemVO;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;

public interface FundQualityScoreService {

    PageResponse<FundScreenerRankItemVO> rank(FundScreenerQueryRequest request);

    FundScreenerExplainVO explain(String fundCode);

    FundScreenerTaskResultVO refreshScore();
}
