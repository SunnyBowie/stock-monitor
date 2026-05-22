const $ = (id) => document.getElementById(id);
let selected = null;
let watchQuotes = [];
let chartMode = "minute";

function cls(v) { return Number(v) >= 0 ? "up" : "down"; }
function money(v) {
  const n = Number(v || 0);
  if (n >= 100000000) return `${(n / 100000000).toFixed(2)}亿`;
  if (n >= 10000) return `${(n / 10000).toFixed(2)}万`;
  return n.toFixed(0);
}
async function api(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });
  const data = await res.json();
  if (!res.ok || data.ok === false) throw new Error(data.error || "请求失败");
  return data;
}
function setStatus(text, danger = false) {
  $("status").textContent = text;
  $("status").style.borderLeftColor = danger ? "#d6422b" : "#0d9488";
}
function renderWatchlist() {
  $("watchCount").textContent = watchQuotes.length;
  $("watchlist").innerHTML = "";
  watchQuotes.forEach((q) => {
    const card = document.createElement("div");
    card.className = `stock-card ${selected?.code === q.code ? "active" : ""}`;
    card.innerHTML = `<div><strong>${q.name}</strong><div class="code">${q.code}</div></div>
      <div class="price ${cls(q.pct)}">${Number(q.price).toFixed(2)}<br>${Number(q.pct).toFixed(2)}%</div>`;
    card.onclick = () => { selected = q; renderWatchlist(); renderDetail(); drawChart(); };
    $("watchlist").appendChild(card);
  });
  if (!selected && watchQuotes.length) selected = watchQuotes[0];
  renderDetail();
}
function renderDetail() {
  const q = selected;
  if (!q) return;
  $("stockTitle").textContent = `${q.name} (${q.code})`;
  $("stockMeta").textContent = `刷新时间 ${q.sampledAt} · 数据源 EastMoney / 模拟兜底`;
  $("mPrice").textContent = Number(q.price).toFixed(2);
  $("mPrice").className = cls(q.pct);
  $("mPct").textContent = `${Number(q.pct).toFixed(2)}%`;
  $("mPct").className = cls(q.pct);
  $("mChange").textContent = Number(q.change).toFixed(2);
  $("mChange").className = cls(q.change);
  $("mOpen").textContent = Number(q.open).toFixed(2);
  $("mHigh").textContent = Number(q.high).toFixed(2);
  $("mLow").textContent = Number(q.low).toFixed(2);
  $("mVolume").textContent = money(q.volume);
  $("mAmount").textContent = money(q.amount);
}
function series(base, count, range) {
  return Array.from({ length: count }, (_, i) => {
    const wave = Math.sin(i / 4) * range * 0.5;
    const drift = (Math.random() - 0.5) * range;
    return Math.max(0.01, base + wave + drift);
  });
}
function drawChart() {
  const canvas = $("chart");
  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  if (!selected) return;
  const data = chartMode === "minute"
    ? series(Number(selected.price), 48, Number(selected.price) * 0.012)
    : series(Number(selected.price) * 0.94, 36, Number(selected.price) * 0.04);
  const pad = 34, w = canvas.width, h = canvas.height;
  const min = Math.min(...data), max = Math.max(...data), span = max - min || 1;
  ctx.strokeStyle = "#e3e9f0"; ctx.lineWidth = 1;
  for (let i = 0; i < 5; i++) {
    const y = pad + i * (h - pad * 2) / 4;
    ctx.beginPath(); ctx.moveTo(pad, y); ctx.lineTo(w - pad, y); ctx.stroke();
  }
  ctx.strokeStyle = chartMode === "minute" ? "#105a8b" : "#0d9488";
  ctx.lineWidth = 3; ctx.beginPath();
  data.forEach((v, i) => {
    const x = pad + i * (w - pad * 2) / (data.length - 1);
    const y = h - pad - ((v - min) / span) * (h - pad * 2);
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  });
  ctx.stroke();
  ctx.fillStyle = "#697789"; ctx.font = "15px Microsoft YaHei";
  ctx.fillText(`${chartMode === "minute" ? "分时趋势" : "日 K 收盘价趋势"}  最高 ${max.toFixed(2)} / 最低 ${min.toFixed(2)}`, pad, 24);
}
async function loadWatchlist() {
  const data = await api("/api/watchlist");
  watchQuotes = data.quotes || [];
  if (selected) selected = watchQuotes.find(q => q.code === selected.code) || watchQuotes[0] || null;
  renderWatchlist(); drawChart(); await loadAlerts();
  setStatus(data.error || `自选股已刷新，共 ${watchQuotes.length} 只`, Boolean(data.error));
}
async function loadMarket() {
  const q = $("searchInput").value.trim();
  const data = await api(`/api/market?q=${encodeURIComponent(q)}`);
  const body = $("marketBody");
  body.innerHTML = "";
  (data.quotes || []).forEach((item) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td>${item.code}</td><td>${item.name}</td><td>${Number(item.price).toFixed(2)}</td>
      <td class="${cls(item.pct)}">${Number(item.pct).toFixed(2)}%</td>
      <td class="${cls(item.change)}">${Number(item.change).toFixed(2)}</td>
      <td>${money(item.amount)}</td><td><button data-code="${item.code}" data-name="${item.name}">添加</button></td>`;
    tr.querySelector("button").onclick = async () => {
      await addStock(item.code, item.name);
    };
    body.appendChild(tr);
  });
  if (data.error) setStatus(data.error, true);
}
async function addStock(code, name) {
  await api("/api/watchlist", { method: "POST", body: JSON.stringify({ code, name }) });
  await loadWatchlist();
}
async function loadAlerts() {
  const data = await api("/api/alerts");
  $("alerts").innerHTML = "";
  (data.alerts || []).forEach((a) => {
    const div = document.createElement("div");
    div.className = "alert-item";
    div.textContent = `${a.stockCode} ${a.text} ${a.triggered ? "已触发" : "监控中"}`;
    $("alerts").appendChild(div);
  });
}
$("addBtn").onclick = async () => {
  try { await addStock($("codeInput").value, $("nameInput").value); }
  catch (e) { setStatus(e.message, true); }
};
$("deleteBtn").onclick = async () => {
  if (!selected || !confirm(`删除 ${selected.name}？`)) return;
  await api(`/api/watchlist/${selected.code}`, { method: "DELETE" });
  selected = null; await loadWatchlist();
};
$("saveAlertBtn").onclick = async () => {
  if (!selected) return setStatus("请先选择股票", true);
  try {
    await api("/api/alerts", { method: "POST", body: JSON.stringify({
      stockCode: selected.code, type: $("alertType").value, threshold: Number($("thresholdInput").value)
    })});
    await loadAlerts(); setStatus("提醒规则已保存");
  } catch (e) { setStatus(e.message, true); }
};
$("refreshBtn").onclick = loadWatchlist;
$("searchBtn").onclick = loadMarket;
document.querySelectorAll(".tabs button").forEach(btn => {
  btn.onclick = () => {
    document.querySelectorAll(".tabs button").forEach(b => b.classList.remove("active"));
    btn.classList.add("active"); chartMode = btn.dataset.tab; drawChart();
  };
});
loadWatchlist();
loadMarket();
setInterval(loadWatchlist, 15000);
