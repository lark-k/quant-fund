package com.lk.quantfund.service;

import com.lk.quantfund.vo.ai.AiAnalysisReportVO;
import java.util.List;

public interface AiAnalysisService {

    AiAnalysisReportVO analyzeHolding(Long holdingId);

    AiAnalysisReportVO analyzeHoldingForUser(Long userId, Long holdingId);

    List<AiAnalysisReportVO> analyzeAccount(Long accountId);

    List<AiAnalysisReportVO> history(Long accountId, Long holdingId, String fundCode);

    AiAnalysisReportVO regenerate(Long reportId);
}
