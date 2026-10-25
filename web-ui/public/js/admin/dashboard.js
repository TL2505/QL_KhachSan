import { api } from "../../api.js";

export async function renderDashboard(container, session) {
    container.innerHTML = `
        <div class="page-header admin-dashboard-header">
            <div>
                <span class="badge badge-outline admin-dashboard-kicker">TỔNG QUAN VẬN HÀNH</span>
                <h1>Báo cáo hoạt động khách sạn</h1>
                <p>Theo dõi doanh thu, công suất phòng, đơn đặt và hoàn tiền trên cùng một màn hình.</p>
                <p class="admin-dashboard-date" id="dashboard-business-date" aria-live="polite">Đang tải ngày kinh doanh…</p>
            </div>
            <button type="button" class="btn btn-outline" id="dashboard-refresh">Làm mới số liệu</button>
        </div>

        <div class="alert alert-error hidden admin-dashboard-error" id="dashboard-error" role="alert"></div>

        <section class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4" id="admin-stats-grid" aria-label="Chỉ số tổng quan">
            <article class="card card-border stat-card admin-overview-kpi admin-overview-kpi-collection">
                <div class="stat-title">Tiền thu hôm nay</div>
                <div class="stat-value" id="stat-revenue">—</div>
                <p class="stat-desc">Các khoản thanh toán đã ghi sổ trong ngày kinh doanh.</p>
            </article>
            <article class="card card-border stat-card admin-overview-kpi admin-overview-kpi-occupancy">
                <div class="stat-title">Công suất phòng</div>
                <div class="stat-value" id="stat-occupancy">—</div>
                <p class="stat-desc" id="stat-occupancy-detail">Phòng đang có khách / phòng có thể kinh doanh.</p>
                <progress class="progress progress-info admin-occupancy-progress" id="stat-occupancy-progress" value="0" max="100" aria-label="Công suất phòng"></progress>
            </article>
            <article class="card card-border stat-card admin-overview-kpi admin-overview-kpi-pending">
                <div class="stat-title">Đơn chờ xác nhận</div>
                <div class="stat-value" id="stat-pending">—</div>
                <p class="stat-desc">Đơn đang cần nhân viên xử lý.</p>
            </article>
            <article class="card card-border stat-card admin-overview-kpi admin-overview-kpi-refund">
                <div class="stat-title">Tỷ lệ hoàn / thu tháng này</div>
                <div class="stat-value" id="stat-refund-rate">—</div>
                <p class="stat-desc" id="stat-refund-detail">Tiền hoàn thành công chia cho tiền đã thu trong tháng.</p>
            </article>
        </section>

        <section class="grid grid-cols-1 lg:grid-cols-3 gap-4 mt-4" aria-label="Doanh thu và hoạt động hôm nay">
            <article class="card card-border admin-dashboard-panel lg:col-span-2">
                <div class="admin-dashboard-panel-heading">
                    <div>
                        <h2>Doanh thu thuần theo tháng</h2>
                        <p>Sáu tháng gần nhất theo ngày kinh doanh; đã trừ điều chỉnh giảm ghi trên folio.</p>
                    </div>
                    <span class="badge badge-ghost">6 tháng</span>
                </div>
                <div class="admin-dashboard-chart-wrap">
                    <canvas id="revenue-chart" width="900" height="320" role="img" aria-label="Biểu đồ doanh thu thuần theo tháng"></canvas>
                </div>
                <p class="admin-dashboard-chart-note" id="revenue-chart-note">Giá trị âm thể hiện điều chỉnh giảm lớn hơn khoản thu được ghi nhận trong tháng.</p>
            </article>

            <article class="card card-border admin-dashboard-panel">
                <div class="admin-dashboard-panel-heading">
                    <div>
                        <h2>Lịch lưu trú hôm nay</h2>
                        <p>Ngày kinh doanh <span id="operations-date-short">—</span></p>
                    </div>
                </div>
                <dl class="admin-dashboard-metric-list">
                    <div><dt>Khách dự kiến nhận phòng</dt><dd id="stat-arrivals">—</dd></div>
                    <div><dt>Khách dự kiến trả phòng</dt><dd id="stat-departures">—</dd></div>
                    <div><dt>Phòng cần dọn</dt><dd id="stat-cleaning">—</dd></div>
                </dl>
                <a class="btn btn-outline btn-sm admin-dashboard-link" href="portal.html?view=bookings">Mở quản lý đặt phòng</a>
            </article>
        </section>

        <section class="card card-border admin-dashboard-panel admin-dashboard-finance mt-4" aria-labelledby="dashboard-finance-title">
            <div class="admin-dashboard-panel-heading">
                <div>
                    <h2 id="dashboard-finance-title">Thu, hoàn và hủy đặt phòng</h2>
                    <p>Đối chiếu theo tháng kinh doanh hiện tại. Tỷ lệ hủy tính trên các đơn được tạo trong tháng.</p>
                </div>
                <a class="btn btn-outline btn-sm" href="portal.html?view=payment-operations">Xem giao dịch</a>
            </div>
            <dl class="admin-dashboard-finance-grid">
                <div><dt>Đã thu trong tháng</dt><dd id="stat-collected-month">—</dd></div>
                <div><dt>Đã hoàn trong tháng</dt><dd id="stat-refunded-month">—</dd></div>
                <div><dt>Chờ xử lý hoàn tiền</dt><dd id="stat-manual-refunds">—</dd><small id="stat-manual-refund-count">—</small></div>
                <div><dt>Đơn hủy / đơn tạo tháng này</dt><dd id="stat-cancellations">—</dd><small id="stat-cancellation-rate">—</small></div>
            </dl>
        </section>
    `;

    const requestState = container.dataset || (container.dataset = {});
    const requestId = String((Number(requestState.adminOverviewRequestId) || 0) + 1);
    requestState.adminOverviewRequestId = requestId;
    const dashboard = container.querySelector("#admin-stats-grid");
    const error = container.querySelector("#dashboard-error");
    const refresh = container.querySelector("#dashboard-refresh");
    refresh?.addEventListener("click", () => renderDashboard(container, session));

    try {
        const stats = await api.get("/reports/admin-overview");
        if (requestState.adminOverviewRequestId !== requestId || !dashboard || !container.contains(dashboard)) return;
        if (!stats) throw new Error("Dữ liệu báo cáo không hợp lệ.");

        const totalRooms = Number(stats.totalRooms) || 0;
        const sellableRooms = Number(stats.sellableRooms) || 0;
        const occupiedRooms = Number(stats.occupiedRooms) || 0;
        const occupancyRate = sellableRooms > 0 ? occupiedRooms / sellableRooms * 100 : 0;
        const collectionsThisMonth = Number(stats.collectionsThisMonth) || 0;
        const refundsThisMonth = Number(stats.refundsThisMonth) || 0;
        const businessDate = String(stats.businessDate || "");

        setText(container, "#stat-revenue", formatVnd(stats.collectionsToday));
        setText(container, "#stat-occupancy", formatPercent(occupancyRate));
        setText(container, "#stat-occupancy-detail", `${occupiedRooms} / ${sellableRooms} phòng có thể kinh doanh · ${totalRooms} phòng tổng cộng`);
        setText(container, "#stat-pending", Number(stats.pendingBookings) || 0);
        setText(container, "#stat-refund-rate", collectionsThisMonth > 0 ? formatPercent(stats.refundRate) : "—");
        setText(container, "#stat-refund-detail", collectionsThisMonth > 0
            ? `${formatVnd(refundsThisMonth)} hoàn / ${formatVnd(collectionsThisMonth)} thu trong tháng.`
            : "Chưa có khoản thu trong tháng để tính tỷ lệ.");
        setText(container, "#dashboard-business-date", businessDate ? `Ngày kinh doanh: ${formatBusinessDate(businessDate)}` : "Ngày kinh doanh chưa được cấu hình.");
        setText(container, "#operations-date-short", businessDate ? formatBusinessDate(businessDate) : "—");
        setText(container, "#stat-arrivals", Number(stats.arrivalsToday) || 0);
        setText(container, "#stat-departures", Number(stats.departuresToday) || 0);
        setText(container, "#stat-cleaning", Number(stats.roomsNeedCleaning) || 0);
        setText(container, "#stat-collected-month", formatVnd(collectionsThisMonth));
        setText(container, "#stat-refunded-month", formatVnd(refundsThisMonth));
        setText(container, "#stat-manual-refunds", formatVnd(stats.outstandingManualRefundAmount));
        setText(container, "#stat-manual-refund-count", `${Number(stats.outstandingManualRefundCount) || 0} yêu cầu đang chờ`);
        const bookingsCreated = Number(stats.bookingsCreatedThisMonth) || 0;
        const cancelledBookings = Number(stats.cancelledBookingsThisMonth) || 0;
        setText(container, "#stat-cancellations", `${cancelledBookings} / ${bookingsCreated}`);
        setText(container, "#stat-cancellation-rate", bookingsCreated > 0
            ? `Tỷ lệ hủy ${formatPercent(stats.cancellationRate)}`
            : "Chưa có đơn tạo trong tháng để tính tỷ lệ.");

        const progress = container.querySelector("#stat-occupancy-progress");
        if (progress) progress.value = occupancyRate;
        const canvas = container.querySelector("#revenue-chart");
        if (canvas && container.contains(canvas)) drawRevenueChart(stats.revenueByMonth || [], canvas);
    } catch (e) {
        if (requestState.adminOverviewRequestId !== requestId || !error || !container.contains(error)) return;
        console.error("Lỗi nạp báo cáo tổng quan: ", e);
        error.textContent = "Không tải được số liệu tổng quan. Hãy kiểm tra kết nối rồi thử làm mới.";
        error.classList.remove("hidden");
    }
}

