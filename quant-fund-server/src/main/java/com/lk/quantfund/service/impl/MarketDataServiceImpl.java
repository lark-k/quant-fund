package com.lk.quantfund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.MarketIndexDaily;
import com.lk.quantfund.mapper.MarketIndexDailyMapper;
import com.lk.quantfund.service.MarketDataService;
import com.lk.quantfund.vo.market.MarketIndexDailyVO;
import com.lk.quantfund.vo.market.MarketIndexIntradayPointVO;
import com.lk.quantfund.vo.market.MarketIndexVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class MarketDataServiceImpl implements MarketDataService {

    private static final String SOURCE_NAME = "EAST_MONEY";
    private static final String DEFAULT_INDEX_SECIDS = "1.000001,0.399001,0.399006,1.000300,1.000905";
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter INTRADAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WebClient.Builder webClientBuilder;
    private final QuantFundProperties properties;
    private final ObjectMapper objectMapper;
    private final MarketIndexDailyMapper marketIndexDailyMapper;

    public MarketDataServiceImpl(WebClient.Builder webClientBuilder,
                                 QuantFundProperties properties,
                                 ObjectMapper objectMapper,
                                 MarketIndexDailyMapper marketIndexDailyMapper) {
        this.webClientBuilder = webClientBuilder;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.marketIndexDailyMapper = marketIndexDailyMapper;
    }

    @Override
    public List<MarketIndexVO> marketReadings() {
        try {
            String response = webClientBuilder.build()
                    .get()
                    .uri(builder -> builder
                            .scheme("https")
                            .host("push2.eastmoney.com")
                            .path("/api/qt/ulist.np/get")
                            .queryParam("fltt", "2")
                            .queryParam("secids", DEFAULT_INDEX_SECIDS)
                            .queryParam("fields", "f12,f14,f2,f3,f4,f6")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(properties.getFundDataSource().getTimeoutMs()))
                    .block();
            return parse(response);
        } catch (Exception exception) {
            return List.of();
        }
    }

    @Override
    public List<MarketIndexDailyVO> historicalIndex(String indexCode, LocalDate startDate, LocalDate endDate) {
        if (indexCode == null || indexCode.isBlank() || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }
        List<MarketIndexDailyVO> cached = safeCachedHistory(indexCode, startDate, endDate);
        if (cacheCoversRange(cached, startDate, endDate)) {
            return cached;
        }
        try {
            String response = webClientBuilder.build()
                    .get()
                    .uri(builder -> builder
                            .scheme("https")
                            .host("push2his.eastmoney.com")
                            .path("/api/qt/stock/kline/get")
                            .queryParam("secid", secid(indexCode))
                            .queryParam("fields1", "f1,f2,f3,f4,f5,f6")
                            .queryParam("fields2", "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61")
                            .queryParam("klt", "101")
                            .queryParam("fqt", "1")
                            .queryParam("beg", startDate.format(COMPACT_DATE))
                            .queryParam("end", endDate.format(COMPACT_DATE))
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(properties.getFundDataSource().getTimeoutMs()))
                    .block();
            List<MarketIndexDaily> parsed = parseHistory(response);
            try {
                upsertHistory(parsed);
            } catch (Exception ignored) {
                // Cache table may be unavailable in a fresh database; still return parsed data.
            }
            List<MarketIndexDailyVO> refreshed = safeCachedHistory(indexCode, startDate, endDate);
            return refreshed.isEmpty() ? parsed.stream().map(this::toVO).toList() : refreshed;
        } catch (Exception exception) {
            return cached;
        }
    }

    @Override
    public List<MarketIndexIntradayPointVO> intradayIndex(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) {
            return List.of();
        }
        try {
            String response = webClientBuilder.build()
                    .get()
                    .uri(builder -> builder
                            .scheme("https")
                            .host("push2his.eastmoney.com")
                            .path("/api/qt/stock/trends2/get")
                            .queryParam("secid", secid(indexCode))
                            .queryParam("fields1", "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13")
                            .queryParam("fields2", "f51,f52,f53,f54,f55,f56,f57,f58")
                            .queryParam("ndays", "1")
                            .queryParam("iscr", "0")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(properties.getFundDataSource().getTimeoutMs()))
                    .block();
            return parseIntraday(response);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<MarketIndexVO> parse(String response) {
        try {
            JsonNode diff = objectMapper.readTree(response).path("data").path("diff");
            if (!diff.isArray()) {
                return List.of();
            }
            List<MarketIndexVO> result = new ArrayList<>();
            for (JsonNode row : diff) {
                result.add(new MarketIndexVO(
                        row.path("f12").asText(),
                        row.path("f14").asText(),
                        decimal(row.path("f2")),
                        decimal(row.path("f4")),
                        decimal(row.path("f3")),
                        decimal(row.path("f6")),
                        LocalDateTime.now(),
                        SOURCE_NAME
                ));
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<MarketIndexDaily> parseHistory(String response) {
        try {
            JsonNode data = objectMapper.readTree(response).path("data");
            String indexCode = data.path("code").asText();
            String indexName = data.path("name").asText(indexCode);
            JsonNode klines = data.path("klines");
            if (!klines.isArray()) {
                return List.of();
            }
            List<MarketIndexDaily> result = new ArrayList<>();
            for (JsonNode row : klines) {
                String[] fields = row.asText().split(",");
                if (fields.length < 9) {
                    continue;
                }
                MarketIndexDaily point = new MarketIndexDaily();
                point.setIndexCode(indexCode);
                point.setIndexName(indexName);
                point.setTradeDate(LocalDate.parse(fields[0]));
                point.setClosePrice(decimal(fields[2]));
                point.setDailyChangeRate(decimal(fields[8]));
                point.setSourceName(SOURCE_NAME);
                result.add(point);
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<MarketIndexIntradayPointVO> parseIntraday(String response) {
        try {
            JsonNode data = objectMapper.readTree(response).path("data");
            String indexCode = data.path("code").asText();
            String indexName = data.path("name").asText(indexCode);
            BigDecimal preClose = decimal(data.path("preClose"));
            if (preClose.compareTo(BigDecimal.ZERO) <= 0) {
                preClose = decimal(data.path("prePrice"));
            }
            if (preClose.compareTo(BigDecimal.ZERO) <= 0) {
                return List.of();
            }
            JsonNode trends = data.path("trends");
            if (!trends.isArray()) {
                return List.of();
            }
            List<MarketIndexIntradayPointVO> result = new ArrayList<>();
            for (JsonNode row : trends) {
                String[] fields = row.asText().split(",");
                if (fields.length < 3) {
                    continue;
                }
                BigDecimal price = decimal(fields[2]);
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                LocalDateTime time = LocalDateTime.parse(fields[0], INTRADAY_TIME);
                if (!isAShareIntradayMinute(time)) {
                    continue;
                }
                BigDecimal returnRate = price.subtract(preClose).multiply(new BigDecimal("100.0000")).divide(preClose, 4, RoundingMode.HALF_UP);
                result.add(new MarketIndexIntradayPointVO(
                        indexCode,
                        indexName,
                        time,
                        price,
                        returnRate,
                        SOURCE_NAME
                ));
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private boolean isAShareIntradayMinute(LocalDateTime time) {
        if (time == null) {
            return false;
        }
        int minutes = time.getHour() * 60 + time.getMinute();
        return (minutes >= 9 * 60 + 30 && minutes <= 11 * 60 + 30)
                || (minutes >= 13 * 60 && minutes <= 15 * 60);
    }

    private List<MarketIndexDailyVO> cachedHistory(String indexCode, LocalDate startDate, LocalDate endDate) {
        return marketIndexDailyMapper.selectList(new LambdaQueryWrapper<MarketIndexDaily>()
                        .eq(MarketIndexDaily::getIndexCode, indexCode)
                        .ge(MarketIndexDaily::getTradeDate, startDate)
                        .le(MarketIndexDaily::getTradeDate, endDate)
                        .orderByAsc(MarketIndexDaily::getTradeDate))
                .stream()
                .map(this::toVO)
                .toList();
    }

    private List<MarketIndexDailyVO> safeCachedHistory(String indexCode, LocalDate startDate, LocalDate endDate) {
        try {
            return cachedHistory(indexCode, startDate, endDate);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private boolean cacheCoversRange(List<MarketIndexDailyVO> cached, LocalDate startDate, LocalDate endDate) {
        if (cached.isEmpty()) {
            return false;
        }
        LocalDate firstDate = cached.getFirst().tradeDate();
        LocalDate lastDate = cached.getLast().tradeDate();
        return !firstDate.isAfter(startDate.plusDays(7))
                && !lastDate.isBefore(endDate.minusDays(7));
    }

    private void upsertHistory(List<MarketIndexDaily> points) {
        LocalDateTime now = LocalDateTime.now();
        for (MarketIndexDaily point : points) {
            MarketIndexDaily existing = marketIndexDailyMapper.selectOne(new LambdaQueryWrapper<MarketIndexDaily>()
                    .eq(MarketIndexDaily::getIndexCode, point.getIndexCode())
                    .eq(MarketIndexDaily::getTradeDate, point.getTradeDate()));
            point.setUpdateTime(now);
            if (existing == null) {
                point.setCreateTime(now);
                point.setDeleted(0);
                marketIndexDailyMapper.insert(point);
            } else {
                point.setId(existing.getId());
                point.setCreateTime(existing.getCreateTime());
                point.setDeleted(existing.getDeleted());
                marketIndexDailyMapper.updateById(point);
            }
        }
    }

    private MarketIndexDailyVO toVO(MarketIndexDaily point) {
        return new MarketIndexDailyVO(
                point.getIndexCode(),
                point.getIndexName(),
                point.getTradeDate(),
                point.getClosePrice(),
                point.getDailyChangeRate(),
                point.getSourceName()
        );
    }

    private String secid(String indexCode) {
        if ("HSI".equalsIgnoreCase(indexCode) || "NDX".equalsIgnoreCase(indexCode) || "SPX".equalsIgnoreCase(indexCode)) {
            return "100." + indexCode.toUpperCase();
        }
        return indexCode.startsWith("399") ? "0." + indexCode : "1." + indexCode;
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || "-".equals(node.asText())) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(node.asText()).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value).setScale(4, RoundingMode.HALF_UP);
    }
}
