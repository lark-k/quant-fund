package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.PortfolioAccount;
import com.lk.quantfund.entity.PortfolioIntradaySnapshot;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.mapper.PortfolioAccountMapper;
import com.lk.quantfund.mapper.PortfolioIntradaySnapshotMapper;
import com.lk.quantfund.scheduler.TradingCalendarService;
import com.lk.quantfund.service.valuation.FundValuationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PortfolioAccountServiceImplTest {

    private final PortfolioAccountMapper portfolioAccountMapper = mock(PortfolioAccountMapper.class);
    private final FundHoldingMapper fundHoldingMapper = mock(FundHoldingMapper.class);
    private final FundNavDailyMapper fundNavDailyMapper = mock(FundNavDailyMapper.class);
    private final PortfolioIntradaySnapshotMapper portfolioIntradaySnapshotMapper = mock(PortfolioIntradaySnapshotMapper.class);
    private final FundValuationService fundValuationService = mock(FundValuationService.class);
    private final TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);

    @BeforeEach
    void setUp() {
        initTableInfo(PortfolioAccount.class);
        initTableInfo(FundHolding.class);
        initTableInfo(PortfolioIntradaySnapshot.class);
    }

    @Test
    void recalculateShouldUpsertIntradaySnapshotByUserAndMinute() {
        PortfolioAccount account = account(10L, "1000.0000", "12.0000");
        when(portfolioAccountMapper.selectOne(any())).thenReturn(account);
        when(fundHoldingMapper.selectList(any())).thenReturn(List.of(holding()));
        when(tradingCalendarService.isIntradayEstimateWindow(any(LocalDateTime.class))).thenReturn(true);
        when(portfolioAccountMapper.selectList(any())).thenReturn(List.of(account));
        PortfolioAccountServiceImpl service = service();
        ArgumentCaptor<PortfolioIntradaySnapshot> snapshotCaptor = ArgumentCaptor.forClass(PortfolioIntradaySnapshot.class);

        assertThatCode(() -> service.recalculateOwnedAccount(4L, 10L))
                .doesNotThrowAnyException();

        verify(portfolioIntradaySnapshotMapper).upsertByUserAndSnapshotTime(snapshotCaptor.capture());
        PortfolioIntradaySnapshot snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.getUserId()).isEqualTo(4L);
        assertThat(snapshot.getSnapshotTime().getSecond()).isZero();
        assertThat(snapshot.getSnapshotTime().getNano()).isZero();
        assertThat(snapshot.getTotalAsset()).isEqualByComparingTo("1000.0000");
        assertThat(snapshot.getDailyProfit()).isEqualByComparingTo("12.0000");
        assertThat(snapshot.getDailyProfitRate()).isEqualByComparingTo("1.2000");
    }

    @Test
    void recalculateShouldIncludeCashWithoutChangingHoldingProfitMetrics() {
        PortfolioAccount account = account(10L, "1000.0000", "12.0000");
        account.setCashAmount(new BigDecimal("250.0000"));
        when(portfolioAccountMapper.selectOne(any())).thenReturn(account);
        when(fundHoldingMapper.selectList(any())).thenReturn(List.of(holding()));
        when(tradingCalendarService.isIntradayEstimateWindow(any(LocalDateTime.class))).thenReturn(false);
        PortfolioAccountServiceImpl service = service();
        ArgumentCaptor<PortfolioAccount> accountCaptor = ArgumentCaptor.forClass(PortfolioAccount.class);

        service.recalculateOwnedAccount(4L, 10L);

        verify(portfolioAccountMapper).updateById(accountCaptor.capture());
        PortfolioAccount saved = accountCaptor.getValue();
        assertThat(saved.getTotalAsset()).isEqualByComparingTo("1250.0000");
        assertThat(saved.getCashAmount()).isEqualByComparingTo("250.0000");
        assertThat(saved.getTotalInvestAmount()).isEqualByComparingTo("900.0000");
        assertThat(saved.getCurrentProfit()).isEqualByComparingTo("112.0000");
        assertThat(saved.getDailyProfit()).isEqualByComparingTo("12.0000");
        assertThat(saved.getCashPositionRate()).isEqualByComparingTo("20.0000");
        assertThat(saved.getEquityPositionRate()).isEqualByComparingTo("80.0000");
    }

    private PortfolioAccountServiceImpl service() {
        return new PortfolioAccountServiceImpl(
                portfolioAccountMapper,
                fundHoldingMapper,
                fundNavDailyMapper,
                portfolioIntradaySnapshotMapper,
                fundValuationService,
                tradingCalendarService
        );
    }

    private PortfolioAccount account(Long id, String totalAsset, String dailyProfit) {
        PortfolioAccount account = new PortfolioAccount();
        account.setId(id);
        account.setUserId(4L);
        account.setAccountName("test");
        account.setPlatformType("MANUAL");
        account.setTotalAsset(new BigDecimal(totalAsset));
        account.setTotalInvestAmount(new BigDecimal("900.0000"));
        account.setCurrentProfit(new BigDecimal("100.0000"));
        account.setCurrentProfitRate(new BigDecimal("11.1111"));
        account.setDailyProfit(new BigDecimal(dailyProfit));
        account.setCashPositionRate(BigDecimal.ZERO);
        account.setEquityPositionRate(BigDecimal.ZERO);
        account.setBondPositionRate(BigDecimal.ZERO);
        account.setMaxSingleFundPositionRate(BigDecimal.ZERO);
        account.setStatus("ENABLED");
        return account;
    }

    private FundHolding holding() {
        FundHolding holding = new FundHolding();
        holding.setId(23L);
        holding.setUserId(4L);
        holding.setAccountId(10L);
        holding.setFundCode("016874");
        holding.setFundName("test fund");
        holding.setFundType("INDEX");
        holding.setHoldingAmount(new BigDecimal("1000.0000"));
        holding.setHoldingCost(new BigDecimal("900.0000"));
        holding.setHoldingProfit(new BigDecimal("100.0000"));
        holding.setDailyProfit(new BigDecimal("12.0000"));
        return holding;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }
}
