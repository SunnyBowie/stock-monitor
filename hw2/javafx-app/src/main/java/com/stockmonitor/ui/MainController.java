package com.stockmonitor.ui;

import com.stockmonitor.application.MarketDataProvider;
import com.stockmonitor.application.MarketUniverseService;
import com.stockmonitor.application.WatchlistService;
import com.stockmonitor.domain.Candle;
import com.stockmonitor.domain.FinancialReport;
import com.stockmonitor.domain.IntradayPoint;
import com.stockmonitor.domain.MarketQuote;
import com.stockmonitor.domain.MarketStatus;
import com.stockmonitor.domain.QuarterValue;
import com.stockmonitor.domain.Stock;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MainController {
    private static final DecimalFormat PRICE = new DecimalFormat("0.00");
    private static final DecimalFormat PCT = new DecimalFormat("+0.00;-0.00");

    private final MarketDataProvider provider;
    private final WatchlistService watchlist;
    private final MarketUniverseService universe;
    private final Stage stage;
    private final BorderPane root = new BorderPane();
    private final ListView<MarketQuote> stockList = new ListView<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Label statusLabel = new Label("准备加载真实行情");
    private final Label stockNameLabel = new Label("--");
    private final Label stockCodeLabel = new Label("--");
    private final Label priceLabel = new Label("--");
    private final Label changeLabel = new Label("--");
    private final Label timeLabel = new Label("--");
    private final GridPane metricGrid = new GridPane();
    private final GridPane financeGrid = new GridPane();
    private final GridPane orderBookGrid = new GridPane();
    private final VBox reportRows = new VBox(8);
    private final Label reportNote = new Label("");
    private final Canvas chartCanvas = new Canvas(760, 360);
    private final Canvas financeCanvas = new Canvas(360, 150);
    private final Button intradayButton = new Button("分时");
    private final Button candleButton = new Button("K 线");
    private final Button incomeButton = new Button("利润表");
    private final Button balanceButton = new Button("资产负债");
    private final Button cashflowButton = new Button("现金流");

    private volatile Stock selectedStock;
    private volatile ChartMode chartMode = ChartMode.INTRADAY;
    private volatile String reportMode = "income";
    private double titleDragOffsetX;
    private double titleDragOffsetY;

    public MainController(MarketDataProvider provider, WatchlistService watchlist, MarketUniverseService universe, Stage stage) {
        this.provider = provider;
        this.watchlist = watchlist;
        this.universe = universe;
        this.stage = stage;
        this.selectedStock = watchlist.list().getFirst();
        buildView();
    }

    public Parent view() {
        return root;
    }

    public void start() {
        universe.start();
        refreshNow();
        scheduler.scheduleAtFixedRate(this::refreshNow, 8, 8, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
        universe.close();
    }

    private void buildView() {
        root.getStyleClass().add("app");
        root.setTop(buildTitleBar());
        root.setLeft(buildSidebar());
        root.setCenter(buildDetailPane());
        root.setBottom(statusLabel);
        BorderPane.setMargin(statusLabel, new Insets(0, 26, 18, 26));
        statusLabel.getStyleClass().add("status");
    }

    private Node buildTitleBar() {
        Label appName = new Label("Stock Monitor");
        appName.getStyleClass().add("titlebar-name");
        Label source = new Label("东方财富实时行情");
        source.getStyleClass().add("titlebar-source");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimize = new Button("—");
        minimize.getStyleClass().add("window-button");
        minimize.setOnAction(event -> stage.setIconified(true));

        Button close = new Button("×");
        close.getStyleClass().addAll("window-button", "close-button");
        close.setOnAction(event -> stage.close());

        HBox titlebar = new HBox(12, appName, source, spacer, minimize, close);
        titlebar.getStyleClass().add("titlebar");
        titlebar.setAlignment(Pos.CENTER_LEFT);
        titlebar.setOnMousePressed(event -> {
            titleDragOffsetX = event.getSceneX();
            titleDragOffsetY = event.getSceneY();
        });
        titlebar.setOnMouseDragged(event -> {
            if (!stage.isMaximized()) {
                stage.setX(event.getScreenX() - titleDragOffsetX);
                stage.setY(event.getScreenY() - titleDragOffsetY);
            }
        });
        return titlebar;
    }

    private Node buildSidebar() {
        VBox sidebar = new VBox(18);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(24, 18, 24, 24));
        sidebar.setPrefWidth(360);

        Label title = new Label("全 A 股");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("A 股 · EastMoney Live");
        subtitle.getStyleClass().add("muted");

        HBox header = new HBox(12, new VBox(4, subtitle, title));
        header.setAlignment(Pos.CENTER_LEFT);

        TextField search = new TextField();
        search.setPromptText("搜索股票代码 / 简称");
        search.getStyleClass().add("search");

        stockList.setItems(universe.visibleQuotes());
        stockList.getStyleClass().add("watchlist");
        stockList.setCellFactory(list -> new StockCell());
        VBox.setVgrow(stockList, Priority.ALWAYS);
        stockList.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value != null) {
                selectedStock = value.stock();
                watchlist.add(value.stock());
                refreshNow();
            }
        });

        search.textProperty().addListener((obs, old, text) -> universe.setQuery(text));

        sidebar.getChildren().addAll(header, search, stockList);
        return sidebar;
    }

    private Node buildDetailPane() {
        VBox content = new VBox(18);
        content.setPadding(new Insets(24, 32, 32, 32));
        content.getStyleClass().add("content");

        HBox hero = new HBox(24);
        hero.setAlignment(Pos.TOP_LEFT);
        VBox heroText = new VBox(8, stockCodeLabel, stockNameLabel, priceLabel, changeLabel, timeLabel);
        HBox.setHgrow(heroText, Priority.ALWAYS);
        stockCodeLabel.getStyleClass().add("muted");
        stockNameLabel.getStyleClass().add("detail-title");
        priceLabel.getStyleClass().add("hero-price");
        changeLabel.getStyleClass().add("change");
        timeLabel.getStyleClass().add("muted");

        HBox chartSwitch = segmented(intradayButton, candleButton);
        intradayButton.setOnAction(event -> {
            chartMode = ChartMode.INTRADAY;
            refreshNow();
        });
        candleButton.setOnAction(event -> {
            chartMode = ChartMode.CANDLE;
            refreshNow();
        });
        hero.getChildren().addAll(heroText, chartSwitch);

        VBox chartCard = card();
        StackPane chartWrap = new StackPane(chartCanvas);
        chartWrap.getStyleClass().add("canvas-wrap");
        chartCanvas.widthProperty().bind(chartWrap.widthProperty());
        chartCanvas.heightProperty().bind(chartWrap.heightProperty());
        chartCard.getChildren().add(chartWrap);
        VBox.setVgrow(chartWrap, Priority.ALWAYS);

        HBox lower = new HBox(16);
        VBox left = new VBox(16);
        VBox right = new VBox(16);
        HBox.setHgrow(left, Priority.ALWAYS);
        left.getChildren().addAll(metricsCard(), financeCard());
        right.getChildren().addAll(orderBookCard(), reportCard());
        lower.getChildren().addAll(left, right);

        content.getChildren().addAll(hero, chartCard, lower);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("scroll");
        return scrollPane;
    }

    private VBox metricsCard() {
        VBox card = card();
        card.getChildren().add(sectionTitle("关键数据", "今日"));
        metricGrid.setHgap(10);
        metricGrid.setVgap(10);
        card.getChildren().add(metricGrid);
        return card;
    }

    private VBox orderBookCard() {
        VBox card = card();
        card.setPrefWidth(310);
        card.getChildren().add(sectionTitle("盘口", "买一 / 卖一"));
        orderBookGrid.setHgap(12);
        orderBookGrid.setVgap(8);
        card.getChildren().add(orderBookGrid);
        return card;
    }

    private VBox financeCard() {
        VBox card = card();
        card.getChildren().add(sectionTitle("财务报表", "近四季"));
        HBox legend = new HBox(14, chip("营收", "blue-dot"), chip("净利润", "amber-dot"));
        StackPane financeWrap = new StackPane(financeCanvas);
        financeWrap.getStyleClass().add("finance-canvas-wrap");
        financeCanvas.widthProperty().bind(financeWrap.widthProperty());
        financeCanvas.heightProperty().bind(financeWrap.heightProperty());
        financeGrid.setHgap(10);
        financeGrid.setVgap(10);
        card.getChildren().addAll(legend, financeWrap, financeGrid);
        return card;
    }

    private VBox reportCard() {
        VBox card = card();
        card.setPrefWidth(310);
        card.getChildren().add(sectionTitle("摘要报表", "可切换"));
        HBox tabs = segmented(incomeButton, balanceButton, cashflowButton);
        incomeButton.setOnAction(event -> {
            reportMode = "income";
            refreshNow();
        });
        balanceButton.setOnAction(event -> {
            reportMode = "balance";
            refreshNow();
        });
        cashflowButton.setOnAction(event -> {
            reportMode = "cashflow";
            refreshNow();
        });
        reportNote.getStyleClass().add("muted");
        card.getChildren().addAll(tabs, reportRows, reportNote);
        return card;
    }

    private HBox sectionTitle(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        Label subLabel = new Label(subtitle);
        subLabel.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(10, titleLabel, spacer, subLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private VBox card() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        return card;
    }

    private HBox segmented(Button... buttons) {
        HBox box = new HBox(4);
        box.getStyleClass().add("segmented");
        for (Button button : buttons) {
            button.getStyleClass().add("segment");
            box.getChildren().add(button);
        }
        return box;
    }

    private HBox chip(String text, String dotClass) {
        Region dot = new Region();
        dot.getStyleClass().addAll("dot", dotClass);
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        HBox chip = new HBox(6, dot, label);
        chip.setAlignment(Pos.CENTER_LEFT);
        return chip;
    }

    private void refreshNow() {
        Stock stock = selectedStock;
        if (stock == null) {
            return;
        }
        MarketQuote quote = universe.cachedQuote(stock);
        String error = null;
        List<IntradayPoint> intraday = List.of();
        List<Candle> candles = List.of();
        try {
            quote = provider.quote(stock);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            error = "报价请求失败：" + ex.getMessage();
        }
        boolean hasLiveQuote = quote != null && quote.currentPrice() > 0;
        if (quote == null) {
            quote = MarketQuote.empty(stock);
        }
        try {
            if (hasLiveQuote && chartMode == ChartMode.INTRADAY) {
                intraday = provider.intraday(quote.stock());
            } else if (hasLiveQuote) {
                candles = provider.candles(quote.stock(), 80);
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            error = error == null ? "图表请求失败：" + ex.getMessage() : error + "；图表请求失败：" + ex.getMessage();
        }
        MarketQuote renderQuote = quote;
        List<IntradayPoint> renderIntraday = intraday;
        List<Candle> renderCandles = candles;
        String renderError = error;
        Platform.runLater(() -> render(renderQuote, renderIntraday, renderCandles, renderError));
    }

    private void render(MarketQuote quote, List<IntradayPoint> intraday, List<Candle> candles, String error) {
        Stock stock = quote.stock();
        stockNameLabel.setText(stock.name());
        stockCodeLabel.setText(stock.code() + " · " + stock.market().displayName());
        priceLabel.setText(quote.currentPrice() <= 0 ? "--" : PRICE.format(quote.currentPrice()));
        priceLabel.getStyleClass().removeAll("up", "down");
        priceLabel.getStyleClass().add(quote.isUp() ? "up" : "down");
        changeLabel.setText(PCT.format(quote.changeAmount()) + " / " + PCT.format(quote.changeRate()) + "%");
        changeLabel.getStyleClass().removeAll("up", "down");
        changeLabel.getStyleClass().add(quote.isUp() ? "up" : "down");
        timeLabel.setText("行情时间 " + quote.quoteTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        statusLabel.setText(error == null
                ? MarketStatus.now(LocalDateTime.now()).label() + " · " + universe.statusText() + " · 详情已刷新 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                : "真实行情暂不可用：" + error);
        stockList.refresh();
        renderMetrics(quote);
        renderOrderBook(quote);
        renderFinance(quote.financialReport());
        renderReport(quote.financialReport());
        updateSegments();
        if (chartMode == ChartMode.INTRADAY) {
            drawIntraday(intraday);
        } else {
            drawCandles(candles);
        }
    }

    private void updateSegments() {
        setSelected(intradayButton, chartMode == ChartMode.INTRADAY);
        setSelected(candleButton, chartMode == ChartMode.CANDLE);
        setSelected(incomeButton, reportMode.equals("income"));
        setSelected(balanceButton, reportMode.equals("balance"));
        setSelected(cashflowButton, reportMode.equals("cashflow"));
    }

    private void setSelected(Button button, boolean selected) {
        if (selected) {
            if (!button.getStyleClass().contains("selected")) {
                button.getStyleClass().add("selected");
            }
        } else {
            button.getStyleClass().remove("selected");
        }
    }

    private void renderMetrics(MarketQuote quote) {
        metricGrid.getChildren().clear();
        List<Map.Entry<String, String>> data = List.of(
                Map.entry("今开", PRICE.format(quote.openPrice())),
                Map.entry("昨收", PRICE.format(quote.previousClose())),
                Map.entry("最高", PRICE.format(quote.highPrice())),
                Map.entry("最低", PRICE.format(quote.lowPrice())),
                Map.entry("成交量", formatVolume(quote.volume())),
                Map.entry("成交额", formatAmount(quote.amount())),
                Map.entry("涨跌额", PCT.format(quote.changeAmount())),
                Map.entry("涨跌幅", PCT.format(quote.changeRate()) + "%")
        );
        for (int i = 0; i < data.size(); i++) {
            metricGrid.add(metricBox(data.get(i).getKey(), data.get(i).getValue()), i % 4, i / 4);
        }
    }

    private void renderOrderBook(MarketQuote quote) {
        orderBookGrid.getChildren().clear();
        orderBookGrid.add(new Label("买一"), 0, 0);
        orderBookGrid.add(new Label(PRICE.format(quote.orderBook().bidPrice())), 1, 0);
        orderBookGrid.add(new Label(formatVolume(quote.orderBook().bidVolume())), 2, 0);
        orderBookGrid.add(new Label("卖一"), 0, 1);
        orderBookGrid.add(new Label(PRICE.format(quote.orderBook().askPrice())), 1, 1);
        orderBookGrid.add(new Label(formatVolume(quote.orderBook().askVolume())), 2, 1);
        orderBookGrid.getChildren().forEach(node -> node.getStyleClass().add("report-row"));
    }

    private void renderFinance(FinancialReport report) {
        financeGrid.getChildren().clear();
        List<Map.Entry<String, String>> data = List.of(
                Map.entry("PE", report.pe() <= 0 ? "--" : PRICE.format(report.pe())),
                Map.entry("PB", report.pb() <= 0 ? "--" : PRICE.format(report.pb())),
                Map.entry("ROE", report.roePercent() <= 0 ? "--" : PRICE.format(report.roePercent()) + "%"),
                Map.entry("毛利率", report.grossMarginPercent() <= 0 ? "--" : PRICE.format(report.grossMarginPercent()) + "%")
        );
        for (int i = 0; i < data.size(); i++) {
            financeGrid.add(metricBox(data.get(i).getKey(), data.get(i).getValue()), i % 4, i / 4);
        }
        drawFinance(report);
    }

    private void renderReport(FinancialReport report) {
        reportRows.getChildren().clear();
        List<Map.Entry<String, String>> rows = switch (reportMode) {
            case "balance" -> List.of(
                    Map.entry("总市值", formatAmount(report.marketCap())),
                    Map.entry("PB", report.pb() <= 0 ? "--" : PRICE.format(report.pb())),
                    Map.entry("资产负债率", report.debtRatioPercent() <= 0 ? "--" : PRICE.format(report.debtRatioPercent()) + "%"),
                    Map.entry("总资产", formatAmount(report.totalAssets()))
            );
            case "cashflow" -> List.of(
                    Map.entry("经营现金流", formatAmount(report.operatingCashFlow())),
                    Map.entry("现金流质量", report.operatingCashFlow() > 0 ? "为正" : "暂无"),
                    Map.entry("货币资金", formatAmount(report.cashAndEquivalent())),
                    Map.entry("数据来源", "东方财富 F10")
            );
            default -> List.of(
                    Map.entry("营收趋势", trend(report.revenue())),
                    Map.entry("净利润趋势", trend(report.netProfit())),
                    Map.entry("ROE", report.roePercent() <= 0 ? "--" : PRICE.format(report.roePercent()) + "%"),
                    Map.entry("毛利率", report.grossMarginPercent() <= 0 ? "--" : PRICE.format(report.grossMarginPercent()) + "%")
            );
        };
        for (Map.Entry<String, String> row : rows) {
            HBox line = new HBox(12, new Label(row.getKey()), new Region(), new Label(row.getValue()));
            HBox.setHgrow(line.getChildren().get(1), Priority.ALWAYS);
            line.getStyleClass().add("report-line");
            reportRows.getChildren().add(line);
        }
        reportNote.setText("摘要报表用于快速观察，不构成投资建议。");
    }

    private VBox metricBox(String label, String value) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("muted");
        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("metric-value");
        VBox box = new VBox(6, labelNode, valueNode);
        box.getStyleClass().add("metric-box");
        return box;
    }

    private void drawIntraday(List<IntradayPoint> points) {
        GraphicsContext gc = prepareChart();
        if (points.isEmpty()) {
            drawEmpty(gc, "暂无分时数据");
            return;
        }
        double min = points.stream().mapToDouble(IntradayPoint::price).min().orElse(0);
        double max = points.stream().mapToDouble(IntradayPoint::price).max().orElse(1);
        double w = chartCanvas.getWidth();
        double h = chartCanvas.getHeight();
        drawGrid(gc, w, h);
        strokeLine(gc, points.stream().map(IntradayPoint::averagePrice).toList(), min, max, Color.rgb(255, 196, 100, 0.55), 1.4);
        strokeLine(gc, points.stream().map(IntradayPoint::price).toList(), min, max, Color.web("#68a7ff"), 2.2);
        drawAxis(gc, PRICE.format(max), PRICE.format(min), List.of("09:30", "11:30", "13:00", "15:00"));
    }

    private void drawCandles(List<Candle> candles) {
        GraphicsContext gc = prepareChart();
        if (candles.isEmpty()) {
            drawEmpty(gc, "暂无 K 线数据");
            return;
        }
        double min = candles.stream().mapToDouble(Candle::low).min().orElse(0);
        double max = candles.stream().mapToDouble(Candle::high).max().orElse(1);
        double w = chartCanvas.getWidth();
        double h = chartCanvas.getHeight();
        drawGrid(gc, w, h);
        double left = 54;
        double right = 24;
        double top = 24;
        double bottom = 42;
        double step = (w - left - right) / candles.size();
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double x = left + i * step + step / 2;
            double highY = y(c.high(), min, max, h, top, bottom);
            double lowY = y(c.low(), min, max, h, top, bottom);
            double openY = y(c.open(), min, max, h, top, bottom);
            double closeY = y(c.close(), min, max, h, top, bottom);
            Color color = c.close() >= c.open() ? Color.web("#ff6b66") : Color.web("#35c58a");
            gc.setStroke(color);
            gc.setFill(color);
            gc.setLineWidth(1.2);
            gc.strokeLine(x, highY, x, lowY);
            gc.fillRect(x - step * 0.28, Math.min(openY, closeY), Math.max(2, step * 0.56), Math.max(2, Math.abs(openY - closeY)));
        }
        drawAxis(gc, PRICE.format(max), PRICE.format(min), List.of("60日", "40日", "20日", "今日"));
    }

    private void drawFinance(FinancialReport report) {
        GraphicsContext gc = financeCanvas.getGraphicsContext2D();
        double w = financeCanvas.getWidth();
        double h = financeCanvas.getHeight();
        gc.setFill(Color.web("#131517"));
        gc.fillRoundRect(0, 0, w, h, 8, 8);
        double max = Math.max(
                report.revenue().stream().mapToDouble(QuarterValue::value).max().orElse(1),
                report.netProfit().stream().mapToDouble(QuarterValue::value).max().orElse(1));
        if (max <= 0) {
            drawEmpty(gc, "暂无财务摘要");
            return;
        }
        double gap = w / 5.5;
        for (int i = 0; i < Math.min(report.revenue().size(), report.netProfit().size()); i++) {
            double x = gap * (i + 0.75);
            double revH = report.revenue().get(i).value() / max * (h - 42);
            double profitH = report.netProfit().get(i).value() / max * (h - 42);
            gc.setFill(Color.rgb(104, 167, 255, 0.72));
            gc.fillRoundRect(x, h - 26 - revH, 12, revH, 5, 5);
            gc.setFill(Color.rgb(255, 196, 100, 0.78));
            gc.fillRoundRect(x + 16, h - 26 - profitH, 12, profitH, 5, 5);
            gc.setFill(Color.web("#989fa6"));
            gc.fillText(report.revenue().get(i).label(), x - 2, h - 8);
        }
    }

    private GraphicsContext prepareChart() {
        GraphicsContext gc = chartCanvas.getGraphicsContext2D();
        gc.setFill(Color.web("#131517"));
        gc.fillRoundRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight(), 8, 8);
        return gc;
    }

    private void drawGrid(GraphicsContext gc, double w, double h) {
        gc.setStroke(Color.web("#24282d"));
        gc.setLineWidth(1);
        for (int i = 0; i <= 4; i++) {
            double y = 24 + i * ((h - 66) / 4);
            gc.strokeLine(54, y, w - 24, y);
        }
    }

    private void strokeLine(GraphicsContext gc, List<Double> values, double min, double max, Color color, double lineWidth) {
        if (values.size() < 2) {
            return;
        }
        double w = chartCanvas.getWidth();
        double h = chartCanvas.getHeight();
        double left = 54;
        double right = 24;
        double top = 24;
        double bottom = 42;
        gc.setStroke(color);
        gc.setLineWidth(lineWidth);
        gc.beginPath();
        for (int i = 0; i < values.size(); i++) {
            double x = left + i * ((w - left - right) / (values.size() - 1));
            double y = y(values.get(i), min, max, h, top, bottom);
            if (i == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();
    }

    private double y(double value, double min, double max, double height, double top, double bottom) {
        return height - bottom - ((value - min) / Math.max(0.0001, max - min)) * (height - top - bottom);
    }

    private void drawAxis(GraphicsContext gc, String high, String low, List<String> labels) {
        double w = chartCanvas.getWidth();
        double h = chartCanvas.getHeight();
        gc.setFill(Color.web("#656d75"));
        gc.setFont(Font.font(12));
        gc.fillText(high, 10, 30);
        gc.fillText(low, 10, h - 44);
        for (int i = 0; i < labels.size(); i++) {
            double x = 54 + i * ((w - 78) / Math.max(1, labels.size() - 1));
            gc.fillText(labels.get(i), x - 16, h - 16);
        }
    }

    private void drawEmpty(GraphicsContext gc, String message) {
        gc.setFill(Color.web("#989fa6"));
        gc.setFont(Font.font(15));
        gc.fillText(message, 32, 42);
    }

    private String formatVolume(long volume) {
        if (volume >= 10_000) {
            return PRICE.format(volume / 10_000.0) + " 万手";
        }
        return Long.toString(volume);
    }

    private String formatAmount(double amount) {
        if (Math.abs(amount) >= 100_000_000) {
            return PRICE.format(amount / 100_000_000.0) + " 亿";
        }
        if (Math.abs(amount) >= 10_000) {
            return PRICE.format(amount / 10_000.0) + " 万";
        }
        return PRICE.format(amount);
    }

    private String trend(List<QuarterValue> values) {
        if (values.size() < 2) {
            return "--";
        }
        double first = values.getFirst().value();
        double last = values.getLast().value();
        if (first <= 0) {
            return "--";
        }
        return PCT.format((last - first) / first * 100) + "%";
    }

    private final class StockCell extends ListCell<MarketQuote> {
        @Override
        protected void updateItem(MarketQuote item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Stock stock = item.stock();
            Label name = new Label(stock.name());
            name.getStyleClass().add("stock-name");
            Label code = new Label(stock.code());
            code.getStyleClass().add("muted");
            VBox left = new VBox(5, name, code);
            Label price = new Label(item.currentPrice() <= 0 ? "--" : PRICE.format(item.currentPrice()));
            price.getStyleClass().add("stock-price");
            Label change = new Label(PCT.format(item.changeRate()) + "%");
            change.getStyleClass().addAll("stock-change", item.isUp() ? "up" : "down");
            VBox right = new VBox(5, price, change);
            right.setAlignment(Pos.CENTER_RIGHT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(12, left, spacer, right);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("stock-row");
            setGraphic(row);
        }
    }
}
