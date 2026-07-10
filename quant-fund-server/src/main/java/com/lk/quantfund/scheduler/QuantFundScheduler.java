package com.lk.quantfund.scheduler;

import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.service.TradeRecordService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuantFundScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuantFundScheduler.class);
    private static final String ZONE = "Asia/Shanghai";
    private static final String TRIGGER_CRON = "CRON";

    private final QuantFundProperties properties;
    private final TradingCalendarService tradingCalendarService;
    private final SchedulerTaskLogService schedulerTaskLogService;
    private final ScheduledFundTaskService scheduledFundTaskService;
    private final ScheduledFundScreenerTaskService scheduledFundScreenerTaskService;
    private final TradeRecordService tradeRecordService;

    public QuantFundScheduler(QuantFundProperties properties,
                              TradingCalendarService tradingCalendarService,
                              SchedulerTaskLogService schedulerTaskLogService,
                              ScheduledFundTaskService scheduledFundTaskService,
                              ScheduledFundScreenerTaskService scheduledFundScreenerTaskService,
                              TradeRecordService tradeRecordService) {
        this.properties = properties;
        this.tradingCalendarService = tradingCalendarService;
        this.schedulerTaskLogService = schedulerTaskLogService;
        this.scheduledFundTaskService = scheduledFundTaskService;
        this.scheduledFundScreenerTaskService = scheduledFundScreenerTaskService;
        this.tradeRecordService = tradeRecordService;
    }

    @Scheduled(cron = "0 5 9 ? * MON-FRI", zone = ZONE)
    public void createDueRegularInvestTrades() {
        runTradingTask("CREATE_DUE_REGULAR_INVEST_TRADES", () ->
                tradeRecordService.createDueRegularInvestTrades(LocalDate.now()));
    }

    @Scheduled(cron = "0 10 9 ? * MON-FRI", zone = ZONE)
    public void settleDueProcessingTrades() {
        runTradingTask("SETTLE_DUE_PROCESSING_TRADES", () ->
                tradeRecordService.settleDueProcessingTrades(LocalDate.now()));
    }

    @Scheduled(cron = "0 30/2 9 ? * MON-FRI", zone = ZONE)
    @Scheduled(cron = "0 */2 10 ? * MON-FRI", zone = ZONE)
    @Scheduled(cron = "0 0-30/2 11 ? * MON-FRI", zone = ZONE)
    public void refreshMorningIntradayEstimates() {
        runTradingTask("REFRESH_MORNING_INTRADAY_ESTIMATES", scheduledFundTaskService::refreshIntradayEstimates);
    }

    @Scheduled(cron = "0 */2 13-14 ? * MON-FRI", zone = ZONE)
    @Scheduled(cron = "0 0 15 ? * MON-FRI", zone = ZONE)
    public void refreshAfternoonIntradayEstimates() {
        runTradingTask("REFRESH_AFTERNOON_INTRADAY_ESTIMATES", scheduledFundTaskService::refreshIntradayEstimates);
    }

    @Scheduled(cron = "0 45 9 ? * MON-FRI", zone = ZONE)
    @Scheduled(cron = "0 30 10 ? * MON-FRI", zone = ZONE)
    @Scheduled(cron = "0 20 11 ? * MON-FRI", zone = ZONE)
    @Scheduled(cron = "0 30 13 ? * MON-FRI", zone = ZONE)
    @Scheduled(cron = "0 30,50,55 14 ? * MON-FRI", zone = ZONE)
    public void generateQuantSignals() {
        runTradingTask("GENERATE_QUANT_SIGNALS", scheduledFundTaskService::generateQuantSignals);
    }

    @Scheduled(cron = "0 50,55 14 ? * MON-FRI", zone = ZONE)
    public void analyzeFocusHoldings() {
        runTradingTask("AI_FOCUS_HOLDING_ANALYSIS", scheduledFundTaskService::analyzeFocusHoldings);
    }

    @Scheduled(cron = "0 30/15 15 ? * MON-FRI", zone = ZONE)
    @Scheduled(cron = "0 */15 16-21 ? * MON-FRI", zone = ZONE)
    @Scheduled(cron = "0 0 22 ? * MON-FRI", zone = ZONE)
    public void syncOfficialNav() {
        runTradingTask("SYNC_OFFICIAL_NAV", scheduledFundTaskService::syncOfficialNav);
    }

    @Scheduled(cron = "0 0 23 ? * *", zone = ZONE)
    public void createHoldingSnapshots() {
        runTask("CREATE_HOLDING_SNAPSHOTS", false, scheduledFundTaskService::createHoldingSnapshots);
    }

    @Scheduled(cron = "0 30 23 ? * FRI", zone = ZONE)
    public void createWeeklyReviewCheckpoint() {
        runTask("WEEKLY_REVIEW_CHECKPOINT", false, scheduledFundTaskService::createWeeklyReviewCheckpoint);
    }

    @Scheduled(cron = "0 30 21 ? * MON-FRI", zone = ZONE)
    public void syncScreenerUniverse() {
        runScreenerTask("SCREENER_SYNC_UNIVERSE", scheduledFundScreenerTaskService::syncUniverse);
    }

    @Scheduled(cron = "0 0 22 ? * MON-FRI", zone = ZONE)
    public void syncScreenerNav() {
        runScreenerTask("SCREENER_SYNC_NAV", scheduledFundScreenerTaskService::syncNav);
    }

    @Scheduled(cron = "0 0 23 ? * MON-FRI", zone = ZONE)
    public void rebuildScreenerUniverse() {
        runScreenerTask("SCREENER_REBUILD_UNIVERSE", scheduledFundScreenerTaskService::rebuildUniverse);
    }

    @Scheduled(cron = "0 20 23 ? * MON-FRI", zone = ZONE)
    public void refreshScreenerFactors() {
        runScreenerTask("SCREENER_REFRESH_FACTORS", scheduledFundScreenerTaskService::refreshFactors);
    }

    @Scheduled(cron = "0 40 23 ? * MON-FRI", zone = ZONE)
    public void refreshScreenerQualityScore() {
        runScreenerTask("SCREENER_REFRESH_QUALITY_SCORE", scheduledFundScreenerTaskService::refreshQualityScore);
    }

    @Scheduled(cron = "0 50 23 ? * MON-FRI", zone = ZONE)
    public void runScreenerIncrementalBacktest() {
        runScreenerTask("SCREENER_INCREMENTAL_BACKTEST", scheduledFundScreenerTaskService::runIncrementalBacktest);
    }

    private void runTradingTask(String taskName, Supplier<SchedulerTaskResult> task) {
        runTask(taskName, true, task);
    }

    private void runScreenerTask(String taskName, Supplier<SchedulerTaskResult> task) {
        runTask(taskName, false, properties.getScheduler()::isScreenerEnabled, "screener scheduler is disabled", task);
    }

    private void runTask(String taskName, boolean tradingDayRequired, Supplier<SchedulerTaskResult> task) {
        runTask(taskName, tradingDayRequired, () -> true, "", task);
    }

    private void runTask(String taskName, boolean tradingDayRequired, BooleanSupplier taskEnabled,
                         String disabledReason, Supplier<SchedulerTaskResult> task) {
        LocalDateTime startTime = LocalDateTime.now();
        SchedulerTaskResult result = new SchedulerTaskResult();
        boolean skipped = false;
        try {
            if (!properties.getScheduler().isEnabled()) {
                skipped = true;
                log.info("Scheduler task {} skipped because scheduler is disabled", taskName);
                return;
            }
            if (tradingDayRequired && !tradingCalendarService.isTradingDay(LocalDate.now())) {
                skipped = true;
                log.info("Scheduler task {} skipped because today is not a trading day", taskName);
                return;
            }
            if (!taskEnabled.getAsBoolean()) {
                skipped = true;
                log.info("Scheduler task {} skipped because {}", taskName, disabledReason);
                return;
            }
            result = task.get();
        } catch (RuntimeException exception) {
            result.failure(exception.getMessage());
            log.warn("Scheduler task {} failed: {}", taskName, exception.getMessage());
        } finally {
            schedulerTaskLogService.save(taskName, TRIGGER_CRON, startTime, result, skipped);
        }
    }
}
