import { api, generateUUID } from "../../api.js";
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
    overdue:    { label: 'Quá hạn trả phòng', cls: 'badge-maintenance' },
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
    if (checkIn === null || checkOut === null || checkOut <= checkIn) {
        return { allowed: false, state: 'invalid', reason: 'Thiếu hoặc sai thời gian lưu trú' };
    }
    const now = Date.now();
    if (now < checkIn) {
        const start = new Date(checkIn).toLocaleString('vi-VN', {
            timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric',
            hour: '2-digit', minute: '2-digit', hour12: false
        });
        return { allowed: false, state: 'upcoming', reason: `Chưa đến giờ nhận phòng. Có thể nhận từ ${start}.` };
    }
    if (now >= checkOut) return { allowed: false, state: 'ended', reason: 'Đã qua giờ check-out' };
    return { allowed: true, state: 'open', reason: '' };
}

function bookingPaymentChoice(booking) {
    return String(booking.paymentChoice ?? booking.payment_choice ?? '').trim().toLowerCase();
}

function bookingPaymentStatus(booking) {
    return String(booking.paymentStatus ?? booking.payment_status ?? booking.payment?.payment_status ?? '').trim().toUpperCase();
}

function needsCounterCollection(booking) {
    if (bookingPaymentChoice(booking) !== 'counter') return false;
    const remaining = Number(booking.payment?.remaining_amount ?? booking.payment?.remainingAmount);
    if (Number.isFinite(remaining)) return remaining > 0;
    return ['WAITING', 'PARTIAL', 'RECONCILIATION_ERROR'].includes(bookingPaymentStatus(booking));
}

function bookingCustomerType(booking) {
    return String(booking.customerType ?? booking.customer_type ?? '').trim().toLowerCase();
}

function isWebBooking(booking) {
    return bookingCustomerType(booking) === 'web';
}

function formatMoney(amount) {
    return `${Number(amount || 0).toLocaleString('vi-VN')} đ`;
}

