package com.stockmonitor.domain;

import java.util.Objects;

public final class Stock {
    private final String code;
    private final String name;
    private final Market market;

    public Stock(String code, String name, Market market) {
        this.code = normalizeCode(code);
        this.name = Objects.requireNonNullElse(name, "").trim();
        this.market = Objects.requireNonNull(market, "market");
        if (this.code.isBlank()) {
            throw new IllegalArgumentException("stock code must not be blank");
        }
        if (this.name.isBlank()) {
            throw new IllegalArgumentException("stock name must not be blank");
        }
    }

    public static Stock aShare(String code, String name) {
        return new Stock(code, name, Market.fromCode(code));
    }

    private static String normalizeCode(String code) {
        return Objects.requireNonNullElse(code, "").trim();
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public Market market() {
        return market;
    }

    public String secid() {
        return market.prefix() + "." + code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Stock stock)) {
            return false;
        }
        return code.equals(stock.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }
}
