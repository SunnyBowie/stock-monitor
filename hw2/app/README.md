# Stock Monitor 技术原型

hw2 技术原型用于验证软件架构风险：分层结构、数据持久化、行情源适配、提醒规则和桌面 UI 是否可行。

## 运行

```powershell
cd D:\CS_project\stock-monitor\hw2\app
python -m stock_monitor
```

## 测试

```powershell
cd D:\CS_project\stock-monitor\hw2\app
python -m unittest discover -s tests -v
```

## 架构

- `domain`：`Stock`、`MarketQuote`、`AlertRule` 等领域对象。
- `application`：自选股、行情刷新、提醒判断等用例服务。
- `infrastructure`：SQLite 仓储、模拟行情数据源。
- `ui`：Tkinter Windows 桌面界面。

本原型实现 3 个核心用例：管理自选股、查看实时行情、设置价格提醒。
