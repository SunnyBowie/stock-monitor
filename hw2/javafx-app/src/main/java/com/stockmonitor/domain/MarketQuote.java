package com.stockmonitor.domain;

import java.time.LocalDateTime;

public record MarketQuote(
        Stock stock,
        double currentPrice,
        double previousClose,
        double openPrice,
        double highPrice,
        double lowPrice,
        long volume,
        double amount,
        double changeAmount,
        double changeRate,
        LocalDateTime quoteTime,
        OrderBook orderBook,
        FinancialReport financialReport
) {
    public boolean isUp() {
        return changeAmount >= 0;
    }

    public static MarketQuote empty(Stock stock) {
        return new MarketQuote(
                stock, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                LocalDateTime.now(), OrderBook.empty(), FinancialReport.empty()
        );
    }
}
