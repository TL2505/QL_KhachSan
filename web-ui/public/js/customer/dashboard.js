import { api } from "../../api.js";

const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[char]));

const reviewControl = booking => {
    if (booking.hasReview) return '<span class="customer-review-complete">Đã đánh giá</span>';
    if (!booking.canReview) return '—';
    return `<form class="customer-review-form" data-booking-id="${escapeHtml(booking.id)}">
        <label>Đánh giá
            <select name="rating" required aria-label="Số sao đánh giá">
                <option value="5">5 sao</option><option value="4">4 sao</option>
                <option value="3">3 sao</option><option value="2">2 sao</option><option value="1">1 sao</option>
            </select>
        </label>
        <label class="customer-review-comment">Chia sẻ trải nghiệm
            <textarea name="comment" rows="2" maxlength="1000" placeholder="Ý kiến của bạn (không bắt buộc)"></textarea>
        </label>
        <button class="btn btn-secondary btn-sm" type="submit">Gửi đánh giá</button>
        <span class="customer-review-message" role="status" aria-live="polite"></span>
    </form>`;
};

const bindReviewForms = container => {
    if (container.dataset.reviewFormsBound) return;
    container.dataset.reviewFormsBound = 'true';
    container.addEventListener('submit', async event => {
        const form = event.target.closest('.customer-review-form');
        if (!form) return;
        event.preventDefault();
        const button = form.querySelector('button[type="submit"]');
        const message = form.querySelector('.customer-review-message');
        button.disabled = true;
        button.textContent = 'Đang gửi…';
        message.textContent = '';
        try {
            await api.post('/customer-portal/reviews', {
                booking_id: Number(form.dataset.bookingId),
                rating: Number(form.elements.rating.value),
                comment: form.elements.comment.value.trim()
            });
            form.replaceWith(Object.assign(document.createElement('span'), {
                className: 'customer-review-complete',
                textContent: 'Đánh giá đã được gửi'
            }));
        } catch (error) {
            message.textContent = error.message || 'Không thể gửi đánh giá. Vui lòng thử lại.';
            button.disabled = false;
            button.textContent = 'Gửi đánh giá';
        }
    });
};

