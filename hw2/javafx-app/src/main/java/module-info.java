module com.stockmonitor {
    requires javafx.controls;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;

    exports com.stockmonitor.domain;
    exports com.stockmonitor.application;
    exports com.stockmonitor.infrastructure;
    exports com.stockmonitor.ui;
    exports com.stockmonitor.tool;
}
