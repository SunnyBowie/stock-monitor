# Stock Monitor JavaFX

JavaFX 原生桌面端技术迭代版本。目标是替代上一轮 Python/Tkinter/Web 原型，形成不依赖外部浏览器的独立 Windows 桌面软件。

## 技术栈

- Java 21
- JavaFX 21
- Java HttpClient
- Jackson JSON
- Maven
- jpackage

## 核心能力

- 启动后通过东方财富批量行情接口导入全部 A 股，目前接口返回总数为 5530。
- 后台按 200 支/页分页刷新全市场行情，避免逐只股票高频轮询导致接口不稳定。
- 搜索框支持股票代码/简称检索，详情页按需请求单股快照、分时、K 线和 F10 财务报表。
- 财务卡片使用东方财富 F10 利润表、资产负债表、现金流量表字段，展示近四期营收与归母净利润。
- 顶部内置最小化与关闭按钮，避免独立打包后系统标题栏被窗口环境遮挡时无法操作。
- 启动时先显示种子股票，真实行情返回后自动替换为全 A 股实时列表；单个图表接口失败不会清空已经取得的报价。

## 运行

```powershell
cd D:\CS_project\stock-monitor\hw2\javafx-app
..\tools\apache-maven-3.9.11\bin\mvn.cmd javafx:run
```

## 构建

```powershell
cd D:\CS_project\stock-monitor\hw2\javafx-app
..\tools\apache-maven-3.9.11\bin\mvn.cmd -DskipTests package
```

## 真实数据烟测

```powershell
cd D:\CS_project\stock-monitor\hw2\javafx-app
D:\java21\bin\java.exe --module-path "target\stock-monitor-javafx-0.4.0.jar;target\dependency" --module com.stockmonitor/com.stockmonitor.tool.EastMoneySmokeProbe
```

## 打包 exe

```powershell
cd D:\CS_project\stock-monitor\hw2\javafx-app
.\packaging\package-windows.ps1
```

如果本机禁止 PowerShell 脚本执行，可以使用：

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\package-windows.ps1
```

如果 Maven 依赖已经在 `target/dependency` 中存在，只需要重新生成 app-image，可以跳过 Maven：

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\package-windows.ps1 -SkipMaven
```

产物位于 `target/installer`。

## 数据说明

行情与财务数据使用东方财富公开接口，包含全 A 股列表、实时快照、分时趋势、K 线和 F10 报表字段。公开接口不具备商业 SLA，商业化版本需接入授权行情源。
