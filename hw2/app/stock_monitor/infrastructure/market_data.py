from __future__ import annotations

import math
import random
from datetime import datetime
import json
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from stock_monitor.domain import MarketQuote, Stock


class MockMarketDataProvider:
    """Deterministic-enough provider for classroom demos without network risk."""

    def __init__(self, seed: int = 20260521):
        self.random = random.Random(seed)
        self.base_prices: dict[str, float] = {
            "600519": 1668.20,
            "000001": 10.84,
            "300750": 212.36,
            "600036": 38.42,
        }
        self.opens: dict[str, float] = {}

    def _base(self, stock: Stock) -> float:
        if stock.code not in self.base_prices:
            self.base_prices[stock.code] = self.random.uniform(8, 120)
        return self.base_prices[stock.code]

    def get_quote(self, stock: Stock) -> MarketQuote:
        base = self._base(stock)
        open_price = self.opens.setdefault(stock.code, round(base * self.random.uniform(0.985, 1.015), 2))
        drift = self.random.uniform(-0.018, 0.018) * base
        price = max(0.01, round(base + drift, 2))
        self.base_prices[stock.code] = price
        high = round(max(open_price, price) * self.random.uniform(1.0, 1.012), 2)
        low = round(min(open_price, price) * self.random.uniform(0.988, 1.0), 2)
        volume = round(self.random.uniform(12_000, 850_000), 0)
        amount = round(volume * price * 100, 2)
        return MarketQuote(
            code=stock.code,
            name=stock.name,
            price=price,
            open_price=open_price,
            high=high,
            low=low,
            volume=volume,
            amount=amount,
            sampled_at=datetime.now(),
        )

    def get_intraday_series(self, stock: Stock, count: int = 40) -> list[float]:
        base = self._base(stock)
        return [
            round(base + math.sin(i / 4) * base * 0.006 + self.random.uniform(-base * 0.004, base * 0.004), 2)
            for i in range(count)
        ]

    def get_daily_close_series(self, stock: Stock, count: int = 30) -> list[float]:
        base = self._base(stock)
        series = []
        price = base * 0.94
        for _ in range(count):
            price = max(0.01, price + self.random.uniform(-base * 0.018, base * 0.022))
            series.append(round(price, 2))
        return series


class EastMoneyMarketDataProvider:
    """EastMoney public quote adapter.

    The adapter uses EastMoney's public quote endpoints for classroom demos.
    It is not a commercial data SLA. If the endpoint is unavailable, callers
    should fall back to MockMarketDataProvider.
    """

    QUOTE_URL = "http://push2.eastmoney.com/api/qt/stock/get"
    LIST_URL = "http://push2.eastmoney.com/api/qt/clist/get"

    def __init__(self, timeout: float = 1.0):
        self.timeout = timeout
        self._mock = MockMarketDataProvider()

    @staticmethod
    def secid_for(code: str) -> str:
        code = code.strip()
        if code.startswith(("6", "9", "688")):
            return f"1.{code}"
        return f"0.{code}"

    def _get_json(self, url: str, params: dict[str, str]) -> dict:
        query = urlencode(params)
        request = Request(
            f"{url}?{query}",
            headers={
                "User-Agent": "Mozilla/5.0 StockMonitor/0.3",
                "Referer": "https://quote.eastmoney.com/",
            },
        )
        with urlopen(request, timeout=self.timeout) as response:
            raw = response.read().decode("utf-8", errors="replace")
        return json.loads(raw)

    @staticmethod
    def _price(value) -> float:
        if value in (None, "-", ""):
            return 0.0
        value = float(value)
        return round(value / 100, 2) if abs(value) > 10000 else round(value, 2)

    def get_quote(self, stock: Stock) -> MarketQuote:
        data = self._get_json(
            self.QUOTE_URL,
            {
                "secid": self.secid_for(stock.code),
                "fields": "f43,f44,f45,f46,f47,f48,f57,f58",
            },
        ).get("data")
        if not data:
            return self._mock.get_quote(stock)
        return MarketQuote(
            code=str(data.get("f57") or stock.code),
            name=str(data.get("f58") or stock.name),
            price=self._price(data.get("f43")),
            open_price=self._price(data.get("f46")),
            high=self._price(data.get("f44")),
            low=self._price(data.get("f45")),
            volume=float(data.get("f47") or 0),
            amount=float(data.get("f48") or 0),
            sampled_at=datetime.now(),
        )

    def search_a_stocks(self, keyword: str = "", page: int = 1, page_size: int = 80) -> list[Stock]:
        fs = "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23"
        data = self._get_json(
            self.LIST_URL,
            {
                "pn": str(page),
                "pz": str(page_size),
                "po": "1",
                "np": "1",
                "fltt": "2",
                "invt": "2",
                "fid": "f3",
                "fs": fs,
                "fields": "f12,f14",
            },
        ).get("data") or {}
        rows = data.get("diff") or []
        stocks = [Stock(code=str(row.get("f12")), name=str(row.get("f14"))) for row in rows]
        keyword = keyword.strip().lower()
        if keyword:
            stocks = [item for item in stocks if keyword in item.code.lower() or keyword in item.name.lower()]
        return stocks

    def get_market_snapshot(self, page: int = 1, page_size: int = 80) -> list[MarketQuote]:
        fs = "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23"
        data = self._get_json(
            self.LIST_URL,
            {
                "pn": str(page),
                "pz": str(page_size),
                "po": "1",
                "np": "1",
                "fltt": "2",
                "invt": "2",
                "fid": "f3",
                "fs": fs,
                "fields": "f2,f3,f4,f5,f6,f12,f14,f15,f16,f17",
            },
        ).get("data") or {}
        quotes = []
        for row in data.get("diff") or []:
            code = str(row.get("f12"))
            name = str(row.get("f14"))
            price = float(row.get("f2") or 0)
            open_price = float(row.get("f17") or price or 0)
            quotes.append(
                MarketQuote(
                    code=code,
                    name=name,
                    price=price,
                    open_price=open_price,
                    high=float(row.get("f15") or price or 0),
                    low=float(row.get("f16") or price or 0),
                    volume=float(row.get("f5") or 0),
                    amount=float(row.get("f6") or 0),
                    sampled_at=datetime.now(),
                )
            )
        return quotes
