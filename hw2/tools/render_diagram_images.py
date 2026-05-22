from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
IMG = ROOT / "docs" / "images"
IMG.mkdir(parents=True, exist_ok=True)


def font(size, bold=False):
    candidates = [
        "C:/Windows/Fonts/msyhbd.ttc" if bold else "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/simhei.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            pass
    return ImageFont.load_default()


def centered(draw, box, text, size=20, bold=False, fill=(20, 35, 55)):
    lines = str(text).split("\n")
    f = font(size, bold)
    dims = [draw.textbbox((0, 0), line, font=f) for line in lines]
    widths = [d[2] - d[0] for d in dims]
    heights = [d[3] - d[1] for d in dims]
    y = box[1] + ((box[3] - box[1]) - sum(heights) - 6 * (len(lines) - 1)) / 2
    for i, line in enumerate(lines):
        x = box[0] + ((box[2] - box[0]) - widths[i]) / 2
        draw.text((x, y), line, font=f, fill=fill)
        y += heights[i] + 6


def rect(draw, box, text, fill=(245, 250, 255), outline=(35, 100, 170), size=20):
    draw.rounded_rectangle(box, radius=14, fill=fill, outline=outline, width=3)
    centered(draw, box, text, size=size)
    return box


def arrow(draw, a, b, label=""):
    draw.line((a[0], a[1], b[0], b[1]), fill=(45, 62, 80), width=3)
    import math

    angle = math.atan2(b[1] - a[1], b[0] - a[0])
    for off in (2.55, -2.55):
        x = b[0] - 13 * math.cos(angle + off)
        y = b[1] - 13 * math.sin(angle + off)
        draw.line((b[0], b[1], x, y), fill=(45, 62, 80), width=3)
    if label:
        mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        draw.text((mx + 6, my - 24), label, font=font(17), fill=(80, 80, 80))


def title(draw, text):
    draw.text((40, 28), text, font=font(34, True), fill=(23, 54, 93))
    draw.line((40, 76, 360, 76), fill=(126, 202, 80), width=8)


def concept_model():
    img = Image.new("RGB", (1400, 900), "white")
    d = ImageDraw.Draw(img)
    title(d, "概念模型：Stock Monitor 核心概念类图")
    boxes = {
        "Watchlist": rect(d, (520, 150, 820, 230), "Watchlist\n自选股清单"),
        "Stock": rect(d, (160, 340, 420, 420), "Stock\n股票"),
        "Quote": rect(d, (570, 340, 850, 430), "MarketQuote\n行情快照"),
        "Report": rect(d, (980, 340, 1260, 430), "FinancialReport\\n财务报表"),
        "Provider": rect(d, (530, 600, 890, 690), "MarketDataProvider\n行情数据源接口"),
    }
    arrow(d, (520, 190), (420, 360), "包含")
    arrow(d, (420, 380), (570, 380), "产生")
    arrow(d, (420, 400), (980, 400), "关联")
    arrow(d, (710, 600), (710, 430), "获取")
    img.save(IMG / "concept_model.png")


def sequence_alert():
    img = Image.new("RGB", (1500, 900), "white")
    d = ImageDraw.Draw(img)
    title(d, "顺序图：全市场导入与详情刷新")
    xs = [140, 420, 700, 980, 1260]
    labels = ["用户", "JavaFX UI", "MarketUniverseService", "EastMoneyProvider", "MarketQuote"]
    for x, label in zip(xs, labels):
        rect(d, (x - 90, 130, x + 90, 190), label, size=18)
        d.line((x, 190, x, 800), fill=(190, 198, 210), width=2)
    steps = [
        (0, 1, 250, "启动软件"),
        (1, 2, 320, "marketPage(page, 200)"),
        (2, 4, 390, "返回 total=5530"),
        (2, 3, 460, "合并行情缓存"),
        (1, 2, 560, "点击个股详情"),
        (2, 4, 630, "quote/intraday/candles/F10"),
        (2, 3, 700, "返回真实图表数据"),
        (1, 0, 770, "展示原生详情页"),
    ]
    for a, b, y, label in steps:
        arrow(d, (xs[a], y), (xs[b], y), label)
    img.save(IMG / "sequence_alert.png")


def vopc_quote():
    img = Image.new("RGB", (1400, 900), "white")
    d = ImageDraw.Draw(img)
    title(d, "VOPC 类图：行情刷新用例实现")
    rect(d, (500, 120, 900, 210), "QuoteRefresh VOPC", fill=(255, 248, 232), outline=(160, 110, 40))
    nodes = [
        ((90, 330, 350, 420), "StockMonitorApp\n<<boundary>>"),
        ((410, 330, 670, 420), "MarketUniverseService\n<<control>>"),
        ((730, 330, 1030, 420), "MarketDataProvider\n<<interface>>"),
        ((1080, 330, 1320, 420), "MarketQuote\n<<entity>>"),
        ((500, 600, 860, 690), "MockMarketDataProvider\n<<adapter>>"),
    ]
    for box, label in nodes:
        rect(d, box, label, size=18)
        arrow(d, (700, 210), ((box[0] + box[2]) // 2, box[1]))
    arrow(d, (350, 375), (410, 375), "调用")
    arrow(d, (670, 375), (730, 375), "依赖接口")
    arrow(d, (880, 420), (700, 600), "实现")
    arrow(d, (1030, 375), (1080, 375), "返回")
    img.save(IMG / "vopc_quote.png")


def merged_class():
    img = Image.new("RGB", (1500, 980), "white")
    d = ImageDraw.Draw(img)
    title(d, "合并整体类图：分层技术原型")
    layers = [
        ((60, 130, 1440, 270), "UI Layer", ["StockMonitorApp", "Sparkline"]),
        ((60, 310, 1440, 470), "Application Layer", ["WatchlistService", "MarketUniverseService", "QuoteRefreshService", "MarketPage", "MarketDataProvider"]),
        ((60, 510, 1440, 670), "Domain Layer", ["Stock", "MarketQuote", "FinancialReport", "OrderBook"]),
        ((60, 710, 1440, 880), "Infrastructure Layer", ["EastMoneyProvider", "MockMarketDataProvider"]),
    ]
    centers = {}
    for layer_box, layer_name, names in layers:
        d.rounded_rectangle(layer_box, radius=18, outline=(180, 190, 205), width=3)
        d.text((layer_box[0] + 20, layer_box[1] + 12), layer_name, font=font(24, True), fill=(23, 54, 93))
        x = layer_box[0] + 260
        y = layer_box[1] + 50
        for name in names:
            box = (x, y, x + 210, y + 60)
            rect(d, box, name, size=16)
            centers[name] = ((box[0] + box[2]) // 2, (box[1] + box[3]) // 2)
            x += 230
    for a, b in [
        ("StockMonitorApp", "WatchlistService"),
        ("StockMonitorApp", "MarketUniverseService"),
        ("StockMonitorApp", "QuoteRefreshService"),
        ("WatchlistService", "MarketPage"),
        ("QuoteRefreshService", "MarketPage"),
        ("MarketUniverseService", "MarketDataProvider"),
        ("EastMoneyProvider", "MarketPage"),
        ("MockMarketDataProvider", "MarketDataProvider"),
        ("FinancialReport", "OrderBook"),
    ]:
        arrow(d, centers[a], centers[b])
    img.save(IMG / "merged_class_model.png")


def architecture():
    img = Image.new("RGB", (1500, 900), "white")
    d = ImageDraw.Draw(img)
    title(d, "软件架构视图：四层架构与外部节点")
    ui = rect(d, (90, 160, 380, 260), "UI Layer\\nJavaFX 原生界面")
    app = rect(d, (470, 160, 780, 260), "Application Layer\n用例服务")
    domain = rect(d, (870, 160, 1160, 260), "Domain Layer\n领域对象")
    infra = rect(d, (470, 430, 780, 540), "Infrastructure Layer\n仓储/数据源适配")
    db = rect(d, (190, 620, 430, 720), "jpackage\\n独立 app-image", fill=(232, 248, 246), outline=(15, 139, 141))
    provider = rect(d, (870, 620, 1180, 720), "EastMoney API\\npush2 + F10", fill=(232, 248, 246), outline=(15, 139, 141))
    arrow(d, (380, 210), (470, 210), "调用")
    arrow(d, (780, 210), (870, 210), "使用")
    arrow(d, (625, 260), (625, 430), "端口")
    arrow(d, (470, 485), (430, 670), "持久化")
    arrow(d, (780, 485), (870, 670), "行情")
    img.save(IMG / "architecture_views.png")


if __name__ == "__main__":
    concept_model()
    sequence_alert()
    vopc_quote()
    merged_class()
    architecture()
    for path in sorted(IMG.glob("*.png")):
        print(path)
