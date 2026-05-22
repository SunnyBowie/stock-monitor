package com.stockmonitor.domain;

public enum Market {
    SH("1", "上证"),
    SZ("0", "深证");

    private final String prefix;
    private final String displayName;

    Market(String prefix, String displayName) {
        this.prefix = prefix;
        this.displayName = displayName;
    }

    public String prefix() {
        return prefix;
    }

    public String displayName() {
        return displayName;
    }

    public static Market fromCode(String code) {
        String normalized = code == null ? "" : code.trim();
        if (normalized.startsWith("6") || normalized.startsWith("9")) {
            return SH;
        }
        return SZ;
    }
}
