import tempfile
import unittest
from pathlib import Path

from stock_monitor.application import AlertService, QuoteService, WatchlistService
from stock_monitor.domain import AlertRule, AlertType
from stock_monitor.infrastructure import MockMarketDataProvider, SQLiteStockRepository
from stock_monitor.web.server import AppState


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = SQLiteStockRepository(Path(self.tmp.name) / "test.db")
        self.watchlist = WatchlistService(self.repo)
        self.provider = MockMarketDataProvider(seed=7)
        self.quotes = QuoteService(self.provider)
        self.alerts = AlertService(self.repo)

    def tearDown(self):
        self.tmp.cleanup()

    def test_add_and_remove_stock(self):
        self.watchlist.add_stock("600519", "贵州茅台")
        self.assertEqual(len(self.watchlist.list_stocks()), 1)
        with self.assertRaises(ValueError):
            self.watchlist.add_stock("600519", "贵州茅台")
        self.watchlist.remove_stock("600519")
        self.assertEqual(self.watchlist.list_stocks(), [])

    def test_refresh_quote_has_change_percent(self):
        self.watchlist.add_stock("000001", "平安银行")
        quote = self.quotes.refresh_quotes(self.watchlist.list_stocks())[0]
        self.assertEqual(quote.code, "000001")
        self.assertIsInstance(quote.change_percent, float)
        self.assertGreater(quote.amount, 0)

    def test_alert_triggered_once(self):
        self.watchlist.add_stock("300750", "宁德时代")
        self.alerts.add_rule(AlertRule("300750", AlertType.ABOVE_PRICE, 1))
        quotes = self.quotes.refresh_quotes(self.watchlist.list_stocks())
        messages = self.alerts.evaluate(quotes)
        self.assertEqual(len(messages), 1)
        second = self.alerts.evaluate(quotes)
        self.assertEqual(second, [])

    def test_app_state_market_has_fallback_quotes(self):
        state = AppState(Path(self.tmp.name) / "app.db")
        quotes = state.market(page=1, page_size=5, keyword="")
        self.assertGreater(len(quotes), 0)
        self.assertTrue(all(item.code for item in quotes))


if __name__ == "__main__":
    unittest.main()
