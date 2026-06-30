import { api } from "../../api.js";

export async function renderDashboard(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Báo Cáo Hoạt Động Khách Sạn</h1>
            <p>Trực quan hóa chỉ số doanh số thực tế và phân tích kinh doanh.</p>
        </div>

        <div class="grid grid-cols-3" id="admin-stats-grid">
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

        <div class="card mt-4">
            <h3>Biểu đồ doanh thu hàng tháng</h3>
            <div style="display: flex; justify-content: center; align-items: center; margin-top: 20px; background-color: var(--bg-main); padding: 20px; border-radius: var(--radius-sm); border: 1px solid var(--border);">
                <canvas id="revenue-chart" width="700" height="260" style="max-width: 100%;"></canvas>
            </div>
        </div>
    `;

    try {
        // Fetch Stats
        const stats = await api.get("/reports/today-stats");
        if (stats) {
            document.getElementById("stat-revenue").textContent = Number(stats.todayRevenue || 0).toLocaleString('vi-VN') + " đ";
            document.getElementById("stat-occupied").textContent = stats.occupiedRooms || 0;
            document.getElementById("stat-pending").textContent = stats.pendingBookings || 0;
        }

        // Fetch monthly revenue and draw Canvas bar chart
        const monthly = await api.get("/reports/monthly-revenue");
        if (monthly && monthly.length > 0) {
            drawRevenueChart(monthly);
        }
    } catch (e) {
        console.error("Lỗi nạp báo cáo thống kê: ", e);
    }
}

function drawRevenueChart(data) {
    const canvas = document.getElementById("revenue-chart");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    const margin = { top: 30, right: 20, bottom: 40, left: 60 };
    const width = canvas.width - margin.left - margin.right;
    const height = canvas.height - margin.top - margin.bottom;

    const maxVal = Math.max(...data.map(d => Number(d.revenue || 0)), 100000);

    ctx.strokeStyle = "#374151";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(margin.left, margin.top);
    ctx.lineTo(margin.left, margin.top + height);
    ctx.lineTo(margin.left + width, margin.top + height);
    ctx.stroke();

    ctx.fillStyle = "#9ca3af";
    ctx.font = "11px 'Plus Jakarta Sans', sans-serif";
    ctx.textAlign = "right";
    const divisions = 4;
    for (let i = 0; i <= divisions; i++) {
        const val = (maxVal / divisions) * i;
        const y = margin.top + height - (height * (i / divisions));
        
        ctx.fillText(Number(val).toLocaleString('vi-VN', { notation: 'compact' }), margin.left - 8, y + 4);
        
        if (i > 0) {
            ctx.strokeStyle = "rgba(55, 65, 81, 0.4)";
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
        const barHeight = (Number(d.revenue || 0) / maxVal) * height;
        const y = margin.top + height - barHeight;

        const gradient = ctx.createLinearGradient(0, y, 0, margin.top + height);
        gradient.addColorStop(0, "#d97706");
        gradient.addColorStop(1, "rgba(217, 119, 6, 0.2)");
        ctx.fillStyle = gradient;
        
        ctx.beginPath();
        ctx.roundRect(x - barWidth/2, y, barWidth, barHeight, [4, 4, 0, 0]);
        ctx.fill();

        ctx.fillStyle = "#9ca3af";
        ctx.fillText(d.monthName || d.month, x, margin.top + height + 18);
        
        ctx.fillStyle = "#f9fafb";
        ctx.fillText(Number(d.revenue).toLocaleString('vi-VN', { notation: 'compact' }), x, y - 6);
    });
}
