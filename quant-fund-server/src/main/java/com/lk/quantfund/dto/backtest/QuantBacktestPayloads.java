package com.lk.quantfund.dto.backtest;

import com.lk.quantfund.dto.quant.QuantNavPointDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class QuantBacktestPayloads {

    private QuantBacktestPayloads() {
    }

    public record RunRequest(
            Long accountId,
            List<String> fundCodes,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @DecimalMin("100") BigDecimal initialCash,
            @DecimalMin("0.0") BigDecimal feeRate,
            @Valid StrategyParams strategyParams,
            @Valid Options options
    ) {
    }

    public record StrategyParams(
            BigDecimal buyThreshold,
            BigDecimal sellThreshold,
            BigDecimal maxSinglePositionRate,
            BigDecimal buyStepRatio,
            BigDecimal sellStepRatio,
            BigDecimal takeProfitRate,
            BigDecimal stopLossRate,
            @Min(2) Integer minNavSamples,
            @Min(0) Integer warmupDays,
            BigDecimal trendHoldReturn20d,
            BigDecimal trendHoldMa20Deviation
    ) {
    }

    public record Options(
            @Min(1) Integer workers,
            Boolean saveEquityCurve,
            Boolean saveTrades,
            Boolean enableMl
    ) {
    }

    public record EngineFund(
            String fundCode,
            String fundName,
            String fundType,
            List<QuantNavPointDTO> navSeries
    ) {
    }

    public record EngineBatchRequest(
            String taskName,
            String strategyName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal initialCash,
            BigDecimal feeRate,
            List<EngineFund> funds,
            StrategyParams strategyParams,
            Options options
    ) {
    }

    public record TrainingLabelConfig(
            Integer horizonDays,
            BigDecimal minForwardReturn,
            BigDecimal maxForwardDrawdown
    ) {
    }

    public record TrainingSampleExportRequest(
            String taskName,
            String strategyName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal initialCash,
            BigDecimal feeRate,
            List<EngineFund> funds,
            StrategyParams strategyParams,
            Options options,
            TrainingLabelConfig labelConfig
    ) {
    }

    public record TrainingSampleExportResponse(
            String fileName,
            Integer rowCount,
            Integer fundCount,
            Integer positiveCount,
            Integer negativeCount,
            List<String> featureColumns,
            TrainingLabelConfig labelConfig,
            String csvContent
    ) {
    }

    public record EquityPoint(
            String date,
            BigDecimal totalAsset,
            BigDecimal cash,
            BigDecimal positionValue,
            BigDecimal positionRate,
            BigDecimal nav,
            BigDecimal signalScore,
            String action
    ) {
    }

    public record Trade(
            String date,
            String action,
            BigDecimal amount,
            BigDecimal share,
            BigDecimal nav,
            BigDecimal fee,
            BigDecimal score,
            String reason,
            BigDecimal tradeRatio,
            BigDecimal positionRateBefore,
            BigDecimal positionRateAfter,
            BigDecimal return5d,
            BigDecimal return20d,
            BigDecimal return60d,
            BigDecimal ma20Deviation,
            BigDecimal maxDrawdown60d,
            BigDecimal trendScore,
            BigDecimal opportunityScore,
            BigDecimal riskScore
    ) {
    }

    public record Result(
            String strategyName,
            String modelVersion,
            String fundCode,
            String fundName,
            String fundType,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal initialCash,
            BigDecimal finalAsset,
            BigDecimal benchmarkFinalAsset,
            BigDecimal positionBenchmarkFinalAsset,
            BigDecimal totalReturnRate,
            BigDecimal annualReturnRate,
            BigDecimal benchmarkReturnRate,
            BigDecimal positionBenchmarkReturnRate,
            BigDecimal excessReturnRate,
            BigDecimal positionExcessReturnRate,
            BigDecimal maxDrawdownRate,
            BigDecimal benchmarkMaxDrawdownRate,
            BigDecimal positionBenchmarkMaxDrawdownRate,
            BigDecimal positionBenchmarkRate,
            BigDecimal winRate,
            BigDecimal sharpeRatio,
            BigDecimal calmarRatio,
            Integer tradeCount,
            BigDecimal turnoverRate,
            Integer navSampleSize,
            BigDecimal dataCoverageRate,
            Boolean mlApplied,
            Integer mlAppliedDays,
            BigDecimal mlScoreAdjustmentAvg,
            BigDecimal mlScoreAdjustmentAbsAvg,
            BigDecimal mlScoreAdjustmentMaxAbs,
            BigDecimal mlExpectedReturnAvg,
            Integer mlExpectedReturnPositiveDays,
            BigDecimal mlExpectedReturnPositiveDayRate,
            BigDecimal mlProbabilityAvg,
            Integer mlBullishDays,
            BigDecimal mlBullishDayRate,
            BigDecimal mlSignalStrengthAvg,
            BigDecimal mlConfidenceScoreAvg,
            BigDecimal mlConfidenceMediumHighDayRate,
            Boolean passed,
            String diagnosis,
            List<EquityPoint> equityCurve,
            List<Trade> trades
    ) {
    }

    public record Summary(
            BigDecimal avgAnnualReturnRate,
            BigDecimal medianAnnualReturnRate,
            BigDecimal p10AnnualReturnRate,
            BigDecimal avgMaxDrawdownRate,
            BigDecimal medianMaxDrawdownRate,
            BigDecimal worstMaxDrawdownRate,
            BigDecimal winFundRate,
            BigDecimal outperformBuyHoldRate,
            BigDecimal outperformPositionBenchmarkRate,
            BigDecimal avgPositionExcessReturnRate,
            BigDecimal avgTradeCount,
            BigDecimal avgSharpeRatio,
            BigDecimal avgCalmarRatio,
            BigDecimal passRate,
            BigDecimal mlAppliedFundRate,
            BigDecimal avgMlScoreAdjustmentAbs,
            BigDecimal maxMlScoreAdjustmentAbs,
            BigDecimal avgMlExpectedReturn,
            BigDecimal avgMlExpectedReturnPositiveDays,
            BigDecimal avgMlExpectedReturnPositiveDayRate,
            BigDecimal avgMlProbability,
            BigDecimal avgMlBullishDays,
            BigDecimal avgMlBullishDayRate,
            BigDecimal avgMlSignalStrength,
            BigDecimal avgMlConfidenceScore,
            BigDecimal avgMlConfidenceMediumHighDayRate,
            String diagnosis
    ) {
    }

    public record BatchResponse(
            String taskId,
            String taskName,
            String status,
            String strategyName,
            String modelVersion,
            Integer fundCount,
            Integer successCount,
            Integer failedCount,
            Summary summary,
            List<Result> results,
            List<Map<String, Object>> errors
    ) {
    }

    public record NavRefreshItem(
            String fundCode,
            String fundName,
            LocalDate requestedStartDate,
            LocalDate requestedEndDate,
            Integer navCount,
            LocalDate firstNavDate,
            LocalDate lastNavDate,
            String status,
            String message
    ) {
    }

    public record NavRefreshResponse(
            Integer fundCount,
            Integer successCount,
            Integer failedCount,
            LocalDate requestedStartDate,
            LocalDate requestedEndDate,
            List<NavRefreshItem> results
    ) {
    }
}
