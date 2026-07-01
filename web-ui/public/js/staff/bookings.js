import { api } from "../../api.js";

const STATUS_LABEL = {
    PENDING:   { label: 'Chờ xác nhận', cls: 'badge-booked' },
    CONFIRMED: { label: 'Đã xác nhận',  cls: 'badge-occupied' },
    CHECKED_IN:{ label: 'Đã check-in',  cls: 'badge-available' },
    CHECKED_OUT:{ label: 'Đã trả phòng', cls: 'badge-cleaning' },
    CANCELLED: { label: 'Đã hủy',       cls: 'badge-maintenance' },
};

function skeletonRows(count = 6) {
    return Array.from({ length: count }, () => `
        <tr>${Array.from({ length: 7 }, (_, i) =>
            `<td><div class="skeleton-block" style="width:${[30,70,90,80,80,80,60][i]}px;border-radius:${i===6?'30px':'4px'};"></div></td>`
        ).join('')}</tr>`).join('');
}

export async function renderBookings(container, session) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Đơn Đặt Phòng</h1>
                <p>Quản lý check-in, check-out và trạng thái đặt phòng của khách hàng.</p>
            </div>
        </div>

        <!-- Summary bar -->
        <div id="staff-bookings-summary" style="display:flex;gap:16px;margin-bottom:24px;"></div>

        <!-- Search + filter -->
        <div class="card" style="padding:14px 20px;margin-bottom:0;">
            <div style="display:flex;align-items:center;gap:10px;">
                <div style="position:relative;flex:1;">
                    <i data-lucide="search" style="width:15px;height:15px;position:absolute;left:12px;top:50%;transform:translateY(-50%);color:var(--text-muted);pointer-events:none;"></i>
                    <input id="staff-bookings-search" class="form-input" type="text"
                        placeholder="Tìm theo mã phòng, ID khách hàng, trạng thái..."
                        style="padding-left:36px;margin:0;">
                </div>
                <select id="staff-bookings-status" class="form-input" style="width:180px;margin:0;">
                    <option value="">Tất cả trạng thái</option>
                    <option value="PENDING">Chờ xác nhận</option>
                    <option value="CONFIRMED">Đã xác nhận</option>
                    <option value="CHECKED_IN">Đã check-in</option>
                    <option value="CHECKED_OUT">Đã trả phòng</option>
                    <option value="CANCELLED">Đã hủy</option>
                </select>
                <button id="btn-refresh-bookings" class="btn btn-secondary" style="padding:10px 14px;flex-shrink:0;" title="Làm mới">
                    <i data-lucide="refresh-cw" style="width:15px;height:15px;"></i>
                </button>
            </div>
        </div>

        <div class="card" style="margin-top:0;border-top:none;border-top-left-radius:0;border-top-right-radius:0;">
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
                        </tr>
                    </thead>
                    <tbody id="staff-bookings-body">
                        ${skeletonRows(6)}
                    </tbody>
                </table>
            </div>
            <div id="pagination-staff-bookings"></div>
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
        if (status) list = list.filter(b => b.status === status);
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
            body.innerHTML = `<tr><td colspan="7">
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
            const s = STATUS_LABEL[b.status] || { label: b.status, cls: '' };
            const tr = document.createElement("tr");
            tr.className = "table-row-animate";
            tr.style.animationDelay = `${i * 0.03}s`;
            tr.innerHTML = `
                <td style="font-weight:700;color:var(--primary);">#${b.id}</td>
                <td><strong>Phòng ${b.roomId}</strong></td>
                <td style="color:var(--text-muted);">KH #${b.customerId}</td>
                <td style="font-size:13px;">${b.checkInDate || '—'}</td>
                <td style="font-size:13px;">${b.checkOutDate || '—'}</td>
                <td style="font-weight:600;color:var(--success);">${Number(b.totalPrice || 0).toLocaleString('vi-VN')} đ</td>
                <td><span class="room-badge ${s.cls}" style="white-space:nowrap;">${s.label}</span></td>
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

    const loadBookings = async () => {
        document.getElementById("staff-bookings-body").innerHTML = skeletonRows(6);
        try {
            allBookings = await api.get("/bookings") || [];

            const pending     = allBookings.filter(b => b.status === 'PENDING').length;
            const checkedIn   = allBookings.filter(b => b.status === 'CHECKED_IN').length;
            const totalRevenue = allBookings
                .filter(b => b.status !== 'CANCELLED')
                .reduce((s, b) => s + Number(b.totalPrice || 0), 0);

            const summaryBar = document.getElementById("staff-bookings-summary");
            summaryBar.innerHTML = `
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--info);">
                    <i data-lucide="calendar-days" style="width:18px;height:18px;color:var(--info);"></i>
                    <div>
                        <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${allBookings.length}</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tổng đơn</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--warning);">
                    <i data-lucide="clock" style="width:18px;height:18px;color:var(--warning);"></i>
                    <div>
                        <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${pending}</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Chờ xác nhận</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--success);">
                    <i data-lucide="door-open" style="width:18px;height:18px;color:var(--success);"></i>
                    <div>
                        <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${checkedIn}</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Đang ở</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--primary);">
                    <i data-lucide="banknote" style="width:18px;height:18px;color:var(--primary);"></i>
                    <div>
                        <div style="font-size:16px;font-weight:700;font-family:var(--font-heading);">${(totalRevenue/1e6).toFixed(1)}M đ</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Doanh thu</div>
                    </div>
                </div>
            `;
            lucide.createIcons();
            renderTable(allBookings);
        } catch (e) {
            document.getElementById("staff-bookings-body").innerHTML =
                `<tr><td colspan="7" style="color:var(--danger);padding:20px;">${e.message}</td></tr>`;
        }
    };
    loadBookings();

    document.getElementById("staff-bookings-search").addEventListener("input", applyFilter);
    document.getElementById("staff-bookings-status").addEventListener("change", applyFilter);
    document.getElementById("btn-refresh-bookings").addEventListener("click", () => {
        document.getElementById("staff-bookings-search").value = "";
        document.getElementById("staff-bookings-status").value = "";
        loadBookings();
    });
}
