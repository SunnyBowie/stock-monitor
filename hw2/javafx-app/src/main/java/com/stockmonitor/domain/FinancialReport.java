package com.stockmonitor.domain;

import java.util.List;

public record FinancialReport(
        double pe,
        double pb,
        double roePercent,
        double grossMarginPercent,
        double debtRatioPercent,
        double marketCap,
        double operatingCashFlow,
        double totalAssets,
        double totalLiabilities,
        double totalEquity,
        double cashAndEquivalent,
        List<QuarterValue> revenue,
        List<QuarterValue> netProfit
) {
    public static FinancialReport empty() {
        return new FinancialReport(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    public int pePercentileEstimate() {
        if (pe <= 0) {
            return 0;
        }
        return (int) Math.max(5, Math.min(95, Math.round(pe / 80.0 * 100.0)));
    }
}
