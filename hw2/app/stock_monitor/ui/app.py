from __future__ import annotations

import tkinter as tk
from pathlib import Path
from tkinter import messagebox, ttk

from stock_monitor.application import AlertService, QuoteService, WatchlistService
from stock_monitor.domain import AlertRule, AlertType, Stock
from stock_monitor.infrastructure import MockMarketDataProvider, SQLiteStockRepository


class Sparkline(tk.Canvas):
    def __init__(self, master, **kwargs):
        super().__init__(master, height=180, bg="white", highlightthickness=1, highlightbackground="#d9e1ec", **kwargs)

    def draw_series(self, values: list[float], title: str) -> None:
        self.delete("all")
        w = max(1, self.winfo_width() or 720)
        h = max(1, self.winfo_height() or 180)
        pad = 26
        if not values:
            self.create_text(w / 2, h / 2, text="暂无图表数据", fill="#65758b")
            return
        lo, hi = min(values), max(values)
        span = hi - lo or 1
        for i in range(5):
            y = pad + i * (h - 2 * pad) / 4
            self.create_line(pad, y, w - pad, y, fill="#eef2f7")
        points = []
        for i, value in enumerate(values):
            x = pad + i * (w - 2 * pad) / max(1, len(values) - 1)
            y = h - pad - (value - lo) / span * (h - 2 * pad)
            points.extend([x, y])
        self.create_line(*points, fill="#2364aa", width=3, smooth=True)
        self.create_text(pad + 8, 16, anchor="w", text=f"{title}  最高 {hi:.2f} / 最低 {lo:.2f}", fill="#43546b")


