package com.lk.quantfund.service.analytics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.FundHolding;
import com.lk.quantfund.entity.FundNavDaily;
import com.lk.quantfund.entity.HoldingSnapshot;
import com.lk.quantfund.mapper.FundNavDailyMapper;
import com.lk.quantfund.scheduler.TradingCalendarService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SnapshotProfitStatusResolver {

    private final FundNavDailyMapper fundNavDailyMapper;
    private final TradingCalendarService tradingCalendarService;

    @Autowired
    public SnapshotProfitStatusResolver(FundNavDailyMapper fundNavDailyMapper,
                                        TradingCalendarService tradingCalendarService) {
        this.fundNavDailyMapper = fundNavDailyMapper;
        this.tradingCalendarService = tradingCalendarService;
    }

    public SnapshotProfitStatusResolver(FundNavDailyMapper fundNavDailyMapper) {
        this(fundNavDailyMapper, new TradingCalendarService(new QuantFundProperties()));
    }

    public Map<LocalDate, ProfitStatus> resolve(List<HoldingSnapshot> snapshots,
                                                List<FundHolding> holdings,
                                                LocalDate startDate,
                                                LocalDate endDate) {
        Map<LocalDate, List<HoldingSnapshot>> snapshotsByDate = snapshots.stream()
                .filter(snapshot -> snapshot.getSnapshotDate() != null)
                .collect(Collectors.groupingBy(HoldingSnapshot::getSnapshotDate, LinkedHashMap::new, Collectors.toList()));
        Map<Long, String> fundCodeByHoldingId = holdings.stream()
                .filter(holding -> holding.getId() != null)
                .filter(holding -> holding.getFundCode() != null && !holding.getFundCode().isBlank())
                .collect(Collectors.toMap(FundHolding::getId, FundHolding::getFundCode, (left, right) -> left, LinkedHashMap::new));
        Map<String, FundHolding> holdingByFundCode = holdings.stream()
                .filter(holding -> holding.getFundCode() != null && !holding.getFundCode().isBlank())
                .collect(Collectors.toMap(FundHolding::getFundCode, holding -> holding, (left, right) -> left, LinkedHashMap::new));
        Map<LocalDate, Set<String>> officialNavByDate = officialNavByDate(
                holdingByFundCode,
                startDate,
                endDate
        );
        Map<LocalDate, ProfitStatus> statuses = new LinkedHashMap<>();
        snapshotsByDate.forEach((date, dateSnapshots) ->
                statuses.put(date, statusOf(date, dateSnapshots, fundCodeByHoldingId, officialNavByDate)));
        return statuses;
    }

    private Map<LocalDate, Set<String>> officialNavByDate(Map<String, FundHolding> holdingByFundCode,
                                                          LocalDate startDate,
                                                          LocalDate endDate) {
        if (holdingByFundCode.isEmpty()) {
            return Map.of();
        }
        LocalDate queryStart = startDate.minusDays(5);
        return fundNavDailyMapper.selectList(new LambdaQueryWrapper<FundNavDaily>()
                        .in(FundNavDaily::getFundCode, holdingByFundCode.keySet())
                        .ge(FundNavDaily::getNavDate, queryStart)
                        .le(FundNavDaily::getNavDate, endDate))
                .stream()
                .filter(nav -> nav.getNavDate() != null)
                .filter(nav -> nav.getFundCode() != null && !nav.getFundCode().isBlank())
                .filter(nav -> {
                    LocalDate effectiveDate = officialNavEffectiveDate(holdingByFundCode.get(nav.getFundCode()), nav.getNavDate());
                    return !effectiveDate.isBefore(startDate) && !effectiveDate.isAfter(endDate);
                })
                .collect(Collectors.groupingBy(
                        nav -> officialNavEffectiveDate(holdingByFundCode.get(nav.getFundCode()), nav.getNavDate()),
                        LinkedHashMap::new,
                        Collectors.mapping(FundNavDaily::getFundCode, Collectors.toSet())
                ));
    }

    private ProfitStatus statusOf(LocalDate date, List<HoldingSnapshot> snapshots,
                                  Map<Long, String> fundCodeByHoldingId,
                                  Map<LocalDate, Set<String>> officialNavByDate) {
        Set<String> snapshotFundCodes = snapshots.stream()
                .map(HoldingSnapshot::getHoldingId)
                .map(fundCodeByHoldingId::get)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        if (snapshotFundCodes.isEmpty()) {
            return snapshotSynced();
        }
        Set<String> officialFundCodes = officialNavByDate.getOrDefault(date, Set.of());
        if (officialFundCodes.containsAll(snapshotFundCodes)) {
            return confirmed();
        }
        return snapshotSynced();
    }

    public ProfitStatus confirmed() {
        return new ProfitStatus("CONFIRMED", "正式净值已确认");
    }

    public ProfitStatus snapshotSynced() {
        return new ProfitStatus("SNAPSHOT_SYNCED", "快照已同步，正式净值来源待确认");
    }

    private LocalDate officialNavEffectiveDate(FundHolding holding, LocalDate navDate) {
        return OfficialNavTiming.effectiveDate(holding, navDate, tradingCalendarService);
    }

    private boolean delayedOfficialNavFund(FundHolding holding) {
        return OfficialNavTiming.isDelayedOfficialNavFund(holding);
    }

    public record ProfitStatus(String code, String text) {
    }
}
