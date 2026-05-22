package com.stockmonitor.ui;

import com.stockmonitor.application.MarketUniverseService;
import com.stockmonitor.application.WatchlistService;
import com.stockmonitor.infrastructure.EastMoneyMarketDataProvider;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class StockMonitorApp extends Application {
    private MainController controller;

    @Override
    public void start(Stage stage) {
        var provider = new EastMoneyMarketDataProvider();
        var universe = new MarketUniverseService(provider);
        var watchlist = new WatchlistService(universe.seedStocks());
        controller = new MainController(provider, watchlist, universe, stage);

        Scene scene = new Scene(controller.view(), 1280, 820);
        scene.getStylesheets().add(getClass().getResource("/com/stockmonitor/styles.css").toExternalForm());

        stage.setTitle("Stock Monitor JavaFX");
        stage.setMinWidth(1060);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();
        controller.start();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
