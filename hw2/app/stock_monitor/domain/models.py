from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import Enum


@dataclass(frozen=True)
class Stock:
    code: str
    name: str

    def validate(self) -> None:
        if not self.code.strip():
            raise ValueError("股票代码不能为空")
        if not self.name.strip():
            raise ValueError("股票名称不能为空")
        if len(self.code.strip()) < 6:
            raise ValueError("股票代码至少应为 6 位")


@dataclass(frozen=True)
class MarketQuote:
    code: str
    name: str
    price: float
    open_price: float
    high: float
    low: float
    volume: float
    amount: float
    sampled_at: datetime

    @property
    def change(self) -> float:
        return round(self.price - self.open_price, 2)

    @property
    def change_percent(self) -> float:
        if self.open_price == 0:
            return 0.0
        return round((self.price - self.open_price) / self.open_price * 100, 2)


class AlertType(str, Enum):
    ABOVE_PERCENT = "above_percent"
    BELOW_PERCENT = "below_percent"
    ABOVE_PRICE = "above_price"
    BELOW_PRICE = "below_price"


@dataclass
class AlertRule:
    stock_code: str
    alert_type: AlertType
    threshold: float
    enabled: bool = True
    triggered: bool = False

    def validate(self) -> None:
        if not self.stock_code.strip():
            raise ValueError("提醒规则必须关联股票")
        if self.threshold <= 0:
            raise ValueError("提醒阈值必须大于 0")

    def is_hit(self, quote: MarketQuote) -> bool:
        if not self.enabled or self.triggered:
            return False
        if quote.code != self.stock_code:
            return False
        if self.alert_type == AlertType.ABOVE_PERCENT:
            return quote.change_percent >= self.threshold
        if self.alert_type == AlertType.BELOW_PERCENT:
            return quote.change_percent <= -self.threshold
        if self.alert_type == AlertType.ABOVE_PRICE:
            return quote.price >= self.threshold
        if self.alert_type == AlertType.BELOW_PRICE:
            return quote.price <= self.threshold
        return False

    def describe(self) -> str:
        labels = {
            AlertType.ABOVE_PERCENT: f"涨超 {self.threshold:.2f}%",
            AlertType.BELOW_PERCENT: f"跌超 {self.threshold:.2f}%",
            AlertType.ABOVE_PRICE: f"涨过 {self.threshold:.2f} 元",
            AlertType.BELOW_PRICE: f"跌破 {self.threshold:.2f} 元",
        }
        return labels[self.alert_type]
