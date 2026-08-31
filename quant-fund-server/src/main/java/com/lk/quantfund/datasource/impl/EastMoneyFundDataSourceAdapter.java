package com.lk.quantfund.datasource.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.config.QuantFundProperties;
import com.lk.quantfund.datasource.FundDataSourceAdapter;
import com.lk.quantfund.datasource.FundDataSourceException;
import com.lk.quantfund.datasource.FundUniverseDataSourceAdapter;
import com.lk.quantfund.datasource.model.FundBasicInfoDTO;
import com.lk.quantfund.datasource.model.FundEstimateDTO;
import com.lk.quantfund.datasource.model.FundNavPointDTO;
import com.lk.quantfund.datasource.model.FundPeerRankDTO;
import com.lk.quantfund.datasource.model.FundSearchResultDTO;
import com.lk.quantfund.datasource.model.FundStockHoldingDTO;
import com.lk.quantfund.datasource.model.FundThemeDTO;
import com.lk.quantfund.datasource.model.MarketFundDTO;
import com.lk.quantfund.service.ApiCallLogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;

@Component
public class EastMoneyFundDataSourceAdapter implements FundDataSourceAdapter, FundUniverseDataSourceAdapter {

    private static final String SOURCE_NAME = "EAST_MONEY";
    private static final String TENCENT_SOURCE_NAME = "TENCENT";
    private static final String EAST_MONEY_REFERER = "https://fundf10.eastmoney.com/";
    private static final String TENCENT_REFERER = "https://gu.qq.com/";
    private static final String TENCENT_QUOTE_URL = "https://qt.gtimg.cn/q=";
    private static final String EAST_MONEY_MOBILE_FUND_DETAIL_URL =
            "https://fundmobapi.eastmoney.com/FundMApi/FundDetailInformation.ashx";
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final Pattern JS_STRING_VAR = Pattern.compile("var\\s+%s\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern ROW_PATTERN = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CELL_PATTERN = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SECID_PATTERN = Pattern.compile("unify/r/([^'\" >]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPORT_DATE_PATTERN = Pattern.compile("截止至：<font[^>]*>(\\d{4}-\\d{2}-\\d{2})</font>");
    private static final Pattern TENCENT_QUOTE_PATTERN = Pattern.compile("v_s_([^=]+)=\\\"([^\\\"]*)\\\";");
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration QUOTE_CACHE_TTL = Duration.ofSeconds(30);
    private static final int HISTORICAL_NAV_PAGE_SIZE = 200;
    private static final int EAST_MONEY_LEGACY_PAGE_CAP = 20;
    private static final String ESTIMATE_FIELDS = "FCODE,SHORTNAME,GSZZL,GZTIME,GSZ";

    private final WebClient fundDataWebClient;
    private final ObjectMapper objectMapper;
    private final QuantFundProperties properties;
    private final ApiCallLogService apiCallLogService;
    private final Map<String, CachedQuote> quoteCache = new ConcurrentHashMap<>();

    public EastMoneyFundDataSourceAdapter(WebClient fundDataWebClient,
                                          ObjectMapper objectMapper,
                                          QuantFundProperties properties,
                                          ApiCallLogService apiCallLogService) {
        this.fundDataWebClient = fundDataWebClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiCallLogService = apiCallLogService;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean enabled() {
        return properties.getFundDataSource().isEastMoneyEnabled();
    }

    @Override
    public List<FundSearchResultDTO> searchFunds(String keyword) {
        String body = get("fund_search", properties.getFundDataSource().getEastMoneySearchUrl() + "?m=1&key=" + keyword);
        try {
            JsonNode root = objectMapper.readTree(stripJsonp(body));
            JsonNode datas = root.path("Datas");
            List<FundSearchResultDTO> results = new ArrayList<>();
            if (datas.isArray()) {
                for (JsonNode item : datas) {
                    String code = text(item, "CODE", "FundCode", "code");
                    String name = text(item, "NAME", "FundName", "name");
                    if (!code.isBlank() && !name.isBlank()) {
                        JsonNode fundBaseInfo = item.path("FundBaseInfo");
                        String fundType = firstText(
                                text(fundBaseInfo, "FTYPE", "FUNDTYPE", "FundType"),
                                text(item, "FundType", "TYPE", "type", "CATEGORYDESC")
                        );
                        String pinyin = firstText(
                                text(item, "PinYin", "PY", "pinyin", "JP"),
                                text(fundBaseInfo, "SHORTNAME")
                        );
                        results.add(new FundSearchResultDTO(code, name, fundType, pinyin, SOURCE_NAME));
                    }
                }
            }
            return results;
        } catch (Exception exception) {
            throw new FundDataSourceException("东方财富基金搜索解析失败", exception);
        }
    }

    @Override
    public List<MarketFundDTO> listAllFunds() {
        String body = get("fund_universe", properties.getFundDataSource().getEastMoneyFundListUrl() + "?v=" + System.currentTimeMillis());
        try {
            return parseFundCodeList(body);
        } catch (Exception exception) {
            throw new FundDataSourceException("东方财富全市场基金列表解析失败", exception);
        }
    }

    @Override
    public List<MarketFundDTO> listFundsByCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return listAllFunds();
        }
        String normalizedCategory = category.trim().toUpperCase();
        return listAllFunds().stream()
                .filter(fund -> normalizeUniverseCategory(fund.fundType(), fund.fundName()).equals(normalizedCategory))
                .toList();
    }

    @Override
    public Optional<MarketFundDTO> getFundProfile(String fundCode) {
        if (!StringUtils.hasText(fundCode)) {
            return Optional.empty();
        }
        String code = fundCode.trim();
        String url = EAST_MONEY_MOBILE_FUND_DETAIL_URL
                + "?FCODE=" + code
                + "&deviceid=Wap&plat=Wap&product=EFund&version=2.0.0";
        try {
            JsonNode data = objectMapper.readTree(get("fund_profile", url)).path("Datas");
            if (data.isMissingNode() || data.isNull() || data.isEmpty()) {
                return Optional.empty();
            }
            String name = firstText(text(data, "SHORTNAME"), code);
            String fundType = text(data, "FTYPE");
            String trackingIndex = firstText(text(data, "INDEXNAME"), trackingIndex(name));
            return Optional.of(new MarketFundDTO(
                    firstText(text(data, "FCODE"), code),
                    name,
                    fundType,
                    shareClass(name),
                    null,
                    emptyToNull(text(data, "JJGS")),
                    emptyToNull(text(data, "JJJL")),
                    parseProfileDate(text(data, "ESTABDATE")),
                    fundSizeFromYuan(text(data, "ENDNAV")),
                    emptyToNull(trackingIndex),
                    activeFund(name),
                    emptyToNull(text(data, "RISKLEVEL")),
                    "NORMAL",
                    SOURCE_NAME
            ));
        } catch (Exception exception) {
            throw new FundDataSourceException("东方财富基金档案解析失败", exception);
        }
    }

    private List<MarketFundDTO> parseFundCodeList(String body) throws Exception {
        int start = body.indexOf('[');
        int end = body.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new FundDataSourceException("东方财富全市场基金列表为空");
        }
        JsonNode root = objectMapper.readTree(body.substring(start, end + 1));
        List<MarketFundDTO> funds = new ArrayList<>();
        if (!root.isArray()) {
            return funds;
        }
        for (JsonNode item : root) {
            if (!item.isArray() || item.size() < 4) {
                continue;
            }
            String code = item.path(0).asText("");
            String name = item.path(2).asText("");
            String fundType = item.path(3).asText("");
            if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
                continue;
            }
            funds.add(new MarketFundDTO(
                    code,
                    name,
                    fundType,
                    shareClass(name),
                    null,
                    null,
                    null,
                    null,
                    null,
                    trackingIndex(name),
                    activeFund(name),
                    null,
                    "NORMAL",
                    SOURCE_NAME
            ));
        }
        return funds;
    }

