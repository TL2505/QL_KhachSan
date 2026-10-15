import { api } from "../../api.js";

export async function renderRoomMap(container, session) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Sơ Đồ Phòng Khách Sạn</h1>
                <p>Tổng quan tình trạng phòng hiện thời. Click vào phòng để thao tác nghiệp vụ nhanh.</p>
            </div>
            <button id="btn-refresh-map" class="btn btn-secondary">
                <i data-lucide="refresh-cw" style="width: 14px; height: 14px;"></i> Làm mới sơ đồ
            </button>
        </div>

        <div class="flex-row mb-4" style="gap: 20px;">
            <div style="font-size: 13px;"><span class="room-badge badge-available">Available</span> Trống</div>
            <div style="font-size: 13px;"><span class="room-badge badge-booked">Booked</span> Chờ nhận phòng</div>
            <div style="font-size: 13px;"><span class="room-badge badge-occupied">Occupied</span> Đang ở</div>
            <div style="font-size: 13px;"><span class="room-badge badge-cleaning">Cleaning</span> Đang dọn dẹp</div>
        </div>

        <div class="room-grid" id="staff-rooms-grid">Đang tải sơ đồ phòng...</div>
    `;
    lucide.createIcons();

    const loadMap = async () => {
        const grid = document.getElementById("staff-rooms-grid");
        try {
            const rooms = await api.get("/rooms");
            grid.innerHTML = "";
            rooms.forEach(r => {
                const card = document.createElement("div");
                card.className = `room-card status-${r.status}`;
                card.innerHTML = `
                    <div class="room-card-number">Phòng ${r.roomNumber}</div>
                    <div class="room-card-type">${r.typeName || 'Standard'}</div>
                    <div style="margin-top: 10px;">
                        <span class="room-badge badge-${r.status}">${r.status}</span>
                    </div>
                `;
                
                card.addEventListener("click", () => handleRoomAction(r, loadMap));
                grid.appendChild(card);
            });
        } catch (e) {
            grid.innerHTML = `<div style="color: var(--danger);">${e.message}</div>`;
        }
    };
    
    loadMap();
    document.getElementById("btn-refresh-map").addEventListener("click", loadMap);
}

async function handleRoomAction(room, reloadCallback) {
    if (room.status === "available") {
        const checkIn = new Date().toISOString().split('T')[0];
        const checkOut = new Date(Date.now() + 86400000).toISOString().split('T')[0];
        
        let customerSelectOptions = "<option value=''>-- Chọn khách hàng --</option>";
        try {
            const customers = await api.get("/customers");
            customers.forEach(c => {
                customerSelectOptions += `<option value="${c.id}">${c.fullName} (CCCD: ${c.identityCard})</option>`;
            });
        } catch (e) {
            console.error(e);
        }

        const modal = document.getElementById("global-modal");
        const content = document.getElementById("global-modal-content");
        modal.classList.add("active");
        
        content.innerHTML = `
            <div class="modal-header">
                <h2>Đặt Phòng ${room.roomNumber}</h2>
                <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
            </div>
            <form id="form-quick-booking">
                <div class="form-group">
                    <label class="form-label">Chọn Khách hàng</label>
                    <select class="form-input" id="qb-customer" required>
                        ${customerSelectOptions}
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label">Ngày Check-in</label>
                    <input class="form-input" type="date" id="qb-checkin" value="${checkIn}" required>
                </div>
                <div class="form-group">
                    <label class="form-label">Ngày Check-out</label>
                    <input class="form-input" type="date" id="qb-checkout" value="${checkOut}" required>
                </div>
                <div class="flex-row mt-4" style="justify-content: flex-end;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy</button>
                    <button type="submit" class="btn btn-primary">Xác nhận đặt</button>
                </div>
            </form>
        `;

        document.getElementById("form-quick-booking").addEventListener("submit", async (e) => {
            e.preventDefault();
            const customerId = document.getElementById("qb-customer").value;
            const checkInDate = document.getElementById("qb-checkin").value;
            const checkOutDate = document.getElementById("qb-checkout").value;

            try {
                await api.post("/bookings", { customerId, roomId: room.id, checkInDate, checkOutDate });
                window.showCustomAlert("Đã đặt phòng thành công!");
                closeModal("global-modal");
                reloadCallback();
            } catch (err) {
                window.showCustomAlert("Lỗi đặt phòng: " + err.message);
            }
        });

    } else if (room.status === "booked") {
        if (await window.showCustomConfirm(`Khách hàng đã đặt Phòng ${room.roomNumber}. Tiến hành nhận phòng (Check-in) ngay?`)) {
            try {
                const activeBookings = await api.get("/bookings");
                const booking = activeBookings.find(b => b.roomId === room.id && b.status === "pending");
                if (!booking) {
                    window.showCustomAlert("Không tìm thấy đơn đặt phòng tương ứng ở trạng thái chờ nhận phòng.");
                    return;
                }
                
                await api.put(`/bookings/checkin/${booking.id}`);
                window.showCustomAlert("Đã nhận phòng thành công!");
                reloadCallback();
            } catch (e) {
                window.showCustomAlert("Lỗi Check-in: " + e.message);
            }
        }
    } else if (room.status === "occupied") {
        try {
            const activeBookings = await api.get("/bookings");
            const booking = activeBookings.find(b => b.roomId === room.id && b.status === "checked_in");
            if (!booking) {
                window.showCustomAlert("Không tìm thấy đơn thuê phòng đang sử dụng.");
                return;
            }
            
            const nights = Math.max(1, Math.round((new Date(booking.checkOutDate) - new Date(booking.checkInDate)) / (1000 * 60 * 60 * 24)));
            const baseCost = room.price * nights;
            
            const modal = document.getElementById("global-modal");
            const content = document.getElementById("global-modal-content");
            modal.classList.add("active");
            
            content.innerHTML = `
                <div class="modal-header">
                    <h2>Trả Phòng & Thanh Toán - Phòng ${room.roomNumber}</h2>
                    <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
                </div>
                <div class="form-group">
                    <p><strong>Khách hàng ID:</strong> ${booking.customerId}</p>
                    <p><strong>Check-in:</strong> ${booking.checkInDate}</p>
                    <p><strong>Check-out:</strong> ${booking.checkOutDate}</p>
                    <p><strong>Số đêm ở:</strong> ${nights} đêm</p>
                    <p><strong>Đơn giá:</strong> ${room.price.toLocaleString('vi-VN')} đ/đêm</p>
                    <h3 class="mt-4" style="color: var(--primary);">Tổng cộng: ${baseCost.toLocaleString('vi-VN')} VNĐ</h3>
                </div>
                <form id="form-checkout-payment">
                    <div class="form-group">
                        <label class="form-label">Phương thức thanh toán</label>
                        <select class="form-input" id="pay-method">
                            <option value="cash">Tiền mặt</option>
                            <option value="card">Thẻ tín dụng / ATM</option>
                            <option value="qr">Chuyển khoản QR</option>
                        </select>
                    </div>
                    <div class="flex-row mt-4" style="justify-content: flex-end;">
                        <button type="button" class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy</button>
                        <button type="submit" class="btn btn-primary">Xác nhận thanh toán & Check-out</button>
                    </div>
                </form>
            `;

            document.getElementById("form-checkout-payment").addEventListener("submit", async (e) => {
                e.preventDefault();
                const paymentMethod = document.getElementById("pay-method").value;
                try {
                    await api.post("/payments", {
                        bookingId: booking.id,
                        amount: baseCost,
                        paymentMethod: paymentMethod
                    });
                    window.showCustomAlert("Đã làm thủ tục trả phòng và lưu hóa đơn doanh thu thành công!");
                    closeModal("global-modal");
                    reloadCallback();
                } catch (err) {
                    window.showCustomAlert("Lỗi thanh toán: " + err.message);
                }
            });

        } catch (e) {
            window.showCustomAlert("Lỗi nạp thông tin trả phòng: " + e.message);
        }
    } else if (room.status === "cleaning") {
        if (await window.showCustomConfirm(`Phòng ${room.roomNumber} đã dọn dẹp xong? Chuyển sang trạng thái Trống sẵn sàng?`)) {
            try {
                await api.put(`/rooms/${room.id}/status`, { status: "available" });
                window.showCustomAlert("Phòng đã sẵn sàng đón khách!");
                reloadCallback();
            } catch (e) {
                window.showCustomAlert("Lỗi cập nhật trạng thái phòng: " + e.message);
            }
        }
    }
}
