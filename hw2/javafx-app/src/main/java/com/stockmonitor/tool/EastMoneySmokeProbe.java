package com.stockmonitor.tool;

import com.stockmonitor.domain.Stock;
import com.stockmonitor.infrastructure.EastMoneyMarketDataProvider;

public final class EastMoneySmokeProbe {
    private EastMoneySmokeProbe() {
    }

    public static void main(String[] args) throws Exception {
        EastMoneyMarketDataProvider provider = new EastMoneyMarketDataProvider();
        var page = provider.marketPage(1, 3);
        System.out.printf("market total=%d page=%d size=%d first=%s %s %.2f %.2f%%%n",
                page.total(),
                page.pageNumber(),
                page.quotes().size(),
                page.quotes().getFirst().stock().code(),
                page.quotes().getFirst().stock().name(),
                page.quotes().getFirst().currentPrice(),
                page.quotes().getFirst().changeRate());

        var quote = provider.quote(Stock.aShare("600519", "贵州茅台"));
        System.out.printf("quote %s %s price=%.2f change=%.2f%% time=%s%n",
                quote.stock().code(),
                quote.stock().name(),
                quote.currentPrice(),
                quote.changeRate(),
                quote.quoteTime());
        System.out.println("intraday points=" + provider.intraday(quote.stock()).size());
        System.out.println("candles=" + provider.candles(quote.stock(), 30).size());
        System.out.println("finance revenue quarters=" + quote.financialReport().revenue().size());
        System.out.println("finance net profit quarters=" + quote.financialReport().netProfit().size());
    }
}
