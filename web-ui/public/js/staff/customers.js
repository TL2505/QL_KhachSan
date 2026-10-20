import { api } from "../../api.js";
import { escapeHtml } from "../shared/ui.js";

export function normalizeStaffCustomer(customer) {
    const text = (...values) => values.find(value => typeof value === "string" && value.trim())?.trim() || "";
    return { ...customer, fullName: text(customer.fullName, customer.full_name),
        identityCard: text(customer.identityCard, customer.identity_card),
        phone: text(customer.phone), email: text(customer.email), address: text(customer.address) };
}

function avatarColor(str) {
    const colors = ['#d97706','#10b981','#3b82f6','#8b5cf6','#06b6d4','#ef4444'];
    let hash = 0;
    for (let c of (str || 'K')) hash = c.charCodeAt(0) + ((hash << 5) - hash);
    return colors[Math.abs(hash) % colors.length];
}
function getInitials(name) {
    if (!name) return 'KH';
    const parts = name.trim().split(/\s+/);
    return parts.length >= 2
        ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
        : name.substring(0, 2).toUpperCase();
}
function skeletonRows(count = 5, cols = 6) {
    return Array.from({ length: count }, () => `
        <tr>${Array.from({ length: cols }, (_, i) => `
            <td><div class="skeleton-block" style="width:${i===0?'36px':i===1?'130px':'90px'};${i===0?'height:36px;border-radius:50%;':''}"></div></td>
        `).join('')}</tr>`).join('');
}

