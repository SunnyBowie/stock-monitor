package com.stockmonitor.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmonitor.application.MarketDataProvider;
import com.stockmonitor.domain.Candle;
import com.stockmonitor.domain.FinancialReport;
import com.stockmonitor.domain.IntradayPoint;
import com.stockmonitor.domain.MarketPage;
import com.stockmonitor.domain.MarketQuote;
import com.stockmonitor.domain.OrderBook;
import com.stockmonitor.domain.QuarterValue;
import com.stockmonitor.domain.Stock;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class EastMoneyMarketDataProvider implements MarketDataProvider {
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private static final String QUOTE_URL = "https://push2.eastmoney.com/api/qt/stock/get";
    private static final String MARKET_URL = "https://push2.eastmoney.com/api/qt/clist/get";
    private static final String ULIST_URL = "https://push2.eastmoney.com/api/qt/ulist.np/get";
    private static final String TRENDS_URL = "https://push2his.eastmoney.com/api/qt/stock/trends2/get";
    private static final String KLINE_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get";
    private static final String REPORT_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get";
    private static final String A_SHARE_FILTER = "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23";
    private static final String MARKET_FIELDS = String.join(",",
            "f2", "f3", "f4", "f5", "f6", "f12", "f14", "f15", "f16", "f17", "f18",
            "f31", "f32", "f34", "f35", "f37", "f40", "f124");

    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public EastMoneyMarketDataProvider() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    @Override
    public MarketPage marketPage(int pageNumber, int pageSize) throws IOException, InterruptedException {
        URI uri = uri(MARKET_URL,
                "pn", Integer.toString(Math.max(1, pageNumber)),
                "pz", Integer.toString(Math.max(1, pageSize)),
                "po", "1",
                "np", "1",
                "fltt", "2",
                "invt", "2",
                "fid", "f3",
                "fs", A_SHARE_FILTER,
                "fields", MARKET_FIELDS);
        JsonNode data = get(uri).path("data");
        int total = data.path("total").asInt(0);
        List<MarketQuote> quotes = parseQuoteList(data.path("diff"));
        return new MarketPage(pageNumber, pageSize, total, quotes);
    }

    @Override
    public MarketQuote quote(Stock stock) throws IOException, InterruptedException {
        String fields = String.join(",",
                "f43", "f44", "f45", "f46", "f47", "f48", "f57", "f58", "f59", "f60",
                "f86", "f169", "f170", "f162", "f167", "f173", "f183", "f184", "f186", "f116");
        URI uri = uri(QUOTE_URL, "secid", stock.secid(), "fields", fields);
        JsonNode data = get(uri).path("data");
        if (data.isMissingNode() || data.isNull()) {
            return MarketQuote.empty(stock);
        }

        Stock realStock = Stock.aShare(text(data, "f57", stock.code()), text(data, "f58", stock.name()));
        int decimals = data.path("f59").asInt(2);
        double price = scaledPrice(data.path("f43").asDouble(0), decimals);
        double previousClose = scaledPrice(data.path("f60").asDouble(0), decimals);
        double open = scaledPrice(data.path("f46").asDouble(0), decimals);
        double high = scaledPrice(data.path("f44").asDouble(0), decimals);
        double low = scaledPrice(data.path("f45").asDouble(0), decimals);
        double change = scaledPrice(data.path("f169").asDouble(0), decimals);
        double pct = data.path("f170").asDouble(0) / 100.0;
        LocalDateTime quoteTime = quoteTime(data.path("f86").asLong(0));
        MarketQuote batchQuote = quoteFromUList(realStock);
        OrderBook orderBook = batchQuote.orderBook();
        FinancialReport financialReport = financialReport(realStock, data);

        return new MarketQuote(
                realStock,
                price,
                previousClose,
                open,
                high,
                low,
                data.path("f47").asLong(0),
                data.path("f48").asDouble(0),
                change,
                pct,
                quoteTime,
                orderBook,
                financialReport
        );
    }

    @Override
    public List<IntradayPoint> intraday(Stock stock) throws IOException, InterruptedException {
        URI uri = uri(TRENDS_URL,
                "secid", stock.secid(),
                "fields1", "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13",
                "fields2", "f51,f52,f53,f54,f55,f56,f57,f58",
                "ndays", "1",
                "iscr", "0",
                "iscca", "0");
        JsonNode trends = get(uri).path("data").path("trends");
        List<IntradayPoint> points = new ArrayList<>();
        if (!trends.isArray()) {
            return points;
        }
        for (JsonNode item : trends) {
            String[] parts = item.asText().split(",");
            if (parts.length < 8) {
                continue;
            }
            LocalTime time = LocalDateTime.parse(parts[0].replace(" ", "T")).toLocalTime();
            double close = parseDouble(parts[2]);
            long volume = (long) parseDouble(parts[5]);
            double average = parseDouble(parts[7]);
            points.add(new IntradayPoint(time, close, average, volume));
        }
        return points;
    }

    @Override
    public List<Candle> candles(Stock stock, int days) throws IOException, InterruptedException {
        URI uri = uri(KLINE_URL,
                "secid", stock.secid(),
                "fields1", "f1,f2,f3,f4,f5,f6",
                "fields2", "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
                "klt", "101",
                "fqt", "1",
                "beg", "20240101",
                "end", "20500101",
                "lmt", Integer.toString(Math.max(days, 30)));
        JsonNode klines = get(uri).path("data").path("klines");
        List<Candle> candles = new ArrayList<>();
        if (!klines.isArray()) {
            return candles;
        }
        for (JsonNode item : klines) {
            String[] parts = item.asText().split(",");
            if (parts.length < 6) {
                continue;
            }
            candles.add(new Candle(
                    LocalDate.parse(parts[0]),
                    parseDouble(parts[1]),
                    parseDouble(parts[2]),
                    parseDouble(parts[3]),
                    parseDouble(parts[4]),
                    (long) parseDouble(parts[5])
            ));
        }
        return candles;
    }

    private MarketQuote quoteFromUList(Stock stock) throws IOException, InterruptedException {
        URI uri = uri(ULIST_URL,
                "secids", stock.secid(),
                "fltt", "2",
                "invt", "2",
                "fields", MARKET_FIELDS);
        List<MarketQuote> quotes = parseQuoteList(get(uri).path("data").path("diff"));
        return quotes.isEmpty() ? MarketQuote.empty(stock) : quotes.getFirst();
    }

    private List<MarketQuote> parseQuoteList(JsonNode rows) {
        List<MarketQuote> quotes = new ArrayList<>();
        if (!rows.isArray()) {
            return quotes;
        }
        for (JsonNode item : rows) {
            String code = text(item, "f12", "");
            String name = text(item, "f14", "");
            if (code.isBlank() || name.isBlank()) {
                continue;
            }
            Stock stock = Stock.aShare(code, name);
            double price = number(item, "f2");
            double previousClose = number(item, "f18");
            double change = number(item, "f4");
            double pct = number(item, "f3");
            double open = number(item, "f17");
            double high = number(item, "f15");
            double low = number(item, "f16");
            OrderBook orderBook = new OrderBook(
                    number(item, "f31"),
                    item.path("f34").asLong(0),
                    number(item, "f32"),
                    item.path("f35").asLong(0)
            );
            FinancialReport report = new FinancialReport(
                    0,
                    0,
                    number(item, "f37"),
                    0,
                    0,
                    number(item, "f40"),
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of()
            );
            quotes.add(new MarketQuote(
                    stock,
                    price,
                    previousClose,
                    open,
                    high,
                    low,
                    item.path("f5").asLong(0),
                    number(item, "f6"),
                    change,
                    pct,
                    quoteTime(item.path("f124").asLong(0)),
                    orderBook,
                    report
            ));
        }
        return quotes;
    }

    private FinancialReport financialReport(Stock stock, JsonNode quoteData) {
        JsonNode incomeRows = reportRows(stock, "RPT_DMSK_FN_INCOME", 4);
        JsonNode balance = firstRow(reportRows(stock, "RPT_DMSK_FN_BALANCE", 1));
        JsonNode cashflow = firstRow(reportRows(stock, "RPT_DMSK_FN_CASHFLOW", 1));
        return new FinancialReport(
                quoteData.path("f162").asDouble(0) / 100.0,
                quoteData.path("f167").asDouble(0) / 100.0,
                quoteData.path("f173").asDouble(0),
                quoteData.path("f186").asDouble(0),
                number(balance, "DEBT_ASSET_RATIO"),
                number(quoteData, "f116"),
                number(cashflow, "NETCASH_OPERATE"),
                number(balance, "TOTAL_ASSETS"),
                number(balance, "TOTAL_LIABILITIES"),
                number(balance, "TOTAL_EQUITY"),
                number(balance, "MONETARYFUNDS"),
                reportSeries(incomeRows, "TOTAL_OPERATE_INCOME"),
                reportSeries(incomeRows, "PARENT_NETPROFIT")
        );
    }

    private JsonNode firstRow(JsonNode rows) {
        return rows.isArray() && !rows.isEmpty() ? rows.get(0) : mapper.createObjectNode();
    }

    private List<QuarterValue> reportSeries(JsonNode rows, String field) {
        List<QuarterValue> values = new ArrayList<>();
        if (!rows.isArray()) {
            return values;
        }
        for (int i = rows.size() - 1; i >= 0; i--) {
            JsonNode row = rows.get(i);
            double value = number(row, field);
            if (value > 0) {
                values.add(new QuarterValue(reportLabel(text(row, "REPORT_DATE", "")), round(value / 100_000_000.0)));
            }
        }
        return values;
    }

    private JsonNode reportRows(Stock stock, String reportName, int pageSize) {
        try {
            URI uri = uri(REPORT_URL,
                    "sortColumns", "REPORT_DATE",
                    "sortTypes", "-1",
                    "pageSize", Integer.toString(pageSize),
                    "pageNumber", "1",
                    "reportName", reportName,
                    "columns", "ALL",
                    "filter", "(SECURITY_CODE=\"" + stock.code() + "\")");
            return get(uri).path("result").path("data");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return mapper.createArrayNode();
        }
    }

    private JsonNode get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 StockMonitorJavaFX/0.4")
                .header("Referer", "https://quote.eastmoney.com/")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " from " + uri);
        }
        return mapper.readTree(response.body());
    }

    private static URI uri(String base, String... pairs) {
        StringBuilder query = new StringBuilder();
        for (int index = 0; index < pairs.length; index += 2) {
            if (!query.isEmpty()) {
                query.append("&");
            }
            query.append(encode(pairs[index])).append("=").append(encode(pairs[index + 1]));
        }
        return URI.create(base + "?" + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
    }

    private static double number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return 0;
        }
        if (value.isNumber()) {
            return value.asDouble(0);
        }
        String text = value.asText();
        return text.equals("-") ? 0 : parseDouble(text);
    }

    private static double scaledPrice(double raw, int decimals) {
        if (raw == 0 || raw == -1) {
            return 0;
        }
        return raw / Math.pow(10, Math.max(0, decimals));
    }

    private static LocalDateTime quoteTime(long epochSeconds) {
        if (epochSeconds <= 0) {
            return LocalDateTime.now(CHINA);
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), CHINA);
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String reportLabel(String reportDate) {
        if (reportDate == null || reportDate.length() < 10) {
            return "--";
        }
        String month = reportDate.substring(5, 7);
        return switch (month) {
            case "03" -> "Q1";
            case "06" -> "Q2";
            case "09" -> "Q3";
            case "12" -> "Q4";
            default -> reportDate.substring(5, 10);
        };
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
