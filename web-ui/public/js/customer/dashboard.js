import { api } from "../../api.js";

export async function renderDashboard(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Xin chào, ${session.fullName || session.username}</h1>
            <p>Chào mừng bạn quay lại hệ thống quản lý phòng Aurelia.</p>
        </div>
        <div class="grid grid-cols-3" id="cust-stats-grid">Loading stats...</div>
        <div class="card mt-4">
            <h3>Lịch sử đặt phòng của tôi</h3>
            <div class="table-container mt-4">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Phòng</th>
                            <th>Ngày Check-in</th>
                            <th>Ngày Check-out</th>
                            <th>Tổng thanh toán</th>
                            <th>Trạng thái</th>
                        </tr>
                    </thead>
                    <tbody id="cust-bookings-body">
                        <tr><td colspan="5" style="text-align: center;">Đang tải lịch sử đặt phòng...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;
    
    try {
        const bookings = await api.get(`/bookings/customer/${session.customerId || 0}`);
        const loyaltyHistory = await api.get(`/loyalty/history/${session.customerId || 0}`);
        const activePromos = await api.get("/promotions/active");

        let totalPoints = 0;
        let tier = "Silver";
        if (loyaltyHistory && loyaltyHistory.length > 0) {
            loyaltyHistory.forEach(log => {
                if (log.changeType === 'earn') totalPoints += log.pointsChanged;
                else totalPoints -= log.pointsChanged;
            });
        }
        
        if (totalPoints >= 1000) tier = "VIP";
        else if (totalPoints >= 300) tier = "Gold";

        const statsGrid = document.getElementById("cust-stats-grid");
        statsGrid.innerHTML = `
            <div class="card stat-card" style="border-color: var(--info);">
                <div class="stat-title">Hạng thẻ hiện tại</div>
                <div class="stat-value" style="color: ${tier === 'VIP' ? 'var(--color-cleaning)' : (tier === 'Gold' ? 'var(--color-booked)' : 'var(--text-muted)')}">${tier}</div>
            </div>
            <div class="card stat-card" style="border-color: var(--primary);">
                <div class="stat-title">Điểm tích lũy</div>
                <div class="stat-value">${totalPoints} Điểm</div>
            </div>
            <div class="card stat-card" style="border-color: var(--success);">
                <div class="stat-title">Ưu đãi đang chạy</div>
                <div class="stat-value">${activePromos ? activePromos.length : 0} Voucher</div>
            </div>
        `;

        const body = document.getElementById("cust-bookings-body");
        body.innerHTML = "";
        if (!bookings || bookings.length === 0) {
            body.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted);">Bạn chưa có đơn đặt phòng nào.</td></tr>`;
            return;
        }

        bookings.forEach(b => {
            const statusMap = {
                "pending": { text: "Chờ nhận phòng", cls: "badge-booked" },
                "checked_in": { text: "Đang ở", cls: "badge-occupied" },
                "checked_out": { text: "Đã trả phòng", cls: "badge-available" },
                "cancelled": { text: "Đã hủy", cls: "badge-maintenance" }
            };
            const stat = statusMap[b.status] || { text: b.status, cls: "badge-maintenance" };
            
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td><strong>Phòng #${b.roomId}</strong></td>
                <td>${b.checkInDate}</td>
                <td>${b.checkOutDate}</td>
                <td>${Number(b.totalPrice).toLocaleString('vi-VN')} đ</td>
                <td><span class="room-badge ${stat.cls}">${stat.text}</span></td>
            `;
            body.appendChild(tr);
        });

    } catch (e) {
        console.error(e);
    }
}
