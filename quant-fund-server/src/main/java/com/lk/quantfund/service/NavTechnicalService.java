package com.lk.quantfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.dto.backtest.NavBacktestRequest;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.FundHoldingMapper;
import com.lk.quantfund.quant.QuantEngineClient;
import com.lk.quantfund.scheduler.TradingCalendarService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NavTechnicalService {
    private final FundHoldingMapper holdings;
    private final FundQueryService funds;
    private final TradingCalendarService calendar;
    private final QuantEngineClient engine;
    private final QuantFundProperties properties;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public NavTechnicalService(FundHoldingMapper holdings, FundQueryService funds, TradingCalendarService calendar,
                               QuantEngineClient engine, QuantFundProperties properties, ObjectMapper json, JdbcTemplate jdbc) {
        this.holdings = holdings; this.funds = funds; this.calendar = calendar; this.engine = engine;
        this.properties = properties; this.json = json; this.jdbc = jdbc;
    }

    private FundHolding owned(long id) {
        Long user = UserContext.getUserId();
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND, "持仓不存在");
        FundHolding holding = holdings.selectOne(new LambdaQueryWrapper<FundHolding>()
                .eq(FundHolding::getId, id).eq(FundHolding::getUserId, user));
        if (holding == null) throw new BusinessException(ErrorCode.NOT_FOUND, "持仓不存在或无权访问");
        return holding;
    }

    private ObjectNode payload(FundHolding holding, LocalDate start, LocalDate end) {
        if (!properties.getQuantEngine().isEnabled()) throw new BusinessException(ErrorCode.BAD_REQUEST, "量化引擎未启用");
        var info = funds.getBasicInfo(holding.getFundCode());
        ObjectNode body = json.createObjectNode();
        body.put("fundType", info.fundType());
        var rows = body.putArray("rows");
        // Existing datasource pagination and caching are reused, without strategy writes.
        for (var p : funds.getHistoricalNav(holding.getFundCode(), start, end)) {
            if (p.navDate().isBefore(start) || p.navDate().isAfter(end)) continue;
            ObjectNode row = rows.addObject();
            row.put("date", p.navDate().toString()); row.put("nav", p.unitNav());
            row.put("dailyGrowthRate", p.dailyGrowthRate()); row.put("sourceName", p.sourceName());
        }
        return body;
    }

    public JsonNode analyze(long holdingId) {
        FundHolding holding = owned(holdingId);
        LocalDateTime now = LocalDateTime.now(ZONE);
        ObjectNode body = payload(holding, now.toLocalDate().minusYears(2), now.toLocalDate());
        body.put("evaluatedAt", now.toString());
        body.put("trading", calendar.isTradingDay(now.toLocalDate()));
        body.put("hasHolding", (holding.getHoldingShare() != null && holding.getHoldingShare().signum() > 0)
                || (holding.getHoldingAmount() != null && holding.getHoldingAmount().signum() > 0));
        var dates = body.putArray("tradingDates");
        now.toLocalDate().minusYears(2).datesUntil(now.toLocalDate().plusDays(1))
                .filter(calendar::isTradingDay).forEach(day -> dates.add(day.toString()));
        return engine.navTechnical("analyze", body);
    }

    public JsonNode run(long holdingId, NavBacktestRequest request) {
        FundHolding holding = owned(holdingId);
        if (!request.startDate().isBefore(request.endDate()) || request.endDate().isAfter(LocalDate.now(ZONE))
                || request.startDate().plusYears(5).isBefore(request.endDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "回测区间须为过去 5 年以内的一段有效区间，截止日不能在未来");
        }
        ObjectNode body = payload(holding, request.startDate().minusYears(1), request.endDate());
        body.setAll((ObjectNode) json.valueToTree(request));
        body.put("startDate", request.startDate().toString());
        body.put("endDate", request.endDate().toString());
        body.put("fundCode", holding.getFundCode());
        var dates = body.putArray("tradingDates");
        request.startDate().datesUntil(request.endDate().plusDays(1))
                .filter(calendar::isTradingDay).forEach(day -> dates.add(day.toString()));
        JsonNode response = engine.navTechnical("backtest", body);
        if (!(response instanceof ObjectNode result) || !response.has("metrics"))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "回测引擎未返回有效结果");
        String id = UUID.randomUUID().toString(), created = LocalDateTime.now(ZONE).withNano(0).toString().replace('T', ' ');
        result.put("id", id); result.put("createdAt", created); result.put("fundName", holding.getFundName());
        jdbc.update("INSERT INTO nav_technical_backtest (id,user_id,holding_id,fund_code,rule_version,created_at,result_json) VALUES (?,?,?,?,?,?,?)",
                id, UserContext.getUserId(), holdingId, holding.getFundCode(), result.path("ruleVersion").asText(), created, result.toString());
        return result;
    }

    public List<Map<String, Object>> history(long holdingId) {
        owned(holdingId);
        return jdbc.query("SELECT id,rule_version,created_at FROM nav_technical_backtest WHERE user_id=? AND holding_id=? ORDER BY created_at DESC,id DESC LIMIT 30",
                (rs, row) -> Map.<String, Object>of("id", rs.getString(1), "ruleVersion", rs.getString(2), "createdAt", rs.getTimestamp(3).toLocalDateTime().toString().replace('T',' ')),
                UserContext.getUserId(), holdingId);
    }

    public JsonNode result(long holdingId, String id) {
        owned(holdingId);
        var rows = jdbc.query("SELECT result_json FROM nav_technical_backtest WHERE id=? AND user_id=? AND holding_id=?",
                (rs, row) -> rs.getString(1), id, UserContext.getUserId(), holdingId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "回测结果不存在或无权访问");
        try { return json.readTree(rows.getFirst()); }
        catch (Exception ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "回测结果读取失败"); }
    }
}
