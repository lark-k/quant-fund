package com.lk.quantfund.vo.screener;

import com.lk.quantfund.constants.SystemConstants;
import java.util.List;
import java.util.Map;

public record FundScreenerExplainVO(
        String fundCode,
        String fundName,
        String fundType,
        Double qualityScore,
        String recommendLevel,
        FundScreenerScoreBreakdownVO scoreBreakdown,
        Map<String, Object> factors,
        List<String> reasons,
        List<String> risks,
        String scoreDate,
        String modelVersion,
        String disclaimer
) {

    public static FundScreenerExplainVO empty(String fundCode) {
        return new FundScreenerExplainVO(
                fundCode,
                null,
                null,
                null,
                "NEUTRAL",
                FundScreenerScoreBreakdownVO.empty(),
                Map.of(),
                List.of("基金优选评分尚未生成，请先执行后续批次的数据同步和评分任务。"),
                List.of("基金优选结果仅供参考，不构成投资建议。"),
                null,
                "screener-rule-v1",
                SystemConstants.DISCLAIMER
        );
    }
}
