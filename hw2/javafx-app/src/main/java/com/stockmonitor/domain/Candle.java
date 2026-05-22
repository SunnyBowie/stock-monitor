package com.stockmonitor.domain;

import java.time.LocalDate;

public record Candle(LocalDate date, double open, double close, double high, double low, long volume) {
}