function setText(container, selector, value) {
    const element = container.querySelector(selector);
    if (element) element.textContent = String(value ?? "—");
}

function formatVnd(value) {
    const amount = Number(value);
    return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 }).format(Number.isFinite(amount) ? amount : 0)} ₫`;
}

function formatPercent(value) {
    const rate = Number(value);
    return `${(Number.isFinite(rate) ? rate : 0).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}%`;
}

function formatBusinessDate(value) {
    const date = new Date(`${value}T00:00:00`);
    return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
}

function drawRevenueChart(data, canvas) {
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    const isLight = document.documentElement.dataset.theme === "light";
    const axisColor = isLight ? "#8b8174" : "#374151";
    const tickColor = isLight ? "#766b5e" : "#9ca3af";
    const gridColor = isLight ? "rgba(120, 105, 88, 0.22)" : "rgba(55, 65, 81, 0.4)";
    const valueColor = isLight ? "#382f25" : "#f9fafb";

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
    
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    const margin = { top: 30, right: 20, bottom: 40, left: 60 };
    const width = canvas.width - margin.left - margin.right;
    const height = canvas.height - margin.top - margin.bottom;

    const values = data.map(item => Number(item.grossRevenue)).filter(Number.isFinite);
    const minValue = Math.min(0, ...values);
    const maxValue = Math.max(0, ...values);
    const span = Math.max(maxValue - minValue, 100000);
    const padding = span * 0.08;
    let upperBound = maxValue > 0 ? maxValue + padding : 0;
    const lowerBound = minValue < 0 ? minValue - padding : 0;
    if (upperBound === lowerBound) upperBound = 100000;
    const valueRange = upperBound - lowerBound;
    const baselineY = margin.top + (upperBound / valueRange) * height;

    ctx.strokeStyle = axisColor;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(margin.left, margin.top);
    ctx.lineTo(margin.left, margin.top + height);
    ctx.lineTo(margin.left + width, margin.top + height);
    ctx.stroke();

    ctx.fillStyle = tickColor;
    ctx.font = "11px 'Plus Jakarta Sans', sans-serif";
    ctx.textAlign = "right";
    const divisions = 4;
    for (let i = 0; i <= divisions; i++) {
        const val = upperBound - (valueRange / divisions) * i;
        const y = margin.top + height - (height * (i / divisions));
        
        ctx.fillText(Number(val).toLocaleString('vi-VN', { notation: 'compact' }), margin.left - 8, y + 4);
        
        ctx.strokeStyle = Math.abs(y - baselineY) < 1 ? axisColor : gridColor;
        ctx.beginPath();
        ctx.moveTo(margin.left, y);
        ctx.lineTo(margin.left + width, y);
        ctx.stroke();
    }
    ctx.strokeStyle = axisColor;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(margin.left, baselineY);
    ctx.lineTo(margin.left + width, baselineY);
    ctx.stroke();

    const barWidth = (width / data.length) * 0.5;
    const gap = (width / data.length) * 0.5;
    
    ctx.textAlign = "center";
    data.forEach((d, idx) => {
        const x = margin.left + gap + (barWidth + gap) * idx;
        const amount = Number(d.grossRevenue) || 0;
        const valueY = margin.top + ((upperBound - amount) / valueRange) * height;
        const barY = Math.min(valueY, baselineY);
        const barHeight = Math.max(Math.abs(valueY - baselineY), 1);
        if (amount < 0) {
            ctx.fillStyle = "#b4535b";
        } else {
            const gradient = ctx.createLinearGradient(0, barY, 0, barY + barHeight);
            gradient.addColorStop(0, "#d97706");
            gradient.addColorStop(1, "rgba(217, 119, 6, 0.2)");
            ctx.fillStyle = gradient;
        }
        ctx.fillRect(x - barWidth / 2, barY, barWidth, barHeight);

        ctx.fillStyle = tickColor;
        const monthLabel = d.monthName || (typeof d.month === "string" && d.month.length >= 7 ? `${d.month.slice(5, 7)}/${d.month.slice(2, 4)}` : d.month);
        ctx.fillText(monthLabel || "—", x, margin.top + height + 18);
        
        ctx.fillStyle = valueColor;
        const labelY = amount < 0 ? Math.min(margin.top + height - 4, barY + barHeight + 14) : Math.max(margin.top + 12, barY - 6);
        ctx.fillText(Number(amount).toLocaleString("vi-VN", { notation: "compact", maximumFractionDigits: 1 }), x, labelY);
    });
}
