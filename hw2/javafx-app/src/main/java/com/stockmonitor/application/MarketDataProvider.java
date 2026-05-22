package com.stockmonitor.application;

import com.stockmonitor.domain.Candle;
import com.stockmonitor.domain.IntradayPoint;
import com.stockmonitor.domain.MarketPage;
import com.stockmonitor.domain.MarketQuote;
import com.stockmonitor.domain.Stock;

import java.io.IOException;
import java.util.List;

public interface MarketDataProvider {
    MarketPage marketPage(int pageNumber, int pageSize) throws IOException, InterruptedException;

    MarketQuote quote(Stock stock) throws IOException, InterruptedException;

    List<IntradayPoint> intraday(Stock stock) throws IOException, InterruptedException;

    List<Candle> candles(Stock stock, int days) throws IOException, InterruptedException;
}
