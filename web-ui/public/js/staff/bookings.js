import { api } from "../../api.js";

import { escapeHtml } from "../shared/ui.js";

function formatBookingDate(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (!Number.isFinite(date.getTime())) return escapeHtml(value);
    const options = { timeZone: 'Asia/Ho_Chi_Minh' };
    const day = date.toLocaleDateString('vi-VN', { ...options, day: '2-digit', month: '2-digit', year: 'numeric' });
    const time = date.toLocaleTimeString('vi-VN', { ...options, hour: '2-digit', minute: '2-digit', hour12: false });
    return `<span>${day}</span><small>${time}</small>`;
}


const STATUS_LABEL = {
    pending:    { label: 'Chờ xác nhận', cls: 'badge-booked' },
    confirmed:  { label: 'Đã xác nhận', cls: 'badge-available' },
    checked_in: { label: 'Đang lưu trú', cls: 'badge-occupied' },
    checked_out:{ label: 'Đã trả phòng', cls: 'badge-cleaning' },
    cancelled:  { label: 'Đã hủy', cls: 'badge-maintenance' },
    no_show:    { label: 'Không đến', cls: 'badge-maintenance' },
};

function bookingTime(value) {
    const time = new Date(value || '').getTime();
    return Number.isFinite(time) ? time : null;
}

// Keep the available actions aligned with the backend's reservation rules.
function decisionAvailability(booking) {
    const checkIn = bookingTime(booking.checkInDate);
    if (checkIn === null) return { allowed: false, reason: 'Thiếu thời điểm nhận phòng' };
    if (checkIn <= Date.now()) return { allowed: false, reason: 'Đã đến hoặc qua thời điểm nhận phòng' };
    return { allowed: true, reason: '' };
}

function checkInAvailability(booking) {
    const checkIn = bookingTime(booking.checkInDate);
    const checkOut = bookingTime(booking.checkOutDate);
    if (checkIn === null || checkOut === null) return { allowed: false, reason: 'Thiếu thời gian lưu trú' };
    const dateOnly = value => {
        const date = new Date(value);
        return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
    };
    const today = dateOnly(Date.now());
    if (today < dateOnly(checkIn)) return { allowed: false, reason: 'Chưa đến ngày nhận phòng' };
    if (today >= dateOnly(checkOut)) return { allowed: false, reason: 'Đã quá ngày lưu trú' };
    return { allowed: true, reason: '' };
}

function skeletonRows(count = 6) {
    return Array.from({ length: count }, () => `
        <tr>${Array.from({ length: 8 }, (_, i) =>
            `<td><div class="skeleton-block" style="width:${[30,70,90,80,80,80,60,90][i]}px;border-radius:${i===6?'30px':'4px'};"></div></td>`
        ).join('')}</tr>`).join('');
}