export async function renderBookings(container) {
    container.innerHTML = `<div class="page-header"><h1>Đặt phòng của tôi</h1><p>Đơn mới nhất được hiển thị đầu tiên.</p></div><div class="card"><div class="table-container"><table class="table"><thead><tr><th>Mã đặt phòng / Phòng</th><th>Nhận phòng</th><th>Trả phòng</th><th>Tổng tiền</th><th>Trạng thái</th><th>Thanh toán / Hóa đơn</th><th>Đánh giá</th></tr></thead><tbody id="customer-portal-bookings"><tr><td colspan="7">Đang tải…</td></tr></tbody></table></div></div>`;
    const tbody = document.getElementById('customer-portal-bookings');
    bindReviewForms(tbody);
    try {
        const list = await api.get('/customer-portal/bookings');
        const statusLabels = { pending: 'Chờ xác nhận', confirmed: 'Đã xác nhận', checked_in: 'Đang lưu trú', checked_out: 'Đã trả phòng', cancelled: 'Đã hủy', no_show: 'Không đến' };
        tbody.innerHTML = list.length ? list.map(b => `<tr><td><strong>${escapeHtml(b.bookingCode || `#${b.id}`)}</strong><br><span>Phòng ${escapeHtml(b.roomNumber || `#${b.roomId}`)}</span></td><td>${new Date(b.checkInDate).toLocaleDateString('vi-VN')}</td><td>${new Date(b.checkOutDate).toLocaleDateString('vi-VN')}</td><td>${Number(b.totalPrice || 0).toLocaleString('vi-VN')} đ</td><td>${escapeHtml(statusLabels[b.status] || b.status)}</td><td>${b.paymentStatus === 'paid' ? 'Đã thanh toán' : b.paymentStatus === 'pending' ? 'Chờ thanh toán' : 'Chưa có thông tin thanh toán'}${b.invoiceId ? `<br><a href="portal.html?view=invoices#invoice-${encodeURIComponent(b.invoiceId)}">Hóa đơn INV-${escapeHtml(b.invoiceId)}</a>` : ''}</td><td>${reviewControl(b)}</td></tr>`).join('') : '<tr><td colspan="7">Chưa có đơn đặt phòng.</td></tr>';
    } catch (e) { tbody.innerHTML = `<tr><td colspan="7">${escapeHtml(e.message)}</td></tr>`; }
}

export async function renderInvoices(container) {
    container.innerHTML = `<div class="page-header"><h1>Hóa đơn của tôi</h1><p>Hóa đơn mới nhất được hiển thị đầu tiên.</p></div><div class="card"><div class="table-container"><table class="table"><thead><tr><th>Hóa đơn</th><th>Phòng</th><th>Ngày xuất</th><th>Thành tiền</th><th></th></tr></thead><tbody id="customer-portal-invoices"><tr><td colspan="5">Đang tải…</td></tr></tbody></table></div></div>`;
    try {
        const list = await api.get('/customer-portal/invoices');
        document.getElementById('customer-portal-invoices').innerHTML = list.length ? list.map(i => `<tr id="invoice-${escapeHtml(i.id)}"><td>INV-${escapeHtml(i.id)}<br><small>Đơn #${escapeHtml(i.bookingId)}</small></td><td>${escapeHtml(i.roomNumber)}</td><td>${new Date(i.issueDate).toLocaleDateString('vi-VN')}</td><td>${Number(i.finalTotal || 0).toLocaleString('vi-VN')} đ</td><td><button class="btn btn-secondary btn-sm" data-invoice-id="${escapeHtml(i.id)}">Xem & in</button></td></tr>`).join('') : '<tr><td colspan="5">Chưa có hóa đơn.</td></tr>';
        const targetInvoice = location.hash.match(/^#invoice-(\d+)$/);
        if (targetInvoice) document.getElementById(`invoice-${targetInvoice[1]}`)?.scrollIntoView({ block: 'center' });
    } catch (e) { document.getElementById('customer-portal-invoices').innerHTML = `<tr><td colspan="5">${escapeHtml(e.message)}</td></tr>`; }
}

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
                            <th>Trạng thái / Thanh toán</th>
                            <th>Hóa đơn</th>
                            <th>Đánh giá</th>
                        </tr>
                    </thead>
                    <tbody id="cust-bookings-body">
                        <tr><td colspan="7" style="text-align:center;padding:40px;color:var(--text-muted);">
                            <i data-lucide="loader" style="width:16px;height:16px;"></i> Đang tải lịch sử đặt phòng...
                        </td></tr>
                    </tbody>
                </table>
            </div>
            <div id="pagination-cust-bookings"></div>
        </div>
    `;
    lucide.createIcons();
    bindReviewForms(container);

    try {
        const [overview, bookings, loyaltyHistory, activePromos] = await Promise.all([
            api.get('/customer-portal/overview'),
            api.get('/customer-portal/bookings'),
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
        totalPoints = Math.max(0, Number(overview?.loyaltyPoints ?? totalPoints));

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
                    <tr><td colspan="7">
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
                        <br><small>${b.paymentStatus === 'paid' ? 'Đã thanh toán' : b.paymentStatus === 'pending' ? 'Chờ thanh toán' : 'Chưa có thông tin thanh toán'}</small>
                    </td>
                    <td>${b.invoiceId ? `<a href="portal.html?view=invoices#invoice-${encodeURIComponent(b.invoiceId)}">INV-${escapeHtml(b.invoiceId)}</a>` : 'Chưa phát hành'}</td>
                    <td>${reviewControl(b)}</td>
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
        const message = e?.message || 'Không thể tải dữ liệu tài khoản. Vui lòng đăng nhập lại hoặc thử lại sau.';
        const statsGrid = document.getElementById('cust-stats-grid');
        const bookingsBody = document.getElementById('cust-bookings-body');
        if (statsGrid) statsGrid.innerHTML = `<div class="customer-error" style="grid-column:1/-1;">${message}</div>`;
        if (bookingsBody) bookingsBody.innerHTML = `<tr><td colspan="7" class="customer-error">${escapeHtml(message)}</td></tr>`;
    }
}
