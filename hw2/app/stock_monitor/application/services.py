from __future__ import annotations

from stock_monitor.application.ports import MarketDataProvider, StockRepository
from stock_monitor.domain import AlertRule, MarketQuote, Stock


class WatchlistService:
    def __init__(self, repository: StockRepository):
        self.repository = repository

    def add_stock(self, code: str, name: str) -> None:
        stock = Stock(code=code.strip(), name=name.strip())
        stock.validate()
        existing = {item.code for item in self.repository.list_stocks()}
        if stock.code in existing:
            raise ValueError("该股票已在自选股列表中")
        self.repository.add_stock(stock)

    def remove_stock(self, code: str) -> None:
        self.repository.remove_stock(code)

    def list_stocks(self) -> list[Stock]:
        return self.repository.list_stocks()


class QuoteService:
    def __init__(self, provider: MarketDataProvider):
        self.provider = provider

    def refresh_quotes(self, stocks: list[Stock]) -> list[MarketQuote]:
        return [self.provider.get_quote(stock) for stock in stocks]

    def intraday_series(self, stock: Stock) -> list[float]:
        return self.provider.get_intraday_series(stock)

    def daily_close_series(self, stock: Stock) -> list[float]:
        return self.provider.get_daily_close_series(stock)


class AlertService:
    def __init__(self, repository: StockRepository):
        self.repository = repository

    def add_rule(self, rule: AlertRule) -> int:
        rule.validate()
        return self.repository.add_alert(rule)

    def list_rules(self) -> list[tuple[int, AlertRule]]:
        return self.repository.list_alerts()

    def evaluate(self, quotes: list[MarketQuote]) -> list[str]:
        quote_by_code = {quote.code: quote for quote in quotes}
        messages: list[str] = []
        for alert_id, rule in self.repository.list_alerts():
            quote = quote_by_code.get(rule.stock_code)
            if quote and rule.is_hit(quote):
                self.repository.mark_alert_triggered(alert_id)
                messages.append(f"{quote.name} {rule.describe()} 已触发，当前 {quote.price:.2f}")
        return messages
