package com.lk.quantfund.service.impl;

import com.lk.quantfund.entity.AiAnalysisReport;
import com.lk.quantfund.enums.ResourceType;
import com.lk.quantfund.mapper.AiAnalysisReportMapper;
import com.lk.quantfund.service.ResourceOwnerLookup;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisReportOwnerLookup implements ResourceOwnerLookup {

    private final AiAnalysisReportMapper aiAnalysisReportMapper;

    public AiAnalysisReportOwnerLookup(AiAnalysisReportMapper aiAnalysisReportMapper) {
        this.aiAnalysisReportMapper = aiAnalysisReportMapper;
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.AI_ANALYSIS_REPORT;
    }

    @Override
    public Optional<Long> findOwnerUserId(Long resourceId) {
        AiAnalysisReport report = aiAnalysisReportMapper.selectById(resourceId);
        return report == null ? Optional.empty() : Optional.ofNullable(report.getUserId());
    }
}

