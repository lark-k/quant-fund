package com.lk.quantfund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.TradeRecord;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(CumulativeProfitServiceTest.Config.class)
class CumulativeProfitServiceTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:cumulative;MODE=MySQL;DB_CLOSE_DELAY=-1"); return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean CumulativeProfitService service(JdbcTemplate jdbc) { return new CumulativeProfitService(jdbc); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired CumulativeProfitService service;
    @Autowired PlatformTransactionManager transactions;
    private static final LocalDate BASE = LocalDate.of(2026, 9, 23);

    @BeforeEach void setup() throws Exception {
        jdbc.execute("DROP ALL OBJECTS"); // Dedicated in-memory test database only.
        jdbc.execute("CREATE TABLE fund_holding(id BIGINT PRIMARY KEY,user_id BIGINT,account_id BIGINT,fund_code VARCHAR(20),holding_share DECIMAL(20,8),holding_cost DECIMAL(20,8),latest_official_nav DECIMAL(20,8),holding_amount DECIMAL(20,8),holding_profit DECIMAL(20,8),daily_profit DECIMAL(20,8),deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE fund_nav_daily(fund_code VARCHAR(20),nav_date DATE,unit_nav DECIMAL(20,8),source_name VARCHAR(30),deleted INT DEFAULT 0,PRIMARY KEY(fund_code,nav_date))");
        jdbc.execute("CREATE TABLE trade_record(id BIGINT,holding_id BIGINT,user_id BIGINT,trade_status VARCHAR(20),deleted INT DEFAULT 0)");
        for (String sql : Files.readString(Path.of("../docs/sql/012_cumulative_profit.sql")).split(";")) if (!sql.isBlank()) jdbc.execute(sql);
    }

    @Test void initialBalancesAndOfficialOnlyUpdatesAreIdempotent() {
        FundHolding h = holding(1, "A", "1000", "1000");
        nav("A", BASE, "1.1"); service.capture(h); seed("A", "40.65");
        jdbc.update("UPDATE cumulative_profit_user SET opening_history=-232.13 WHERE user_id=1");
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("-191.48");
        Map<String,Object> legacy = jdbc.queryForMap("SELECT * FROM fund_holding WHERE id=1");
        // Intraday estimates and user-facing holding profit cannot change this book.
        h.setCurrentEstimateNav(b("5")); h.setHoldingProfit(b("99999"));
        assertThat(service.overview(1L).funds().getFirst().cumulativeProfit()).isEqualByComparingTo("40.65");
        nav("A", BASE.plusDays(1), "1.12");
        assertThat(service.overview(1L).funds().getFirst().cumulativeProfit()).isEqualByComparingTo("60.65");
        int events = jdbc.queryForObject("SELECT COUNT(*) FROM cumulative_profit_event", Integer.class);
        service.overview(1L); service.overview(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cumulative_profit_event", Integer.class)).isEqualTo(events);
        assertThat(jdbc.queryForMap("SELECT * FROM fund_holding WHERE id=1")).isEqualTo(legacy);
    }

    @Test void allSevenSeedsAndHistoricalCarrySumToConfirmedTotal() {
        String[] values = {"-15.10","40.65","-46.83","-93.59","-109.09","-174.10","-135.32"};
        for (int i=0;i<values.length;i++) { FundHolding h=holding(i+1,"F"+i,"100","100"); nav(h.getFundCode(),BASE,"1"); service.capture(h);seed(h.getFundCode(),values[i]); }
        jdbc.update("UPDATE cumulative_profit_user SET opening_history=-232.13 WHERE user_id=1");
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("-765.51");
        assertThat(service.overview(1L).historicalProfit()).isEqualByComparingTo("-232.13");
    }

    @Test void partialAndFullSaleRetainProfitAndDeductFeesOnce() {
        FundHolding h=holding(1,"A","1000","1000"); nav("A",BASE,"1.1"); service.capture(h);
        TradeRecord sell=trade(10,"SELL","500","550","1");
        service.completedTrade(h,sell,BASE); service.completedTrade(h,sell,BASE);
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("99");
        nav("A",BASE.plusDays(1),"1.2");
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("149");
        service.completedTrade(h,trade(11,"SELL","500","600","2"),BASE.plusDays(1));
        jdbc.update("UPDATE fund_holding SET holding_share=0,holding_cost=0,holding_profit=0 WHERE id=1");
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("147");
        nav("A",BASE.plusDays(2),"9");
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("147");
    }

    @Test void clearDeleteAndRebuyTransferHistoryWithoutDoubleCounting() {
        FundHolding h=holding(1,"A","1000","1000");nav("A",BASE,"1.1");service.capture(h);seed("A","40.65");
        jdbc.update("UPDATE cumulative_profit_user SET opening_history=-232.13 WHERE user_id=1");
        service.completedTrade(h,trade(10,"SELL","1000","1100","0"),BASE);
        service.archive(h);jdbc.update("UPDATE fund_holding SET deleted=1 WHERE id=1");
        var archived=service.overview(1L);
        assertThat(archived.totalCumulativeProfit()).isEqualByComparingTo("-191.48");
        assertThat(archived.historicalProfit()).isEqualByComparingTo("-191.48");
        FundHolding reopened=holding(2,"A","0","0");service.rebase(reopened);
        service.completedTrade(reopened,trade(11,"BUY","100","110","0"),BASE);
        assertThat(service.overview(1L).historicalProfit()).isEqualByComparingTo("-232.13");
        nav("A",BASE.plusDays(1),"1.2");
        assertThat(service.overview(1L).funds().getFirst().cumulativeProfit()).isEqualByComparingTo("50.65");
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("-181.48");
    }

    @Test void delayedNavRefreshesImmediatelyAndOldDatesNeverRegressTheBook() {
        FundHolding h=holding(1,"012922","100","100");nav(h.getFundCode(),BASE,"1");service.capture(h);seed(h.getFundCode(),"-109.09");
        nav(h.getFundCode(),BASE.plusDays(1),"1.1");
        var updated=service.overview(1L).funds().getFirst();
        assertThat(updated.officialNavDate()).isEqualTo(BASE.plusDays(1));
        assertThat(updated.cumulativeProfit()).isEqualByComparingTo("-99.09");
        jdbc.update("DELETE FROM fund_nav_daily WHERE nav_date>?",java.sql.Date.valueOf(BASE));
        assertThat(service.overview(1L).funds().getFirst().cumulativeProfit()).isEqualByComparingTo("-99.09");
    }

    @Test void manualProfitAndShareCorrectionsAreNeutralAndFutureDailyProfitUsesNewExposure() {
        FundHolding h=holding(1,"A","100","100");nav("A",BASE,"1");service.capture(h);seed("A","-15.10");
        h.setHoldingShare(b("200"));h.setHoldingCost(b("1"));h.setHoldingProfit(b("199"));service.rebase(h);
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("-15.10");
        nav("A",BASE.plusDays(1),"1.1");
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("4.90");
    }

    @Test void confirmedBuyWaitsForApplicableNavAndAccountsForFees() {
        FundHolding h=holding(1,"A","100","100");nav("A",BASE,"1");service.capture(h);
        service.completedTrade(h,trade(1,"BUY","100","120","2"),BASE.plusDays(1));
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("-2");
        nav("A",BASE.plusDays(1),"1.2");
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("18");
        service.overview(1L);
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("18");
    }

    @Test void laterSettlementAtOlderNavDoesNotRevalueAlreadyMarkedRemainingShares() {
        FundHolding h=holding(1,"A","1000","1000");nav("A",BASE,"1");service.capture(h);
        nav("A",BASE.plusDays(2),"1.2");service.overview(1L);
        service.completedTrade(h,trade(1,"SELL","500","550","0"),BASE.plusDays(1));
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("150");
    }

    @Test void pendingSaleAfterDeleteStillSettlesIntoHistory() {
        FundHolding h=holding(1,"A","100","100");nav("A",BASE,"1");service.capture(h);
        jdbc.update("INSERT INTO trade_record(id,holding_id,user_id,trade_status) VALUES(1,1,1,'PROCESSING')");
        service.archive(h);jdbc.update("UPDATE fund_holding SET deleted=1 WHERE id=1");
        service.completedTrade(h,trade(1,"SELL","100","110","1"),BASE.plusDays(1));
        assertThat(service.overview(1L).historicalProfit()).isEqualByComparingTo("9");
        nav("A",BASE.plusDays(2),"9");
        assertThat(service.overview(1L).historicalProfit()).isEqualByComparingTo("9");
    }

    @Test void navCorrectionsReplaceOnlyTheDifference() {
        FundHolding h=holding(1,"A","100","100");nav("A",BASE,"1");service.capture(h);
        nav("A",BASE.plusDays(1),"1.1");service.overview(1L);
        jdbc.update("UPDATE fund_nav_daily SET unit_nav=1.11 WHERE nav_date=?",java.sql.Date.valueOf(BASE.plusDays(1)));
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("11");
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("11");
    }

    @Test void accountsAndUsersNeverShareBalances() {
        FundHolding h=holding(1,"A","100","100");nav("A",BASE,"1.1");service.capture(h);
        FundHolding other=holding(2,"A","0","0"); other.setUserId(2L);other.setAccountId(2L);
        jdbc.update("UPDATE fund_holding SET user_id=2,account_id=2 WHERE id=2");service.rebase(other);
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("10");
        assertThat(service.overview(2L).totalCumulativeProfit()).isEqualByComparingTo("0");
    }

    @Test void rollbackLeavesNoPartiallyPostedTrade() {
        FundHolding h=holding(1,"A","100","100");nav("A",BASE,"1");service.capture(h);
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            service.completedTrade(h,trade(1,"SELL","100","110","0"),BASE);
            throw new IllegalStateException("simulate downstream transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cumulative_profit_event WHERE event_key='trade:1'",Integer.class)).isZero();
    }

    @Test void fractionalNavAndSharesDoNotPostRoundingDustOnEveryRefresh() {
        FundHolding h=holding(1,"A","772.5342","2300"); nav("A",BASE,"2.974301"); service.capture(h);
        nav("A",BASE.plusDays(1),"2.981357"); service.overview(1L);
        int events=jdbc.queryForObject("SELECT COUNT(*) FROM cumulative_profit_event",Integer.class);
        var total=service.overview(1L).totalCumulativeProfit();
        for(int i=0;i<5;i++) assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo(total);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cumulative_profit_event",Integer.class)).isEqualTo(events);
    }

    @Test void mockAndFutureNavsCannotBecomeOfficialProfit() {
        FundHolding h=holding(1,"A","100","100");nav("A",BASE,"1");service.capture(h);
        nav("A",BASE.plusDays(1),"9");jdbc.update("UPDATE fund_nav_daily SET source_name='MOCK_FALLBACK' WHERE nav_date=?",java.sql.Date.valueOf(BASE.plusDays(1)));
        nav("A",LocalDate.now().plusDays(1),"8");
        assertThat(service.overview(1L).totalCumulativeProfit()).isEqualByComparingTo("0");
    }

    private FundHolding holding(long id,String code,String shares,String cost) {
        FundHolding h=new FundHolding();h.setId(id);h.setUserId(1L);h.setAccountId(1L);h.setFundCode(code);
        h.setHoldingShare(b(shares));h.setHoldingCost(b(cost));h.setLatestOfficialNav(b("1"));
        jdbc.update("INSERT INTO fund_holding(id,user_id,account_id,fund_code,holding_share,holding_cost,latest_official_nav) VALUES(?,1,1,?,?,?,1)",id,code,b(shares),b(cost));return h;
    }
    private void seed(String code,String profit){jdbc.update("UPDATE cumulative_profit_fund SET profit=? WHERE user_id=1 AND account_id=1 AND fund_code=?",b(profit),code);}
    private void nav(String code,LocalDate date,String nav){jdbc.update("INSERT INTO fund_nav_daily(fund_code,nav_date,unit_nav) VALUES(?,?,?)",code,java.sql.Date.valueOf(date),b(nav));}
    private TradeRecord trade(long id,String type,String shares,String amount,String fee){
        TradeRecord t=new TradeRecord();t.setId(id);t.setTradeType(type);t.setTradeShare(b(shares));t.setTradeAmount(b(amount));t.setTradeFee(b(fee));t.setTradeStatus("COMPLETED");return t;
    }
    private static BigDecimal b(String value){return new BigDecimal(value);}
}
