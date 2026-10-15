import { api } from "../../api.js";

export async function renderDashboard(container, session) {
    const displayName = session.fullName || session.username || 'Bạn';
    const initials = (() => {
        const parts = displayName.trim().split(' ');
        return parts.length >= 2
            ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
            : displayName[0].toUpperCase();
    })();

    const hour = new Date().getHours();
    const greeting = hour < 12 ? 'Chào buổi sáng' : hour < 18 ? 'Chào buổi chiều' : 'Chào buổi tối';

    container.innerHTML = `
        <!-- Greeting Banner -->
        <div class="greeting-banner">
            <div class="greeting-avatar">${initials}</div>
            <div class="greeting-text">
                <h2>${greeting}, ${displayName}! 👋</h2>
                <p>Chào mừng bạn quay lại hệ thống đặt phòng Aurelia. Dưới đây là thông tin tổng quan tài khoản của bạn.</p>
            </div>
        </div>

        <!-- Stats Grid (populated after fetch) -->
        <div class="grid grid-cols-3" id="cust-stats-grid" style="margin-bottom:24px;">
            <!-- skeleton -->
            ${[1,2,3].map(i => `
                <div class="stat-card-enhanced">
                    <div class="skeleton-block" style="width:44px;height:44px;border-radius:var(--radius-sm);margin-bottom:4px;"></div>
                    <div class="skeleton-block" style="width:60%;height:12px;border-radius:4px;"></div>
                    <div class="skeleton-block" style="width:40%;height:28px;border-radius:4px;margin-top:4px;"></div>
                </div>
            `).join('')}
        </div>

        <!-- Booking History -->
        <div class="card">
            <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;">
                <div>
                    <h3 style="font-size:16px;font-weight:700;margin-bottom:2px;">Lịch Sử Đặt Phòng</h3>
                    <p style="font-size:13px;color:var(--text-muted);">Tất cả các lần đặt phòng của bạn tại Aurelia</p>
                </div>
                <i data-lucide="calendar-days" style="width:20px;height:20px;color:var(--text-muted);"></i>
            </div>
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Phòng</th>
                            <th>Ngày Check-in</th>
                            <th>Ngày Check-out</th>
                            <th style="text-align:right;">Tổng thanh toán</th>
                            <th>Trạng thái</th>
                        </tr>
                    </thead>
                    <tbody id="cust-bookings-body">
                        <tr><td colspan="5" style="text-align:center;padding:40px;color:var(--text-muted);">
                            <i data-lucide="loader" style="width:16px;height:16px;"></i> Đang tải lịch sử đặt phòng...
                        </td></tr>
                    </tbody>
                </table>
            </div>
            <div id="pagination-cust-bookings"></div>
        </div>
    `;
    lucide.createIcons();

    try {
        const [bookings, loyaltyHistory, activePromos] = await Promise.all([
            api.get(`/bookings/customer/${session.customerId || 0}`),
            api.get(`/loyalty/history/${session.customerId || 0}`),
            api.get("/promotions/active")
        ]);

        // Tính điểm & tier
        let totalPoints = 0;
        if (loyaltyHistory && loyaltyHistory.length > 0) {
            loyaltyHistory.forEach(log => {
                if (log.changeType === 'earn') totalPoints += log.pointsChanged;
                else totalPoints -= log.pointsChanged;
            });
        }
        totalPoints = Math.max(0, totalPoints);

        let tier = 'Silver', tierColor = 'var(--text-muted)', tierIcon = '🥈', tierAccent = '#9ca3af';
        if (totalPoints >= 1000) { tier = 'VIP';   tierColor = '#22d3ee'; tierIcon = '👑'; tierAccent = '#06b6d4'; }
        else if (totalPoints >= 300) { tier = 'Gold'; tierColor = '#fbbf24'; tierIcon = '⭐'; tierAccent = '#f59e0b'; }

        // Stat cards
        const statsGrid = document.getElementById("cust-stats-grid");
        statsGrid.innerHTML = `
            <div class="stat-card-enhanced" style="--accent-color:${tierAccent};animation-delay:0s;">
                <div class="stat-card-icon" style="background:rgba(6,182,212,0.12);">
                    <i data-lucide="award" style="width:22px;height:22px;color:${tierColor};"></i>
                </div>
                <div class="stat-card-title">Hạng thành viên</div>
                <div class="stat-card-value" style="color:${tierColor};">${tierIcon} ${tier}</div>
            </div>
            <div class="stat-card-enhanced" style="--accent-color:var(--primary);animation-delay:0.08s;">
                <div class="stat-card-icon" style="background:var(--primary-light);">
                    <i data-lucide="coins" style="width:22px;height:22px;color:var(--primary);"></i>
                </div>
                <div class="stat-card-title">Điểm tích lũy</div>
                <div class="stat-card-value">${totalPoints.toLocaleString('vi-VN')}</div>
            </div>
            <div class="stat-card-enhanced" style="--accent-color:var(--success);animation-delay:0.16s;">
                <div class="stat-card-icon" style="background:rgba(16,185,129,0.12);">
                    <i data-lucide="tag" style="width:22px;height:22px;color:var(--success);"></i>
                </div>
                <div class="stat-card-title">Ưu đãi đang chạy</div>
                <div class="stat-card-value">${activePromos ? activePromos.length : 0} <span style="font-size:16px;font-weight:400;color:var(--text-muted);">Voucher</span></div>
            </div>
        `;
        lucide.createIcons();

        // Booking table
        let allBookings = bookings;
        let currentPage = 1;
        let itemsPerPage = 10;

        const renderTable = () => {
            const body = document.getElementById("cust-bookings-body");
            body.innerHTML = "";

            if (!allBookings || allBookings.length === 0) {
                body.innerHTML = `
                    <tr><td colspan="5">
                        <div class="empty-state">
                            <div class="empty-state-icon"><i data-lucide="calendar-x" style="width:28px;height:28px;"></i></div>
                            <p>Chưa có đơn đặt phòng nào</p>
                            <small>Khám phá phòng và đặt ngay để tích lũy điểm thưởng!</small>
                        </div>
                    </td></tr>`;
                lucide.createIcons();
                const paginationContainer = document.getElementById("pagination-cust-bookings");
                if (paginationContainer) paginationContainer.innerHTML = '';
                return;
            }

            const start = (currentPage - 1) * itemsPerPage;
            const end = start + itemsPerPage;
            const paginatedList = allBookings.slice(start, end);

            paginatedList.forEach((b, i) => {
                const stat = statusMap[b.status] || { text: b.status, cls: "badge-maintenance", dot: "var(--color-maintenance)" };
                const tr = document.createElement("tr");
                tr.className = "table-row-animate";
                tr.style.animationDelay = `${i * 0.05}s`;
                tr.innerHTML = `
                    <td>
                        <div style="display:flex;align-items:center;gap:8px;">
                            <div style="width:32px;height:32px;border-radius:var(--radius-sm);background:var(--primary-light);display:flex;align-items:center;justify-content:center;">
                                <i data-lucide="door-open" style="width:14px;height:14px;color:var(--primary);"></i>
                            </div>
                            <strong>Phòng #${b.roomId}</strong>
                        </div>
                    </td>
                    <td style="font-size:14px;">${b.checkInDate}</td>
                    <td style="font-size:14px;">${b.checkOutDate}</td>
                    <td style="text-align:right;font-weight:700;font-family:var(--font-heading);">${Number(b.totalPrice).toLocaleString('vi-VN')} đ</td>
                    <td>
                        <span class="room-badge ${stat.cls}">
                            <span class="status-dot" style="background:${stat.dot};"></span>${stat.text}
                        </span>
                    </td>
                `;
                body.appendChild(tr);
            });
            lucide.createIcons();

            if (window.renderPaginationComponent) {
                window.renderPaginationComponent(
                    "pagination-cust-bookings",
                    allBookings.length,
                    itemsPerPage,
                    currentPage,
                    (newPage) => {
                        currentPage = newPage;
                        renderTable();
                    },
                    (newItemsPerPage) => {
                        itemsPerPage = newItemsPerPage;
                        currentPage = 1;
                        renderTable();
                    }
                );
            }
        };

        renderTable();

    } catch (e) {
        console.error(e);
    }
}