export async function renderBookings(container, session) {
    container.innerHTML = `
        <div class="staff-data-view staff-bookings-view">
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Đơn Đặt Phòng</h1>
                <p>Quản lý check-in, check-out và trạng thái đặt phòng của khách hàng.</p>
            </div>
        </div>

        <!-- Summary bar -->
        <div id="staff-bookings-summary" class="stats"></div>

        <!-- Search + filter -->
        <div class="card staff-filter-card">
            <div class="staff-bookings-filters">
                <div class="staff-filter-search">
                    <i data-lucide="search" style="width:15px;height:15px;position:absolute;left:12px;top:50%;transform:translateY(-50%);color:var(--text-muted);pointer-events:none;"></i>
                    <input id="staff-bookings-search" class="form-input" type="text"
                        aria-label="Tìm đơn đặt phòng" placeholder="Tìm mã đặt phòng, phòng, khách hàng..."
                        style="padding-left:36px;margin:0;">
                </div>
                <select id="staff-bookings-status" class="form-input" aria-label="Trạng thái đặt phòng">
                    <option value="">Tất cả trạng thái</option>
                    <option value="PENDING">Chờ xác nhận</option>
                    <option value="CONFIRMED">Đã xác nhận</option>
                    <option value="CHECKED_IN">Đã check-in</option>
                    <option value="CHECKED_OUT">Đã trả phòng</option>
                    <option value="CANCELLED">Đã hủy</option>
                </select>
                <button id="btn-refresh-bookings" class="btn btn-secondary" style="padding:10px 14px;flex-shrink:0;" title="Làm mới" aria-label="Làm mới đơn đặt phòng">
                    <i data-lucide="refresh-cw" style="width:15px;height:15px;"></i>
                </button>
            </div>
        </div>

        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th style="width:60px;">Mã ĐP</th>
                            <th>Phòng</th>
                            <th>Khách hàng</th>
                            <th>Check-in</th>
                            <th>Check-out</th>
                            <th>Tổng tiền</th>
                            <th>Trạng thái</th>
                            <th>Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="staff-bookings-body">
                        ${skeletonRows(6)}
                    </tbody>
                </table>
            </div>
            <div id="pagination-staff-bookings"></div>
        </div>
        </div>
    `;
    lucide.createIcons();

    let allBookings = [];
    let currentDisplayList = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const applyFilter = () => {
        const q      = document.getElementById("staff-bookings-search").value.toLowerCase().trim();
        const status = document.getElementById("staff-bookings-status").value;
        let list = allBookings;
        if (status) list = list.filter(b => String(b.status || '').toUpperCase() === status);
        if (q) list = list.filter(b =>
            String(b.id).includes(q) ||
            String(b.roomId).includes(q) ||
            String(b.customerId).includes(q) ||
            (b.status || '').toLowerCase().includes(q)
        );
        currentPage = 1;
        renderTable(list);
    };

    const renderTable = (list = currentDisplayList) => {
        currentDisplayList = list;
        const body = document.getElementById("staff-bookings-body");
        body.innerHTML = "";
        if (!list || list.length === 0) {
            body.innerHTML = `<tr><td colspan="8">
                <div class="empty-state">
                    <div class="empty-state-icon"><i data-lucide="calendar-x" style="width:26px;height:26px;"></i></div>
                    <p>Không tìm thấy đơn đặt phòng nào</p>
                </div>
            </td></tr>`;
            lucide.createIcons();
            const paginationContainer = document.getElementById("pagination-staff-bookings");
            if (paginationContainer) paginationContainer.innerHTML = '';
            return;
        }

        const start = (currentPage - 1) * itemsPerPage;
        const end = start + itemsPerPage;
        const paginatedList = currentDisplayList.slice(start, end);

        paginatedList.forEach((b, i) => {
            const normalizedStatus = String(b.status || '').toLowerCase();
            const s = STATUS_LABEL[normalizedStatus] || { label: 'Chưa xác định', cls: 'badge-maintenance' };
            let actions = '<span class="staff-bookings-na">Không có thao tác</span>';
            const decision = decisionAvailability(b);
            if (normalizedStatus === 'pending') {
                actions = decision.allowed
                    ? `<button class="btn btn-primary btn-sm" data-booking-action="confirmed" data-booking-id="${b.id}">Xác nhận</button><button class="btn btn-danger btn-sm" data-booking-action="cancelled" data-booking-id="${b.id}">Hủy đơn</button>`
                    : `<span class="staff-bookings-na" title="${decision.reason}">${decision.reason}</span>`;
            }
            if (normalizedStatus === 'confirmed') {
                const checkIn = checkInAvailability(b);
                if (checkIn.allowed) {
                    actions = `<button class="btn btn-primary btn-sm" data-booking-action="checkin" data-booking-id="${b.id}">Nhận phòng</button>`;
                } else if (decision.allowed) {
                    actions = `<button class="btn btn-danger btn-sm" data-booking-action="cancelled" data-booking-id="${b.id}">Hủy đơn</button>`;
                } else {
                    actions = `<span class="staff-bookings-na" title="${checkIn.reason}">${checkIn.reason}</span>`;
                }
            }
            if (normalizedStatus === 'checked_in') actions = `<button class="btn btn-primary btn-sm" data-booking-action="checkout" data-booking-id="${b.id}">Trả phòng</button>`;
            const tr = document.createElement("tr");
            tr.className = "table-row-animate";
            tr.style.animationDelay = `${i * 0.03}s`;
            tr.innerHTML = `
                <td style="font-weight:700;color:var(--primary);">#${b.id}</td>
                <td><strong>Phòng ${b.roomId}</strong></td>
                <td style="color:var(--text-muted);">KH #${b.customerId}</td>
                <td class="booking-datetime">${formatBookingDate(b.checkInDate)}</td>
                <td class="booking-datetime">${formatBookingDate(b.checkOutDate)}</td>
                <td style="font-weight:600;color:var(--success);">${Number(b.totalPrice || 0).toLocaleString('vi-VN')} đ</td>
                <td><span class="room-badge ${s.cls}" style="white-space:nowrap;">${s.label}</span></td>
                <td><div class="staff-bookings-actions">${actions}</div></td>
            `;
            body.appendChild(tr);
        });
        lucide.createIcons();

        if (window.renderPaginationComponent) {
            window.renderPaginationComponent(
                "pagination-staff-bookings",
                currentDisplayList.length,
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

    const bookingBody = document.getElementById("staff-bookings-body");
    let loadSequence = 0;
    const loadBookings = async () => {
        if (!bookingBody.isConnected) return;
        const sequence = ++loadSequence;
        bookingBody.innerHTML = skeletonRows(6);
        try {
            const loaded = await api.get("/bookings");
            if (!bookingBody.isConnected || sequence !== loadSequence) return;
            if (!Array.isArray(loaded)) throw new Error('Không thể tải danh sách đặt phòng. Vui lòng thử lại.');
            allBookings = loaded;

            const pending     = allBookings.filter(b => String(b.status || '').toLowerCase() === 'pending').length;
            const checkedIn   = allBookings.filter(b => String(b.status || '').toLowerCase() === 'checked_in').length;
            const totalRevenue = allBookings
                .filter(b => String(b.status || '').toLowerCase() !== 'cancelled')
                .reduce((s, b) => s + Number(b.totalPrice || 0), 0);

            const summaryBar = document.getElementById("staff-bookings-summary");
            summaryBar.innerHTML = `
                <div class="card stat stat-card"><div class="stat-title">Tổng đơn</div><div class="stat-value">${allBookings.length}</div></div>
                <div class="card stat stat-card"><div class="stat-title">Chờ xác nhận</div><div class="stat-value">${pending}</div></div>
                <div class="card stat stat-card"><div class="stat-title">Đang ở</div><div class="stat-value">${checkedIn}</div></div>
                <div class="card stat stat-card"><div class="stat-title">Doanh thu</div><div class="stat-value">${(totalRevenue/1e6).toFixed(1)}M đ</div></div>
            `;
            lucide.createIcons();
            // Preserve the API's created_at DESC, id DESC order before slicing pages.
            applyFilter();
        } catch (e) {
            if (!bookingBody.isConnected || sequence !== loadSequence) return;
            bookingBody.innerHTML = `<tr><td colspan="8" style="color:var(--danger);padding:20px;"></td></tr>`;
            bookingBody.querySelector('td').textContent = e.message;
        }
    };
    refreshBookingListOnReturn(bookingBody, loadBookings);
    loadBookings();

    document.getElementById("staff-bookings-search").addEventListener("input", applyFilter);
    document.getElementById("staff-bookings-status").addEventListener("change", applyFilter);
    document.getElementById("btn-refresh-bookings").addEventListener("click", () => {
        document.getElementById("staff-bookings-search").value = "";
        document.getElementById("staff-bookings-status").value = "";
        loadBookings();
    });

    document.getElementById("staff-bookings-body").addEventListener("click", async (event) => {
        const button = event.target.closest("button[data-booking-action]");
        if (!button) return;
        const action = button.dataset.bookingAction;
        const bookingId = encodeURIComponent(button.dataset.bookingId);
        if (action === 'checkout') { window.location.href = 'portal.html?view=checkout'; return; }
        const checkIn = action === 'checkin';
        const target = action === 'confirmed' ? 'confirmed' : 'cancelled';
        const message = checkIn ? 'Xác nhận nhận phòng cho đơn này?' : target === 'confirmed' ? 'Xác nhận đơn đặt phòng này?' : 'Hủy đơn này? Chỉ đơn chưa đến ngày nhận phòng và chưa phát sinh thanh toán hoặc hóa đơn mới được hủy.';
        const accepted = await window.showCustomConfirm(message, { title: checkIn ? 'Nhận phòng' : target === 'confirmed' ? 'Xác nhận đơn' : 'Hủy đơn', confirmLabel: checkIn ? 'Nhận phòng' : target === 'confirmed' ? 'Xác nhận' : 'Hủy đơn', kind: checkIn || target === 'confirmed' ? 'primary' : 'danger' });
        if (!accepted) return;
        button.disabled = true;
        try {
            if (checkIn) await api.put(`/bookings/checkin/${bookingId}`, {});
            else await api.put(`/bookings/${bookingId}/reception-status`, { status: target });
            await loadBookings();
            await window.showCustomAlert?.(
                checkIn ? 'Đã ghi nhận khách nhận phòng.' : target === 'confirmed' ? 'Đơn đặt phòng đã được xác nhận.' : 'Đơn đặt phòng đã được hủy.',
                { title: 'Thành công', kind: 'success' }
            );
        } catch (error) {
            await window.showCustomAlert?.(error.message, { title: 'Không thể cập nhật đơn' });
            button.disabled = false;
        }
    });
}