function counterCollectionError(error) {
    if (error?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    if (error?.status === 403) return 'Tài khoản hiện tại không có quyền ghi nhận khoản thu tại quầy.';
    if (error?.status === 409) return 'Số dư hoặc trạng thái đơn vừa thay đổi. Hãy tải lại đơn rồi kiểm tra trước khi thu tiếp.';
    return 'Chưa nhận được xác nhận ghi sổ. Hãy thử lại cùng phương thức thu để tránh ghi nhận trùng.';
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
        <div id="staff-overdue-alert" class="staff-overdue-alert" hidden></div>

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
                    <option value="OVERDUE">Quá hạn trả phòng</option>
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
    const collectionAttempts = new Map();

    const applyFilter = (keepPage = false) => {
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
        if (!keepPage) currentPage = 1;
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
            const paymentChoice = bookingPaymentChoice(b);
            const webBooking = isWebBooking(b);
            const bookingChannel = bookingCustomerType(b);
            const s = STATUS_LABEL[normalizedStatus] || { label: 'Chưa xác định', cls: 'badge-maintenance' };
            const statusLabel = normalizedStatus === 'overdue'
                ? 'Quá hạn · cần liên hệ khách'
                : normalizedStatus === 'pending' && paymentChoice === 'counter'
                ? 'Chờ thanh toán tại quầy'
                : normalizedStatus === 'pending' && paymentChoice === 'qr'
                    ? 'Chờ thanh toán QR'
                : s.label;
            let actions = '<span class="staff-bookings-na">Không có thao tác</span>';
            const decision = decisionAvailability(b);
            if (normalizedStatus === 'pending') {
                const controls = [];
                if (needsCounterCollection(b)) {
                    controls.push(`<button class="btn btn-primary btn-sm" type="button" data-booking-action="collect-counter" data-booking-id="${escapeHtml(b.id)}">Thu tiền tại quầy</button>`);
                } else if (paymentChoice === 'qr') {
                    controls.push('<span class="staff-bookings-na">Chờ khách thanh toán QR</span>');
                } else {
                    controls.push('<span class="staff-bookings-na">Chưa có phương thức thu</span>');
                }
                if (decision.allowed) controls.push(`<button class="btn btn-danger btn-sm" type="button" data-booking-action="cancelled" data-booking-id="${escapeHtml(b.id)}">Hủy đơn</button>`);
                actions = controls.join('');
            }
            if (normalizedStatus === 'confirmed') {
                const checkIn = checkInAvailability(b);
                if (webBooking) {
                    const controls = [];
                    if (needsCounterCollection(b)) {
                        controls.push(`<button class="btn btn-primary btn-sm" type="button" data-booking-action="collect-counter" data-booking-id="${escapeHtml(b.id)}">Thu tiền tại quầy</button>`);
                    } else {
                        controls.push('<span class="staff-bookings-na staff-booking-automation-note">Hệ thống tự check-in/check-out · không cần nhận phòng thủ công</span>');
                    }
                    if (decision.allowed) controls.push(`<button class="btn btn-danger btn-sm" data-booking-action="cancelled" data-booking-id="${escapeHtml(b.id)}">Hủy đơn</button>`);
                    actions = controls.join('');
                } else if (checkIn.allowed) {
                    actions = `<button class="btn btn-primary btn-sm" data-booking-action="checkin" data-booking-id="${b.id}">Nhận phòng</button>`;
                } else if (checkIn.state === 'upcoming') {
                    const controls = [
                        `<button class="btn btn-primary btn-sm btn-disabled" type="button" disabled aria-describedby="checkin-note-${escapeHtml(b.id)}" title="${escapeHtml(checkIn.reason)}">Nhận phòng</button>`,
                        `<span class="staff-bookings-na" id="checkin-note-${escapeHtml(b.id)}">${escapeHtml(checkIn.reason)}</span>`
                    ];
                    if (decision.allowed) controls.push(`<button class="btn btn-danger btn-sm" type="button" data-booking-action="cancelled" data-booking-id="${escapeHtml(b.id)}">Hủy đơn</button>`);
                    actions = controls.join('');
                } else if (checkIn.state === 'ended') {
                    actions = `<span class="staff-bookings-na">${escapeHtml(checkIn.reason)} · Đơn đã kết thúc</span>`;
                } else if (decision.allowed) {
                    actions = `<button class="btn btn-danger btn-sm" data-booking-action="cancelled" data-booking-id="${b.id}">Hủy đơn</button>`;
                } else {
                    actions = `<span class="staff-bookings-na" title="${escapeHtml(checkIn.reason)}">${escapeHtml(checkIn.reason)}</span>`;
                }
            }
            if (normalizedStatus === 'checked_in') actions = webBooking
                ? needsCounterCollection(b)
                    ? `<button class="btn btn-primary btn-sm" type="button" data-booking-action="collect-counter" data-booking-id="${escapeHtml(b.id)}">Thu tiền tại quầy</button>`
                    : '<span class="staff-bookings-na staff-booking-automation-note">Hệ thống sẽ tự check-out · không cần thao tác trả phòng</span>'
                : `<button class="btn btn-primary btn-sm" data-booking-action="checkout" data-booking-id="${b.id}">Trả phòng</button>`;
            if (normalizedStatus === 'overdue') actions = `<button class="btn btn-primary btn-sm" data-booking-action="checkout" data-booking-id="${escapeHtml(b.id)}">Thu tiền & trả phòng</button>`;
            const channelNote = webBooking
                ? `<small class="staff-booking-channel-note is-automatic">Đặt qua Web · hệ thống tự check-in/check-out</small>${normalizedStatus === 'overdue' ? '<small class="staff-booking-channel-note is-overdue">Quá hạn 30 phút · gọi khách và thu tiền tại quầy</small>' : needsCounterCollection(b) && ['confirmed', 'checked_in'].includes(normalizedStatus) ? '<small class="staff-booking-channel-note is-counter-due">Còn số dư cần thu tại quầy</small>' : paymentChoice === 'counter' && bookingPaymentStatus(b) === 'PAID' ? '<small class="staff-booking-channel-note is-counter-due">Đã thanh toán tại quầy</small>' : ''}`
                : bookingChannel === 'walk-in'
                    ? '<small class="staff-booking-channel-note">Đặt tại quầy · nhân viên thao tác</small>'
                    : '';
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
                <td><div class="staff-booking-status-stack"><span class="room-badge ${s.cls}" style="white-space:nowrap;">${statusLabel}</span>${channelNote}</div></td>
                <td><div class="staff-bookings-actions flex flex-wrap gap-2">${actions}</div></td>
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

    let loadingBookings = false;
    const loadBookings = async ({ quiet = false } = {}) => {
        if (loadingBookings) return false;
        loadingBookings = true;
        const searchValue = document.getElementById("staff-bookings-search")?.value || '';
        const statusValue = document.getElementById("staff-bookings-status")?.value || '';
        if (!quiet) document.getElementById("staff-bookings-body").innerHTML = skeletonRows(6);
        try {
            allBookings = await api.get("/bookings") || [];

            const overdueBookings = allBookings.filter(b => String(b.status || '').toLowerCase() === 'overdue');
            let customers = [];
            if (overdueBookings.length) {
                try { customers = await api.get('/customers') || []; } catch { }
            }
            const customerById = new Map(customers.map(customer => [String(customer.id), customer]));
            const overdueAlert = document.getElementById('staff-overdue-alert');
            if (overdueAlert) {
                overdueAlert.hidden = overdueBookings.length === 0;
                overdueAlert.innerHTML = overdueBookings.length ? `
                    <div class="staff-overdue-alert-heading"><strong>Cần liên hệ khách quá giờ trả phòng (${overdueBookings.length})</strong><a class="btn btn-sm btn-primary" href="portal.html?view=checkout">Mở danh sách thu tiền</a></div>
                    <ul>${overdueBookings.map(booking => {
                        const customer = customerById.get(String(booking.customerId));
                        const phone = String(customer?.phone || '').trim();
                        const safePhone = phone.replace(/[^+\d]/g, '');
                        return `<li><span>Đơn #${escapeHtml(booking.id)} · phòng #${escapeHtml(booking.roomId)} · ${escapeHtml(customer?.fullName || `Khách #${booking.customerId}`)}</span>${safePhone ? `<a href="tel:${escapeHtml(safePhone)}">${escapeHtml(phone)}</a>` : '<span>Chưa có số điện thoại</span>'}</li>`;
                    }).join('')}</ul>` : '';
            }

            const pending     = allBookings.filter(b => String(b.status || '').toLowerCase() === 'pending').length;
            const checkedIn   = allBookings.filter(b => ['checked_in','overdue'].includes(String(b.status || '').toLowerCase())).length;
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
            allBookings.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
            if (quiet) {
                document.getElementById("staff-bookings-search").value = searchValue;
                document.getElementById("staff-bookings-status").value = statusValue;
                applyFilter(true);
            } else renderTable(allBookings);
            return true;
        } catch (e) {
            document.getElementById("staff-bookings-body").innerHTML =
                `<tr><td colspan="8" style="color:var(--danger);padding:20px;">${escapeHtml(e.message)}</td></tr>`;
            return false;
        } finally {
            loadingBookings = false;
        }
    };

    const openCounterCollection = async (bookingId) => {
        const booking = allBookings.find(item => String(item.id) === String(bookingId));
        if (!booking || !['pending', 'confirmed', 'checked_in'].includes(String(booking.status || '').toLowerCase()) || !needsCounterCollection(booking)) {
            await window.showCustomAlert?.('Đơn này không còn số dư cần thu tại quầy. Vui lòng làm mới danh sách.', { title: 'Không thể thu tiền' });
            await loadBookings();
            return;
        }

        let folios;
        try {
            const response = await api.get(`/folios/booking/${encodeURIComponent(booking.id)}`);
            folios = Array.isArray(response) ? response : response?.data;
        } catch (error) {
            await window.showCustomAlert?.(counterCollectionError(error), { title: 'Không tải được số dư folio' });
            return;
        }
        let openFolios = (Array.isArray(folios) ? folios : []).filter(folio => Number.isFinite(Number(folio.balance)) && Number(folio.balance) > 0);
        if (!openFolios.length) {
            await loadBookings();
            const refreshed = allBookings.find(item => String(item.id) === String(booking.id));
            const alreadyPaid = bookingPaymentStatus(refreshed || {}) === 'PAID';
            await window.showCustomAlert?.(
                alreadyPaid ? `Đơn #${booking.id} đã được thanh toán đủ.` : 'Đơn không còn số dư cần thu. Vui lòng kiểm tra trạng thái mới nhất.',
                { title: alreadyPaid ? 'Đã thanh toán đủ' : 'Không có số dư cần thu', kind: alreadyPaid ? 'success' : 'warning' }
            );
            return;
        }

        const sumBalances = rows => rows.reduce((sum, folio) => sum + Math.round(Number(folio.balance) * 100), 0) / 100;
        const balanceSignature = rows => rows.map(folio => `${folio.id}:${Number(folio.balance).toFixed(2)}`).join('|');
        const renderFolioLines = rows => rows.map(folio => `
            <div class="modal-detail-row">
                <p class="modal-detail-label">Folio ${escapeHtml(folio.folio_type || String(folio.id))}</p>
                <p class="modal-detail-value">${formatMoney(folio.balance)}</p>
            </div>`).join('');
        let totalDue = sumBalances(openFolios);
        let submitButtonLabel = `Xác nhận đã thu ${formatMoney(totalDue)}`;
        const pendingAttempt = openFolios
            .map(folio => collectionAttempts.get(`${booking.id}:${folio.id}`))
            .find(Boolean);
        const modal = document.getElementById('global-modal');
        const content = document.getElementById('global-modal-content');
        if (!modal || !content) {
            await window.showCustomAlert?.('Không mở được hộp thoại thu tiền. Vui lòng tải lại trang.', { title: 'Không thể thu tiền' });
            return;
        }

        content.innerHTML = `
            <div class="modal-header">
                <h2>Thu tiền tại quầy · Đơn #${escapeHtml(booking.id)}</h2>
                <button class="modal-close" type="button" id="counter-collection-close" aria-label="Đóng">&times;</button>
            </div>
            <div class="modal-body">
                <p>Phòng ${escapeHtml(booking.roomId)} · Khách hàng #${escapeHtml(booking.customerId)}</p>
                <div class="modal-detail-list" id="counter-collection-breakdown">
                    ${renderFolioLines(openFolios)}
                    <div class="modal-detail-row">
                        <p class="modal-detail-label">Tổng cần thu</p>
                        <p class="modal-detail-value" id="counter-collection-total">${formatMoney(totalDue)}</p>
                    </div>
                </div>
                <label for="counter-collection-method" class="block mt-4 mb-2">Phương thức thu</label>
                <select id="counter-collection-method" class="select select-md form-input w-full" aria-label="Phương thức thu tại quầy">
                    <option value="cash">Tiền mặt</option>
                    <option value="POS">Thẻ ngân hàng (POS)</option>
                </select>
                <p class="mt-3 text-sm">Chỉ xác nhận sau khi đã nhận đủ số tiền. Hệ thống ghi nhận giao dịch vào folio và cập nhật trạng thái thanh toán; đơn chờ xác nhận sẽ được xác nhận khi số dư về 0.</p>
                <p id="counter-collection-retry-note" class="mt-2 text-sm" role="status" aria-live="polite"></p>
                <p id="counter-collection-error" class="mt-2 text-sm text-error" role="alert" aria-live="assertive"></p>
            </div>
            <div class="modal-actions">
                <button class="btn btn-secondary" type="button" id="counter-collection-cancel">Hủy</button>
                <button class="btn btn-primary" type="button" id="counter-collection-submit">${submitButtonLabel}</button>
            </div>`;
        modal.classList.add('active');
        modal.setAttribute('aria-hidden', 'false');

        const methodSelect = content.querySelector('#counter-collection-method');
        const retryNote = content.querySelector('#counter-collection-retry-note');
        const errorMessage = content.querySelector('#counter-collection-error');
        const submitButton = content.querySelector('#counter-collection-submit');
        const close = () => {
            modal.classList.remove('active');
            modal.setAttribute('aria-hidden', 'true');
            content.replaceChildren();
            modal.removeEventListener('click', onBackdropClick);
            document.removeEventListener('keydown', onKeyDown);
        };
        const onBackdropClick = event => { if (event.target === modal) close(); };
        const onKeyDown = event => { if (event.key === 'Escape' && modal.classList.contains('active')) close(); };
        modal.addEventListener('click', onBackdropClick);
        document.addEventListener('keydown', onKeyDown);
        content.querySelector('#counter-collection-close').addEventListener('click', close);
        content.querySelector('#counter-collection-cancel').addEventListener('click', close);
        if (pendingAttempt) {
            methodSelect.value = pendingAttempt.paymentMethod;
            methodSelect.disabled = true;
            retryNote.textContent = 'Lần ghi nhận trước chưa nhận được phản hồi. Hãy thử lại cùng phương thức để hệ thống kiểm tra giao dịch mà không ghi trùng.';
        }

        submitButton.addEventListener('click', async () => {
            const method = methodSelect.value;
            submitButton.disabled = true;
            methodSelect.disabled = true;
            content.querySelector('#counter-collection-cancel').disabled = true;
            errorMessage.textContent = '';
            retryNote.textContent = 'Đang ghi nhận khoản thu…';
            let activeAttemptKey = null;
            try {
                const latestResponse = await api.get(`/folios/booking/${encodeURIComponent(booking.id)}`);
                const latestFolios = Array.isArray(latestResponse) ? latestResponse : latestResponse?.data;
                const latestOpenFolios = (Array.isArray(latestFolios) ? latestFolios : [])
                    .filter(folio => Number.isFinite(Number(folio.balance)) && Number(folio.balance) > 0);
                if (!latestOpenFolios.length) {
                    await loadBookings();
                    const refreshed = allBookings.find(item => String(item.id) === String(booking.id));
                    const alreadyPaid = bookingPaymentStatus(refreshed || {}) === 'PAID';
                    close();
                    await window.showCustomAlert?.(
                        alreadyPaid ? `Đơn #${booking.id} đã được thanh toán đủ.` : 'Đơn không còn số dư cần thu. Vui lòng kiểm tra trạng thái mới nhất.',
                        { title: alreadyPaid ? 'Đã thanh toán đủ' : 'Không có số dư cần thu', kind: alreadyPaid ? 'success' : 'warning' }
                    );
                    return;
                }
                if (balanceSignature(latestOpenFolios) !== balanceSignature(openFolios)) {
                    openFolios = latestOpenFolios;
                    totalDue = sumBalances(openFolios);
                    content.querySelector('#counter-collection-breakdown').innerHTML = `${renderFolioLines(openFolios)}
                        <div class="modal-detail-row">
                            <p class="modal-detail-label">Tổng cần thu</p>
                            <p class="modal-detail-value" id="counter-collection-total">${formatMoney(totalDue)}</p>
                        </div>`;
                    submitButtonLabel = `Xác nhận đã thu ${formatMoney(totalDue)}`;
                    retryNote.textContent = 'Số dư vừa thay đổi. Hãy kiểm tra số tiền mới rồi xác nhận lại.';
                    const retryAttempt = openFolios.map(folio => collectionAttempts.get(`${booking.id}:${folio.id}`)).find(Boolean);
                    if (retryAttempt) {
                        methodSelect.value = retryAttempt.paymentMethod;
                        methodSelect.disabled = true;
                    } else methodSelect.disabled = false;
                    return;
                }
                for (const folio of openFolios) {
                    const amount = Number(folio.balance);
                    const signature = JSON.stringify({ amount, payment_method: method, checkout: false });
                    const attemptKey = `${booking.id}:${folio.id}`;
                    const savedAttempt = collectionAttempts.get(attemptKey);
                    const attempt = savedAttempt?.signature === signature
                        ? savedAttempt
                        : { signature, key: generateUUID(), paymentMethod: method };
                    collectionAttempts.set(attemptKey, attempt);
                    activeAttemptKey = attemptKey;
                    const signal = typeof AbortSignal.timeout === 'function' ? AbortSignal.timeout(15000) : undefined;
                    await api.post(`/folios/${encodeURIComponent(folio.id)}/pay`, {
                        amount,
                        payment_method: method,
                        checkout: false
                    }, { headers: { 'Idempotency-Key': attempt.key }, signal, retryOnNetworkError: false });
                    collectionAttempts.delete(attemptKey);
                }

                const bookingsReloaded = await loadBookings();
                const refreshed = allBookings.find(item => String(item.id) === String(booking.id));
                const fullyPaid = bookingsReloaded && bookingPaymentStatus(refreshed || {}) === 'PAID';
                close();
                await window.showCustomAlert?.(
                    fullyPaid
                        ? `Đã ghi nhận ${formatMoney(totalDue)} bằng ${method === 'cash' ? 'tiền mặt' : 'POS'}. Đơn #${booking.id} đã thanh toán đủ.`
                        : bookingsReloaded
                            ? `Đã ghi nhận ${formatMoney(totalDue)} bằng ${method === 'cash' ? 'tiền mặt' : 'POS'}. Đơn #${booking.id} vẫn còn số dư cần thu.`
                            : `Máy chủ đã ghi nhận khoản thu ${formatMoney(totalDue)} bằng ${method === 'cash' ? 'tiền mặt' : 'POS'}, nhưng danh sách chưa tải lại được. Hãy làm mới để kiểm tra trạng thái đơn.`,
                    { title: fullyPaid ? 'Đã thu đủ tiền' : 'Đã ghi nhận khoản thu', kind: 'success' }
                );
            } catch (error) {
                if (activeAttemptKey && Number(error?.status) > 0 && Number(error.status) < 500) {
                    collectionAttempts.delete(activeAttemptKey);
                }
                retryNote.textContent = collectionAttempts.has(activeAttemptKey) ? 'Hãy bấm thử lại cùng phương thức thu để đối chiếu giao dịch.' : '';
                errorMessage.textContent = counterCollectionError(error);
                submitButtonLabel = 'Thử lại ghi nhận khoản thu';
                const retryAttempt = openFolios.map(folio => collectionAttempts.get(`${booking.id}:${folio.id}`)).find(Boolean);
                if (retryAttempt) {
                    methodSelect.value = retryAttempt.paymentMethod;
                    methodSelect.disabled = true;
                } else {
                    methodSelect.disabled = false;
                }
                content.querySelector('#counter-collection-cancel').disabled = false;
            } finally {
                if (submitButton.isConnected) {
                    submitButton.disabled = false;
                    submitButton.textContent = submitButtonLabel;
                }
            }
        });
    };
    loadBookings();
    const overdueRefreshTimer = window.setInterval(() => {
        if (!document.getElementById('staff-bookings-body')) { window.clearInterval(overdueRefreshTimer); return; }
        if (document.visibilityState === 'visible') loadBookings({ quiet: true });
    }, 30000);

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
        if (action === 'collect-counter') {
            button.disabled = true;
            await openCounterCollection(button.dataset.bookingId);
            if (button.isConnected) button.disabled = false;
            return;
        }
        const checkIn = action === 'checkin';
        const target = 'cancelled';
        const message = checkIn ? 'Xác nhận nhận phòng cho đơn này?' : 'Hủy đơn này? Chỉ đơn chưa đến ngày nhận phòng và chưa phát sinh thanh toán hoặc hóa đơn mới được hủy.';
        const accepted = await window.showCustomConfirm(message, { title: checkIn ? 'Nhận phòng' : 'Hủy đơn', confirmLabel: checkIn ? 'Nhận phòng' : 'Hủy đơn', kind: checkIn ? 'primary' : 'danger' });
        if (!accepted) return;
        button.disabled = true;
        try {
            if (checkIn) await api.put(`/bookings/checkin/${bookingId}`, {});
            else await api.put(`/bookings/${bookingId}/reception-status`, { status: target });
            await loadBookings();
            await window.showCustomAlert?.(
                checkIn ? 'Đã ghi nhận khách nhận phòng.' : 'Đơn đặt phòng đã được hủy.',
                { title: 'Thành công', kind: 'success' }
            );
        } catch (error) {
            await window.showCustomAlert?.(error.message, { title: 'Không thể cập nhật đơn' });
            button.disabled = false;
        }
    });
}
