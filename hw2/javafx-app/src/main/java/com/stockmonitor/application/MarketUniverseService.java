package com.stockmonitor.application;

import com.stockmonitor.domain.MarketPage;
import com.stockmonitor.domain.MarketQuote;
import com.stockmonitor.domain.Stock;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MarketUniverseService implements AutoCloseable {
    private static final int PAGE_SIZE = 200;
    private static final int SEARCH_LIMIT = 120;

    private final MarketDataProvider provider;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, MarketQuote> quoteCache = new LinkedHashMap<>();
    private final ObservableList<MarketQuote> visibleQuotes = FXCollections.observableArrayList();

    private volatile String query = "";
    private volatile int total;
    private volatile int nextPage = 1;
    private volatile boolean fullImportComplete;
    private volatile String status = "正在导入全 A 股";
    private volatile boolean seedOnly = true;

    public MarketUniverseService(MarketDataProvider provider) {
        this.provider = provider;
    }

    public ObservableList<MarketQuote> visibleQuotes() {
        return visibleQuotes;
    }

    public List<Stock> seedStocks() {
        return List.of(
                Stock.aShare("600519", "贵州茅台"),
                Stock.aShare("000001", "平安银行"),
                Stock.aShare("300750", "宁德时代"),
                Stock.aShare("600036", "招商银行"),
                Stock.aShare("688111", "金山办公")
        );
    }

    public void start() {
        publishSeedQuotes();
        scheduler.execute(this::loadAllPages);
        scheduler.scheduleWithFixedDelay(this::refreshNextPage, 10, 3, TimeUnit.SECONDS);
    }

    public void setQuery(String query) {
        this.query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        publishVisible();
    }

    public String statusText() {
        int loaded = quoteCache.size();
        if (seedOnly) {
            return "正在连接东方财富，先显示种子股票 " + loaded + " 支";
        }
        String suffix = fullImportComplete ? "已完成全量导入" : "全量导入中";
        return suffix + " " + loaded + "/" + Math.max(total, loaded) + " · " + status;
    }

    public MarketQuote cachedQuote(Stock stock) {
        return quoteCache.get(stock.code());
    }

    private void publishSeedQuotes() {
        merge(seedStocks().stream().map(MarketQuote::empty).toList());
        publishVisible();
    }

    private void loadAllPages() {
        try {
            int page = 1;
            while (!Thread.currentThread().isInterrupted()) {
                MarketPage marketPage = provider.marketPage(page, PAGE_SIZE);
                if (seedOnly) {
                    synchronized (this) {
                        quoteCache.clear();
                        seedOnly = false;
                    }
                }
                merge(marketPage.quotes());
                total = Math.max(total, marketPage.total());
                status = "批量行情页 " + page + "/" + Math.max(1, marketPage.totalPages());
                publishVisible();
                if (page >= marketPage.totalPages() || marketPage.quotes().isEmpty()) {
                    fullImportComplete = true;
                    nextPage = 1;
                    status = "全 A 股导入完成，后台分页刷新中";
                    publishVisible();
                    return;
                }
                page++;
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            status = "全市场导入暂不可用：" + ex.getMessage();
            publishVisible();
        }
    }

    private void refreshNextPage() {
        if (!fullImportComplete) {
            return;
        }
        try {
            MarketPage page = provider.marketPage(nextPage, PAGE_SIZE);
            merge(page.quotes());
            total = Math.max(total, page.total());
            status = "后台刷新第 " + nextPage + "/" + Math.max(1, page.totalPages()) + " 页";
            nextPage = nextPage >= Math.max(1, page.totalPages()) ? 1 : nextPage + 1;
            publishVisible();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            status = "后台刷新暂不可用：" + ex.getMessage();
            publishVisible();
        }
    }

    private synchronized void merge(List<MarketQuote> quotes) {
        for (MarketQuote quote : quotes) {
            quoteCache.put(quote.stock().code(), quote);
        }
    }

    private void publishVisible() {
        List<MarketQuote> snapshot = snapshot();
        Platform.runLater(() -> visibleQuotes.setAll(snapshot));
    }

    private synchronized List<MarketQuote> snapshot() {
        return quoteCache.values().stream()
                .filter(this::matches)
                .sorted(Comparator.comparingDouble((MarketQuote quote) -> Math.abs(quote.changeRate())).reversed())
                .limit(SEARCH_LIMIT)
                .toList();
    }

    private boolean matches(MarketQuote quote) {
        String q = query;
        return q.isBlank()
                || quote.stock().code().contains(q)
                || quote.stock().name().toLowerCase(Locale.ROOT).contains(q);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
