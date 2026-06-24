package com.lk.quantfund.datasource.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.datasource.model.FundSearchResultDTO;
import com.lk.quantfund.service.ApiCallLogService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.http.HttpStatus;

class EastMoneyFundDataSourceAdapterTest {

    @Test
    void shouldParseJsonpEstimateResponse() {
        String jsonp = "jsonpgz({\"fundcode\":\"000001\",\"name\":\"华夏成长混合\",\"gsz\":\"1.2345\",\"gszzl\":\"0.66\",\"gztime\":\"2026-06-23 14:55\"})";
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK).body(jsonp).build());
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        ApiCallLogService apiCallLogService = (provider, apiName, requestUrl, requestMethod, success, statusCode, errorMessage, costTimeMs, fallbackUsed) -> { };

        EastMoneyFundDataSourceAdapter adapter = new EastMoneyFundDataSourceAdapter(
                webClient,
                new ObjectMapper(),
                new QuantFundProperties(),
                apiCallLogService
        );

        Optional<FundEstimateDTO> estimate = adapter.getIntradayEstimate("000001");

        assertThat(estimate).isPresent();
        assertThat(estimate.get().fundCode()).isEqualTo("000001");
        assertThat(estimate.get().fundName()).isEqualTo("华夏成长混合");
        assertThat(estimate.get().delayed()).isFalse();
    }

    @Test
    void shouldIgnoreInvalidCharsetHeaderWhenParsingEstimateResponse() {
        String jsonp = "jsonpgz({\"fundcode\":\"161725\",\"name\":\"招商中证白酒指数A\",\"gsz\":\"0.9123\",\"gszzl\":\"-0.18\",\"gztime\":\"2026-06-23 14:55\"})";
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/javascript; charset=UTF-8,gbk")
                .body(jsonp)
                .build());
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        ApiCallLogService apiCallLogService = (provider, apiName, requestUrl, requestMethod, success, statusCode, errorMessage, costTimeMs, fallbackUsed) -> { };

        EastMoneyFundDataSourceAdapter adapter = new EastMoneyFundDataSourceAdapter(
                webClient,
                new ObjectMapper(),
                new QuantFundProperties(),
                apiCallLogService
        );

        Optional<FundEstimateDTO> estimate = adapter.getIntradayEstimate("161725");

        assertThat(estimate).isPresent();
        assertThat(estimate.get().fundCode()).isEqualTo("161725");
        assertThat(estimate.get().fundName()).isEqualTo("招商中证白酒指数A");
        assertThat(estimate.get().estimateGrowthRate()).isEqualByComparingTo("-0.18");
    }

    @Test
    void shouldUrlEncodeChineseKeywordAndParseSearchResponse() {
        String body = "{\"Datas\":[{\"CODE\":\"161725\",\"NAME\":\"招商中证白酒指数(LOF)A\",\"JP\":\"ZSZZBJZSLOFA\",\"CATEGORYDESC\":\"基金\",\"FundBaseInfo\":{\"FTYPE\":\"指数型-股票\"}}]}";
        AtomicReference<String> requestedUrl = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            requestedUrl.set(request.url().toString());
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
        };
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        ApiCallLogService apiCallLogService = (provider, apiName, requestUrl, requestMethod, success, statusCode, errorMessage, costTimeMs, fallbackUsed) -> { };

        EastMoneyFundDataSourceAdapter adapter = new EastMoneyFundDataSourceAdapter(
                webClient,
                new ObjectMapper(),
                new QuantFundProperties(),
                apiCallLogService
        );

        List<FundSearchResultDTO> results = adapter.searchFunds("白酒");

        assertThat(requestedUrl.get()).contains("key=%E7%99%BD%E9%85%92");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().fundCode()).isEqualTo("161725");
        assertThat(results.getFirst().fundName()).isEqualTo("招商中证白酒指数(LOF)A");
        assertThat(results.getFirst().fundType()).isEqualTo("指数型-股票");
        assertThat(results.getFirst().pinyin()).isEqualTo("ZSZZBJZSLOFA");
        assertThat(results.getFirst().sourceName()).isEqualTo("EAST_MONEY");
    }

    @Test
    void shouldUseOfficialHistoricalNavEndpointWithBrowserHeaders() {
        String body = "{\"Data\":{\"LSJZList\":[{\"FSRQ\":\"2026-06-24\",\"DWJZ\":\"2.4778\",\"LJJZ\":\"2.4778\",\"JZZZL\":\"4.52\"}]},\"ErrCode\":0}";
        AtomicReference<ClientRequest> requested = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            requested.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
        };
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        ApiCallLogService apiCallLogService = (provider, apiName, requestUrl, requestMethod, success, statusCode, errorMessage, costTimeMs, fallbackUsed) -> { };

        EastMoneyFundDataSourceAdapter adapter = new EastMoneyFundDataSourceAdapter(
                webClient,
                new ObjectMapper(),
                new QuantFundProperties(),
                apiCallLogService
        );

        List<FundNavPointDTO> points = adapter.getHistoricalNav(
                "016874",
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 24)
        );

        assertThat(requested.get().headers().getFirst("Referer")).isEqualTo("https://fundf10.eastmoney.com/");
        assertThat(requested.get().headers().getFirst("User-Agent")).contains("Mozilla/5.0");
        assertThat(points).hasSize(1);
        assertThat(points.getFirst().navDate()).isEqualTo(LocalDate.of(2026, 6, 24));
        assertThat(points.getFirst().unitNav()).isEqualByComparingTo("2.4778");
        assertThat(points.getFirst().dailyGrowthRate()).isEqualByComparingTo("4.52");
    }
}
