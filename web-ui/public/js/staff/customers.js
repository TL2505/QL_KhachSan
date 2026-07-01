import { api } from "../../api.js";

function avatarColor(str) {
    const colors = ['#d97706','#10b981','#3b82f6','#8b5cf6','#06b6d4','#ef4444'];
    let hash = 0;
    for (let c of (str || 'K')) hash = c.charCodeAt(0) + ((hash << 5) - hash);
    return colors[Math.abs(hash) % colors.length];
}
function getInitials(name) {
    if (!name) return 'KH';
    const parts = name.trim().split(' ');
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
        <div id="staff-cust-summary" style="display:flex;gap:16px;margin-bottom:24px;"></div>

        <!-- Search bar -->
        <div class="card" style="padding:14px 20px;margin-bottom:0;">
            <div style="display:flex;align-items:center;gap:10px;">
                <div style="position:relative;flex:1;">
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
                <td><div class="user-avatar" style="background:${color};">${initials}</div></td>
                <td>
                    <div style="font-weight:600;">${c.fullName || '—'}</div>
                    <div style="font-size:11px;color:var(--text-muted);">ID: ${c.id}</div>
                </td>
                <td style="font-size:13px;font-family:monospace;">${c.identityCard || '—'}</td>
                <td style="font-size:13px;">${c.phone
                    ? `<a href="tel:${c.phone}" style="color:var(--text-main);text-decoration:none;">${c.phone}</a>`
                    : '<span style="color:var(--text-muted);">—</span>'}</td>
                <td style="font-size:13px;">${c.email
                    ? `<a href="mailto:${c.email}" style="color:var(--primary);text-decoration:none;">${c.email}</a>`
                    : '<span style="color:var(--text-muted);">—</span>'}</td>
                <td style="font-size:12px;color:var(--text-muted);max-width:150px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" title="${c.address||''}">${c.address || '—'}</td>
                <td style="text-align:right;">
                    <div class="action-btns" style="justify-content:flex-end;">
                        <button class="btn-icon btn-view-sc" data-id="${c.id}" title="Xem chi tiết">
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
                    <div class="modal-header">
                        <h2>Hồ Sơ Khách Hàng</h2>
                        <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
                    </div>
                    <div style="display:flex;align-items:center;gap:16px;margin-bottom:20px;padding:14px;background:var(--bg-card);border-radius:var(--radius-md);">
                        <div class="user-avatar" style="width:52px;height:52px;font-size:18px;background:${color};">${initials}</div>
                        <div>
                            <div style="font-size:17px;font-weight:700;">${c.fullName || '—'}</div>
                            <div style="font-size:12px;color:var(--text-muted);">Khách hàng #${c.id}</div>
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
                            <div style="font-weight:600;">${val || '—'}</div>
                        </div>`).join('')}
                    </div>
                    <div class="flex-row" style="justify-content:flex-end;">
                        <button class="btn btn-secondary" onclick="closeModal('global-modal')">Đóng</button>
                    </div>
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
            allCustomers = await api.get("/customers") || [];
            const summaryBar = document.getElementById("staff-cust-summary");
            summaryBar.innerHTML = `
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--info);">
                    <i data-lucide="users" style="width:18px;height:18px;color:var(--info);"></i>
                    <div>
                        <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${allCustomers.length}</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tổng khách</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--success);">
                    <i data-lucide="mail" style="width:18px;height:18px;color:var(--success);"></i>
                    <div>
                        <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${allCustomers.filter(c => c.email).length}</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Có email</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--primary);">
                    <i data-lucide="phone" style="width:18px;height:18px;color:var(--primary);"></i>
                    <div>
                        <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${allCustomers.filter(c => c.phone).length}</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Có SĐT</div>
                    </div>
                </div>
            `;
            lucide.createIcons();
            currentPage = 1;
            renderTable(allCustomers);
        } catch (e) {
            document.getElementById("staff-customers-body").innerHTML =
                `<tr><td colspan="7" style="color:var(--danger);padding:20px;">${e.message}</td></tr>`;
        }
    };
    loadCustomers();

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
}
