import { api } from "../../api.js";

export async function renderDashboard(container, session) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <h1>Báo Cáo Hoạt Động Khách Sạn</h1>
            <button id="btn-refresh-dashboard" class="btn btn-secondary" type="button">Làm mới báo cáo</button>
        </div>
        <p>Trực quan hóa chỉ số doanh số thực tế và phân tích kinh doanh.</p>

        <div class="grid grid-cols-3" id="admin-stats-grid">
            <div class="card stat-card" style="border-color: var(--success);">
                <div class="stat-title">Doanh thu hôm nay</div>
                <div class="stat-value" id="stat-revenue" aria-live="polite">Đang tải…</div>
            </div>
            <div class="card stat-card" style="border-color: var(--info);">
                <div class="stat-title">Phòng đang thuê</div>
                <div class="stat-value" id="stat-occupied" aria-live="polite">Đang tải…</div>
            </div>
            <div class="card stat-card" style="border-color: var(--warning);">
                <div class="stat-title">Đơn đặt mới chờ xử lý</div>
                <div class="stat-value" id="stat-pending" aria-live="polite">Đang tải…</div>
            </div>
        </div>

        <div class="card mt-4">
            <h3>Biểu đồ doanh thu hàng tháng</h3>
            <div id="revenue-chart-panel" style="display: flex; justify-content: center; align-items: center; margin-top: 20px; background-color: var(--bg-main); padding: 20px; border-radius: var(--radius-sm); border: 1px solid var(--border);">
                <canvas id="revenue-chart" width="700" height="260" style="max-width: 100%;"></canvas>
                <p id="revenue-chart-state" role="status" hidden></p>
            </div>
        </div>
    `;
    let requestGeneration = 0;
    const loadReport = async () => {
        const current = ++requestGeneration;
        const isCurrent = () => current === requestGeneration && container.contains(document.getElementById("admin-stats-grid"));
        const values = ["stat-revenue", "stat-occupied", "stat-pending"].map(id => document.getElementById(id));
        const canvas = document.getElementById("revenue-chart");
        const chartState = document.getElementById("revenue-chart-state");
        values.forEach(value => { if (value) value.textContent = "Đang tải…"; });
        if (canvas) canvas.hidden = false;
        if (chartState) { chartState.hidden = true; chartState.textContent = ""; }
        try {
            const stats = await api.get("/reports/today-stats");
            if (!isCurrent()) return;
            if (!stats || typeof stats !== "object") throw new Error("Report unavailable");
            setMetric("stat-revenue", stats.revenueToday, value => Number(value).toLocaleString("vi-VN") + " đ");
            setMetric("stat-occupied", stats.occupiedRooms);
            setMetric("stat-pending", stats.pendingCheckIns);
        } catch {
            if (!isCurrent()) return;
            ["stat-revenue", "stat-occupied", "stat-pending"].forEach(id => setMetricError(id));
        }
        try {
            const monthly = await api.get("/reports/monthly-revenue");
            if (!isCurrent()) return;
            if (!Array.isArray(monthly) || monthly.some(item => !item || !isNumeric(item.grossRevenue))) throw new Error("Report unavailable");
            if (!monthly.length) {
                if (canvas) canvas.hidden = true;
                if (chartState) { chartState.hidden = false; chartState.textContent = "Chưa có dữ liệu doanh thu theo tháng."; }
            } else drawRevenueChart(monthly);
        } catch {
            if (!isCurrent()) return;
            if (canvas) canvas.hidden = true;
            if (chartState) { chartState.hidden = false; chartState.textContent = "Không thể tải biểu đồ doanh thu. Hãy thử làm mới báo cáo."; }
        }
    };
    document.getElementById("btn-refresh-dashboard").addEventListener("click", loadReport);
    await loadReport();
}

function setMetric(id, value, format = raw => String(raw)) {
    const target = document.getElementById(id);
    const number = isNumeric(value) ? Number(value) : NaN;
    if (target) target.textContent = Number.isFinite(number) ? format(number) : "—";
}

function isNumeric(value) {
    return (typeof value === "number" && Number.isFinite(value)) || (typeof value === "string" && value.trim() !== "" && Number.isFinite(Number(value)));
}

function setMetricError(id) {
    const target = document.getElementById(id);
    if (target) target.textContent = "Không tải được";
}

function drawRevenueChart(data) {
    const canvas = document.getElementById("revenue-chart");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    const margin = { top: 30, right: 20, bottom: 40, left: 60 };
    const width = canvas.width - margin.left - margin.right;
    const height = canvas.height - margin.top - margin.bottom;

    const maxVal = Math.max(...data.map(d => Number(d.grossRevenue || 0)), 100000);

    const styles = getComputedStyle(document.documentElement);
    const primary = styles.getPropertyValue("--primary").trim() || "#1B2A4A";
    const muted = styles.getPropertyValue("--text-muted").trim() || "#757575";
    const text = styles.getPropertyValue("--text-main").trim() || "#212121";
    ctx.strokeStyle = muted;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(margin.left, margin.top);
    ctx.lineTo(margin.left, margin.top + height);
    ctx.lineTo(margin.left + width, margin.top + height);
    ctx.stroke();

    ctx.fillStyle = muted;
    ctx.font = "11px Inter, system-ui, sans-serif";
    ctx.textAlign = "right";
    const divisions = 4;
    for (let i = 0; i <= divisions; i++) {
        const val = (maxVal / divisions) * i;
        const y = margin.top + height - (height * (i / divisions));
        
        ctx.fillText(Number(val).toLocaleString('vi-VN', { notation: 'compact' }), margin.left - 8, y + 4);
        
        if (i > 0) {
            ctx.strokeStyle = `${muted}66`;
            ctx.beginPath();
            ctx.moveTo(margin.left, y);
            ctx.lineTo(margin.left + width, y);
            ctx.stroke();
        }
    }

    const barWidth = (width / data.length) * 0.5;
    const gap = (width / data.length) * 0.5;
    
    ctx.textAlign = "center";
    data.forEach((d, idx) => {
        const x = margin.left + gap + (barWidth + gap) * idx;
        const barHeight = (Number(d.grossRevenue || 0) / maxVal) * height;
        const y = margin.top + height - barHeight;

        const gradient = ctx.createLinearGradient(0, y, 0, margin.top + height);
        gradient.addColorStop(0, primary);
        gradient.addColorStop(1, `${primary}22`);
        ctx.fillStyle = gradient;
        
        ctx.beginPath();
        ctx.roundRect(x - barWidth/2, y, barWidth, barHeight, [4, 4, 0, 0]);
        ctx.fill();

        ctx.fillStyle = muted;
        ctx.fillText(d.monthName || d.month, x, margin.top + height + 18);
        
        ctx.fillStyle = text;
        ctx.fillText(Number(d.grossRevenue).toLocaleString('vi-VN', { notation: 'compact' }), x, y - 6);
    });
}
