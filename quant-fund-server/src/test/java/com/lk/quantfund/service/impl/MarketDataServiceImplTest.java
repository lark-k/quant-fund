package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.entity.MarketIndexDaily;
import com.lk.quantfund.mapper.MarketIndexDailyMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class MarketDataServiceImplTest {

    private final MarketIndexDailyMapper marketIndexDailyMapper = mock(MarketIndexDailyMapper.class);

    @Test
    void marketReadingsShouldFallbackToTencentQuotesWhenEastMoneyLiveConnectionCloses() {
        AtomicInteger historyRequests = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> {
            if ("push2.eastmoney.com".equals(request.url().getHost())) {
                return Mono.error(new RuntimeException("connection closed before response"));
            }
            if ("qt.gtimg.cn".equals(request.url().getHost())) {
                String body = """
                        v_s_sh000001="1~上证指数~000001~3952.18~-4.39~-0.11~510581645~97036515~~701350.87~ZS~";
                        v_s_sz399001="51~深证成指~399001~13953.07~-95.81~-0.68~630346080~113134987~~447661.54~ZS~";
                        v_s_sz399006="51~创业板指~399006~3424.40~-48.95~-1.41~186154222~54425510~~185993.81~ZS~";
                        v_s_sh000300="1~沪深300~000300~4609.18~-21.10~-0.46~180989495~54981348~~551688.28~ZS~";
                        v_s_sh000905="1~中证500~000905~7895.45~-50.88~-0.64~171070479~37630398~~174278.51~ZS~";
                        """;
                return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
            }
            historyRequests.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());
        };
        MarketDataServiceImpl service = new MarketDataServiceImpl(
                WebClient.builder().exchangeFunction(exchangeFunction),
                new QuantFundProperties(),
                new ObjectMapper(),
                marketIndexDailyMapper
        );

        var readings = service.marketReadings();

        assertThat(historyRequests).hasValue(0);
        assertThat(readings).hasSize(5);
        assertThat(readings.getFirst().name()).isEqualTo("上证指数");
        assertThat(readings.getFirst().latestPrice()).isEqualByComparingTo("3952.1800");
        assertThat(readings.getFirst().changeRate()).isEqualByComparingTo("-0.1100");
        assertThat(readings.getFirst().sourceName()).isEqualTo("TENCENT_QUOTE_FALLBACK");
    }

    @Test
    void marketReadingsShouldFallbackToLatestDailyKlinesWhenQuoteSourcesFail() {
        AtomicInteger historyRequests = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> {
            if ("push2.eastmoney.com".equals(request.url().getHost())
                    || "qt.gtimg.cn".equals(request.url().getHost())) {
                return Mono.error(new RuntimeException("connection closed before response"));
            }
            historyRequests.incrementAndGet();
            String body = """
                    {"data":{"name":"最近收盘指数","klines":[
                    "2026-08-28,4000.00,4010.00,4020.00,3990.00,100,200,1.00,0.25,10.00,0.10"
                    ]}}
                    """;
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
        };
        MarketDataServiceImpl service = new MarketDataServiceImpl(
                WebClient.builder().exchangeFunction(exchangeFunction),
                new QuantFundProperties(),
                new ObjectMapper(),
                marketIndexDailyMapper
        );

        var readings = service.marketReadings();

        assertThat(historyRequests).hasValue(5);
        assertThat(readings).hasSize(5);
        assertThat(readings).extracting(reading -> reading.code())
                .containsExactly("000001", "399001", "399006", "000300", "000905");
        assertThat(readings.getFirst().latestPrice()).isEqualByComparingTo("4010.0000");
        assertThat(readings.getFirst().changeRate()).isEqualByComparingTo("0.2500");
        assertThat(readings.getFirst().sourceName()).isEqualTo("EAST_MONEY_HISTORY_FALLBACK");
    }

    @Test
    void marketReadingsShouldReuseRecentSuccessWhenLiveEndpointTemporarilyFails() {
        AtomicInteger liveRequests = new AtomicInteger();
        AtomicInteger historyRequests = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> {
            if ("push2his.eastmoney.com".equals(request.url().getHost())) {
                historyRequests.incrementAndGet();
                return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());
            }
            if (liveRequests.incrementAndGet() == 1) {
                String body = """
                        {"data":{"diff":[
                        {"f12":"000300","f14":"沪深300","f2":4010.00,"f3":0.25,"f4":10.00,"f6":200.00}
                        ]}}
                        """;
                return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
            }
            return Mono.error(new RuntimeException("connection closed before response"));
        };
        MarketDataServiceImpl service = new MarketDataServiceImpl(
                WebClient.builder().exchangeFunction(exchangeFunction),
                new QuantFundProperties(),
                new ObjectMapper(),
                marketIndexDailyMapper
        );

        var first = service.marketReadings();
        var second = service.marketReadings();

        assertThat(first).hasSize(1);
        assertThat(second).isEqualTo(first);
        assertThat(liveRequests).hasValue(2);
        assertThat(historyRequests).hasValue(0);
    }

    @Test
    void historicalIndexShouldFetchParseAndCacheDailyKlines() {
        String body = """
                {"data":{"code":"000300","name":"沪深300","klines":[
                "2026-06-24,4000.00,4010.00,4020.00,3990.00,100,200,1.00,0.25,10.00,0.10",
                "2026-06-25,4010.00,4040.00,4050.00,4000.00,100,200,1.00,0.75,30.00,0.10"
                ]}}
                """;
        AtomicReference<ClientRequest> request = new AtomicReference<>();
        ExchangeFunction exchangeFunction = clientRequest -> {
            request.set(clientRequest);
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
        };
        when(marketIndexDailyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(
                        point(LocalDate.of(2026, 6, 24), "4010.0000", "0.2500"),
                        point(LocalDate.of(2026, 6, 25), "4040.0000", "0.7500")
                ));
        when(marketIndexDailyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MarketDataServiceImpl service = new MarketDataServiceImpl(
                WebClient.builder().exchangeFunction(exchangeFunction),
                new QuantFundProperties(),
                new ObjectMapper(),
                marketIndexDailyMapper
        );

        var history = service.historicalIndex("000300", LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 25));

        assertThat(request.get().url().toString()).contains("secid=1.000300");
        assertThat(history).hasSize(2);
        assertThat(history.get(1).closePrice()).isEqualByComparingTo("4040.0000");
        ArgumentCaptor<MarketIndexDaily> captor = ArgumentCaptor.forClass(MarketIndexDaily.class);
        verify(marketIndexDailyMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(MarketIndexDaily::getIndexCode).containsOnly("000300");
        assertThat(captor.getAllValues().get(0).getDailyChangeRate()).isEqualByComparingTo("0.2500");
    }

    @Test
    void historicalIndexShouldFallbackWhenCacheTableUnavailable() {
        String body = """
                {"data":{"code":"000300","name":"沪深300","klines":[
                "2026-06-24,4000.00,4010.00,4020.00,3990.00,100,200,1.00,0.25,10.00,0.10"
                ]}}
                """;
        AtomicReference<ClientRequest> request = new AtomicReference<>();
        ExchangeFunction exchangeFunction = clientRequest -> {
            request.set(clientRequest);
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
        };
        doThrow(new RuntimeException("table missing")).when(marketIndexDailyMapper).selectList(any(LambdaQueryWrapper.class));

        MarketDataServiceImpl service = new MarketDataServiceImpl(
                WebClient.builder().exchangeFunction(exchangeFunction),
                new QuantFundProperties(),
                new ObjectMapper(),
                marketIndexDailyMapper
        );

        var history = service.historicalIndex("000300", LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 24));

        assertThat(request.get().url().toString()).contains("secid=1.000300");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).closePrice()).isEqualByComparingTo("4010.0000");
    }

    @Test
    void historicalIndexShouldRefreshWhenCachedRangeIsTooShort() {
        String body = """
                {"data":{"code":"399006","name":"创业板指","klines":[
                "2025-06-26,2000.00,2010.00,2020.00,1990.00,100,200,1.00,0.50,10.00,0.10",
                "2026-06-26,2600.00,2620.00,2630.00,2590.00,100,200,1.00,0.77,20.00,0.10"
                ]}}
                """;
        AtomicReference<ClientRequest> request = new AtomicReference<>();
        ExchangeFunction exchangeFunction = clientRequest -> {
            request.set(clientRequest);
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
        };
        when(marketIndexDailyMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        point(LocalDate.of(2026, 6, 22), "2550.0000", "0.1000"),
                        point(LocalDate.of(2026, 6, 26), "2620.0000", "0.7700")
                ))
                .thenReturn(List.of(
                        point(LocalDate.of(2025, 6, 26), "2010.0000", "0.5000"),
                        point(LocalDate.of(2026, 6, 26), "2620.0000", "0.7700")
                ));
        when(marketIndexDailyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        MarketDataServiceImpl service = new MarketDataServiceImpl(
                WebClient.builder().exchangeFunction(exchangeFunction),
                new QuantFundProperties(),
                new ObjectMapper(),
                marketIndexDailyMapper
        );

        var history = service.historicalIndex("399006", LocalDate.of(2025, 6, 26), LocalDate.of(2026, 6, 26));

        assertThat(request.get().url().toString()).contains("secid=0.399006");
        assertThat(history).hasSize(2);
        assertThat(history.getFirst().tradeDate()).isEqualTo(LocalDate.of(2025, 6, 26));
    }

    @Test
    void historicalIndexShouldUseGlobalMarketSecidForNasdaq() {
        AtomicReference<ClientRequest> request = new AtomicReference<>();
        ExchangeFunction exchangeFunction = clientRequest -> {
            request.set(clientRequest);
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("{\"data\":{\"code\":\"NDX\",\"name\":\"纳斯达克\",\"klines\":[]}}").build());
        };
        when(marketIndexDailyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        MarketDataServiceImpl service = new MarketDataServiceImpl(
                WebClient.builder().exchangeFunction(exchangeFunction),
                new QuantFundProperties(),
                new ObjectMapper(),
                marketIndexDailyMapper
        );

        service.historicalIndex("NDX", LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 25));

        assertThat(request.get().url().toString()).contains("secid=100.NDX");
    }

    private MarketIndexDaily point(LocalDate date, String closePrice, String dailyChangeRate) {
        MarketIndexDaily point = new MarketIndexDaily();
        point.setIndexCode("000300");
        point.setIndexName("沪深300");
        point.setTradeDate(date);
        point.setClosePrice(new java.math.BigDecimal(closePrice));
        point.setDailyChangeRate(new java.math.BigDecimal(dailyChangeRate));
        point.setSourceName("EAST_MONEY");
        return point;
    }
}
