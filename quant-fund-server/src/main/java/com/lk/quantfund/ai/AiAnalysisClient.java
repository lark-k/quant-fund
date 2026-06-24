package com.lk.quantfund.ai;

import com.lk.quantfund.ai.model.AiAnalysisContext;
import com.lk.quantfund.ai.model.AiAnalysisResult;

public interface AiAnalysisClient {

    AiAnalysisResult analyze(AiAnalysisContext context);
}

