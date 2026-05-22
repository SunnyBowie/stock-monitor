package com.stockmonitor.domain;

import java.util.List;

public record MarketPage(
        int pageNumber,
        int pageSize,
        int total,
        List<MarketQuote> quotes
) {
    public int totalPages() {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil(total / (double) pageSize);
    }
}
