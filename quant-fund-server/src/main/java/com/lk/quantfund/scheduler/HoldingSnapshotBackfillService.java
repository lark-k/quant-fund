package com.lk.quantfund.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldingSnapshotBackfillService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");

    private final FundHoldingMapper fundHoldingMapper;
    private final PortfolioAccountMapper portfolioAccountMapper;
    private final HoldingSnapshotMapper holdingSnapshotMapper;

    public HoldingSnapshotBackfillService(FundHoldingMapper fundHoldingMapper,
                                          PortfolioAccountMapper portfolioAccountMapper,
                                          HoldingSnapshotMapper holdingSnapshotMapper) {
        this.fundHoldingMapper = fundHoldingMapper;
        this.portfolioAccountMapper = portfolioAccountMapper;
        this.holdingSnapshotMapper = holdingSnapshotMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureRecentSnapshots(Long userId) {
        if (userId == null) {
            return;
        }
        ensureSnapshot(userId, LocalDate.now().minusDays(1));
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
            if (!holdingExistedOn(holding, snapshotDate) || snapshotExists(holding.getId(), snapshotDate)) {
                continue;
            }
            PortfolioAccount account = portfolioAccountMapper.selectById(holding.getAccountId());
            if (account == null) {
                continue;
            }
            HoldingSnapshot snapshot = new HoldingSnapshot();
            snapshot.setUserId(holding.getUserId());
            snapshot.setAccountId(holding.getAccountId());
            snapshot.setHoldingId(holding.getId());
            snapshot.setSnapshotDate(snapshotDate);
            snapshot.setTotalAsset(scale(account.getTotalAsset()));
            snapshot.setHoldingAmount(scale(holding.getHoldingAmount()));
            snapshot.setHoldingProfit(scale(holding.getHoldingProfit()));
            snapshot.setDailyProfit(scale(holding.getDailyProfit()));
            snapshot.setPositionRate(positionRate(holding.getHoldingAmount(), account.getTotalAsset()));
            snapshot.setCreateTime(now);
            snapshot.setUpdateTime(now);
            snapshot.setDeleted(0);
            holdingSnapshotMapper.insert(snapshot);
        }
    }

    private boolean holdingExistedOn(FundHolding holding, LocalDate snapshotDate) {
        if (holding.getCreateTime() == null) {
            return true;
        }
        return !holding.getCreateTime().toLocalDate().isAfter(snapshotDate);
    }

    private boolean snapshotExists(Long holdingId, LocalDate snapshotDate) {
        Long count = holdingSnapshotMapper.selectCount(new LambdaQueryWrapper<HoldingSnapshot>()
                .eq(HoldingSnapshot::getHoldingId, holdingId)
                .eq(HoldingSnapshot::getSnapshotDate, snapshotDate));
        return count != null && count > 0;
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
}
