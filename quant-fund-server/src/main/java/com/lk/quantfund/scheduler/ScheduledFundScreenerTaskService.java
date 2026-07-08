package com.lk.quantfund.scheduler;

import com.lk.quantfund.service.FundFactorService;
import com.lk.quantfund.service.FundQualityScoreService;
import com.lk.quantfund.service.FundScreenerNavService;
import com.lk.quantfund.service.FundUniverseService;
import com.lk.quantfund.vo.screener.FundScreenerTaskResultVO;
import org.springframework.stereotype.Service;

@Service
public class ScheduledFundScreenerTaskService {

    private final FundUniverseService fundUniverseService;
    private final FundScreenerNavService fundScreenerNavService;
    private final FundFactorService fundFactorService;
    private final FundQualityScoreService fundQualityScoreService;

    public ScheduledFundScreenerTaskService(FundUniverseService fundUniverseService,
                                            FundScreenerNavService fundScreenerNavService,
                                            FundFactorService fundFactorService,
                                            FundQualityScoreService fundQualityScoreService) {
        this.fundUniverseService = fundUniverseService;
        this.fundScreenerNavService = fundScreenerNavService;
        this.fundFactorService = fundFactorService;
        this.fundQualityScoreService = fundQualityScoreService;
    }

    public SchedulerTaskResult syncUniverse() {
        return adapt(fundUniverseService.syncUniverse());
    }

    public SchedulerTaskResult syncNav() {
        return adapt(fundScreenerNavService.syncNav());
    }

    public SchedulerTaskResult rebuildUniverse() {
        return adapt(fundUniverseService.rebuildUniverse());
    }

    public SchedulerTaskResult refreshFactors() {
        return adapt(fundFactorService.refreshFactors());
    }

    public SchedulerTaskResult refreshQualityScore() {
        return adapt(fundQualityScoreService.refreshScore());
    }

    private SchedulerTaskResult adapt(FundScreenerTaskResultVO taskResult) {
        SchedulerTaskResult result = new SchedulerTaskResult();
        if ("SKIPPED".equals(taskResult.status())) {
            return result;
        }
        for (int index = 0; index < taskResult.successCount(); index++) {
            result.success();
        }
        for (int index = 0; index < taskResult.failureCount(); index++) {
            String message = index < taskResult.errorSummaries().size()
                    ? taskResult.errorSummaries().get(index)
                    : taskResult.message();
            result.failure(message);
        }
        return result;
    }
}