    private String normalizeUniverseCategory(String fundType, String fundName) {
        String text = (cleanText(fundType) + cleanText(fundName)).toUpperCase();
        if (text.contains("QDII")) {
            return "QDII";
        }
        if (text.contains("货币")) {
            return "MONEY";
        }
        if (text.contains("债")) {
            return "BOND";
        }
        if (text.contains("ETF") || text.contains("指数") || text.contains("INDEX")) {
            return "INDEX";
        }
        if (text.contains("混合") || text.contains("MIX")) {
            return "MIXED";
        }
        if (text.contains("股票") || text.contains("权益") || text.contains("EQUITY")) {
            return "ACTIVE_EQUITY";
        }
        return "UNKNOWN";
    }

    private String shareClass(String fundName) {
        String name = cleanText(fundName).toUpperCase();
        if (name.endsWith("C")) {
            return "C";
        }
        if (name.endsWith("E")) {
            return "E";
        }
        if (name.endsWith("A")) {
            return "A";
        }
        if (name.contains("ETF")) {
            return "ETF";
        }
        if (name.contains("LOF")) {
            return "LOF";
        }
        return null;
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public Optional<FundBasicInfoDTO> getBasicInfo(String fundCode) {
        try {
            String script = getDetailScript(fundCode);
            String fundName = jsString(script, "fS_name")
                    .or(() -> getIntradayEstimate(fundCode).map(FundEstimateDTO::fundName))
                    .orElse(fundCode);
            String managerNames = managerNames(script);
            String fundType = normalizeFundType(fundName, "");
            String trackingIndex = trackingIndex(fundName);
            String themeTags = themeTags(fundCode);
            return Optional.of(new FundBasicInfoDTO(fundCode, fundName, fundType, activeFund(fundName),
                    trackingIndex, managerNames, null, null, themeTags, SOURCE_NAME));
        } catch (RuntimeException exception) {
            return getIntradayEstimate(fundCode)
                    .map(estimate -> new FundBasicInfoDTO(fundCode, estimate.fundName(), normalizeFundType(estimate.fundName(), "UNKNOWN"),
                            activeFund(estimate.fundName()), trackingIndex(estimate.fundName()), null, null, null, null, SOURCE_NAME));
        }
    }

    @Override
    public List<FundNavPointDTO> getHistoricalNav(String fundCode, LocalDate startDate, LocalDate endDate) {
        List<FundNavPointDTO> points = new ArrayList<>();
        for (int pageIndex = 1; pageIndex <= 80; pageIndex++) {
            String url = properties.getFundDataSource().getEastMoneyHistoricalNavUrl()
                    + "?fundCode=" + fundCode
                    + "&pageIndex=" + pageIndex + "&pageSize=" + HISTORICAL_NAV_PAGE_SIZE
                    + "&startDate=" + (startDate == null ? "" : startDate)
                    + "&endDate=" + (endDate == null ? "" : endDate)
                    + "&_=" + System.currentTimeMillis();
            try {
                List<FundNavPointDTO> pagePoints = parseHistoricalNavPage(fundCode, get("historical_nav", url));
                points.addAll(pagePoints);
                if (!mayHaveMoreHistoricalNavPages(pagePoints.size())) {
                    break;
                }
            } catch (Exception exception) {
                if (points.isEmpty()) {
                    throw new FundDataSourceException("东方财富历史净值解析失败", exception);
                }
                break;
            }
        }
        return points.stream()
                .sorted(Comparator.comparing(FundNavPointDTO::navDate))
                .toList();
    }

    private boolean mayHaveMoreHistoricalNavPages(int rowCount) {
        return rowCount == HISTORICAL_NAV_PAGE_SIZE || rowCount == EAST_MONEY_LEGACY_PAGE_CAP;
    }

    private List<FundNavPointDTO> parseHistoricalNavPage(String fundCode, String body) throws Exception {
        JsonNode root = objectMapper.readTree(stripJsonp(body));
        int errCode = root.path("ErrCode").asInt(0);
        if (errCode != 0) {
            throw new FundDataSourceException("东方财富历史净值接口返回错误: " + errCode);
        }
        JsonNode list = root.path("Data").path("LSJZList");
        if (!list.isArray()) {
            throw new FundDataSourceException("东方财富历史净值接口未返回净值列表");
        }
        List<FundNavPointDTO> points = new ArrayList<>();
        for (JsonNode item : list) {
            LocalDate navDate = LocalDate.parse(text(item, "FSRQ"), DateTimeFormatter.ISO_LOCAL_DATE);
            points.add(new FundNavPointDTO(fundCode, navDate,
                    decimal(text(item, "DWJZ")),
                    decimal(text(item, "LJJZ")),
                    decimal(text(item, "JZZZL")),
                    SOURCE_NAME));
        }
        return points;
    }

    @Override
    public Optional<FundEstimateDTO> getIntradayEstimate(String fundCode) {
        String url = properties.getFundDataSource().getEastMoneyEstimateUrl();
        String body = postEstimate(url, fundCode);
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false) || root.path("errorCode").asInt(-1) != 0) {
                throw new FundDataSourceException("EastMoney valuation API returned an error");
            }
            JsonNode estimates = root.path("data");
            if (!estimates.isArray()) {
                return Optional.empty();
            }
            JsonNode estimate = null;
            for (JsonNode item : estimates) {
                if (fundCode.equals(text(item, "FCODE"))) {
                    estimate = item;
                    break;
                }
            }
            if (estimate == null
                    || !StringUtils.hasText(text(estimate, "GZTIME"))
                    || !StringUtils.hasText(text(estimate, "GSZZL"))
                    || !StringUtils.hasText(text(estimate, "GSZ"))) {
                return Optional.empty();
            }
            String estimateTime = text(estimate, "GZTIME");
            LocalDateTime updateTime = parseEstimateTime(estimateTime);
            return Optional.of(new FundEstimateDTO(
                    text(estimate, "FCODE"),
                    text(estimate, "SHORTNAME"),
                    decimal(text(estimate, "GSZ")),
                    decimal(text(estimate, "GSZZL")),
                    updateTime.toLocalDate(),
                    updateTime,
                    SOURCE_NAME,
                    false,
                    body
            ));
        } catch (Exception exception) {
            if (exception instanceof FundDataSourceException dataSourceException) {
                throw dataSourceException;
            }
            throw new FundDataSourceException("东方财富当天估值解析失败", exception);
        }
    }

    @Override
    public List<FundStockHoldingDTO> getHeavyStocks(String fundCode) {
        String url = properties.getFundDataSource().getEastMoneyFundArchiveUrl()
                + "?type=jjcc&code=" + fundCode + "&topline=10&year=&month=&rt=" + System.currentTimeMillis();
        String body = get("heavy_stocks", url);
        try {
            String html = archiveContent(body);
            LocalDate reportDate = reportDate(html);
            List<StockRow> rows = parseStockRows(fundCode, html, reportDate);
            if (rows.isEmpty()) {
                Optional<String> targetEtfCode = targetEtfFallbackCode(fundCode);
                if (targetEtfCode.isPresent()) {
                    String targetUrl = properties.getFundDataSource().getEastMoneyFundArchiveUrl()
                            + "?type=jjcc&code=" + targetEtfCode.get() + "&topline=10&year=&month=&rt=" + System.currentTimeMillis();
                    String targetHtml = archiveContent(get("heavy_stocks", targetUrl));
                    reportDate = reportDate(targetHtml);
                    rows = parseStockRows(fundCode, targetHtml, reportDate);
                }
            }
            LocalDate effectiveReportDate = reportDate;
            Map<String, QuoteInfo> quotes = quotes(rows.stream().map(StockRow::marketSecId).filter(StringUtils::hasText).distinct().toList());
            return rows.stream()
                    .map(row -> {
                        QuoteInfo quote = quotes.getOrDefault(row.stockCode(), QuoteInfo.empty());
                        String stockName = StringUtils.hasText(quote.name()) ? quote.name() : row.stockName();
                        return new FundStockHoldingDTO(
                                fundCode,
                                row.stockCode(),
                                stockName,
                                row.positionRate(),
                                classifyIndustry(stockName, row.marketSecId(), fundCode),
                                quote.latestPrice(),
                                quote.changeRate(),
                                row.marketSecId(),
                                effectiveReportDate,
                                SOURCE_NAME
                        );
                    })
                    .toList();
        } catch (Exception exception) {
            throw new FundDataSourceException("东方财富基金重仓解析失败", exception);
        }
    }

    @Override
    public List<FundThemeDTO> getRelatedThemes(String fundCode) {
        if (isOverseasActiveFund(fundCode)) {
            return List.of(overseasFundTheme(fundCode));
        }
        Optional<FundThemeDTO> indexTheme = indexTheme(fundCode);
        if (indexTheme.isPresent()) {
            return List.of(indexTheme.get());
        }
        List<FundStockHoldingDTO> stocks = getHeavyStocks(fundCode);
        if (stocks.isEmpty()) {
            return List.of();
        }
        Map<String, List<FundStockHoldingDTO>> byTheme = new LinkedHashMap<>();
        for (FundStockHoldingDTO stock : stocks) {
            String themeName = StringUtils.hasText(stock.industry()) ? stock.industry() : "未分类";
            byTheme.computeIfAbsent(themeName, ignored -> new ArrayList<>()).add(stock);
        }
        return byTheme.entrySet().stream()
                .map(entry -> {
                    BigDecimal weight = entry.getValue().stream()
                            .map(FundStockHoldingDTO::positionRate)
                            .map(this::valueOrZero)
                            .reduce(ZERO, BigDecimal::add);
                    BigDecimal weightedRate = weightedChangeRate(entry.getValue());
                    return new FundThemeDTO(fundCode, entry.getKey(), "HEAVY_STOCK_WEIGHTED", weight, weightedRate, SOURCE_NAME);
                })
                .sorted(Comparator.comparing(FundThemeDTO::weight, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .toList();
    }

    private boolean isOverseasActiveFund(String fundCode) {
        return "012922".equals(fundCode) || "012921".equals(fundCode);
    }

    private FundThemeDTO overseasFundTheme(String fundCode) {
        List<FundStockHoldingDTO> stocks;
        try {
            stocks = getHeavyStocks(fundCode);
        } catch (RuntimeException exception) {
            stocks = List.of();
        }
        BigDecimal overseasHoldingRate = overseasHoldingChangeRate(stocks);
        Optional<BigDecimal> overseasIndexRate = overseasHoldingRate == null ? overseasFundRate() : Optional.empty();
        BigDecimal estimatedRate = overseasHoldingRate == null ? overseasIndexRate.orElse(null) : overseasHoldingRate;
        return new FundThemeDTO(
                fundCode,
                "海外基金",
                overseasHoldingRate == null ? "OVERSEAS_FUND_INDEX" : "OVERSEAS_HEAVY_STOCK_WEIGHTED",
                new BigDecimal("100.0000"),
                estimatedRate,
                overseasHoldingRate == null ? SOURCE_NAME + "_OVERSEAS" : SOURCE_NAME
        );
    }

    private BigDecimal overseasHoldingChangeRate(List<FundStockHoldingDTO> stocks) {
        return weightedChangeRate(stocks.stream()
                .filter(stock -> isOverseasMarketSecId(stock.marketSecId()))
                .toList());
    }

    private boolean isOverseasMarketSecId(String marketSecId) {
        String secid = marketSecId == null ? "" : marketSecId;
        return secid.startsWith("105.") || secid.startsWith("106.") || secid.startsWith("116.");
    }

    private Optional<String> targetEtfFallbackCode(String fundCode) {
        return switch (fundCode) {
            case "013402", "013403", "023763" -> Optional.of("513180");
            default -> Optional.empty();
        };
    }

    private Optional<BigDecimal> overseasFundRate() {
        BigDecimal nasdaqRate = latestDailyIndexRate("100.NDX");
        BigDecimal sp500Rate = latestDailyIndexRate("100.SPX");
        if (nasdaqRate != null && sp500Rate != null) {
            return Optional.of(nasdaqRate.multiply(new BigDecimal("0.7000"))
                    .add(sp500Rate.multiply(new BigDecimal("0.3000")))
                    .setScale(4, RoundingMode.HALF_UP));
        }
        if (nasdaqRate != null) {
            return Optional.of(nasdaqRate.setScale(4, RoundingMode.HALF_UP));
        }
        if (sp500Rate != null) {
            return Optional.of(sp500Rate.setScale(4, RoundingMode.HALF_UP));
        }
        return Optional.empty();
    }

    private BigDecimal latestDailyIndexRate(String secid) {
        String today = LocalDate.now().format(COMPACT_DATE);
        String url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
                + "?secid=" + secid
                + "&fields1=f1,f2,f3,f4,f5,f6"
                + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                + "&klt=101&fqt=1&beg=" + LocalDate.now().minusDays(10).format(COMPACT_DATE)
                + "&end=" + today;
        try {
            String body = get("overseas_index_daily", url);
            JsonNode klines = objectMapper.readTree(stripJsonp(body)).path("data").path("klines");
            if (!klines.isArray() || klines.isEmpty()) {
                return null;
            }
            String[] fields = klines.get(klines.size() - 1).asText().split(",");
            return fields.length > 8 ? decimal(fields[8]) : null;
        } catch (RuntimeException exception) {
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private Optional<FundThemeDTO> indexTheme(String fundCode) {
        IndexThemeMapping mapping = indexThemeMapping(fundCode);
        if (mapping == null) {
            return Optional.empty();
        }
        Map<String, QuoteInfo> quote = quotes(List.of(mapping.secid()));
        QuoteInfo info = quote.get(mapping.code());
        if (info == null || info.changeRate() == null) {
            return Optional.empty();
        }
        return Optional.of(new FundThemeDTO(
                fundCode,
                mapping.themeName(),
                "TRACKING_INDEX",
                new BigDecimal("100.0000"),
                info.changeRate(),
                SOURCE_NAME + "_INDEX"
        ));
    }

    private IndexThemeMapping indexThemeMapping(String fundCode) {
        return switch (fundCode) {
            case "013402", "013403", "023763" -> new IndexThemeMapping("HSTECH", "124.HSTECH", "恒生科技");
            case "025833" -> new IndexThemeMapping("931994", "2.931994", "中证电网设备");
            case "161725" -> new IndexThemeMapping("399997", "0.399997", "中证白酒");
            default -> null;
        };
    }

    @Override
    public Optional<FundPeerRankDTO> getPeerRank(String fundCode) {
        try {
            String script = getDetailScript(fundCode);
            RankPoint rank = latestRank(script);
            BigDecimal percentile = latestPercentile(script);
            if (rank == null) {
                return Optional.empty();
            }
            String rankText = "同类第 " + rank.rank() + "/" + rank.total();
            return Optional.of(new FundPeerRankDTO(fundCode, rankText, "同类基金", rank.rank(), rank.total(),
                    percentile, "最新", SOURCE_NAME));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String getDetailScript(String fundCode) {
        String url = properties.getFundDataSource().getEastMoneyFundDetailUrl().replace("{fundCode}", fundCode)
                + "?v=" + System.currentTimeMillis();
        return get("fund_detail_script", url);
    }

    private Map<String, QuoteInfo> quotes(List<String> secids) {
        if (secids.isEmpty()) {
            return Map.of();
        }
        Map<String, QuoteInfo> result = new HashMap<>();
        List<String> unresolvedSecids = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String secid : secids) {
            CachedQuote cached = quoteCache.get(secid);
            if (cached != null && cached.isFresh(now)) {
                result.put(stockCode(secid), cached.quote());
            } else {
                unresolvedSecids.add(secid);
            }
        }
        if (unresolvedSecids.isEmpty()) {
            return result;
        }

        Map<String, QuoteInfo> eastMoneyQuotes = eastMoneyQuotes(unresolvedSecids);
        result.putAll(eastMoneyQuotes);
        List<String> fallbackSecids = unresolvedSecids.stream()
                .filter(secid -> {
                    QuoteInfo quote = eastMoneyQuotes.get(stockCode(secid));
                    return quote == null || quote.changeRate() == null;
                })
                .toList();
        if (!fallbackSecids.isEmpty()) {
            result.putAll(tencentQuotes(fallbackSecids));
        }
        for (String secid : unresolvedSecids) {
            QuoteInfo quote = result.get(stockCode(secid));
            if (quote != null) {
                quoteCache.put(secid, new CachedQuote(quote, now));
            }
        }
        return result;
    }

    private Map<String, QuoteInfo> eastMoneyQuotes(List<String> secids) {
        try {
            String url = properties.getFundDataSource().getEastMoneyQuoteUrl()
                    + "?fltt=2&secids=" + String.join(",", secids)
                    + "&fields=f12,f14,f2,f3";
            String body = get("stock_quotes", url);
            JsonNode diff = objectMapper.readTree(stripJsonp(body)).path("data").path("diff");
            Map<String, QuoteInfo> result = new HashMap<>();
            if (diff.isArray()) {
                for (JsonNode item : diff) {
                    String code = text(item, "f12");
                    if (StringUtils.hasText(code)) {
                        result.put(code, new QuoteInfo(text(item, "f14"), decimal(text(item, "f2")), decimal(text(item, "f3"))));
                    }
                }
            }
            return result;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Map<String, QuoteInfo> tencentQuotes(List<String> secids) {
        Map<String, String> stockCodeByTencentCode = new LinkedHashMap<>();
        for (String secid : secids) {
            String tencentCode = tencentQuoteCode(secid);
            if (StringUtils.hasText(tencentCode)) {
                stockCodeByTencentCode.put(tencentCode, stockCode(secid));
            }
        }
        if (stockCodeByTencentCode.isEmpty()) {
            return Map.of();
        }
        String query = stockCodeByTencentCode.keySet().stream()
                .map(code -> "s_" + code)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        try {
            String body = getExternal(TENCENT_SOURCE_NAME, "stock_quotes_fallback",
                    TENCENT_QUOTE_URL + query, TENCENT_REFERER, true);
            Map<String, QuoteInfo> result = new HashMap<>();
            Matcher matcher = TENCENT_QUOTE_PATTERN.matcher(body);
            while (matcher.find()) {
                String stockCode = stockCodeByTencentCode.get(matcher.group(1));
                String[] fields = matcher.group(2).split("~", -1);
                if (!StringUtils.hasText(stockCode) || fields.length < 6) {
                    continue;
                }
                BigDecimal latestPrice = decimal(fields[3]);
                BigDecimal changeRate = decimal(fields[5]);
                if (latestPrice != null || changeRate != null) {
                    result.put(stockCode, new QuoteInfo(fields[1], latestPrice, changeRate));
                }
            }
            return result;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String tencentQuoteCode(String secid) {
        if (!StringUtils.hasText(secid)) {
            return null;
        }
        int separator = secid.indexOf('.');
        if (separator <= 0 || separator == secid.length() - 1) {
            return null;
        }
        String market = secid.substring(0, separator);
        String code = secid.substring(separator + 1);
        return switch (market) {
            case "1" -> "sh" + code;
            case "0" -> "sz" + code;
            case "116" -> "hk" + code;
            case "105", "106" -> "us" + code;
            default -> null;
        };
    }

    private String stockCode(String secid) {
        int separator = secid == null ? -1 : secid.indexOf('.');
        return separator >= 0 && separator < secid.length() - 1 ? secid.substring(separator + 1) : secid;
    }

    private String archiveContent(String body) throws Exception {
        Matcher matcher = Pattern.compile("content:\"(.*)\"\\s*(?:,|})", Pattern.DOTALL).matcher(body);
        if (!matcher.find()) {
            return body;
        }
        return objectMapper.readValue("\"" + matcher.group(1) + "\"", String.class);
    }

    private LocalDate reportDate(String html) {
        Matcher matcher = REPORT_DATE_PATTERN.matcher(html);
        if (matcher.find()) {
            return LocalDate.parse(matcher.group(1));
        }
        return null;
    }

    private List<StockRow> parseStockRows(String fundCode, String html, LocalDate reportDate) {
        List<StockRow> rows = new ArrayList<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            String row = rowMatcher.group(1);
            List<String> cells = cells(row);
            if (cells.size() < 7) {
                continue;
            }
            String marketSecId = marketSecId(row);
            String stockCode = clean(cells.get(1));
            String stockName = clean(cells.get(2));
            BigDecimal positionRate = decimal(clean(cells.get(6)));
            if (StringUtils.hasText(stockCode) && StringUtils.hasText(stockName) && positionRate != null) {
                rows.add(new StockRow(fundCode, stockCode, stockName, positionRate, marketSecId, reportDate));
            }
        }
        return rows;
    }

    private List<String> cells(String row) {
        List<String> result = new ArrayList<>();
        Matcher matcher = CELL_PATTERN.matcher(row);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private String marketSecId(String row) {
        Matcher matcher = SECID_PATTERN.matcher(row);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String clean(String html) {
        return html.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("（", "(")
                .replace("）", ")")
                .trim();
    }

    private Optional<String> jsString(String script, String variable) {
        Matcher matcher = Pattern.compile(String.format(JS_STRING_VAR.pattern(), variable)).matcher(script);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private String managerNames(String script) {
        JsonNode managers = jsJson(script, "Data_currentFundManager");
        if (!managers.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode manager : managers) {
            String name = text(manager, "name");
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join("、", names);
    }

    private String themeTags(String fundCode) {
        try {
            String tags = String.join(",", getRelatedThemes(fundCode).stream().map(FundThemeDTO::themeName).limit(5).toList());
            return StringUtils.hasText(tags) ? tags : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private JsonNode jsJson(String script, String variable) {
        String marker = "var " + variable + " =";
        int start = script.indexOf(marker);
        if (start < 0) {
            marker = "var " + variable + "=";
            start = script.indexOf(marker);
        }
        if (start < 0) {
            return objectMapper.missingNode();
        }
        int valueStart = script.indexOf('=', start) + 1;
        int valueEnd = script.indexOf(";", valueStart);
        if (valueEnd <= valueStart) {
            return objectMapper.missingNode();
        }
        try {
            return objectMapper.readTree(script.substring(valueStart, valueEnd).trim());
        } catch (Exception exception) {
            return objectMapper.missingNode();
        }
    }

    private RankPoint latestRank(String script) {
        JsonNode ranks = jsJson(script, "Data_rateInSimilarType");
        if (!ranks.isArray() || ranks.isEmpty()) {
            return null;
        }
        JsonNode latest = ranks.get(ranks.size() - 1);
        Integer rank = latest.path("y").isMissingNode() ? null : latest.path("y").asInt();
        Integer total = latest.path("sc").isMissingNode() ? null : latest.path("sc").asInt();
        return rank == null || total == null ? null : new RankPoint(rank, total);
    }

    private BigDecimal latestPercentile(String script) {
        JsonNode points = jsJson(script, "Data_rateInSimilarPersent");
        if (!points.isArray() || points.isEmpty()) {
            return null;
        }
        JsonNode latest = points.get(points.size() - 1);
        return latest.isArray() && latest.size() >= 2 ? latest.get(1).decimalValue() : null;
    }

    private BigDecimal weightedChangeRate(List<FundStockHoldingDTO> stocks) {
        BigDecimal weighted = ZERO;
        BigDecimal weight = ZERO;
        for (FundStockHoldingDTO stock : stocks) {
            if (stock.changeRate() == null || stock.positionRate() == null) {
                continue;
            }
            weighted = weighted.add(stock.positionRate().multiply(stock.changeRate()));
            weight = weight.add(stock.positionRate());
        }
        if (weight.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return weighted.divide(weight, 4, RoundingMode.HALF_UP);
    }

    private String normalizeFundType(String fundName, String rawType) {
        String name = fundName == null ? "" : fundName;
        String raw = rawType == null ? "" : rawType;
        if (name.contains("QDII") || raw.toUpperCase().contains("QDII")) return "QDII";
        if (name.contains("ETF联接")) return "ETF_LINK";
        if (name.contains("ETF")) return "ETF";
        if (name.contains("债")) return "BOND";
        if (name.contains("货币")) return "MONEY_MARKET";
        if (name.contains("指数") || raw.contains("指数")) return "INDEX";
        if (name.contains("股票") || raw.contains("股票")) return "ACTIVE_EQUITY";
        if (name.contains("混合") || raw.contains("混合")) return "MIXED";
        return StringUtils.hasText(raw) ? raw : "UNKNOWN";
    }

    private boolean activeFund(String fundName) {
        String name = fundName == null ? "" : fundName;
        return !(name.contains("指数") || name.contains("ETF") || name.contains("QDII"));
    }

    private String trackingIndex(String fundName) {
        if (fundName == null) return null;
        if (fundName.contains("电网设备") || fundName.contains("特高压")) return "中证电网设备";
        if (fundName.contains("白酒")) return "中证白酒";
        if (fundName.contains("沪深300")) return "沪深300";
        if (fundName.contains("中证")) return fundName.substring(fundName.indexOf("中证")).replaceAll("[A-Z（）()]+", "").trim();
        return null;
    }

    private String classifyIndustry(String stockName, String marketSecId, String fundCode) {
        String secid = marketSecId == null ? "" : marketSecId;
        String name = stockName == null ? "" : stockName;
        if (secid.startsWith("105.") || secid.startsWith("106.") || secid.startsWith("116.")) return "海外基金";
        if (containsAny(name, "特变电工", "平高电气", "许继电气", "国电南瑞", "思源电气", "中国西电", "四方股份", "东方电缆", "保变电气", "神马电力")) return "中证电网设备";
        if (containsAny(name, "沪电", "胜宏", "景旺", "东山精密", "鹏鼎", "生益", "深南电路", "崇达技术", "兴森科技", "世运电路", "奥士康")) return "PCB";
        if (containsAny(name, "中际旭创", "新易盛", "天孚通信", "光迅科技", "太辰光", "源杰科技", "剑桥科技", "联特科技")) return "CPO";
        if (containsAny(name, "长飞光纤", "亨通光电", "中天科技", "烽火通信", "永鼎股份", "通鼎互联")) return "光纤";
        if (containsAny(name, "润泽科技", "数据港", "奥飞数据", "光环新网", "首都在线", "优刻得", "云赛智联")) return "算力租赁";
        if (containsAny(name, "兆易创新", "北京君正", "江波龙", "佰维存储", "德明利", "澜起科技", "聚辰股份")) return "存储芯片";
        if (containsAny(name, "茅台", "五粮液", "老窖", "汾酒", "洋河", "古井", "今世缘", "口子窖")) return "白酒";
        if (containsAny(name, "宁德", "比亚迪", "亿纬", "阳光电源", "天齐", "赣锋", "隆基", "通威")) return "新能源";
        if (containsAny(name, "半导体", "芯片", "中芯", "北方华创", "韦尔", "兆易", "寒武纪")) return "半导体";
        if (containsAny(name, "恒瑞", "药明", "迈瑞", "爱尔", "片仔癀", "康龙")) return "医药";
        if (containsAny(name, "银行", "招商银行", "平安", "保险", "证券")) return "金融";
        return fundCode.startsWith("012") ? "海外基金" : "其他重仓";
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String get(String apiName, String url) {
        return getExternal(SOURCE_NAME, apiName, url, EAST_MONEY_REFERER, false);
    }

    private String getExternal(String provider, String apiName, String url, String referer, boolean fallbackUsed) {
        long start = System.currentTimeMillis();
        try {
            String body = fundDataWebClient.get()
                    .uri(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Referer", referer)
                    .header("Accept", "application/json,text/javascript,*/*;q=0.01")
                    .exchangeToMono(response -> response.body((message, context) -> DataBufferUtils.join(message.getBody())
                                    .map(this::toBytes))
                            .defaultIfEmpty(new byte[0])
                            .map(bytes -> {
                                String responseBody = decodeBody(bytes);
                                if (response.statusCode().isError()) {
                                    throw new FundDataSourceException("external fund api returned error: " + response.statusCode());
                                }
                                return responseBody;
                            }))
                    .timeout(Duration.ofMillis(properties.getFundDataSource().getTimeoutMs()))
                    .block();
            apiCallLogService.record(provider, apiName, url, "GET", true, 200, null,
                    System.currentTimeMillis() - start, fallbackUsed);
            return body;
        } catch (RuntimeException exception) {
            apiCallLogService.record(provider, apiName, url, "GET", false, null, exception.getMessage(),
                    System.currentTimeMillis() - start, fallbackUsed);
            throw exception;
        }
    }

    private byte[] toBytes(DataBuffer dataBuffer) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    private String decodeBody(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8.replace("\uFEFF", "");
        }
        return new String(bytes, GB18030).replace("\uFEFF", "");
    }

    private String stripJsonp(String body) {
        if (body == null) {
            return "{}";
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        int start = body.indexOf('(');
        int end = body.lastIndexOf(')');
        int firstObject = body.indexOf('{');
        int firstArray = body.indexOf('[');
        int firstJson = firstObject >= 0 && firstArray >= 0 ? Math.min(firstObject, firstArray) : Math.max(firstObject, firstArray);
        if (start >= 0 && end > start && (firstJson < 0 || start < firstJson)) {
            return body.substring(start + 1, end);
        }
        return body;
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.asText("");
            }
        }
        return "";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value) || "--".equals(value)) {
            return null;
        }
        return new BigDecimal(value.replace("%", "").replace(",", "").trim());
    }

    private String postEstimate(String url, String fundCode) {
        long start = System.currentTimeMillis();
        try {
            String body = fundDataWebClient.post()
                    .uri(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Referer", "https://fund.eastmoney.com/")
                    .header("Accept", "application/json,text/plain,*/*")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("FCODES", fundCode).with("FIELDS", ESTIMATE_FIELDS))
                    .exchangeToMono(response -> response.body((message, context) -> DataBufferUtils.join(message.getBody())
                                    .map(this::toBytes))
                            .defaultIfEmpty(new byte[0])
                            .map(bytes -> {
                                String responseBody = decodeBody(bytes);
                                if (response.statusCode().isError()) {
                                    throw new FundDataSourceException("external fund api returned error: " + response.statusCode());
                                }
                                return responseBody;
                            }))
                    .timeout(Duration.ofMillis(properties.getFundDataSource().getTimeoutMs()))
                    .block();
            apiCallLogService.record(SOURCE_NAME, "intraday_estimate", url, "POST", true, 200, null,
                    System.currentTimeMillis() - start, false);
            return body;
        } catch (RuntimeException exception) {
            apiCallLogService.record(SOURCE_NAME, "intraday_estimate", url, "POST", false, null,
                    exception.getMessage(), System.currentTimeMillis() - start, false);
            throw exception;
        }
    }

    private BigDecimal fundSizeFromYuan(String value) {
        BigDecimal yuan = decimal(value);
        if (yuan == null) {
            return null;
        }
        return yuan.divide(new BigDecimal("100000000"), 4, RoundingMode.HALF_UP);
    }

    private LocalDate parseProfileDate(String value) {
        if (!StringUtils.hasText(value) || value.length() < 10) {
            return null;
        }
        return LocalDate.parse(value.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }

    private LocalDateTime parseEstimateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (RuntimeException ignored) {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
    }

    private record StockRow(String fundCode, String stockCode, String stockName, BigDecimal positionRate,
                            String marketSecId, LocalDate reportDate) {
    }

    private record QuoteInfo(String name, BigDecimal latestPrice, BigDecimal changeRate) {
        static QuoteInfo empty() {
            return new QuoteInfo(null, null, null);
        }
    }

    private record CachedQuote(QuoteInfo quote, LocalDateTime cachedAt) {
        boolean isFresh(LocalDateTime now) {
            return cachedAt.plus(QUOTE_CACHE_TTL).isAfter(now);
        }
    }

    private record IndexThemeMapping(String code, String secid, String themeName) {
    }

    private record RankPoint(Integer rank, Integer total) {
    }
}
