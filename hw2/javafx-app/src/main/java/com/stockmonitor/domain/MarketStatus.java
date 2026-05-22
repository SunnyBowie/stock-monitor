package com.stockmonitor.domain;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

public enum MarketStatus {
    PRE_OPEN("未开盘"),
    TRADING("交易中"),
    LUNCH_BREAK("午间休市"),
    CLOSED("已收盘"),
    WEEKEND("非交易日");

    private final String label;

    MarketStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static MarketStatus now(LocalDateTime dateTime) {
        DayOfWeek day = dateTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return WEEKEND;
        }
        LocalTime time = dateTime.toLocalTime();
        if (time.isBefore(LocalTime.of(9, 30))) {
            return PRE_OPEN;
        }
        if (!time.isBefore(LocalTime.of(9, 30)) && !time.isAfter(LocalTime.of(11, 30))) {
            return TRADING;
        }
        if (time.isAfter(LocalTime.of(11, 30)) && time.isBefore(LocalTime.of(13, 0))) {
            return LUNCH_BREAK;
        }
        if (!time.isBefore(LocalTime.of(13, 0)) && !time.isAfter(LocalTime.of(15, 0))) {
            return TRADING;
        }
        return CLOSED;
    }
}
