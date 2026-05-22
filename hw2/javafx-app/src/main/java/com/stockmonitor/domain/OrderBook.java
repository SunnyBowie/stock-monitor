package com.stockmonitor.domain;

public record OrderBook(double bidPrice, long bidVolume, double askPrice, long askVolume) {
    public static OrderBook empty() {
        return new OrderBook(0, 0, 0, 0);
    }
}
