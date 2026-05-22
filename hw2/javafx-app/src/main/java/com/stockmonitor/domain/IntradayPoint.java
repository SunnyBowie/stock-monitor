package com.stockmonitor.domain;

import java.time.LocalTime;

public record IntradayPoint(LocalTime time, double price, double averagePrice, long volume) {
}
