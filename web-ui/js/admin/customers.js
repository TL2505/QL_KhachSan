import { api } from "../../api.js";

function avatarColor(str) {
    const colors = ['#d97706','#10b981','#3b82f6','#8b5cf6','#06b6d4','#ef4444','#f59e0b'];
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

function skeletonRows(count = 5) {
    return Array.from({ length: count }, () => `
        <tr class="skeleton-row">
            <td><div class="skeleton-block" style="width:36px;height:36px;border-radius:50%;"></div></td>
            <td><div class="skeleton-block" style="width:140px;margin-bottom:6px;"></div><div class="skeleton-block" style="width:90px;height:11px;"></div></td>
            <td><div class="skeleton-block" style="width:100px;"></div></td>
            <td><div class="skeleton-block" style="width:90px;"></div></td>
            <td><div class="skeleton-block" style="width:130px;"></div></td>
            <td><div class="skeleton-block" style="width:120px;"></div></td>
            <td><div class="skeleton-block" style="width:60px;border-radius:30px;"></div></td>
        </tr>
    `).join('');
}

export async function renderAdminCustomers(container, session) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Quản Lý Khách Hàng</h1>
                <p>Toàn bộ hồ sơ khách hàng đã đăng ký và lưu trú tại Aurelia.</p>
            </div>
            <button id="btn-add-customer-admin" class="btn btn-primary">
                <i data-lucide="user-plus" style="width:16px;height:16px;"></i> Thêm khách hàng
            </button>
        </div>

        <!-- Summary bar -->
        <div id="admin-customers-summary" style="display:flex;gap:16px;margin-bottom:24px;"></div>

        <!-- Search bar -->
        <div class="card" style="padding:16px 20px;margin-bottom:0;">
            <div style="display:flex;align-items:center;gap:12px;">
                <div style="position:relative;flex:1;">
                    <i data-lucide="search" style="width:16px;height:16px;position:absolute;left:12px;top:50%;transform:translateY(-50%);color:var(--text-muted);pointer-events:none;"></i>
                    <input id="admin-customers-search" class="form-input" type="text"
                        placeholder="Tìm theo tên, CCCD, số điện thoại, email..."
                        style="padding-left:38px;margin:0;">
                </div>
                <button id="btn-refresh-customers" class="btn btn-secondary" style="padding:10px 16px;flex-shrink:0;">
                    <i data-lucide="refresh-cw" style="width:15px;height:15px;"></i>
                </button>
            </div>
        </div>

        <div class="card" style="margin-top:0;border-top:none;border-top-left-radius:0;border-top-right-radius:0;">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th style="width:52px;">Avatar</th>
                            <th>Họ và tên</th>
                            <th>Số CCCD/Hộ chiếu</th>
                            <th>Điện thoại</th>
                            <th>Email</th>
                            <th>Địa chỉ</th>
                            <th style="text-align:right;">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="admin-customers-body">
                        ${skeletonRows(5)}
                    </tbody>
                </table>
            </div>
            <div id="pagination-admin-customers"></div>
        </div>
    `;
    lucide.createIcons();

    let allCustomers = [];
    let currentDisplayList = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const renderTable = (list = currentDisplayList) => {
        currentDisplayList = list;
        const body = document.getElementById("admin-customers-body");
        body.innerHTML = "";
        if (!list || list.length === 0) {
            body.innerHTML = `
                <tr><td colspan="7">
                    <div class="empty-state">
                        <div class="empty-state-icon"><i data-lucide="users" style="width:28px;height:28px;"></i></div>
                        <p>Không tìm thấy khách hàng nào</p>
                        <small>Thử thay đổi từ khóa tìm kiếm hoặc thêm khách hàng mới.</small>
                    </div>
                </td></tr>`;
            lucide.createIcons();
            const paginationContainer = document.getElementById("pagination-admin-customers");
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
            tr.style.animationDelay = `${i * 0.035}s`;
            tr.innerHTML = `
                <td>
                    <div class="user-avatar" style="background:${color};">${initials}</div>
                </td>
                <td>
                    <div style="font-weight:600;font-size:14px;">${c.fullName || '—'}</div>
                    <div style="font-size:12px;color:var(--text-muted);">ID: ${c.id}</div>
                </td>
                <td style="font-size:13px;font-family:monospace;color:var(--text-muted);">${c.identityCard || '—'}</td>
                <td style="font-size:13px;">
                    ${c.phone ? `<a href="tel:${c.phone}" style="color:var(--text-main);text-decoration:none;">${c.phone}</a>` : '<span style="color:var(--text-muted);">—</span>'}
                </td>
                <td style="font-size:13px;">
                    ${c.email ? `<a href="mailto:${c.email}" style="color:var(--primary);text-decoration:none;">${c.email}</a>` : '<span style="color:var(--text-muted);">—</span>'}
                </td>
                <td style="font-size:13px;color:var(--text-muted);max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${c.address || ''}">${c.address || '—'}</td>
                <td style="text-align:right;">
                    <div class="action-btns" style="justify-content:flex-end;">
                        <button class="btn-icon btn-view-cust" data-id="${c.id}" title="Xem chi tiết">
                            <i data-lucide="eye" style="width:14px;height:14px;"></i>
                        </button>
                        <button class="btn-icon danger btn-delete-cust" data-id="${c.id}" data-name="${c.fullName}" title="Xóa khách hàng">
                            <i data-lucide="trash-2" style="width:14px;height:14px;"></i>
                        </button>
                    </div>
                </td>
            `;

            // View detail
            tr.querySelector(".btn-view-cust").addEventListener("click", () => {
                const modal   = document.getElementById("global-modal");
                const content = document.getElementById("global-modal-content");
                modal.classList.add("active");
                content.innerHTML = `
                    <div class="modal-header">
                        <h2>Hồ Sơ Khách Hàng</h2>
                        <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
                    </div>
                    <div style="display:flex;align-items:center;gap:16px;margin-bottom:24px;padding:16px;background:var(--bg-card);border-radius:var(--radius-md);">
                        <div class="user-avatar" style="width:56px;height:56px;font-size:20px;background:${color};">${initials}</div>
                        <div>
                            <div style="font-size:18px;font-weight:700;">${c.fullName || '—'}</div>
                            <div style="font-size:13px;color:var(--text-muted);">ID Khách hàng: #${c.id}</div>
                        </div>
                    </div>
                    <div class="grid grid-cols-2" style="gap:12px;">
                        <div style="background:var(--bg-card);border-radius:var(--radius-sm);padding:14px;">
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;margin-bottom:4px;">CCCD / Hộ chiếu</div>
                            <div style="font-weight:600;font-family:monospace;">${c.identityCard || '—'}</div>
                        </div>
                        <div style="background:var(--bg-card);border-radius:var(--radius-sm);padding:14px;">
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;margin-bottom:4px;">Điện thoại</div>
                            <div style="font-weight:600;">${c.phone || '—'}</div>
                        </div>
                        <div style="background:var(--bg-card);border-radius:var(--radius-sm);padding:14px;">
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;margin-bottom:4px;">Email</div>
                            <div style="font-weight:600;word-break:break-all;">${c.email || '—'}</div>
                        </div>
                        <div style="background:var(--bg-card);border-radius:var(--radius-sm);padding:14px;">
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;margin-bottom:4px;">Địa chỉ</div>
                            <div style="font-weight:600;">${c.address || '—'}</div>
                        </div>
                    </div>
                    <div class="flex-row" style="justify-content:flex-end;margin-top:20px;">
                        <button class="btn btn-secondary" onclick="closeModal('global-modal')">Đóng</button>
                    </div>
                `;
                lucide.createIcons();
            });

            // Delete
            tr.querySelector(".btn-delete-cust").addEventListener("click", () => {
                const modal   = document.getElementById("global-modal");
                const content = document.getElementById("global-modal-content");
                modal.classList.add("active");
                content.innerHTML = `
                    <div class="modal-header">
                        <h2 style="color:var(--danger);">Xác nhận xóa khách hàng</h2>
                        <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
                    </div>
                    <div style="text-align:center;padding:12px 0 24px;">
                        <div style="width:56px;height:56px;border-radius:50%;background:rgba(239,68,68,0.15);display:flex;align-items:center;justify-content:center;margin:0 auto 16px;">
                            <i data-lucide="user-x" style="width:24px;height:24px;color:var(--danger);"></i>
                        </div>
                        <p style="font-size:15px;font-weight:600;margin-bottom:8px;">Xóa khách hàng <strong>${c.fullName}</strong>?</p>
                        <p style="font-size:13px;color:var(--text-muted);">Toàn bộ dữ liệu liên quan sẽ bị xóa vĩnh viễn.</p>
                    </div>
                    <div class="flex-row" style="justify-content:flex-end;">
                        <button class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy bỏ</button>
                        <button class="btn btn-danger" id="confirm-delete-cust">Xóa khách hàng</button>
                    </div>
                `;
                lucide.createIcons();
                document.getElementById("confirm-delete-cust").addEventListener("click", async () => {
                    try {
                        await api.delete(`/customers/${c.id}`);
                        closeModal("global-modal");
                        loadCustomers();
                    } catch (err) {
                        alert("Lỗi: " + err.message);
                    }
                });
            });

            body.appendChild(tr);
        });
        lucide.createIcons();

        if (window.renderPaginationComponent) {
            window.renderPaginationComponent(
                "pagination-admin-customers",
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
        const body = document.getElementById("admin-customers-body");
        body.innerHTML = skeletonRows(5);
        try {
            allCustomers = await api.get("/customers") || [];

            // Summary bar
            const summaryBar = document.getElementById("admin-customers-summary");
            summaryBar.innerHTML = `
                <div class="card" style="margin:0;flex:1;padding:16px 20px;display:flex;align-items:center;gap:12px;border-left:3px solid var(--info);">
                    <i data-lucide="users" style="width:20px;height:20px;color:var(--info);"></i>
                    <div>
                        <div style="font-size:22px;font-weight:700;font-family:var(--font-heading);">${allCustomers.length}</div>
                        <div style="font-size:12px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tổng khách hàng</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:16px 20px;display:flex;align-items:center;gap:12px;border-left:3px solid var(--success);">
                    <i data-lucide="user-check" style="width:20px;height:20px;color:var(--success);"></i>
                    <div>
                        <div style="font-size:22px;font-weight:700;font-family:var(--font-heading);">${allCustomers.filter(c => c.email).length}</div>
                        <div style="font-size:12px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Có email</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:16px 20px;display:flex;align-items:center;gap:12px;border-left:3px solid var(--primary);">
                    <i data-lucide="phone" style="width:20px;height:20px;color:var(--primary);"></i>
                    <div>
                        <div style="font-size:22px;font-weight:700;font-family:var(--font-heading);">${allCustomers.filter(c => c.phone).length}</div>
                        <div style="font-size:12px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Có SĐT</div>
                    </div>
                </div>
            `;
            lucide.createIcons();

            currentPage = 1;
            renderTable(allCustomers);
        } catch (e) {
            body.innerHTML = `<tr><td colspan="7" style="color:var(--danger);padding:20px;">${e.message}</td></tr>`;
        }
    };
    loadCustomers();

    // Live search filter
    document.getElementById("admin-customers-search").addEventListener("input", (e) => {
        const q = e.target.value.toLowerCase().trim();
        if (!q) { renderTable(allCustomers); return; }
        const filtered = allCustomers.filter(c =>
            (c.fullName     || '').toLowerCase().includes(q) ||
            (c.identityCard || '').toLowerCase().includes(q) ||
            (c.phone        || '').toLowerCase().includes(q) ||
            (c.email        || '').toLowerCase().includes(q)
        );
        currentPage = 1;
        renderTable(filtered);
    });

    // Refresh button
    document.getElementById("btn-refresh-customers").addEventListener("click", () => {
        document.getElementById("admin-customers-search").value = "";
        loadCustomers();
    });

    // Add customer button
    document.getElementById("btn-add-customer-admin").addEventListener("click", () => {
        const modal   = document.getElementById("global-modal");
        const content = document.getElementById("global-modal-content");
        modal.classList.add("active");

        content.innerHTML = `
            <div class="modal-header">
                <h2>Thêm Khách Hàng Mới</h2>
                <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
            </div>
            <form id="form-add-cust-admin">
                <div class="form-group">
                    <label class="form-label">Họ và tên *</label>
                    <input class="form-input" type="text" id="ac-name" required placeholder="Nguyễn Văn A">
                </div>
                <div class="form-group">
                    <label class="form-label">Số CCCD / Hộ chiếu *</label>
                    <input class="form-input" type="text" id="ac-idcard" required placeholder="012345678901">
                </div>
                <div class="grid grid-cols-2" style="gap:16px;">
                    <div class="form-group">
                        <label class="form-label">Số điện thoại</label>
                        <input class="form-input" type="text" id="ac-phone" placeholder="0912 345 678">
                    </div>
                    <div class="form-group">
                        <label class="form-label">Email</label>
                        <input class="form-input" type="email" id="ac-email" placeholder="email@example.com">
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label">Địa chỉ</label>
                    <input class="form-input" type="text" id="ac-address" placeholder="Số nhà, đường, thành phố...">
                </div>
                <div id="ac-error" style="color:var(--danger);font-size:13px;margin-bottom:12px;"></div>
                <div class="flex-row" style="justify-content:flex-end;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy</button>
                    <button type="submit" class="btn btn-primary">
                        <i data-lucide="user-plus" style="width:14px;height:14px;"></i> Thêm khách hàng
                    </button>
                </div>
            </form>
        `;
        lucide.createIcons();

        document.getElementById("form-add-cust-admin").addEventListener("submit", async (e) => {
            e.preventDefault();
            const errDiv = document.getElementById("ac-error");
            try {
                await api.post("/customers", {
                    fullName:     document.getElementById("ac-name").value.trim(),
                    identityCard: document.getElementById("ac-idcard").value.trim(),
                    phone:        document.getElementById("ac-phone").value.trim(),
                    email:        document.getElementById("ac-email").value.trim(),
                    address:      document.getElementById("ac-address").value.trim(),
                });
                closeModal("global-modal");
                loadCustomers();
            } catch (err) {
                errDiv.textContent = err.message;
            }
        });
    });
}
