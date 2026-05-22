package com.stockmonitor.application;

import com.stockmonitor.domain.Stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WatchlistService {
    private final List<Stock> stocks = new ArrayList<>();

    public WatchlistService() {
    }

    public WatchlistService(List<Stock> initialStocks) {
        initialStocks.forEach(this::add);
    }

    public List<Stock> list() {
        return Collections.unmodifiableList(stocks);
    }

    public void add(Stock stock) {
        if (stocks.stream().noneMatch(item -> item.code().equals(stock.code()))) {
            stocks.add(stock);
        }
    }

    public void remove(Stock stock) {
        stocks.remove(stock);
    }
}
