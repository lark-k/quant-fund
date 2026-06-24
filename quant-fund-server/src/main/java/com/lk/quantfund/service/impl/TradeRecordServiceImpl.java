package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.trade.TradeRecordRequest;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.enums.FundType;
import com.lk.quantfund.enums.TradeStatus;
import com.lk.quantfund.enums.TradeType;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.TradeRecordMapper;
import com.lk.quantfund.service.PortfolioAccountService;
import com.lk.quantfund.service.TradeRecordService;
import com.lk.quantfund.vo.trade.TradeRecordVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
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

    public TradeRecordServiceImpl(TradeRecordMapper tradeRecordMapper,
                                  FundHoldingMapper fundHoldingMapper,
                                  PortfolioAccountMapper portfolioAccountMapper,
                                  PortfolioAccountService portfolioAccountService) {
        this.tradeRecordMapper = tradeRecordMapper;
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.portfolioAccountService = portfolioAccountService;
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
        record.setRemark(trimToNull(request.remark()));
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
    public TradeRecordVO detail(Long tradeId) {
        return toVO(loadOwnedTrade(UserContext.getUserId(), tradeId));
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
}
