# Stock Monitor

Windows 端简约股票看盘软件课程项目。项目采用面向对象分析与设计方法，围绕自选股管理、实时行情查看、分时图、K 线趋势、财务报表可视化和价格提醒完成需求分析、架构设计、原型与迭代文档。

本轮 hw1 迭代将产品定位调整为“1 屏解决日常，2 层解决深度”：首页只保留股票简称、现价、涨跌幅；个股详情页再展示分时/K 线、关键盘口和折叠式财务报表，避免传统看盘软件的信息焦虑。

## 目录

- `docs/word/`：按课程模板整理的 Word 交付物。
- `docs/diagrams/`：PlantUML 用例图与架构图源码。
- `docs/research/`：行情数据源可获得性分析。
- `prototype/index.html`：可直接打开的 HTML 界面原型。

## 技术定位

- 客户端：Windows 桌面应用，推荐 WPF/WinUI 或 Electron/Tauri。
- 数据层：SQLite 保存自选股、提醒规则、最近行情缓存和轻量财务摘要缓存。
- 行情源：课程演示优先 AKShare/东方财富，历史日线和财务摘要可使用 AKShare/BaoStock；商业发布需要授权行情源。
- 关键对象：`Stock`、`Watchlist`、`MarketQuote`、`FinancialSummary`、`AlertRule`、`MarketDataProvider`、`AlertService`。

## 原型使用

直接打开：

```text
D:\CS_project\stock-monitor\hw1\prototype\index.html
```

原型使用模拟行情和模拟财务摘要，每 8 秒刷新一次行情，用于展示简约看盘的信息层级、图表切换、财报折叠和需求覆盖关系。
