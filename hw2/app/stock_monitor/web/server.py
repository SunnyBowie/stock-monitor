from __future__ import annotations

import json
import threading
import webbrowser
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from stock_monitor.application import AlertService, QuoteService, WatchlistService
from stock_monitor.domain import AlertRule, AlertType, Stock
from stock_monitor.infrastructure import EastMoneyMarketDataProvider, MockMarketDataProvider, SQLiteStockRepository


class AppState:
    def __init__(self, db_path: Path):
        self.repository = SQLiteStockRepository(db_path)
        self.eastmoney = EastMoneyMarketDataProvider(timeout=1.0)
        self.mock = MockMarketDataProvider()
        self.provider = self.eastmoney
        self.watchlist = WatchlistService(self.repository)
        self.quotes = QuoteService(self.provider)
        self.alerts = AlertService(self.repository)
        self.last_error = ""
        self._seed()

    def _seed(self) -> None:
        if self.watchlist.list_stocks():
            return
        for code, name in [("600519", "贵州茅台"), ("000001", "平安银行"), ("300750", "宁德时代")]:
            try:
                self.watchlist.add_stock(code, name)
            except ValueError:
                pass

    def quote_for(self, stock: Stock):
        try:
            quote = self.provider.get_quote(stock)
            if quote.price <= 0:
                raise ValueError("行情为空")
            self.last_error = ""
            return quote
        except Exception as exc:  # network fallback for classroom demos
            self.last_error = f"真实行情接口暂不可用，已切换模拟数据：{exc}"
            return self.mock.get_quote(stock)

    def market(self, page: int, page_size: int, keyword: str):
        try:
            quotes = self.eastmoney.get_market_snapshot(page=page, page_size=page_size)
            if keyword:
                quotes = [q for q in quotes if keyword in q.code or keyword in q.name]
            self.last_error = ""
            return quotes
        except Exception as exc:
            self.last_error = f"市场列表接口不可用，已展示模拟样例：{exc}"
            stocks = [Stock("600519", "贵州茅台"), Stock("000001", "平安银行"), Stock("300750", "宁德时代")]
            return [self.mock.get_quote(stock) for stock in stocks]


def quote_to_dict(quote):
    return {
        "code": quote.code,
        "name": quote.name,
        "price": quote.price,
        "open": quote.open_price,
        "high": quote.high,
        "low": quote.low,
        "volume": quote.volume,
        "amount": quote.amount,
        "change": quote.change,
        "pct": quote.change_percent,
        "sampledAt": quote.sampled_at.strftime("%H:%M:%S"),
    }


class StockMonitorHandler(SimpleHTTPRequestHandler):
    state: AppState
    assets_dir: Path

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(self.assets_dir), **kwargs)

    def log_message(self, format, *args):  # keep exe console quiet
        return

    def send_json(self, payload, status=200):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def read_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        if not length:
            return {}
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def do_GET(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        try:
            if parsed.path == "/api/watchlist":
                quotes = [self.state.quote_for(stock) for stock in self.state.watchlist.list_stocks()]
                messages = self.state.alerts.evaluate(quotes)
                self.send_json({"quotes": [quote_to_dict(q) for q in quotes], "messages": messages, "error": self.state.last_error})
                return
            if parsed.path == "/api/market":
                page = int(params.get("page", ["1"])[0])
                keyword = params.get("q", [""])[0].strip()
                quotes = self.state.market(page=page, page_size=120, keyword=keyword)
                self.send_json({"quotes": [quote_to_dict(q) for q in quotes], "error": self.state.last_error})
                return
            if parsed.path == "/api/alerts":
                alerts = [
                    {"id": alert_id, "stockCode": rule.stock_code, "text": rule.describe(), "triggered": rule.triggered}
                    for alert_id, rule in self.state.alerts.list_rules()
                ]
                self.send_json({"alerts": alerts})
                return
        except Exception as exc:
            self.send_json({"error": str(exc)}, status=500)
            return
        return super().do_GET()

    def do_POST(self):
        parsed = urlparse(self.path)
        payload = self.read_json()
        try:
            if parsed.path == "/api/watchlist":
                self.state.watchlist.add_stock(str(payload.get("code", "")), str(payload.get("name", "")))
                self.send_json({"ok": True})
                return
            if parsed.path == "/api/alerts":
                rule = AlertRule(
                    stock_code=str(payload.get("stockCode", "")),
                    alert_type=AlertType(str(payload.get("type", AlertType.ABOVE_PERCENT.value))),
                    threshold=float(payload.get("threshold", 0)),
                )
                alert_id = self.state.alerts.add_rule(rule)
                self.send_json({"ok": True, "id": alert_id})
                return
        except Exception as exc:
            self.send_json({"ok": False, "error": str(exc)}, status=400)
            return
        self.send_json({"error": "not found"}, status=404)

    def do_DELETE(self):
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/watchlist/"):
            code = parsed.path.rsplit("/", 1)[-1]
            self.state.watchlist.remove_stock(code)
            self.send_json({"ok": True})
            return
        self.send_json({"error": "not found"}, status=404)


def run_server(open_browser: bool = True, port: int = 8766):
    app_root = Path(__file__).resolve().parents[3]
    db_path = app_root / "data" / "watchlist_v2.db"
    assets_dir = Path(__file__).resolve().parent / "static"
    StockMonitorHandler.state = AppState(db_path)
    StockMonitorHandler.assets_dir = assets_dir
    server = ThreadingHTTPServer(("127.0.0.1", port), StockMonitorHandler)
    url = f"http://127.0.0.1:{port}/index.html"
    if open_browser:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    print(f"Stock Monitor v2 running at {url}")
    server.serve_forever()


if __name__ == "__main__":
    run_server()
