package com.lk.quantfund.service;

import static org.assertj.core.api.Assertions.*;
import com.lk.quantfund.entity.TradeRecord;
import com.lk.quantfund.exception.BusinessException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(FundCashServiceTest.Config.class)
class FundCashServiceTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:fundcash;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");return ds; }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean FundCashService service(JdbcTemplate jdbc) { return new FundCashService(jdbc); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired FundCashService service;
    @Autowired PlatformTransactionManager manager;
    private static BigDecimal b(String n) { return new BigDecimal(n); }
    @BeforeEach void setup() throws Exception {
        jdbc.execute("DROP ALL OBJECTS"); // Dedicated H2 database only.
        jdbc.execute("CREATE TABLE portfolio_account(id BIGINT PRIMARY KEY,user_id BIGINT, cash_amount DECIMAL(20,4),deleted INT DEFAULT 0,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE fund_holding(id BIGINT PRIMARY KEY,user_id BIGINT,account_id BIGINT,fund_code VARCHAR(20),fund_name VARCHAR(200),deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE trade_record(id BIGINT PRIMARY KEY,user_id BIGINT,account_id BIGINT,fund_code VARCHAR(20),trade_type VARCHAR(30),trade_status VARCHAR(30),trade_amount DECIMAL(20,4),trade_fee DECIMAL(20,4),deleted INT DEFAULT 0)");
        for(String sql:Files.readString(Path.of("../docs/sql/014_fund_cash.sql")).split(";")) if(!sql.isBlank()) jdbc.execute(sql);
        jdbc.update("INSERT INTO portfolio_account(id,user_id,cash_amount) VALUES(10,1,1000),(20,2,400)");
        jdbc.update("INSERT INTO fund_holding(id,user_id,account_id,fund_code,fund_name) VALUES(1,1,10,'000001','基金甲'),(2,1,10,'000002','基金乙'),(3,2,20,'000001','他人基金')");
    }
    private FundCashService.Snapshot allocate() {
        var s=service.get(1L,10L);
        return service.save(1L,10L,new FundCashService.Update(s.version(),b("100"),List.of(
            new FundCashService.Allocation("000001",b("600")),new FundCashService.Allocation("000002",b("300")))));
    }
    private BigDecimal balance(String code) { return service.get(1L,10L).funds().stream().filter(f->f.fundCode().equals(code)).findFirst().orElseThrow().balance(); }
    private TradeRecord trade(long id,String code,String type,String amount,String fee) {
        var t=new TradeRecord(); t.setId(id);t.setUserId(1L);t.setAccountId(10L);t.setFundCode(code);t.setFundName(code);
        t.setTradeType(type);t.setTradeStatus("COMPLETED");t.setTradeAmount(b(amount));t.setTradeFee(b(fee));return t;
    }
    @Test void retainsLegacyCashAndNeverReplaysHistory() {
        jdbc.update("INSERT INTO trade_record(id,user_id,account_id,fund_code,trade_type,trade_status,trade_amount,trade_fee) VALUES(1,1,10,'000001','SELL','COMPLETED',900,0)");
        var first=service.get(1L,10L);
        assertThat(first.total()).isEqualByComparingTo("1000");assertThat(first.unallocated()).isEqualByComparingTo("1000");
        assertThat(first.funds()).hasSize(2);assertThat(service.get(1L,10L)).isEqualTo(first);
        assertThat(allocate().total()).isEqualByComparingTo("1000");
    }
    @Test void buysSalesFeesAndIdempotencyBalanceExactly() {
        allocate();var buy=trade(11,"000001","BUY","100","1.5");
        assertThat(service.completed(buy)).isTrue();assertThat(service.completed(buy)).isFalse();
        assertThat(balance("000001")).isEqualByComparingTo("498.5");
        service.completed(trade(12,"000001","SELL","200","2"));
        service.completed(trade(13,"000002","REGULAR_INVEST","50","0.5"));
        assertThat(balance("000001")).isEqualByComparingTo("696.5");assertThat(balance("000002")).isEqualByComparingTo("249.5");
        assertThat(service.get(1L,10L).total()).isEqualByComparingTo("1046");
        assertThat(jdbc.queryForObject("SELECT cash_amount FROM portfolio_account WHERE id=10",BigDecimal.class)).isEqualByComparingTo("1046");
    }
    @Test void pendingTradesDoNotBookCashAndExposeAmounts() {
        allocate(); var t=trade(11,"000001","BUY","100","1"); t.setTradeStatus("PROCESSING");
        assertThat(service.completed(t)).isFalse();
        jdbc.update("INSERT INTO trade_record(id,user_id,account_id,fund_code,trade_type,trade_status,trade_amount,trade_fee) VALUES(11,1,10,'000001','BUY','PROCESSING',100,1)");
        assertThat(service.get(1L,10L).funds().getFirst().pendingBuy()).isEqualByComparingTo("101");
        jdbc.update("UPDATE trade_record SET deleted=1 WHERE id=11");
        assertThat(service.get(1L,10L).funds().getFirst().pendingBuy()).isZero();assertThat(balance("000001")).isEqualByComparingTo("600");
    }
    @Test void removedAndNewHoldingsDoNotLoseBalances() {
        allocate(); jdbc.update("UPDATE fund_holding SET deleted=1 WHERE id=1");
        assertThat(service.get(1L,10L).funds().stream().filter(f->f.fundCode().equals("000001")).findFirst().orElseThrow().archived()).isTrue();
        service.completed(trade(21,"000001","SELL","99","1"));assertThat(balance("000001")).isEqualByComparingTo("698");
        jdbc.update("INSERT INTO fund_holding(id,user_id,account_id,fund_code,fund_name) VALUES(4,1,10,'000003','新基金')");
        assertThat(service.get(1L,10L).funds()).hasSize(3);assertThat(balance("000003")).isZero();
    }
    @Test void staleEditorAndCrossUserWritesAreRejected() {
        var s=allocate(); service.completed(trade(11,"000001","BUY","100","0"));
        var req=new FundCashService.Update(s.version(),b("100"),List.of(new FundCashService.Allocation("000001",b("600")),new FundCashService.Allocation("000002",b("300"))));
        assertThatThrownBy(()->service.save(1L,10L,req)).isInstanceOf(BusinessException.class).hasMessageContaining("已更新");
        assertThatThrownBy(()->service.get(2L,10L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.save(2L,10L,req)).isInstanceOf(BusinessException.class);
        assertThat(balance("000001")).isEqualByComparingTo("500");
    }
    @Test void incompleteAndDuplicateFundListsRejected() {
        var s=allocate();
        var row=new FundCashService.Allocation("000001",b("600"));
        assertThatThrownBy(()->service.save(1L,10L,new FundCashService.Update(s.version(),b("0"),List.of(row)))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.save(1L,10L,new FundCashService.Update(s.version(),b("0"),List.of(row,row)))).isInstanceOf(BusinessException.class);
        assertThat(service.get(1L,10L).total()).isEqualByComparingTo("1000");
    }
    @Test void failedHoldingTransactionRollsBackCashAndAllowsRetry() {
        allocate(); var t=trade(11,"000001","BUY","100","1");
        assertThatThrownBy(()->new TransactionTemplate(manager).execute(status->{service.completed(t);throw new IllegalStateException("holding failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(balance("000001")).isEqualByComparingTo("600");assertThat(service.completed(t)).isTrue();assertThat(balance("000001")).isEqualByComparingTo("499");
    }
    @Test void deficitsArePreservedRatherThanClampedOrTakenFromOtherFunds() {
        allocate();service.completed(trade(11,"000001","BUY","700","1"));
        assertThat(balance("000001")).isEqualByComparingTo("-101");assertThat(balance("000002")).isEqualByComparingTo("300");
        assertThat(service.get(1L,10L).total()).isEqualByComparingTo("299");
    }
    @Test void linkedConversionUsesSourceProceedsAndChargesFeesOnce() {
        allocate();service.completed(trade(11,"000001","CONVERT_OUT","200","2"));
        jdbc.update("INSERT INTO trade_record(id,user_id,account_id,fund_code,trade_type,trade_status,trade_amount,trade_fee) VALUES(11,1,10,'000001','CONVERT_OUT','COMPLETED',200,2)");
        var in=trade(12,"000002","CONVERT_IN","197","1");in.setRelatedTradeId(11L);
        service.completed(in);service.completed(in);
        assertThat(balance("000001")).isEqualByComparingTo("600");assertThat(balance("000002")).isEqualByComparingTo("300");
        assertThat(service.get(1L,10L).total()).isEqualByComparingTo("1000");
    }
    @Test void concurrentDuplicateConfirmationsBookOnce() throws Exception {
        allocate();var t=trade(99,"000001","BUY","100","0");
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=executor.submit(()->{start.await();return service.completed(t);});
            var b=executor.submit(()->{start.await();return service.completed(t);});start.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(balance("000001")).isEqualByComparingTo("500");
    }
}
