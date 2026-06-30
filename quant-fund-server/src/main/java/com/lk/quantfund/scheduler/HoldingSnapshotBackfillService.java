package com.lk.quantfund.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.HoldingSnapshotMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldingSnapshotBackfillService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");

    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final HoldingSnapshotMapper holdingSnapshotMapper;
    private final TradingCalendarService tradingCalendarService;
    private final QuantFundProperties properties;

    public HoldingSnapshotBackfillService(FundHoldingMapper fundHoldingMapper,
                                          PortfolioAccountMapper portfolioAccountMapper,
                                          HoldingSnapshotMapper holdingSnapshotMapper,
                                          TradingCalendarService tradingCalendarService,
                                          QuantFundProperties properties) {
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.holdingSnapshotMapper = holdingSnapshotMapper;
        this.tradingCalendarService = tradingCalendarService;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureRecentSnapshots(Long userId) {
        ensureRecentSnapshots(userId, LocalDate.now());
    }

    void ensureRecentSnapshots(Long userId, LocalDate today) {
        if (userId == null) {
            return;
        }
        int remainingTradingDays = properties.getScheduler().getSnapshotBackfillTradingDays();
        LocalDate cursor = today.minusDays(1);
        while (remainingTradingDays > 0) {
            if (tradingCalendarService.isTradingDay(cursor)) {
                ensureSnapshot(userId, cursor);
                remainingTradingDays--;
            }
            cursor = cursor.minusDays(1);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureSnapshot(Long userId, LocalDate snapshotDate) {
        if (userId == null || snapshotDate == null || !snapshotDate.isBefore(LocalDate.now())) {
            return;
        }
        List<FundHolding> holdings = fundHoldingMapper.selectList(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getUserId, userId)
                .orderByAsc(FundHolding::getFundCode));
        LocalDateTime now = LocalDateTime.now();
        for (FundHolding holding : holdings) {
            if (!holdingExistedOn(holding, snapshotDate)) {
                continue;
            }
            PortfolioAccount account = portfolioAccountMapper.selectById(holding.getAccountId());
            if (account == null) {
                continue;
            }
            upsertSnapshot(holding, account, snapshotDate, now);
        }
    }

    private boolean holdingExistedOn(FundHolding holding, LocalDate snapshotDate) {
        if (holding.getCreateTime() == null) {
            return true;
        }
        return !holding.getCreateTime().toLocalDate().isAfter(snapshotDate);
    }

    private BigDecimal positionRate(BigDecimal holdingAmount, BigDecimal totalAsset) {
        if (totalAsset == null || totalAsset.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return scale(holdingAmount).multiply(HUNDRED).divide(totalAsset, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }

    private void upsertSnapshot(FundHolding holding, PortfolioAccount account, LocalDate snapshotDate, LocalDateTime now) {
        HoldingSnapshot snapshot = holdingSnapshotMapper.selectOne(new LambdaQueryWrapper<HoldingSnapshot>()
                .eq(HoldingSnapshot::getHoldingId, holding.getId())
                .eq(HoldingSnapshot::getSnapshotDate, snapshotDate)
                .last("LIMIT 1"));
        if (snapshot == null) {
            snapshot = holdingSnapshotMapper.selectByHoldingAndDateIncludingDeleted(holding.getId(), snapshotDate);
        }
        if (snapshot != null) {
            return;
        }
        if (holding.getUpdateTime() != null && holding.getUpdateTime().toLocalDate().isAfter(snapshotDate)) {
            return;
        }
        snapshot = new HoldingSnapshot();
        snapshot.setCreateTime(now);
        snapshot.setDeleted(0);
        snapshot.setUserId(holding.getUserId());
        snapshot.setAccountId(holding.getAccountId());
        snapshot.setHoldingId(holding.getId());
        snapshot.setSnapshotDate(snapshotDate);
        snapshot.setTotalAsset(scale(account.getTotalAsset()));
        snapshot.setHoldingAmount(scale(holding.getHoldingAmount()));
        snapshot.setHoldingProfit(scale(holding.getHoldingProfit()));
        snapshot.setDailyProfit(scale(holding.getDailyProfit()));
        snapshot.setPositionRate(positionRate(holding.getHoldingAmount(), account.getTotalAsset()));
        snapshot.setUpdateTime(now);
        try {
            holdingSnapshotMapper.insert(snapshot);
        } catch (DuplicateKeyException exception) {
            // Another process created or deleted the historical snapshot first; never revive deleted history here.
        }
    }
}
