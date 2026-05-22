# Stock Monitor

Windows 端股票盯盘软件课程项目。项目采用面向对象分析与设计方法，围绕自选股管理、实时行情查看、简化分时图、日 K 趋势图和价格提醒完成需求分析、架构设计、原型与迭代文档。

## 目录

- `docs/word/`：按课程模板整理的 Word 交付物。
- `docs/diagrams/`：PlantUML 用例图与架构图源码。
- `docs/research/`：行情数据源可获得性分析。
- `prototype/index.html`：可直接打开的 HTML 界面原型。

## 技术定位

- 客户端：Windows 桌面应用，推荐 WPF/WinUI 或 Electron/Tauri。
- 数据层：SQLite 保存自选股、提醒规则和最近行情缓存。
- 行情源：课程演示优先 AKShare/东方财富，历史日线可使用 BaoStock；商业发布需要授权行情源。
- 关键对象：`Stock`、`Watchlist`、`MarketQuote`、`AlertRule`、`MarketDataProvider`、`AlertService`。

## 原型使用

直接打开：

```text
D:\CS_project\stock-monitor\prototype\index.html
```

原型使用模拟行情，每 8 秒刷新一次，用于展示交互流程和需求覆盖关系。