export async function renderCustomers(container, session) {
    container.innerHTML = `
        <style>
            body.staff-portal:has(.staff-customers-view) .portal-shell { grid-template-columns:248px minmax(0,1fr); }
            body.staff-portal:has(.staff-customers-view) :is(.portal-sidebar,.portal-nav) { min-width:0; }
            @media(max-width:1000px) { body.staff-portal:has(.staff-customers-view) .portal-shell { grid-template-columns:minmax(0,1fr); } }
            .staff-customers-view { min-width:0; --text-muted:var(--portal-muted); --text-main:var(--portal-text); --primary:var(--portal-gold); }
            #staff-cust-summary { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); align-items:stretch; gap:16px; margin-bottom:24px; }
            #staff-cust-summary .staff-customer-stat { display:grid; grid-template-columns:44px minmax(0,1fr); align-items:center; gap:16px; min-height:112px; padding:22px; border:1px solid var(--portal-border); border-left:3px solid var(--stat-color); border-radius:13px; background:var(--portal-panel); text-align:left; }
            .staff-customer-stat-icon { display:grid; place-items:center; width:44px; height:44px; border-radius:12px; color:var(--stat-color); background:color-mix(in srgb,var(--stat-color) 10%,transparent); }
            .staff-customer-stat-icon svg { display:block; width:21px; height:21px; }
            .staff-customer-stat-info { display:grid; align-content:center; gap:7px; min-width:0; }
            .staff-customer-stat-value { display:block; color:var(--portal-text); font-size:28px; font-weight:700; line-height:1; font-variant-numeric:tabular-nums; }
            .staff-customer-stat-label { display:block; color:var(--portal-muted); font-size:11px; line-height:1.4; letter-spacing:.04em; text-transform:uppercase; }
            .staff-customers-view .staff-customer-search { min-width:0; }
            .staff-customers-view .staff-customer-search input { width:100%; }
            .staff-customers-view .user-avatar, .staff-customer-detail .user-avatar { display:grid; place-items:center; width:36px; height:36px; border-radius:50%; color:#fff; font-size:12px; font-weight:700; line-height:1; flex:none; }
            .staff-customers-view .staff-customer-name { min-width:140px; overflow-wrap:anywhere; }
            .staff-customers-view .staff-customer-email { min-width:160px; overflow-wrap:anywhere; white-space:normal; }
            .staff-customers-view .staff-customer-address { min-width:140px; max-width:260px; overflow-wrap:anywhere; white-space:normal; }
            .staff-customers-view .staff-customer-phone, .staff-customers-view .staff-customer-identity { white-space:nowrap; font-variant-numeric:tabular-nums; }
            .staff-customers-view .btn-view-sc { display:inline-grid; place-items:center; width:34px; height:34px; padding:0; border:1px solid var(--portal-border); border-radius:8px; background:transparent; color:var(--portal-soft); cursor:pointer; }
            .staff-customer-detail { --text-muted:var(--portal-muted); --bg-card:var(--portal-surface); --radius-md:10px; --radius-sm:8px; }
            .staff-customer-detail .grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); }
            .staff-customer-detail .grid > div { min-width:0; overflow-wrap:anywhere; }
            @media(max-width:700px) { #staff-cust-summary { gap:8px; } #staff-cust-summary .staff-customer-stat { grid-template-columns:minmax(0,1fr); gap:10px; min-height:120px; padding:14px 12px; } .staff-customer-stat-icon { width:30px; height:30px; border-radius:8px; } .staff-customer-stat-icon svg { width:17px; height:17px; } .staff-customer-stat-value { font-size:25px; } .staff-customer-stat-label { font-size:10px; } }
            @media(max-width:450px) { .staff-customer-detail .grid { grid-template-columns:minmax(0,1fr); } }
        </style>
        <section class="staff-customers-view">
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Hồ Sơ Khách Hàng</h1>
                <p>Tra cứu và quản lý thông tin khách hàng đăng ký lưu trú.</p>
            </div>
            <button id="btn-add-customer" class="btn btn-primary">
                <i data-lucide="user-plus" style="width:14px;height:14px;"></i> Thêm khách hàng
            </button>
        </div>

        <!-- Summary bar -->
        <div id="staff-cust-summary" aria-label="Thống kê khách hàng"></div>

        <!-- Search bar -->
        <div class="card" style="padding:14px 20px;margin-bottom:0;">
            <div style="display:flex;align-items:center;gap:10px;">
                <div class="staff-customer-search" style="position:relative;flex:1;">
                    <i data-lucide="search" style="width:15px;height:15px;position:absolute;left:12px;top:50%;transform:translateY(-50%);color:var(--text-muted);pointer-events:none;"></i>
                    <input id="staff-cust-search" class="form-input" type="text"
                        placeholder="Tìm theo tên, CCCD, SĐT, email..."
                        style="padding-left:36px;margin:0;">
                </div>
                <button id="btn-refresh-cust" class="btn btn-secondary" style="padding:10px 14px;flex-shrink:0;" title="Làm mới">
                    <i data-lucide="refresh-cw" style="width:15px;height:15px;"></i>
                </button>
            </div>
        </div>

        <div class="card" style="margin-top:0;border-top:none;border-top-left-radius:0;border-top-right-radius:0;">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th style="width:52px;"></th>
                            <th>Họ và tên</th>
                            <th>Số CCCD/Passport</th>
                            <th>Điện thoại</th>
                            <th>Email</th>
                            <th>Địa chỉ</th>
                            <th style="text-align:right;">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="staff-customers-body">
                        ${skeletonRows(5, 7)}
                    </tbody>
                </table>
            </div>
            <div id="pagination-staff-customers"></div>
        </div>
        </section>
    `;
    lucide.createIcons();

    let allCustomers = [];
    let currentDisplayList = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const renderTable = (list = currentDisplayList) => {
        currentDisplayList = list;
        const body = document.getElementById("staff-customers-body");
        body.innerHTML = "";
        if (!list || list.length === 0) {
            body.innerHTML = `<tr><td colspan="7">
                <div class="empty-state">
                    <div class="empty-state-icon"><i data-lucide="users" style="width:26px;height:26px;"></i></div>
                    <p>Không tìm thấy khách hàng nào</p>
                </div>
            </td></tr>`;
            lucide.createIcons();
            const paginationContainer = document.getElementById("pagination-staff-customers");
            if (paginationContainer) paginationContainer.innerHTML = '';
            return;
        }

        const start = (currentPage - 1) * itemsPerPage;
        const end = start + itemsPerPage;
        const paginatedList = currentDisplayList.slice(start, end);

        paginatedList.forEach((c, i) => {
            const initials = getInitials(c.fullName);
            const color    = avatarColor(c.fullName);
            const tr = document.createElement("tr");
            tr.className = "table-row-animate";
            tr.style.animationDelay = `${i * 0.04}s`;
            tr.innerHTML = `
                <td><div class="user-avatar" style="background:${color};">${escapeHtml(initials)}</div></td>
                <td class="staff-customer-name">
                    <div style="font-weight:600;">${escapeHtml(c.fullName || '—')}</div>
                    <div style="font-size:11px;color:var(--text-muted);">ID: ${escapeHtml(c.id)}</div>
                </td>
                <td class="staff-customer-identity" style="font-size:13px;font-family:monospace;">${escapeHtml(c.identityCard || '—')}</td>
                <td class="staff-customer-phone" style="font-size:13px;">${c.phone
                    ? `<a href="tel:${escapeHtml(c.phone)}" style="color:var(--text-main);text-decoration:none;">${escapeHtml(c.phone)}</a>`
                    : '<span style="color:var(--text-muted);">—</span>'}</td>
                <td class="staff-customer-email" style="font-size:13px;">${c.email
                    ? `<a href="mailto:${escapeHtml(c.email)}" style="color:var(--portal-soft);text-decoration:none;">${escapeHtml(c.email)}</a>`
                    : '<span style="color:var(--text-muted);">—</span>'}</td>
                <td class="staff-customer-address" style="font-size:12px;color:var(--text-muted);" title="${escapeHtml(c.address || '')}">${escapeHtml(c.address || '—')}</td>
                <td style="text-align:right;">
                    <div class="action-btns" style="justify-content:flex-end;">
                        <button class="btn-icon btn-view-sc" data-id="${escapeHtml(c.id)}" title="Xem chi tiết">
                            <i data-lucide="eye" style="width:14px;height:14px;"></i>
                        </button>
                    </div>
                </td>
            `;
            // View detail (staff chỉ xem, không xóa)
            tr.querySelector(".btn-view-sc").addEventListener("click", () => {
                const modal   = document.getElementById("global-modal");
                const content = document.getElementById("global-modal-content");
                modal.classList.add("active");
                content.innerHTML = `
                    <section class="staff-customer-detail">
                    <div class="modal-header">
                        <h2>Hồ Sơ Khách Hàng</h2>
                        <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
                    </div>
                    <div style="display:flex;align-items:center;gap:16px;margin-bottom:20px;padding:14px;background:var(--bg-card);border-radius:var(--radius-md);">
                        <div class="user-avatar" style="width:52px;height:52px;font-size:18px;background:${color};">${escapeHtml(initials)}</div>
                        <div>
                            <div style="font-size:17px;font-weight:700;">${escapeHtml(c.fullName || '—')}</div>
                            <div style="font-size:12px;color:var(--text-muted);">Khách hàng #${escapeHtml(c.id)}</div>
                        </div>
                    </div>
                    <div class="grid grid-cols-2" style="gap:10px;margin-bottom:20px;">
                        ${[
                            ['CCCD / Hộ chiếu', c.identityCard],
                            ['Điện thoại',       c.phone],
                            ['Email',            c.email],
                            ['Địa chỉ',          c.address],
                        ].map(([label, val]) => `
                        <div style="background:var(--bg-card);border-radius:var(--radius-sm);padding:12px;">
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;margin-bottom:3px;">${label}</div>
                            <div style="font-weight:600;">${escapeHtml(val || '—')}</div>
                        </div>`).join('')}
                    </div>
                    <div class="flex-row" style="justify-content:flex-end;">
                        <button class="btn btn-secondary" onclick="closeModal('global-modal')">Đóng</button>
                    </div>
                    </section>
                `;
                lucide.createIcons();
            });
            body.appendChild(tr);
        });
        lucide.createIcons();

        if (window.renderPaginationComponent) {
            window.renderPaginationComponent(
                "pagination-staff-customers",
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

    const loadCustomers = async () => {
        document.getElementById("staff-customers-body").innerHTML = skeletonRows(5, 7);
        try {
            allCustomers = (await api.get("/customers") || []).map(normalizeStaffCustomer);
            const summaryBar = document.getElementById("staff-cust-summary");
            summaryBar.innerHTML = [
                ['users', 'Tổng khách', allCustomers.length, '#60a5fa'],
                ['mail', 'Có email', allCustomers.filter(c => c.email).length, '#34d399'],
                ['phone', 'Có SĐT', allCustomers.filter(c => c.phone).length, 'var(--portal-gold)']
            ].map(([icon, label, count, color]) => `
                <div class="staff-customer-stat" style="--stat-color:${color}">
                    <span class="staff-customer-stat-icon" aria-hidden="true"><i data-lucide="${icon}"></i></span>
                    <div class="staff-customer-stat-info"><strong class="staff-customer-stat-value">${count}</strong><span class="staff-customer-stat-label">${label}</span></div>
                </div>`).join('');
            lucide.createIcons();
            currentPage = 1;
            renderTable(allCustomers);
        } catch (e) {
            document.getElementById("staff-customers-body").innerHTML =
                `<tr><td colspan="7" style="color:var(--danger);padding:20px;">${escapeHtml(e.message)}</td></tr>`;
        }
    };

    document.getElementById("staff-cust-search").addEventListener("input", e => {
        const q = e.target.value.toLowerCase().trim();
        currentPage = 1;
        if (!q) { renderTable(allCustomers); return; }
        renderTable(allCustomers.filter(c =>
            (c.fullName     || '').toLowerCase().includes(q) ||
            (c.identityCard || '').toLowerCase().includes(q) ||
            (c.phone        || '').toLowerCase().includes(q) ||
            (c.email        || '').toLowerCase().includes(q)
        ));
    });

    document.getElementById("btn-refresh-cust").addEventListener("click", () => {
        document.getElementById("staff-cust-search").value = "";
        loadCustomers();
    });

    document.getElementById("btn-add-customer").addEventListener("click", () => {
        const modal   = document.getElementById("global-modal");
        const content = document.getElementById("global-modal-content");
        modal.classList.add("active");
        content.innerHTML = `
            <div class="modal-header">
                <h2>Thêm Khách Hàng</h2>
                <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
            </div>
            <form id="form-add-cust-staff">
                <div class="form-group">
                    <label class="form-label">Họ và tên *</label>
                    <input class="form-input" type="text" id="sc-name" required placeholder="Nguyễn Văn A">
                </div>
                <div class="form-group">
                    <label class="form-label">Số CCCD / Hộ chiếu *</label>
                    <input class="form-input" type="text" id="sc-idcard" required placeholder="012345678901">
                </div>
                <div class="grid grid-cols-2" style="gap:16px;">
                    <div class="form-group">
                        <label class="form-label">Điện thoại</label>
                        <input class="form-input" type="text" id="sc-phone" placeholder="0912 345 678">
                    </div>
                    <div class="form-group">
                        <label class="form-label">Email</label>
                        <input class="form-input" type="email" id="sc-email" placeholder="email@example.com">
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label">Địa chỉ</label>
                    <input class="form-input" type="text" id="sc-address" placeholder="Số nhà, đường, thành phố...">
                </div>
                <div id="sc-error" style="color:var(--danger);font-size:13px;margin-bottom:8px;"></div>
                <div class="flex-row" style="justify-content:flex-end;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy</button>
                    <button type="submit" class="btn btn-primary">
                        <i data-lucide="user-plus" style="width:13px;height:13px;"></i> Thêm mới
                    </button>
                </div>
            </form>
        `;
        lucide.createIcons();
        document.getElementById("form-add-cust-staff").addEventListener("submit", async e => {
            e.preventDefault();
            const errDiv = document.getElementById("sc-error");
            try {
                await api.post("/customers", {
                    fullName:     document.getElementById("sc-name").value.trim(),
                    identityCard: document.getElementById("sc-idcard").value.trim(),
                    phone:        document.getElementById("sc-phone").value.trim(),
                    email:        document.getElementById("sc-email").value.trim(),
                    address:      document.getElementById("sc-address").value.trim(),
                });
                closeModal("global-modal");
                loadCustomers();
            } catch (err) {
                errDiv.textContent = err.message;
            }
        });
    });
    await loadCustomers();
}
