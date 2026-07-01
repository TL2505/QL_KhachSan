import { api } from "../../api.js";

export async function renderDiscovery(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Tìm & Đặt Phòng</h1>
            <p>Chọn khoảng thời gian lưu trú để tìm các phòng trống tốt nhất</p>
        </div>

        <div class="card">
            <div class="flex-row justify-between" style="flex-wrap: wrap;">
                <div class="form-group" style="flex: 1; min-width: 200px;">
                    <label class="form-label">Ngày Check-in</label>
                    <input class="form-input" type="date" id="search-checkin" required>
                </div>
                <div class="form-group" style="flex: 1; min-width: 200px;">
                    <label class="form-label">Ngày Check-out</label>
                    <input class="form-input" type="date" id="search-checkout" required>
                </div>
                <div class="form-group" style="display: flex; align-items: flex-end; margin-bottom: 20px;">
                    <button id="btn-search-rooms" class="btn btn-primary" style="height: 46px;">
                        <i data-lucide="search" style="width: 16px; height: 16px;"></i> Tìm phòng trống
                    </button>
                </div>
            </div>
        </div>

        <div id="search-results-section" style="display: none;">
            <h3 style="font-size: 18px; margin-bottom: 15px;">Kết quả tìm kiếm</h3>
            <div class="room-grid" id="search-rooms-grid"></div>
        </div>
    `;
    lucide.createIcons();

    const today = new Date().toISOString().split('T')[0];
    const tomorrow = new Date(Date.now() + 86400000).toISOString().split('T')[0];
    document.getElementById("search-checkin").value = today;
    document.getElementById("search-checkout").value = tomorrow;

    document.getElementById("btn-search-rooms").addEventListener("click", async () => {
        const inDate = document.getElementById("search-checkin").value;
        const outDate = document.getElementById("search-checkout").value;
        if (!inDate || !outDate) return window.showCustomAlert("Vui lòng chọn ngày đầy đủ");

        const resultsSection = document.getElementById("search-results-section");
        const grid = document.getElementById("search-rooms-grid");
        
        resultsSection.style.display = "block";
        grid.innerHTML = "Đang quét phòng trống...";

        try {
            const rooms = await api.get(`/rooms/search?checkIn=${inDate}&checkOut=${outDate}`);
            grid.innerHTML = "";
            if (!rooms || rooms.length === 0) {
                grid.innerHTML = "<div style='color: var(--text-muted); padding: 20px;'>Không còn phòng trống trong khoảng thời gian này.</div>";
                return;
            }

            rooms.forEach(r => {
                const card = document.createElement("div");
                card.className = "room-card status-available";
                card.innerHTML = `
                    <div class="room-card-number">Phòng ${r.roomNumber}</div>
                    <div class="room-card-type">${r.typeName || 'Standard'}</div>
                    <div class="text-gold" style="font-weight: 600; margin-bottom: 12px;">${Number(r.price).toLocaleString('vi-VN')} đ/đêm</div>
                    <button class="btn btn-primary btn-block btn-sm" style="padding: 6px 12px; font-size: 13px;">Đặt phòng</button>
                `;
                
                card.querySelector("button").addEventListener("click", () => {
                    processBooking(r, inDate, outDate, session);
                });
                
                grid.appendChild(card);
            });
        } catch (e) {
            grid.innerHTML = `<div style="color: var(--danger);">${e.message}</div>`;
        }
    });
}

async function processBooking(room, checkIn, checkOut, session) {
    if (!session.customerId) {
        window.showCustomAlert("Tài khoản của bạn không được liên kết với hồ sơ khách hàng. Vui lòng liên hệ Admin!");
        return;
    }
    
    const nights = Math.max(1, Math.round((new Date(checkOut) - new Date(checkIn)) / (1000 * 60 * 60 * 24)));
    const totalCost = room.price * nights;

    const confirmBooking = await window.showCustomConfirm(`Xác nhận đặt Phòng ${room.roomNumber}\nCheck-in: ${checkIn}\nCheck-out: ${checkOut}\nTổng số đêm: ${nights} đêm\nTổng tiền tạm tính: ${totalCost.toLocaleString('vi-VN')} VNĐ\n\nBạn có muốn đặt phòng này không?`);
    
    if (confirmBooking) {
        try {
            await api.post("/bookings", {
                customerId: session.customerId,
                roomId: room.id,
                checkInDate: checkIn,
                checkOutDate: checkOut
            });
            window.showCustomAlert("Đã gửi yêu cầu đặt phòng thành công!");
            document.getElementById("btn-search-rooms").click();
        } catch (e) {
            window.showCustomAlert("Lỗi đặt phòng: " + e.message);
        }
    }
}
