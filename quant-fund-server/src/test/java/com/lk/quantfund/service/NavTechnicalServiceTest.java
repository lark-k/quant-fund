package com.lk.quantfund.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.datasource.model.FundBasicInfoDTO;
import com.lk.quantfund.dto.backtest.NavBacktestRequest;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.quant.QuantEngineClient;
import com.lk.quantfund.scheduler.TradingCalendarService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class NavTechnicalServiceTest {
    final FundHoldingMapper holdings = mock(FundHoldingMapper.class);
    final FundQueryService funds = mock(FundQueryService.class);
    final QuantEngineClient engine = mock(QuantEngineClient.class);
    final QuantFundProperties props = new QuantFundProperties();
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:nav"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    final NavTechnicalService service = new NavTechnicalService(holdings, funds, new TradingCalendarService(props), engine, props, json, jdbc);

    NavTechnicalServiceTest() {
        props.getQuantEngine().setEnabled(true);
        jdbc.execute("CREATE TABLE nav_technical_backtest(id VARCHAR(36) PRIMARY KEY,user_id BIGINT,holding_id BIGINT,fund_code VARCHAR(16),rule_version VARCHAR(40),created_at TIMESTAMP,result_json CLOB)");
    }
    NavBacktestRequest request() {
        return new NavBacktestRequest(LocalDate.of(2025,1,1), LocalDate.of(2025,12,31), new BigDecimal("10000"), new BigDecimal("25"), new BigDecimal("50"), new BigDecimal("0.15"), new BigDecimal("1.5"), new BigDecimal("0.5"), BigDecimal.ZERO,1,1,3,null,null);
    }
    void setupHolding() {
        FundHolding h = new FundHolding(); h.setId(3L); h.setUserId(7L); h.setAccountId(12L); h.setFundCode("021180"); h.setFundName("测试基金"); h.setHoldingShare(BigDecimal.TEN);
        when(holdings.selectOne(any(LambdaQueryWrapper.class))).thenReturn(h);
        when(funds.getBasicInfo("021180")).thenReturn(new FundBasicInfoDTO("021180","测试基金","MIXED",true,null,null,null,null,null,"TEST"));
        when(funds.getHistoricalNav(anyString(),any(),any())).thenReturn(List.of());
    }
    @Test void missingHoldingStopsBeforeAnyEngineOrDataAccess() {
        try (var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getUserId).thenReturn(7L);
            assertThatThrownBy(()->service.run(99,request())).isInstanceOf(BusinessException.class);
            verifyNoInteractions(funds,engine);
        }
    }
    @Test void resultPersistenceAndOwnerIsolation() {
        setupHolding();
        try (var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getUserId).thenReturn(7L);
            ObjectNode result=json.createObjectNode(); result.put("ruleVersion","NAV-TA v1"); result.putObject("metrics").put("tradeCount",2);
            when(engine.navTechnical(eq("backtest"),any())).thenReturn(result);
            var saved=service.run(3,request());
            assertThat(service.history(3)).hasSize(1);
            assertThat(service.result(3,saved.path("id").asText())).isEqualTo(saved);
            var body=ArgumentCaptor.forClass(Object.class); verify(engine).navTechnical(eq("backtest"),body.capture());
            assertThat(((ObjectNode)body.getValue()).path("fundType").asText()).isEqualTo("MIXED");
            verify(funds).getHistoricalNav("021180",LocalDate.of(2024,1,1),LocalDate.of(2025,12,31));
            context.when(UserContext::getUserId).thenReturn(8L);
            assertThat(service.history(3)).isEmpty();
            assertThatThrownBy(()->service.result(3,saved.path("id").asText())).isInstanceOf(BusinessException.class);
            context.when(UserContext::getUserId).thenReturn(7L);
            assertThatThrownBy(()->service.result(4,saved.path("id").asText())).isInstanceOf(BusinessException.class);
        }
    }
    @Test void analysisUsesSameEngineAndDoesNotSaveBacktests() {
        setupHolding();
        try (var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getUserId).thenReturn(7L);
            when(engine.navTechnical(eq("analyze"),any())).thenReturn(json.createObjectNode().put("action","WATCH"));
            assertThat(service.analyze(3).path("action").asText()).isEqualTo("WATCH");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM nav_technical_backtest",Integer.class)).isZero();
            verify(engine,never()).navTechnical(eq("backtest"),any());
            var body = ArgumentCaptor.forClass(Object.class);
            verify(engine).navTechnical(eq("analyze"), body.capture());
            var payload = (ObjectNode) body.getValue();
            assertThat(payload.path("hasHolding").asBoolean()).isTrue();
            assertThat(payload.path("tradingDates").isArray()).isTrue();
            assertThat(payload.has("cash")).isFalse();
        }
    }
    @Test void reviewedVersionAcceptedAndUnknownVersionRejected() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var approved = json.valueToTree(request());
            ((ObjectNode) approved).put("ruleVersion", "NAV-TA v3.1-trend");
            assertThat(validator.validate(json.convertValue(approved, NavBacktestRequest.class))).isEmpty();
            ((ObjectNode) approved).put("ruleVersion", "NAV-TA v9");
            assertThat(validator.validate(json.convertValue(approved, NavBacktestRequest.class))).isNotEmpty();
        }
    }
    @Test void analysisPassesOwnedFundSnapshotWithoutCallingAnyCashMutation() {
        setupHolding();
        var cash=mock(FundCashService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"fundCashService",cash);
        when(cash.analysisContext(7L,12L,"021180")).thenReturn(new FundCashService.AnalysisContext(
            new BigDecimal("123.45"),BigDecimal.TEN,new BigDecimal("20"),0,null,2,"2026-10-02T12:00:00"));
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getUserId).thenReturn(7L);
            when(engine.navTechnical(eq("analyze"),any())).thenReturn(json.createObjectNode());
            service.analyze(3);
            var body=ArgumentCaptor.forClass(Object.class);
            verify(engine).navTechnical(eq("analyze"),body.capture());
            assertThat(((ObjectNode)body.getValue()).path("execution").path("cashBalance").decimalValue()).isEqualByComparingTo("123.45");
            verify(cash).analysisContext(7L,12L,"021180");
            verifyNoMoreInteractions(cash);
        }
    }
    @Test void fundChangedDuringNavFetchCannotMixItsCashWithOldFundPrices() {
        setupHolding();
        var cash=mock(FundCashService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"fundCashService",cash);
        var initial=new FundHolding();initial.setId(3L);initial.setUserId(7L);initial.setAccountId(12L);initial.setFundCode("021180");
        var changed=new FundHolding();changed.setId(3L);changed.setUserId(7L);changed.setAccountId(12L);changed.setFundCode("016874");
        when(holdings.selectOne(any(LambdaQueryWrapper.class))).thenReturn(initial,changed);
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getUserId).thenReturn(7L);
            assertThatThrownBy(()->service.analyze(3)).isInstanceOf(BusinessException.class).hasMessageContaining("已变化");
            verifyNoInteractions(cash,engine);
        }
    }
}
