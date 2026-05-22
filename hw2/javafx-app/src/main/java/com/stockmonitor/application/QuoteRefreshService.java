package com.stockmonitor.application;

import com.stockmonitor.domain.MarketQuote;
import com.stockmonitor.domain.Stock;

import java.io.IOException;
import java.util.List;

public final class QuoteRefreshService {
    private final MarketDataProvider provider;

    public QuoteRefreshService(MarketDataProvider provider) {
        this.provider = provider;
    }

    public List<MarketQuote> refresh(List<Stock> stocks) throws IOException, InterruptedException {
        return stocks.stream()
                .map(this::safeQuote)
                .toList();
    }

    private MarketQuote safeQuote(Stock stock) {
        try {
            return provider.quote(stock);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return MarketQuote.empty(stock);
        }
    }
}
