import { api } from "../../api.js";

const money = value => `${Number(value || 0).toLocaleString("vi-VN")} đ`;
const compactMoney = value => `${Number(value || 0).toLocaleString("vi-VN", { notation: "compact", maximumFractionDigits: 1 })} đ`;

export async function renderDashboard(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Báo Cáo Hoạt Động Khách Sạn</h1>
            <p>Trực quan hóa chỉ số doanh số thực tế và phân tích kinh doanh.</p>
        </div>

        <div class="grid dashboard-overview-grid" id="admin-stats-grid">
            <div class="card stat-card" style="border-color: var(--success);">
                <div class="stat-title">Doanh thu hôm nay</div>
                <div class="stat-value" id="stat-revenue">0 đ</div>
            </div>
            <div class="card stat-card" style="border-color: var(--info);">
                <div class="stat-title">Phòng đang thuê</div>
                <div class="stat-value" id="stat-occupied">0</div>
            </div>
            <div class="card stat-card" style="border-color: var(--warning);">
                <div class="stat-title">Đơn đặt mới chờ xử lý</div>
                <div class="stat-value" id="stat-pending">0</div>
            </div>
        </div>

        <section class="card monthly-revenue-card bg-base-200 mt-4" aria-labelledby="monthly-revenue-title">
            <div class="card-body gap-5">
                <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                        <h2 id="monthly-revenue-title" class="card-title text-xl font-bold tracking-tight">Doanh thu theo tháng</h2>
                        <p class="text-sm text-base-content/60">Doanh thu phòng · dịch vụ · số hóa đơn</p>
                    </div>
                    <label class="form-control w-full sm:w-44">
                        <span class="sr-only">Khoảng thời gian biểu đồ</span>
                        <select id="revenue-period" class="select select-bordered select-sm w-full" aria-label="Khoảng thời gian biểu đồ">
                            <option value="6">6 tháng gần nhất</option>
                            <option value="12" selected>12 tháng gần nhất</option>
                            <option value="all">Toàn bộ dữ liệu</option>
                        </select>
                    </label>
                </div>

                <div id="monthly-revenue-kpis" class="grid monthly-revenue-kpis" aria-live="polite">
                    <div class="stats stats-vertical w-full rounded-box bg-base-100 shadow-sm"><div class="stat"><div class="stat-title">Doanh thu</div><div id="monthly-kpi-revenue" class="stat-value text-2xl">—</div><div id="monthly-kpi-label" class="stat-desc">Chưa có dữ liệu</div></div></div>
                    <div class="stats stats-vertical w-full rounded-box bg-base-100 shadow-sm"><div class="stat"><div class="stat-title">So với tháng trước</div><div id="monthly-kpi-growth" class="stat-value text-2xl">—</div><div id="monthly-kpi-growth-note" class="stat-desc">Tăng trưởng doanh thu</div></div></div>
                    <div class="stats stats-vertical w-full rounded-box bg-base-100 shadow-sm"><div class="stat"><div class="stat-title">Hóa đơn</div><div id="monthly-kpi-invoices" class="stat-value text-2xl">—</div><div id="monthly-kpi-invoices-note" class="stat-desc">Trong tháng</div></div></div>
                    <div class="stats stats-vertical w-full rounded-box bg-base-100 shadow-sm"><div class="stat"><div class="stat-title">Tỷ trọng dịch vụ</div><div id="monthly-kpi-service" class="stat-value text-2xl">—</div><div id="monthly-kpi-service-note" class="stat-desc">Trong tổng doanh thu tháng</div></div></div>
                </div>

                <div id="revenue-chart-empty" class="alert alert-info" role="status" hidden>Chưa có dữ liệu doanh thu hàng tháng.</div>
                <div id="revenue-chart-wrap" class="rounded-box bg-base-100/50 p-2 sm:p-4 shadow-inner">
                    <div class="flex flex-wrap items-center justify-center gap-x-5 gap-y-2 pb-2 text-xs sm:text-sm" aria-label="Chú giải biểu đồ">
                        <span class="inline-flex items-center gap-2"><span class="h-3 w-3 rounded-sm bg-amber-500"></span>Doanh thu phòng</span>
                        <span class="inline-flex items-center gap-2"><span class="h-3 w-3 rounded-sm bg-cyan-500"></span>Doanh thu dịch vụ</span>
                        <span class="inline-flex items-center gap-2"><span class="h-0.5 w-5 bg-emerald-500"></span>Số hóa đơn</span>
                    </div>
                    <canvas id="revenue-chart" width="1000" height="380" class="block w-full" role="img" aria-label="Biểu đồ doanh thu phòng, dịch vụ và số hóa đơn theo tháng"></canvas>
                </div>
            </div>
        </section>
    `;

    try {
        const revenue = container.querySelector("#stat-revenue");
        const occupied = container.querySelector("#stat-occupied");
        const pending = container.querySelector("#stat-pending");
        const canvas = container.querySelector("#revenue-chart");
        const stats = await api.get("/reports/today-stats");
        if (!revenue || !occupied || !pending || !container.contains(revenue)) return;
        if (stats) {
            revenue.textContent = money(stats.revenueToday);
            occupied.textContent = stats.occupiedRooms || 0;
            pending.textContent = stats.pendingCheckIns || 0;
        }

        const monthly = await api.get("/reports/monthly-revenue");
        if (!container.contains(canvas)) return;
        const normalized = (monthly || []).map(item => ({
            ...item,
            roomRevenue: Number(item.totalRoomRevenue || 0),
            serviceRevenue: Number(item.totalServiceRevenue || 0),
            grossRevenue: Number(item.grossRevenue || 0),
            invoiceCount: Number(item.totalInvoices || 0)
        })).sort((a, b) => String(a.month).localeCompare(String(b.month)));

        if (!normalized.length) {
            container.querySelector("#revenue-chart-empty").hidden = false;
            container.querySelector("#revenue-chart-wrap").hidden = true;
            return;
        }

        updateRevenueSummary(normalized);
        const period = container.querySelector("#revenue-period");
        const renderPeriod = () => {
            const count = period.value === "all" ? normalized.length : Number(period.value);
            drawRevenueChart(normalized.slice(-count), canvas);
        };
        period.addEventListener("change", renderPeriod);
        renderPeriod();
    } catch (error) {
        console.error("Lỗi nạp báo cáo thống kê: ", error);
        const empty = container.querySelector("#revenue-chart-empty");
        if (empty && container.isConnected) {
            empty.textContent = "Không thể tải dữ liệu doanh thu. Vui lòng thử tải lại trang.";
            empty.className = "alert alert-error";
            empty.hidden = false;
        }
    }
}

function updateRevenueSummary(data) {
    const latest = data[data.length - 1];
    const previous = data.length > 1 ? data[data.length - 2] : null;
    const revenue = latest.grossRevenue || latest.roomRevenue + latest.serviceRevenue;
    const serviceShare = revenue > 0 ? latest.serviceRevenue / revenue * 100 : 0;
    const monthLabel = formatMonth(latest.month);

    document.getElementById("monthly-kpi-revenue").textContent = money(revenue);
    document.getElementById("monthly-kpi-label").textContent = monthLabel;
    document.getElementById("monthly-kpi-invoices").textContent = latest.invoiceCount.toLocaleString("vi-VN");
    document.getElementById("monthly-kpi-invoices-note").textContent = `Trong ${monthLabel}`;
    document.getElementById("monthly-kpi-service").textContent = `${serviceShare.toLocaleString("vi-VN", { maximumFractionDigits: 1 })}%`;
    document.getElementById("monthly-kpi-service-note").textContent = `${money(latest.serviceRevenue)} từ dịch vụ`;

    const growth = document.getElementById("monthly-kpi-growth");
    const growthNote = document.getElementById("monthly-kpi-growth-note");
    if (!previous) {
        growth.textContent = "—";
        growthNote.textContent = "Chưa có tháng trước để so sánh";
        growth.className = "stat-value text-2xl";
        return;
    }
    const previousRevenue = previous.grossRevenue || previous.roomRevenue + previous.serviceRevenue;
    const percent = previousRevenue > 0 ? (revenue - previousRevenue) / previousRevenue * 100 : null;
    growth.textContent = percent === null ? (revenue > 0 ? "Mới" : "0%") : `${percent > 0 ? "↑ " : percent < 0 ? "↓ " : ""}${Math.abs(percent).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}%`;
    growth.className = `stat-value text-2xl ${percent === null || percent === 0 ? "" : percent > 0 ? "text-success" : "text-error"}`;
    growthNote.textContent = `So với ${formatMonth(previous.month)}`;
}

function formatMonth(value) {
    const match = String(value || "").match(/^(\d{4})-(\d{2})/);
    return match ? `${match[2]}/${match[1]}` : String(value || "—");
}

function drawRevenueChart(data, canvas) {
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const chartWidth = Math.max(320, canvas.getBoundingClientRect().width || 900);
    const chartHeight = Math.max(280, Math.min(420, chartWidth * 0.38));
    const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
    const pixelWidth = Math.round(chartWidth * pixelRatio);
    const pixelHeight = Math.round(chartHeight * pixelRatio);
    if (canvas.width !== pixelWidth) canvas.width = pixelWidth;
    if (canvas.height !== pixelHeight) canvas.height = pixelHeight;
    canvas.style.height = `${chartHeight}px`;
    ctx.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
    const isLight = document.documentElement.dataset.theme === "light";
    const axisColor = isLight ? "#8b8174" : "#64748b";
    const tickColor = isLight ? "#766b5e" : "#94a3b8";
    const gridColor = isLight ? "rgba(120, 105, 88, 0.2)" : "rgba(100, 116, 139, 0.24)";
    const valueColor = isLight ? "#382f25" : "#e2e8f0";

    if (canvas._themeObserver) canvas._themeObserver.disconnect();
    if (typeof MutationObserver !== "undefined") {
        canvas._themeObserver = new MutationObserver(() => {
            if (!canvas.isConnected) {
                canvas._themeObserver.disconnect();
                delete canvas._themeObserver;
                return;
            }
            drawRevenueChart(data, canvas);
        });
        canvas._themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
    }
    if (!canvas._resizeObserver && typeof ResizeObserver !== "undefined") {
        canvas._resizeObserver = new ResizeObserver(entries => {
            const nextWidth = entries[0]?.contentRect?.width || 0;
            if (canvas.isConnected && Math.abs(nextWidth - canvas._chartWidth) > 2) drawRevenueChart(data, canvas);
        });
        canvas._resizeObserver.observe(canvas);
    }
    canvas._chartWidth = chartWidth;

    ctx.clearRect(0, 0, chartWidth, chartHeight);
    const margin = { top: 38, right: 56, bottom: 54, left: 76 };
    const width = chartWidth - margin.left - margin.right;
    const height = chartHeight - margin.top - margin.bottom;
    const maxRevenue = Math.max(...data.map(item => Math.max(item.grossRevenue, item.roomRevenue + item.serviceRevenue)), 100000);
    const maxInvoices = Math.max(...data.map(item => item.invoiceCount), 1);
    const divisions = 4;
    const baseline = margin.top + height;

    ctx.font = "12px 'Plus Jakarta Sans', sans-serif";
    ctx.lineWidth = 1;
    for (let i = 0; i <= divisions; i++) {
        const fraction = i / divisions;
        const y = baseline - height * fraction;
        ctx.strokeStyle = i === 0 ? axisColor : gridColor;
        ctx.beginPath();
        ctx.moveTo(margin.left, y);
        ctx.lineTo(margin.left + width, y);
        ctx.stroke();
        ctx.fillStyle = tickColor;
        ctx.textAlign = "right";
        ctx.fillText(Number(maxRevenue * fraction).toLocaleString("vi-VN", { notation: "compact", maximumFractionDigits: 1 }), margin.left - 10, y + 4);
        ctx.textAlign = "left";
        ctx.fillText(String(Math.round(maxInvoices * fraction)), margin.left + width + 10, y + 4);
    }

    ctx.save();
    ctx.translate(18, margin.top + height / 2);
    ctx.rotate(-Math.PI / 2);
    ctx.fillStyle = tickColor;
    ctx.textAlign = "center";
    ctx.fillText("Doanh thu (đ)", 0, 0);
    ctx.restore();
    ctx.save();
    ctx.translate(chartWidth - 8, margin.top + height / 2);
    ctx.rotate(Math.PI / 2);
    ctx.fillStyle = tickColor;
    ctx.textAlign = "center";
    ctx.fillText("Hóa đơn", 0, 0);
    ctx.restore();

    const slotWidth = width / data.length;
    const barWidth = Math.min(54, slotWidth * 0.52);
    const invoicePoints = [];
    data.forEach((item, index) => {
        const centerX = margin.left + slotWidth * (index + 0.5);
        const room = Math.max(0, item.roomRevenue);
        const service = Math.max(0, item.serviceRevenue);
        const roomHeight = room / maxRevenue * height;
        const serviceHeight = service / maxRevenue * height;
        const roomTop = baseline - roomHeight;
        const serviceTop = roomTop - serviceHeight;

        ctx.fillStyle = "#f59e0b";
        ctx.beginPath();
        ctx.roundRect(centerX - barWidth / 2, roomTop, barWidth, roomHeight, [5, 5, 0, 0]);
        ctx.fill();
        ctx.fillStyle = "#06b6d4";
        ctx.fillRect(centerX - barWidth / 2, serviceTop, barWidth, serviceHeight);

        const total = item.grossRevenue || room + service;
        ctx.fillStyle = valueColor;
        ctx.textAlign = "center";
        ctx.font = "600 11px 'Plus Jakarta Sans', sans-serif";
        if (data.length <= 8) ctx.fillText(compactMoney(total), centerX, Math.max(margin.top + 12, serviceTop - 8));
        ctx.fillStyle = tickColor;
        ctx.font = "11px 'Plus Jakarta Sans', sans-serif";
        ctx.fillText(formatMonth(item.month), centerX, baseline + 24);
        invoicePoints.push({ x: centerX, y: baseline - item.invoiceCount / maxInvoices * height, count: item.invoiceCount });
    });

    if (invoicePoints.length > 1) {
        ctx.strokeStyle = "#10b981";
        ctx.lineWidth = 2.5;
        ctx.beginPath();
        invoicePoints.forEach((point, index) => index ? ctx.lineTo(point.x, point.y) : ctx.moveTo(point.x, point.y));
        ctx.stroke();
    }
    invoicePoints.forEach(point => {
        ctx.fillStyle = "#10b981";
        ctx.beginPath();
        ctx.arc(point.x, point.y, 4, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = isLight ? "#fff" : "#0f172a";
        ctx.lineWidth = 2;
        ctx.stroke();
    });
}
