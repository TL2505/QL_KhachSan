import { api } from "../../api.js";

export async function renderBookings(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Danh Sách Đơn Đặt Phòng</h1>
            <p>Quản lý toàn bộ danh sách đặt phòng và thủ tục checkin/checkout của khách hàng.</p>
        </div>
        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Đơn hàng ID</th>
                            <th>Mã phòng</th>
                            <th>Khách hàng ID</th>
                            <th>Check-in</th>
                            <th>Check-out</th>
                            <th>Tổng tiền</th>
                            <th>Trạng thái</th>
                        </tr>
                    </thead>
                    <tbody id="staff-bookings-body">
                        <tr><td colspan="7" style="text-align: center;">Đang tải đơn đặt phòng...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;

    try {
        const bookings = await api.get("/bookings");
        const body = document.getElementById("staff-bookings-body");
        body.innerHTML = "";
        
        if (!bookings || bookings.length === 0) {
            body.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">Không có đơn đặt phòng nào.</td></tr>`;
            return;
        }

        bookings.forEach(b => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>#${b.id}</td>
                <td><strong>Phòng #${b.roomId}</strong></td>
                <td>Khách hàng #${b.customerId}</td>
                <td>${b.checkInDate}</td>
                <td>${b.checkOutDate}</td>
                <td>${Number(b.totalPrice).toLocaleString('vi-VN')} đ</td>
                <td><span class="room-badge badge-${b.status}">${b.status}</span></td>
            `;
            body.appendChild(tr);
        });
    } catch (e) {
        console.error(e);
    }
}
