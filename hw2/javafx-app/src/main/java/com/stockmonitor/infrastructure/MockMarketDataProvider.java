package com.stockmonitor.infrastructure;

import com.stockmonitor.application.MarketDataProvider;
import com.stockmonitor.domain.Candle;
import com.stockmonitor.domain.FinancialReport;
import com.stockmonitor.domain.IntradayPoint;
import com.stockmonitor.domain.MarketPage;
import com.stockmonitor.domain.MarketQuote;
import com.stockmonitor.domain.OrderBook;
import com.stockmonitor.domain.QuarterValue;
import com.stockmonitor.domain.Stock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class MockMarketDataProvider implements MarketDataProvider {
    private final Random random = new Random(20260522);

    @Override
    public MarketPage marketPage(int pageNumber, int pageSize) {
        List<Stock> stocks = List.of(
                Stock.aShare("600519", "贵州茅台"),
                Stock.aShare("000001", "平安银行"),
                Stock.aShare("300750", "宁德时代"),
                Stock.aShare("600036", "招商银行"),
                Stock.aShare("688111", "金山办公")
        );
        return new MarketPage(pageNumber, pageSize, stocks.size(), stocks.stream().map(this::quote).toList());
    }

    @Override
    public MarketQuote quote(Stock stock) {
        double base = switch (stock.code()) {
            case "600519" -> 1290.20;
            case "000001" -> 10.69;
            case "300750" -> 411.55;
            case "600036" -> 38.42;
            default -> 88.80;
        };
        double price = Math.max(0.01, base * (1 + (random.nextDouble() - 0.48) * 0.018));
        double previous = base * 0.992;
        double change = price - previous;
        double pct = previous == 0 ? 0 : change / previous * 100;
        FinancialReport report = new FinancialReport(
                18 + random.nextDouble() * 35,
                1.2 + random.nextDouble() * 5,
                10 + random.nextDouble() * 22,
                18 + random.nextDouble() * 55,
                22 + random.nextDouble() * 40,
                price * 100_000_000,
                price * 320_000,
                price * 130_000_000,
                price * 28_000_000,
                price * 102_000_000,
                price * 18_000_000,
                quarterValues(price, 0.72),
                quarterValues(price, 0.18)
        );
        return new MarketQuote(
                stock,
                round(price),
                round(previous),
                round(base),
                round(Math.max(base, price) * 1.006),
                round(Math.min(base, price) * 0.994),
                120_000 + random.nextInt(800_000),
                price * 35_000_000,
                round(change),
                round(pct),
                LocalDateTime.now(),
                new OrderBook(round(price - 0.02), 2300, round(price + 0.02), 1800),
                report
        );
    }

    @Override
    public List<IntradayPoint> intraday(Stock stock) {
        double base = quote(stock).currentPrice();
        List<IntradayPoint> points = new ArrayList<>();
        LocalTime start = LocalTime.of(9, 30);
        for (int i = 0; i < 120; i++) {
            double wave = Math.sin(i / 9.0) * base * 0.006;
            double noise = (random.nextDouble() - 0.5) * base * 0.003;
            double price = round(base + wave + noise);
            points.add(new IntradayPoint(start.plusMinutes(i), price, round(base + wave * 0.45), 400 + random.nextInt(3000)));
        }
        return points;
    }

    @Override
    public List<Candle> candles(Stock stock, int days) {
        double base = quote(stock).currentPrice();
        List<Candle> candles = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(days);
        double last = base * 0.92;
        for (int i = 0; i < days; i++) {
            double open = last * (1 + (random.nextDouble() - 0.5) * 0.025);
            double close = open * (1 + (random.nextDouble() - 0.5) * 0.038);
            double high = Math.max(open, close) * (1 + random.nextDouble() * 0.018);
            double low = Math.min(open, close) * (1 - random.nextDouble() * 0.018);
            candles.add(new Candle(start.plusDays(i), round(open), round(close), round(high), round(low), 50_000 + random.nextInt(400_000)));
            last = close;
        }
        return candles;
    }

    private List<QuarterValue> quarterValues(double base, double ratio) {
        return List.of(
                new QuarterValue("Q1", round(base * ratio * 0.78)),
                new QuarterValue("Q2", round(base * ratio * 0.88)),
                new QuarterValue("Q3", round(base * ratio * 0.94)),
                new QuarterValue("Q4", round(base * ratio))
        );
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
