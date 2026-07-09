package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.dto.trade.ConvertPairTradeRequest;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.trade.TradeRecordRequest;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.InvestmentPlan;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.enums.FundType;
import com.lk.quantfund.enums.TradeStatus;
import com.lk.quantfund.enums.TradeType;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.InvestmentPlanMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.TradeRecordMapper;
import com.lk.quantfund.scheduler.SchedulerTaskResult;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.FundQueryService;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.TradeRecordService;
import com.lk.quantfund.vo.trade.TradeRecordVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TradeRecordServiceImpl implements TradeRecordService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0000");

    private final TradeRecordMapper tradeRecordMapper;
    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final PortfolioAccountService portfolioAccountService;
    private final InvestmentPlanMapper investmentPlanMapper;
    private final FundQueryService fundQueryService;
    private final TradingCalendarService tradingCalendarService;

    public TradeRecordServiceImpl(TradeRecordMapper tradeRecordMapper,
                                  FundHoldingMapper fundHoldingMapper,
                                  PortfolioAccountMapper portfolioAccountMapper,
                                  PortfolioAccountService portfolioAccountService,
                                  InvestmentPlanMapper investmentPlanMapper,
                                  FundQueryService fundQueryService,
                                  TradingCalendarService tradingCalendarService) {
        this.tradeRecordMapper = tradeRecordMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.portfolioAccountService = portfolioAccountService;
        this.investmentPlanMapper = investmentPlanMapper;
        this.fundQueryService = fundQueryService;
        this.tradingCalendarService = tradingCalendarService;
    }

    @Override
    public TradeRecordVO create(TradeRecordRequest request) {
        if (request.tradeType() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "trade type is required");
        }
        return createAs(request, request.tradeType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeRecordVO createAs(TradeRecordRequest request, TradeType tradeType) {
        Long userId = UserContext.getUserId();
        ensurePositiveAmount(request.tradeAmount());
        ensureAccountOwned(userId, request.accountId());
        TradeStatus status = request.tradeStatus() == null ? TradeStatus.COMPLETED : request.tradeStatus();
        validateRelatedTrade(userId, request, tradeType);
        FundHolding holding = resolveHoldingForTrade(userId, request, tradeType);

        LocalDateTime now = LocalDateTime.now();
        TradeRecord record = new TradeRecord();
        record.setUserId(userId);
        record.setAccountId(request.accountId());
        record.setHoldingId(holding == null ? null : holding.getId());
        record.setFundCode(request.fundCode().trim());
        record.setFundName(request.fundName().trim());
        record.setTradeType(tradeType.name());
        record.setTradeStatus(status.name());
        record.setTradeAmount(valueOrZero(request.tradeAmount()));
        record.setTradeShare(deriveShare(request));
        record.setTradeNav(request.tradeNav());
        record.setTradeFee(valueOrZero(request.tradeFee()));
        record.setTradeTime(request.tradeTime() == null ? now : request.tradeTime());
        record.setRelatedTradeId(request.relatedTradeId());
        record.setRemark(simulatedRemark(request.remark()));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(0);
        tradeRecordMapper.insert(record);

        if (status == TradeStatus.COMPLETED) {
            applyCompletedTrade(userId, record, holding);
            portfolioAccountService.recalculateOwnedAccount(userId, request.accountId());
        }
        return toVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TradeRecordVO> createConvertPair(ConvertPairTradeRequest request) {
        Long userId = UserContext.getUserId();
        FundHolding outHolding = ensureHoldingOwned(userId, request.outHoldingId());
        if (!outHolding.getAccountId().equals(request.accountId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "holding does not belong to the selected account");
        }
        TradeStatus status = request.tradeStatus() == null ? TradeStatus.COMPLETED : request.tradeStatus();
        TradeRecordVO out = createAs(convertOutRequest(request, status, outHolding), TradeType.CONVERT_OUT);
        TradeRecordVO in = createAs(convertInRequest(request, status, out.id()), TradeType.CONVERT_IN);
        return List.of(out, in);
    }

    @Override
    public TradeRecordVO detail(Long tradeId) {
        return toVO(loadOwnedTrade(UserContext.getUserId(), tradeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProcessing(Long tradeId) {
        TradeRecord record = loadOwnedTrade(UserContext.getUserId(), tradeId);
        if (!TradeStatus.PROCESSING.name().equals(record.getTradeStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "only pending trade record can be deleted");
        }
        tradeRecordMapper.deleteById(record.getId());
    }

    @Override
    public List<TradeRecordVO> list(Long accountId, Long holdingId, TradeType tradeType, TradeStatus tradeStatus) {
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<TradeRecord> wrapper = new LambdaQueryWrapper<TradeRecord>()
                .eq(TradeRecord::getUserId, userId);
        if (accountId != null) {
            ensureAccountOwned(userId, accountId);
            wrapper.eq(TradeRecord::getAccountId, accountId);
        }
        if (holdingId != null) {
            ensureHoldingOwned(userId, holdingId);
            wrapper.eq(TradeRecord::getHoldingId, holdingId);
        }
        if (tradeType != null) {
            wrapper.eq(TradeRecord::getTradeType, tradeType.name());
        }
        if (tradeStatus != null) {
            wrapper.eq(TradeRecord::getTradeStatus, tradeStatus.name());
        }
        wrapper.orderByDesc(TradeRecord::getTradeTime);
        return tradeRecordMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public List<TradeRecordVO> processing() {
        return list(null, null, null, TradeStatus.PROCESSING);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TradeRecordVO> settleDueProcessingTrades() {
        Long userId = UserContext.getUserId();
        settleProcessingTrades(LocalDate.now(), userId);
        return list(null, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulerTaskResult compensateDueRegularInvestTrades() {
        return createDueRegularInvestTrades(LocalDate.now(), UserContext.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulerTaskResult settleDueProcessingTrades(LocalDate today) {
        return settleProcessingTrades(today == null ? LocalDate.now() : today, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulerTaskResult createDueRegularInvestTrades(LocalDate today) {
        return createDueRegularInvestTrades(today, null);
    }

    private SchedulerTaskResult createDueRegularInvestTrades(LocalDate today, Long onlyUserId) {
        SchedulerTaskResult result = new SchedulerTaskResult();
        LocalDate runDate = today == null ? LocalDate.now() : today;
        if (investmentPlanMapper == null) {
            return result;
        }
        List<InvestmentPlan> plans = investmentPlanMapper.selectList(new LambdaQueryWrapper<InvestmentPlan>()
                .eq(InvestmentPlan::getStatus, "ENABLED")
                .eq(onlyUserId != null, InvestmentPlan::getUserId, onlyUserId)
                .le(InvestmentPlan::getNextExecuteDate, runDate));
        for (InvestmentPlan plan : plans) {
            try {
                createRegularInvestTrade(plan, runDate);
                plan.setNextExecuteDate(nextPlanExecuteDate(plan, runDate));
                plan.setUpdateTime(LocalDateTime.now());
                investmentPlanMapper.updateById(plan);
                result.success();
            } catch (RuntimeException exception) {
                result.failure(plan.getFundCode() + ": " + exception.getMessage());
            }
        }
        return result;
    }

    private SchedulerTaskResult settleProcessingTrades(LocalDate today, Long onlyUserId) {
        SchedulerTaskResult result = new SchedulerTaskResult();
        List<TradeRecord> records = tradeRecordMapper.selectList(new LambdaQueryWrapper<TradeRecord>()
                .eq(TradeRecord::getTradeStatus, TradeStatus.PROCESSING.name())
                .eq(onlyUserId != null, TradeRecord::getUserId, onlyUserId)
                .orderByAsc(TradeRecord::getTradeTime));
        for (TradeRecord record : records) {
            try {
                if (settleProcessingTrade(record, today)) {
                    result.success();
                }
            } catch (RuntimeException exception) {
                result.failure(record.getFundCode() + ": " + exception.getMessage());
            }
        }
        return result;
    }

    private boolean settleProcessingTrade(TradeRecord record, LocalDate today) {
        TradeSettlement settlement = tradeSettlement(record);
        if (today.isBefore(settlement.settleDate())) {
            return false;
        }
        Optional<BigDecimal> officialNav = officialNav(record.getFundCode(), settlement.navDate());
        if (officialNav.isEmpty()) {
            return false;
        }
        FundHolding holding = record.getHoldingId() == null ? null : ensureHoldingOwned(record.getUserId(), record.getHoldingId());
        if (holding == null) {
            holding = findHolding(record.getUserId(), record.getAccountId(), record.getFundCode());
        }
        record.setTradeNav(officialNav.get());
        record.setTradeShare(resolveConfirmedShare(record, holding, officialNav.get()));
        if (isDecreaseTrade(TradeType.valueOf(record.getTradeType()))) {
            record.setTradeAmount(scale(valueOrZero(record.getTradeShare()).multiply(officialNav.get())));
        }
        record.setTradeStatus(TradeStatus.COMPLETED.name());
        record.setUpdateTime(LocalDateTime.now());
        tradeRecordMapper.updateById(record);
        applyCompletedTrade(record.getUserId(), record, holding);
        portfolioAccountService.recalculateOwnedAccount(record.getUserId(), record.getAccountId());
        return true;
    }

    private FundHolding resolveHoldingForTrade(Long userId, TradeRecordRequest request, TradeType tradeType) {
        if (request.holdingId() != null) {
            FundHolding holding = ensureHoldingOwned(userId, request.holdingId());
            if (!holding.getAccountId().equals(request.accountId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "holding does not belong to the selected account");
            }
            return holding;
        }
        FundHolding existing = fundHoldingMapper.selectOne(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId)
                .eq(FundHolding::getAccountId, request.accountId())
                .eq(FundHolding::getFundCode, request.fundCode().trim())
                .last("LIMIT 1"));
        if (existing != null || isIncreaseTrade(tradeType)) {
            return existing;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "holding is required for sell or convert-out trade");
    }

    private void createRegularInvestTrade(InvestmentPlan plan, LocalDate runDate) {
        if (plan.getAmount() == null || plan.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "regular investment amount must be greater than zero");
        }
        if (planAlreadyGenerated(plan, runDate)) {
            return;
        }
        FundHolding holding = findHolding(plan.getUserId(), plan.getAccountId(), plan.getFundCode());
        LocalDateTime now = LocalDateTime.now();
        TradeRecord record = new TradeRecord();
        record.setUserId(plan.getUserId());
        record.setAccountId(plan.getAccountId());
        record.setHoldingId(holding == null ? null : holding.getId());
        record.setFundCode(plan.getFundCode());
        record.setFundName(plan.getFundName());
        record.setTradeType(TradeType.REGULAR_INVEST.name());
        record.setTradeStatus(TradeStatus.PROCESSING.name());
        record.setTradeAmount(valueOrZero(plan.getAmount()));
        record.setTradeShare(null);
        record.setTradeNav(null);
        record.setTradeFee(ZERO);
        record.setTradeTime(runDate.atTime(14, 59));
        record.setRelatedTradeId(null);
        record.setRemark(simulatedRemark("定投计划#" + plan.getId() + " " + runDate));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(0);
        tradeRecordMapper.insert(record);
    }

    private boolean planAlreadyGenerated(InvestmentPlan plan, LocalDate runDate) {
        TradeRecord existing = tradeRecordMapper.selectOne(new LambdaQueryWrapper<TradeRecord>()
                .eq(TradeRecord::getUserId, plan.getUserId())
                .eq(TradeRecord::getAccountId, plan.getAccountId())
                .eq(TradeRecord::getFundCode, plan.getFundCode())
                .eq(TradeRecord::getTradeType, TradeType.REGULAR_INVEST.name())
                .like(TradeRecord::getRemark, "定投计划#" + plan.getId() + " " + runDate)
                .last("LIMIT 1"));
        return existing != null;
    }

    private LocalDate nextPlanExecuteDate(InvestmentPlan plan, LocalDate runDate) {
        String frequency = plan.getFrequency() == null ? "WEEKLY" : plan.getFrequency().trim().toUpperCase();
        if ("DAILY".equals(frequency)) {
            return nextDailyPlanExecuteDate(plan, runDate);
        }
        LocalDate next = switch (frequency) {
            case "BIWEEKLY", "EVERY_TWO_WEEKS" -> runDate.plusWeeks(2);
            case "MONTHLY" -> runDate.plusMonths(1);
            default -> runDate.plusWeeks(1);
        };
        while (!tradingCalendarService.isTradingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    private LocalDate nextDailyPlanExecuteDate(InvestmentPlan plan, LocalDate runDate) {
        int tradingDays = isOverseasT2Fund(plan.getFundCode(), plan.getFundName(), null) ? 2 : 1;
        LocalDate next = runDate;
        for (int index = 0; index < tradingDays; index++) {
            next = tradingCalendarService.nextTradingDay(next);
        }
        return next;
    }

    private TradeSettlement tradeSettlement(TradeRecord record) {
        LocalDate applicationDate = applicationDate(record.getTradeTime());
        int delayDays = settlementDelayTradingDays(record);
        LocalDate navDate = applicationDate;
        LocalDate settleDate = applicationDate;
        for (int index = 0; index < delayDays; index++) {
            settleDate = tradingCalendarService.nextTradingDay(settleDate);
        }
        return new TradeSettlement(applicationDate, navDate, settleDate);
    }

    private LocalDate applicationDate(LocalDateTime tradeTime) {
        LocalDateTime time = tradeTime == null ? LocalDateTime.now() : tradeTime;
        LocalDate date = time.toLocalDate();
        if (!tradingCalendarService.isTradingDay(date) || time.toLocalTime().isAfter(LocalTime.of(15, 0))) {
            return tradingCalendarService.nextTradingDay(date);
        }
        return date;
    }

    private int settlementDelayTradingDays(TradeRecord record) {
        return isOverseasT2Fund(record.getFundCode(), record.getFundName(), null) ? 2 : 1;
    }

    private boolean isOverseasT2Fund(String fundCode, String fundName, String fundType) {
        String code = fundCode == null ? "" : fundCode.trim();
        String text = ((fundName == null ? "" : fundName) + " " + (fundType == null ? "" : fundType)).toUpperCase();
        if ("012922".equals(code) || text.contains("易方达全球精选") || text.contains("全球精选")) {
            return true;
        }
        if (text.contains("恒生") || text.contains("港股") || text.contains("香港") || text.contains("HSTECH")) {
            return false;
        }
        return text.contains("QDII")
                && (text.contains("全球") || text.contains("海外") || text.contains("纳斯达克")
                || text.contains("标普") || text.contains("美国") || text.contains("美股"));
    }

    private Optional<BigDecimal> officialNav(String fundCode, LocalDate navDate) {
        try {
            return fundQueryService.getHistoricalNav(fundCode, navDate.minusDays(10), navDate.plusDays(1)).stream()
                    .filter(point -> navDate.equals(point.navDate()))
                    .filter(point -> point.unitNav() != null)
                    .map(FundNavPointDTO::unitNav)
                    .findFirst();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private BigDecimal resolveConfirmedShare(TradeRecord record, FundHolding holding, BigDecimal tradeNav) {
        BigDecimal explicitShare = valueOrZero(record.getTradeShare());
        if (explicitShare.compareTo(BigDecimal.ZERO) > 0) {
            return explicitShare;
        }
        TradeType tradeType = TradeType.valueOf(record.getTradeType());
        if ((tradeType == TradeType.SELL || tradeType == TradeType.CONVERT_OUT)
                && holding != null
                && valueOrZero(record.getTradeAmount()).compareTo(valueOrZero(holding.getHoldingAmount())) >= 0) {
            return valueOrZero(holding.getHoldingShare());
        }
        if (tradeNav.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return valueOrZero(record.getTradeAmount()).divide(tradeNav, 4, RoundingMode.HALF_UP);
    }

    private FundHolding findHolding(Long userId, Long accountId, String fundCode) {
        return fundHoldingMapper.selectOne(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId)
                .eq(FundHolding::getAccountId, accountId)
                .eq(FundHolding::getFundCode, fundCode == null ? null : fundCode.trim())
                .last("LIMIT 1"));
    }

    private TradeRecordRequest convertOutRequest(ConvertPairTradeRequest request, TradeStatus status, FundHolding outHolding) {
        return new TradeRecordRequest(
                request.accountId(),
                request.outHoldingId(),
                outHolding.getFundCode(),
                outHolding.getFundName(),
                TradeType.CONVERT_OUT,
                status,
                request.outTradeAmount(),
                request.outTradeShare(),
                request.outTradeNav(),
                request.outTradeFee(),
                request.tradeTime(),
                null,
                request.remark()
        );
    }

    private TradeRecordRequest convertInRequest(ConvertPairTradeRequest request, TradeStatus status, Long relatedTradeId) {
        return new TradeRecordRequest(
                request.accountId(),
                request.inHoldingId(),
                request.inFundCode(),
                request.inFundName(),
                TradeType.CONVERT_IN,
                status,
                request.inTradeAmount(),
                request.inTradeShare(),
                request.inTradeNav(),
                request.inTradeFee(),
                request.tradeTime(),
                relatedTradeId,
                request.remark()
        );
    }

    private void validateRelatedTrade(Long userId, TradeRecordRequest request, TradeType tradeType) {
        if (request.relatedTradeId() == null) {
            return;
        }
        if (tradeType != TradeType.CONVERT_IN) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "related trade is only allowed for convert-in trade");
        }
        TradeRecord related = tradeRecordMapper.selectOne(new LambdaQueryWrapper<TradeRecord>()
                .eq(TradeRecord::getId, request.relatedTradeId())
                .eq(TradeRecord::getUserId, userId)
                .eq(TradeRecord::getAccountId, request.accountId())
                .eq(TradeRecord::getTradeType, TradeType.CONVERT_OUT.name())
                .last("LIMIT 1"));
        if (related == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "related convert-out trade not found");
        }
    }

    private void applyCompletedTrade(Long userId, TradeRecord record, FundHolding holding) {
        if (isIncreaseTrade(TradeType.valueOf(record.getTradeType()))) {
            FundHolding target = holding == null ? createHoldingFromTrade(userId, record) : holding;
            increaseHolding(target, record);
            fundHoldingMapper.updateById(target);
            record.setHoldingId(target.getId());
            tradeRecordMapper.updateById(record);
            return;
        }
        decreaseHolding(holding, record);
        fundHoldingMapper.updateById(holding);
    }

    private FundHolding createHoldingFromTrade(Long userId, TradeRecord record) {
        LocalDateTime now = LocalDateTime.now();
        FundHolding holding = new FundHolding();
        holding.setUserId(userId);
        holding.setAccountId(record.getAccountId());
        holding.setFundCode(record.getFundCode());
        holding.setFundName(record.getFundName());
        holding.setFundType(FundType.UNKNOWN.name());
        holding.setActiveFund(0);
        holding.setHoldingAmount(ZERO);
        holding.setHoldingShare(ZERO);
        holding.setHoldingCost(ZERO);
        holding.setCurrentEstimateNav(record.getTradeNav());
        holding.setLatestOfficialNav(record.getTradeNav());
        holding.setHoldingProfit(ZERO);
        holding.setHoldingProfitRate(ZERO);
        holding.setDailyProfit(ZERO);
        holding.setHoldingDays(0);
        holding.setSourcePlatform("MANUAL");
        holding.setRegularInvestment(0);
        holding.setCoreHolding(0);
        holding.setWatchFocus(0);
        holding.setCreateTime(now);
        holding.setUpdateTime(now);
        holding.setDeleted(0);
        fundHoldingMapper.insert(holding);
        return holding;
    }

    private void increaseHolding(FundHolding holding, TradeRecord record) {
        BigDecimal share = valueOrZero(record.getTradeShare());
        if (share.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "trade share or trade nav is required for buy or convert-in trade");
        }
        BigDecimal costIncrease = valueOrZero(record.getTradeAmount()).add(valueOrZero(record.getTradeFee()));
        holding.setHoldingShare(valueOrZero(holding.getHoldingShare()).add(share));
        holding.setHoldingCost(valueOrZero(holding.getHoldingCost()).add(costIncrease));
        if (record.getTradeNav() != null) {
            holding.setCurrentEstimateNav(record.getTradeNav());
            holding.setLatestOfficialNav(record.getTradeNav());
        }
        holding.setHoldingAmount(estimateAmount(holding, valueOrZero(holding.getHoldingAmount()).add(record.getTradeAmount())));
        recalculateHolding(holding);
    }

    private void decreaseHolding(FundHolding holding, TradeRecord record) {
        if (holding == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "holding is required for sell or convert-out trade");
        }
        BigDecimal oldShare = valueOrZero(holding.getHoldingShare());
        BigDecimal tradeShare = valueOrZero(record.getTradeShare());
        if (tradeShare.compareTo(BigDecimal.ZERO) <= 0 && record.getTradeNav() != null && record.getTradeNav().compareTo(BigDecimal.ZERO) > 0) {
            tradeShare = valueOrZero(record.getTradeAmount()).divide(record.getTradeNav(), 4, RoundingMode.HALF_UP);
        }
        if (tradeShare.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "trade share or trade nav is required for sell or convert-out trade");
        }
        if (oldShare.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "holding share is not enough for sell or convert-out trade");
        }
        if (tradeShare.compareTo(BigDecimal.ZERO) > 0 && oldShare.compareTo(BigDecimal.ZERO) > 0 && tradeShare.compareTo(oldShare) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "trade share cannot exceed holding share");
        }

        BigDecimal costReduction = costReduction(holding, record, oldShare, tradeShare);
        holding.setHoldingShare(maxZero(oldShare.subtract(tradeShare)));
        holding.setHoldingCost(maxZero(valueOrZero(holding.getHoldingCost()).subtract(costReduction)));
        if (record.getTradeNav() != null) {
            holding.setCurrentEstimateNav(record.getTradeNav());
            holding.setLatestOfficialNav(record.getTradeNav());
        }
        holding.setHoldingAmount(maxZero(valueOrZero(holding.getHoldingAmount()).subtract(valueOrZero(record.getTradeAmount()))));
        recalculateHolding(holding);
    }

    private BigDecimal costReduction(FundHolding holding, TradeRecord record, BigDecimal oldShare, BigDecimal tradeShare) {
        if (oldShare.compareTo(BigDecimal.ZERO) > 0 && tradeShare.compareTo(BigDecimal.ZERO) > 0) {
            return valueOrZero(holding.getHoldingCost()).multiply(tradeShare).divide(oldShare, 4, RoundingMode.HALF_UP);
        }
        return valueOrZero(record.getTradeAmount());
    }

    private void recalculateHolding(FundHolding holding) {
        if (valueOrZero(holding.getHoldingShare()).compareTo(BigDecimal.ZERO) <= 0) {
            holding.setHoldingShare(ZERO);
            holding.setHoldingAmount(ZERO);
            holding.setHoldingCost(ZERO);
            holding.setHoldingProfit(ZERO);
            holding.setHoldingProfitRate(ZERO);
            holding.setDailyProfit(ZERO);
            holding.setUpdateTime(LocalDateTime.now());
            return;
        }
        BigDecimal amount = estimateAmount(holding, holding.getHoldingAmount());
        BigDecimal profit = amount.subtract(valueOrZero(holding.getHoldingCost()));
        holding.setHoldingAmount(amount);
        holding.setHoldingProfit(scale(profit));
        holding.setHoldingProfitRate(rate(profit, holding.getHoldingCost()));
        holding.setDailyProfit(ZERO);
        holding.setUpdateTime(LocalDateTime.now());
    }

    private BigDecimal estimateAmount(FundHolding holding, BigDecimal fallbackAmount) {
        BigDecimal nav = holding.getCurrentEstimateNav() != null ? holding.getCurrentEstimateNav() : holding.getLatestOfficialNav();
        if (nav != null && valueOrZero(holding.getHoldingShare()).compareTo(BigDecimal.ZERO) > 0) {
            return scale(valueOrZero(holding.getHoldingShare()).multiply(nav));
        }
        return valueOrZero(fallbackAmount);
    }

    private BigDecimal deriveShare(TradeRecordRequest request) {
        if (request.tradeShare() != null && request.tradeShare().compareTo(BigDecimal.ZERO) > 0) {
            return valueOrZero(request.tradeShare());
        }
        if (request.tradeNav() != null && request.tradeNav().compareTo(BigDecimal.ZERO) > 0) {
            return valueOrZero(request.tradeAmount()).divide(request.tradeNav(), 4, RoundingMode.HALF_UP);
        }
        return ZERO;
    }

    private boolean isIncreaseTrade(TradeType tradeType) {
        return tradeType == TradeType.BUY || tradeType == TradeType.REGULAR_INVEST || tradeType == TradeType.CONVERT_IN;
    }

    private boolean isDecreaseTrade(TradeType tradeType) {
        return tradeType == TradeType.SELL || tradeType == TradeType.CONVERT_OUT;
    }

    private TradeRecord loadOwnedTrade(Long userId, Long tradeId) {
        TradeRecord record = tradeRecordMapper.selectOne(new LambdaQueryWrapper<TradeRecord>()
                .eq(TradeRecord::getId, tradeId)
                .eq(TradeRecord::getUserId, userId)
                .last("LIMIT 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "trade record not found");
        }
        return record;
    }

    private void ensureAccountOwned(Long userId, Long accountId) {
        PortfolioAccount account = portfolioAccountMapper.selectOne(new LambdaQueryWrapper<PortfolioAccount>()
                .eq(PortfolioAccount::getId, accountId)
                .eq(PortfolioAccount::getUserId, userId)
                .last("LIMIT 1"));
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "portfolio account not found");
        }
    }

    private FundHolding ensureHoldingOwned(Long userId, Long holdingId) {
        FundHolding holding = fundHoldingMapper.selectOne(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getId, holdingId)
                .eq(FundHolding::getUserId, userId)
                .last("LIMIT 1"));
        if (holding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "fund holding not found");
        }
        return holding;
    }

    private void ensurePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "trade amount must be greater than zero");
        }
    }

    private TradeRecordVO toVO(TradeRecord record) {
        return new TradeRecordVO(
                record.getId(),
                record.getAccountId(),
                record.getHoldingId(),
                record.getFundCode(),
                record.getFundName(),
                record.getTradeType(),
                record.getTradeStatus(),
                valueOrZero(record.getTradeAmount()),
                record.getTradeShare(),
                record.getTradeNav(),
                valueOrZero(record.getTradeFee()),
                record.getTradeTime(),
                record.getRelatedTradeId(),
                record.getRemark(),
                record.getUpdateTime(),
                SystemConstants.SIMULATED_TRADE_NOTICE,
                SystemConstants.DISCLAIMER
        );
    }

    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal base = valueOrZero(denominator);
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return valueOrZero(numerator).multiply(ONE_HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO : scale(value);
    }

    private BigDecimal maxZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? ZERO : scale(value);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String simulatedRemark(String value) {
        String remark = trimToNull(value);
        if (remark == null) {
            return SystemConstants.SIMULATED_TRADE_NOTICE;
        }
        if (remark.contains(SystemConstants.SIMULATED_TRADE_NOTICE)) {
            return remark;
        }
        return remark + "，" + SystemConstants.SIMULATED_TRADE_NOTICE;
    }

    private record TradeSettlement(LocalDate applicationDate, LocalDate navDate, LocalDate settleDate) {
    }
}
