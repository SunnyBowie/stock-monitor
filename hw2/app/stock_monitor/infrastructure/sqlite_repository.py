from __future__ import annotations

import sqlite3
from pathlib import Path

from stock_monitor.domain import AlertRule, AlertType, Stock


class SQLiteStockRepository:
    def __init__(self, db_path: str | Path):
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_schema()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_schema(self) -> None:
        conn = self._connect()
        try:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS stocks (
                    code TEXT PRIMARY KEY,
                    name TEXT NOT NULL
                )
                """
            )
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS alerts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    stock_code TEXT NOT NULL,
                    alert_type TEXT NOT NULL,
                    threshold REAL NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    triggered INTEGER NOT NULL DEFAULT 0
                )
                """
            )
            conn.commit()
        finally:
            conn.close()

    def add_stock(self, stock: Stock) -> None:
        conn = self._connect()
        try:
            conn.execute("INSERT INTO stocks(code, name) VALUES (?, ?)", (stock.code, stock.name))
            conn.commit()
        finally:
            conn.close()

    def remove_stock(self, code: str) -> None:
        conn = self._connect()
        try:
            conn.execute("DELETE FROM stocks WHERE code = ?", (code,))
            conn.execute("DELETE FROM alerts WHERE stock_code = ?", (code,))
            conn.commit()
        finally:
            conn.close()

    def list_stocks(self) -> list[Stock]:
        conn = self._connect()
        try:
            rows = conn.execute("SELECT code, name FROM stocks ORDER BY code").fetchall()
            return [Stock(code=row["code"], name=row["name"]) for row in rows]
        finally:
            conn.close()

    def add_alert(self, rule: AlertRule) -> int:
        conn = self._connect()
        try:
            cur = conn.execute(
                """
                INSERT INTO alerts(stock_code, alert_type, threshold, enabled, triggered)
                VALUES (?, ?, ?, ?, ?)
                """,
                (rule.stock_code, rule.alert_type.value, rule.threshold, int(rule.enabled), int(rule.triggered)),
            )
            conn.commit()
            return int(cur.lastrowid)
        finally:
            conn.close()

    def list_alerts(self) -> list[tuple[int, AlertRule]]:
        conn = self._connect()
        try:
            rows = conn.execute(
                "SELECT id, stock_code, alert_type, threshold, enabled, triggered FROM alerts ORDER BY id"
            ).fetchall()
            alerts: list[tuple[int, AlertRule]] = []
            for row in rows:
                rule = AlertRule(
                    stock_code=row["stock_code"],
                    alert_type=AlertType(row["alert_type"]),
                    threshold=float(row["threshold"]),
                    enabled=bool(row["enabled"]),
                    triggered=bool(row["triggered"]),
                )
                alerts.append((int(row["id"]), rule))
            return alerts
        finally:
            conn.close()

    def mark_alert_triggered(self, alert_id: int) -> None:
        conn = self._connect()
        try:
            conn.execute("UPDATE alerts SET triggered = 1 WHERE id = ?", (alert_id,))
            conn.commit()
        finally:
            conn.close()
