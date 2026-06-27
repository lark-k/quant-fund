package com.lk.quantfund.datasource.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.datasource.model.FundSearchResultDTO;
import com.lk.quantfund.datasource.model.FundStockHoldingDTO;
import com.lk.quantfund.datasource.model.FundThemeDTO;
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

    @Test
    void shouldContinueHistoricalNavPaginationWhenEastMoneyCapsPageSizeToTwenty() {
        AtomicReference<String> lastRequestedUrl = new AtomicReference<>();
        final int[] requestCount = {0};
        ExchangeFunction exchangeFunction = request -> {
            requestCount[0]++;
            lastRequestedUrl.set(request.url().toString());
            String body = requestCount[0] == 1 ? historicalNavBody(20) : historicalNavBody(1);
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
                LocalDate.of(2025, 12, 17),
                LocalDate.of(2026, 6, 26)
        );

        assertThat(points).hasSize(21);
        assertThat(requestCount[0]).isEqualTo(2);
        assertThat(lastRequestedUrl.get()).contains("pageIndex=2").contains("pageSize=20");
    }

    private String historicalNavBody(int count) {
        StringBuilder builder = new StringBuilder("{\"Data\":{\"LSJZList\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append("{\"FSRQ\":\"")
                    .append(LocalDate.of(2026, 6, 26).minusDays(index))
                    .append("\",\"DWJZ\":\"2.")
                    .append(String.format("%04d", index))
                    .append("\",\"LJJZ\":\"2.")
                    .append(String.format("%04d", index))
                    .append("\",\"JZZZL\":\"0.10\"}");
        }
        builder.append("]},\"ErrCode\":0}");
        return builder.toString();
    }

    @Test
    void shouldEstimateOverseasActiveFundFromOverseasHoldingsOnly() {
        String heavyStocksHtml = """
                <table><tbody>
                <tr><td>1</td><td><a href='//quote.eastmoney.com/unify/r/106.TSM'>TSM</a></td><td>台积电</td><td>--</td><td>--</td><td>资讯</td><td>60.00%</td></tr>
                <tr><td>2</td><td><a href='//quote.eastmoney.com/unify/r/105.LITE'>LITE</a></td><td>Lumentum Holdings Inc</td><td>--</td><td>--</td><td>资讯</td><td>40.00%</td></tr>
                <tr><td>3</td><td><a href='//quote.eastmoney.com/unify/r/0.300502'>300502</a></td><td>新易盛</td><td>--</td><td>--</td><td>资讯</td><td>100.00%</td></tr>
                </tbody></table>
                """;
        String quotes = """
                {"data":{"diff":[
                {"f12":"TSM","f14":"台积电","f2":100.00,"f3":2.00},
                {"f12":"LITE","f14":"Lumentum Holdings Inc","f2":100.00,"f3":3.925},
                {"f12":"300502","f14":"新易盛","f2":100.00,"f3":-9.00}
                ]}}
                """;
        ExchangeFunction exchangeFunction = request -> {
            String url = request.url().toString();
            if (url.contains("FundArchivesDatas")) {
                return Mono.just(ClientResponse.create(HttpStatus.OK).body(heavyStocksHtml).build());
            }
            if (url.contains("ulist.np/get")) {
                return Mono.just(ClientResponse.create(HttpStatus.OK).body(quotes).build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());
        };
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        ApiCallLogService apiCallLogService = (provider, apiName, requestUrl, requestMethod, success, statusCode, errorMessage, costTimeMs, fallbackUsed) -> { };
        EastMoneyFundDataSourceAdapter adapter = new EastMoneyFundDataSourceAdapter(
                webClient,
                new ObjectMapper(),
                new QuantFundProperties(),
                apiCallLogService
        );

        List<FundThemeDTO> themes = adapter.getRelatedThemes("012922");

        assertThat(themes).hasSize(1);
        assertThat(themes.getFirst().themeName()).isEqualTo("海外基金");
        assertThat(themes.getFirst().themeType()).isEqualTo("OVERSEAS_HEAVY_STOCK_WEIGHTED");
        assertThat(themes.getFirst().weight()).isEqualByComparingTo("100.0000");
        assertThat(themes.getFirst().estimatedRate()).isEqualByComparingTo("2.7700");
    }

    @Test
    void shouldUseTargetEtfHoldingsWhenEtfFeederHasNoDirectStocks() {
        String feederBody = "var apidata={ content:\"\",arryear:[],curyear:2026};";
        String targetEtfBody = "var apidata={ content:\""
                + "<div><label>截止至：<font class='px12'>2026-03-31</font></label><table><tbody>"
                + "<tr><td>1</td><td><a href='//quote.eastmoney.com/unify/r/116.01211'>01211</a></td><td><a href='//quote.eastmoney.com/unify/r/116.01211'>BYD</a></td><td>--</td><td>--</td><td>info</td><td>9.43%</td></tr>"
                + "</tbody></table></div>\",arryear:[2026],curyear:2026};";
        String quotes = """
                {"data":{"diff":[
                {"f12":"01211","f14":"BYD Company","f2":110.00,"f3":-1.25}
                ]}}
                """;
        ExchangeFunction exchangeFunction = request -> {
            String url = request.url().toString();
            if (url.contains("FundArchivesDatas") && url.contains("code=013403")) {
                return Mono.just(ClientResponse.create(HttpStatus.OK).body(feederBody).build());
            }
            if (url.contains("FundArchivesDatas") && url.contains("code=513180")) {
                return Mono.just(ClientResponse.create(HttpStatus.OK).body(targetEtfBody).build());
            }
            if (url.contains("ulist.np/get")) {
                return Mono.just(ClientResponse.create(HttpStatus.OK).body(quotes).build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());
        };
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        ApiCallLogService apiCallLogService = (provider, apiName, requestUrl, requestMethod, success, statusCode, errorMessage, costTimeMs, fallbackUsed) -> { };
        EastMoneyFundDataSourceAdapter adapter = new EastMoneyFundDataSourceAdapter(
                webClient,
                new ObjectMapper(),
                new QuantFundProperties(),
                apiCallLogService
        );

        List<FundStockHoldingDTO> stocks = adapter.getHeavyStocks("013403");

        assertThat(stocks).hasSize(1);
        assertThat(stocks.getFirst().fundCode()).isEqualTo("013403");
        assertThat(stocks.getFirst().stockCode()).isEqualTo("01211");
        assertThat(stocks.getFirst().stockName()).isEqualTo("BYD Company");
        assertThat(stocks.getFirst().positionRate()).isEqualByComparingTo("9.43");
        assertThat(stocks.getFirst().marketSecId()).isEqualTo("116.01211");
        assertThat(stocks.getFirst().reportDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void shouldUseHangSengTechIndexThemeForHangSengTechEtfFeeder() {
        String quotes = """
                {"data":{"diff":[
                {"f12":"HSTECH","f14":"恒生科技指数","f2":4255.59,"f3":-3.41}
                ]}}
                """;
        ExchangeFunction exchangeFunction = request -> {
            String url = request.url().toString();
            if (url.contains("ulist.np/get")) {
                return Mono.just(ClientResponse.create(HttpStatus.OK).body(quotes).build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());
        };
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        ApiCallLogService apiCallLogService = (provider, apiName, requestUrl, requestMethod, success, statusCode, errorMessage, costTimeMs, fallbackUsed) -> { };
        EastMoneyFundDataSourceAdapter adapter = new EastMoneyFundDataSourceAdapter(
                webClient,
                new ObjectMapper(),
                new QuantFundProperties(),
                apiCallLogService
        );

        List<FundThemeDTO> themes = adapter.getRelatedThemes("013403");

        assertThat(themes).hasSize(1);
        assertThat(themes.getFirst().themeName()).isEqualTo("恒生科技");
        assertThat(themes.getFirst().themeType()).isEqualTo("TRACKING_INDEX");
        assertThat(themes.getFirst().estimatedRate()).isEqualByComparingTo("-3.41");
    }
}