class StockMonitorApp(tk.Tk):
    def __init__(self, db_path: Path):
        super().__init__()
        self.title("Stock Monitor 技术原型")
        self.geometry("1120x720")
        self.minsize(980, 620)

        self.repository = SQLiteStockRepository(db_path)
        self.provider = MockMarketDataProvider()
        self.watchlist = WatchlistService(self.repository)
        self.quotes = QuoteService(self.provider)
        self.alerts = AlertService(self.repository)
        self.current_stock: Stock | None = None
        self.current_quotes = {}
        self.chart_mode = "分时"

        self._seed_demo_data()
        self._build_ui()
        self.refresh_watchlist()
        self.refresh_quotes()

    def _seed_demo_data(self) -> None:
        if self.watchlist.list_stocks():
            return
        for code, name in [("600519", "贵州茅台"), ("000001", "平安银行"), ("300750", "宁德时代")]:
            self.watchlist.add_stock(code, name)

    def _build_ui(self) -> None:
        self.columnconfigure(1, weight=1)
        self.rowconfigure(0, weight=1)

        left = ttk.Frame(self, padding=12)
        left.grid(row=0, column=0, sticky="ns")
        left.columnconfigure(0, weight=1)
        ttk.Label(left, text="Stock Monitor", font=("Microsoft YaHei", 16, "bold")).grid(row=0, column=0, columnspan=2, sticky="w")
        ttk.Label(left, text="自选股观察清单").grid(row=1, column=0, columnspan=2, sticky="w", pady=(6, 10))
        self.code_var = tk.StringVar()
        self.name_var = tk.StringVar()
        ttk.Entry(left, textvariable=self.code_var, width=12).grid(row=2, column=0, sticky="ew", pady=3)
        ttk.Entry(left, textvariable=self.name_var, width=12).grid(row=2, column=1, sticky="ew", pady=3)
        ttk.Button(left, text="添加", command=self.add_stock).grid(row=3, column=0, sticky="ew", pady=6)
        ttk.Button(left, text="删除", command=self.remove_stock).grid(row=3, column=1, sticky="ew", pady=6)
        self.stock_list = tk.Listbox(left, width=28, height=24)
        self.stock_list.grid(row=4, column=0, columnspan=2, sticky="nsew", pady=8)
        self.stock_list.bind("<<ListboxSelect>>", self.on_stock_select)
        left.rowconfigure(4, weight=1)

        center = ttk.Frame(self, padding=12)
        center.grid(row=0, column=1, sticky="nsew")
        center.columnconfigure(0, weight=1)
        center.rowconfigure(3, weight=1)
        self.title_var = tk.StringVar(value="请选择股票")
        self.status_var = tk.StringVar(value="准备刷新")
        ttk.Label(center, textvariable=self.title_var, font=("Microsoft YaHei", 18, "bold")).grid(row=0, column=0, sticky="w")
        ttk.Label(center, textvariable=self.status_var).grid(row=0, column=1, sticky="e")
        metrics = ttk.Frame(center)
        metrics.grid(row=1, column=0, columnspan=2, sticky="ew", pady=12)
        self.metric_vars = {}
        for idx, label in enumerate(["现价", "涨跌幅", "今开", "最高", "最低", "成交额"]):
            frame = ttk.LabelFrame(metrics, text=label, padding=8)
            frame.grid(row=0, column=idx, padx=4, sticky="ew")
            var = tk.StringVar(value="--")
            ttk.Label(frame, textvariable=var, font=("Microsoft YaHei", 12, "bold")).pack()
            self.metric_vars[label] = var
        chart_buttons = ttk.Frame(center)
        chart_buttons.grid(row=2, column=0, columnspan=2, sticky="w")
        ttk.Button(chart_buttons, text="分时", command=lambda: self.set_chart("分时")).pack(side="left", padx=4)
        ttk.Button(chart_buttons, text="日 K", command=lambda: self.set_chart("日 K")).pack(side="left", padx=4)
        ttk.Button(chart_buttons, text="刷新行情", command=self.refresh_quotes).pack(side="left", padx=12)
        self.chart = Sparkline(center)
        self.chart.grid(row=3, column=0, columnspan=2, sticky="nsew", pady=10)

        right = ttk.Frame(self, padding=12)
        right.grid(row=0, column=2, sticky="ns")
        ttk.Label(right, text="价格提醒", font=("Microsoft YaHei", 14, "bold")).grid(row=0, column=0, columnspan=2, sticky="w")
        self.alert_type_var = tk.StringVar(value=AlertType.ABOVE_PERCENT.value)
        self.threshold_var = tk.StringVar(value="3")
        ttk.Combobox(
            right,
            textvariable=self.alert_type_var,
            state="readonly",
            values=[item.value for item in AlertType],
            width=20,
        ).grid(row=1, column=0, columnspan=2, sticky="ew", pady=8)
        ttk.Entry(right, textvariable=self.threshold_var).grid(row=2, column=0, sticky="ew")
        ttk.Button(right, text="保存提醒", command=self.add_alert).grid(row=2, column=1, padx=6)
        self.alert_list = tk.Listbox(right, width=34, height=18)
        self.alert_list.grid(row=3, column=0, columnspan=2, sticky="nsew", pady=12)
        right.rowconfigure(3, weight=1)

    def refresh_watchlist(self) -> None:
        self.stock_list.delete(0, tk.END)
        for stock in self.watchlist.list_stocks():
            self.stock_list.insert(tk.END, f"{stock.code}  {stock.name}")
        if self.stock_list.size() and self.current_stock is None:
            self.stock_list.selection_set(0)
            self.on_stock_select(None)
        self.refresh_alerts()

    def add_stock(self) -> None:
        try:
            self.watchlist.add_stock(self.code_var.get(), self.name_var.get())
            self.code_var.set("")
            self.name_var.set("")
            self.refresh_watchlist()
        except ValueError as exc:
            messagebox.showwarning("输入错误", str(exc))

    def remove_stock(self) -> None:
        if not self.current_stock:
            return
        if messagebox.askyesno("确认删除", f"删除 {self.current_stock.name} 及其提醒规则？"):
            self.watchlist.remove_stock(self.current_stock.code)
            self.current_stock = None
            self.refresh_watchlist()
            self.refresh_quotes()

    def on_stock_select(self, _event) -> None:
        selection = self.stock_list.curselection()
        if not selection:
            return
        stock = self.watchlist.list_stocks()[selection[0]]
        self.current_stock = stock
        self.title_var.set(f"{stock.name} ({stock.code})")
        self.update_detail()

    def refresh_quotes(self) -> None:
        stocks = self.watchlist.list_stocks()
        quotes = self.quotes.refresh_quotes(stocks)
        self.current_quotes = {quote.code: quote for quote in quotes}
        messages = self.alerts.evaluate(quotes)
        if messages:
            messagebox.showinfo("价格提醒", "\n".join(messages))
        self.status_var.set(f"已刷新 {quotes[0].sampled_at:%H:%M:%S}" if quotes else "无自选股")
        self.update_detail()
        self.refresh_alerts()

    def update_detail(self) -> None:
        if not self.current_stock:
            for var in self.metric_vars.values():
                var.set("--")
            self.chart.draw_series([], "暂无")
            return
        quote = self.current_quotes.get(self.current_stock.code) or self.provider.get_quote(self.current_stock)
        self.current_quotes[self.current_stock.code] = quote
        self.metric_vars["现价"].set(f"{quote.price:.2f}")
        self.metric_vars["涨跌幅"].set(f"{quote.change_percent:.2f}%")
        self.metric_vars["今开"].set(f"{quote.open_price:.2f}")
        self.metric_vars["最高"].set(f"{quote.high:.2f}")
        self.metric_vars["最低"].set(f"{quote.low:.2f}")
        self.metric_vars["成交额"].set(f"{quote.amount / 100000000:.2f} 亿")
        self.draw_chart()

    def set_chart(self, mode: str) -> None:
        self.chart_mode = mode
        self.draw_chart()

    def draw_chart(self) -> None:
        if not self.current_stock:
            return
        if self.chart_mode == "分时":
            data = self.quotes.intraday_series(self.current_stock)
        else:
            data = self.quotes.daily_close_series(self.current_stock)
        self.chart.draw_series(data, self.chart_mode)

    def add_alert(self) -> None:
        if not self.current_stock:
            messagebox.showwarning("未选择股票", "请先选择一只股票")
            return
        try:
            threshold = float(self.threshold_var.get())
            rule = AlertRule(self.current_stock.code, AlertType(self.alert_type_var.get()), threshold)
            self.alerts.add_rule(rule)
            self.refresh_alerts()
        except ValueError as exc:
            messagebox.showwarning("输入错误", str(exc))

    def refresh_alerts(self) -> None:
        self.alert_list.delete(0, tk.END)
        for _, rule in self.alerts.list_rules():
            status = "已触发" if rule.triggered else "监控中"
            self.alert_list.insert(tk.END, f"{rule.stock_code} {rule.describe()} [{status}]")


def main() -> None:
    db_path = Path(__file__).resolve().parents[3] / "data" / "watchlist.db"
    app = StockMonitorApp(db_path)
    app.mainloop()
