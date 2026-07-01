import { api } from "../../api.js";

// Generate a consistent avatar color from a string
function avatarColor(str) {
    const colors = ['#d97706','#10b981','#3b82f6','#8b5cf6','#06b6d4','#ef4444','#f59e0b'];
    let hash = 0;
    for (let c of (str || 'U')) hash = c.charCodeAt(0) + ((hash << 5) - hash);
    return colors[Math.abs(hash) % colors.length];
}

function getInitials(name) {
    if (!name) return '?';
    const parts = name.trim().split(' ');
    return parts.length >= 2
        ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
        : name[0].toUpperCase();
}

function getRoleBadge(roleName) {
    const role = (roleName || 'STAFF').toUpperCase();
    if (role === 'ADMIN') return `<span class="badge-role-admin">⚡ Admin</span>`;
    return `<span class="badge-role-staff">👤 Staff</span>`;
}

function skeletonRows(count = 5) {
    return Array.from({ length: count }, () => `
        <tr class="skeleton-row">
            <td><div class="skeleton-block" style="width:30px;"></div></td>
            <td><div style="display:flex;align-items:center;gap:10px;">
                <div class="skeleton-block" style="width:36px;height:36px;border-radius:50%;flex-shrink:0;"></div>
                <div style="flex:1;"><div class="skeleton-block" style="width:120px;margin-bottom:6px;"></div><div class="skeleton-block" style="width:80px;height:12px;"></div></div>
            </div></td>
            <td><div class="skeleton-block" style="width:100px;"></div></td>
            <td><div class="skeleton-block" style="width:60px;border-radius:30px;"></div></td>
            <td><div class="skeleton-block" style="width:70px;"></div></td>
        </tr>
    `).join('');
}

export async function renderUsers(container, session) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Quản Lý Nhân Viên & Người Dùng</h1>
                <p>Danh sách tài khoản quản lý và nhân viên vận hành khách sạn.</p>
            </div>
            <button id="btn-add-staff" class="btn btn-primary">
                <i data-lucide="user-plus" style="width: 16px; height: 16px;"></i> Thêm tài khoản
            </button>
        </div>

        <!-- Summary bar -->
        <div id="users-summary-bar" style="display:flex;gap:16px;margin-bottom:24px;"></div>

        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th style="width:50px;">#</th>
                            <th>Nhân viên</th>
                            <th>Email</th>
                            <th>Quyền hạn</th>
                            <th style="text-align:right;">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="admin-users-body">
                        ${skeletonRows(4)}
                    </tbody>
                </table>
            </div>
            <div id="pagination-admin-users"></div>
        </div>
    `;
    lucide.createIcons();

    const loadUsers = async () => {
        const body = document.getElementById("admin-users-body");
        try {
            const rawList = await api.get("/users");
            // Chỉ hiển thị tài khoản ADMIN và STAFF — không hiển thị CUSTOMER
            const list = (rawList || []).filter(u => {
                const role = (u.roleName || '').toUpperCase();
                return role === 'ADMIN' || role === 'STAFF';
            });
            body.innerHTML = "";

            // Summary bar
            const admins = list.filter(u => (u.roleName || '').toUpperCase() === 'ADMIN').length;
            const staff  = list.length - admins;
            const summaryBar = document.getElementById("users-summary-bar");
            summaryBar.innerHTML = `
                <div class="card" style="margin:0;flex:1;padding:16px 20px;display:flex;align-items:center;gap:12px;border-left:3px solid var(--primary);">
                    <i data-lucide="users" style="width:20px;height:20px;color:var(--primary);"></i>
                    <div>
                        <div style="font-size:22px;font-weight:700;font-family:var(--font-heading);">${list.length}</div>
                        <div style="font-size:12px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tổng tài khoản</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:16px 20px;display:flex;align-items:center;gap:12px;border-left:3px solid var(--warning);">
                    <i data-lucide="shield" style="width:20px;height:20px;color:var(--warning);"></i>
                    <div>
                        <div style="font-size:22px;font-weight:700;font-family:var(--font-heading);">${admins}</div>
                        <div style="font-size:12px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Admin</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:16px 20px;display:flex;align-items:center;gap:12px;border-left:3px solid var(--info);">
                    <i data-lucide="user" style="width:20px;height:20px;color:var(--info);"></i>
                    <div>
                        <div style="font-size:22px;font-weight:700;font-family:var(--font-heading);">${staff}</div>
                        <div style="font-size:12px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Nhân viên</div>
                    </div>
                </div>
            `;
            lucide.createIcons();

            allUsers = list;
            currentPage = 1;
            renderTable();
        } catch (e) {
            const body = document.getElementById("admin-users-body");
            body.innerHTML = `<tr><td colspan="5" style="color:var(--danger);padding:20px;">${e.message}</td></tr>`;
        }
    };

    let allUsers = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const renderTable = () => {
        const body = document.getElementById("admin-users-body");
        if (!allUsers || allUsers.length === 0) {
            body.innerHTML = `
                <tr><td colspan="5">
                    <div class="empty-state">
                        <div class="empty-state-icon"><i data-lucide="users" style="width:28px;height:28px;"></i></div>
                        <p>Chưa có tài khoản nào</p>
                        <small>Nhấn "Thêm tài khoản" để tạo người dùng đầu tiên.</small>
                    </div>
                </td></tr>`;
            lucide.createIcons();
            
            const paginationContainer = document.getElementById("pagination-admin-users");
            if (paginationContainer) paginationContainer.innerHTML = '';
            return;
        }

        body.innerHTML = "";
        const start = (currentPage - 1) * itemsPerPage;
        const end = start + itemsPerPage;
        const paginatedList = allUsers.slice(start, end);

        paginatedList.forEach((u, i) => {
                const initials = getInitials(u.fullName || u.username);
                const color    = avatarColor(u.username);
                const tr = document.createElement("tr");
                tr.className = "table-row-animate";
                tr.style.animationDelay = `${i * 0.04}s`;
                tr.innerHTML = `
                    <td style="color:var(--text-muted);font-size:13px;">${u.id}</td>
                    <td>
                        <div class="user-avatar-wrap">
                            <div class="user-avatar" style="background:${color};">${initials}</div>
                            <div>
                                <div class="user-avatar-name">${u.fullName || '—'}</div>
                                <div class="user-avatar-username">@${u.username}</div>
                            </div>
                        </div>
                    </td>
                    <td style="color:var(--text-muted);font-size:13px;">${u.email || '—'}</td>
                    <td>${getRoleBadge(u.roleName)}</td>
                    <td style="text-align:right;">
                        <div class="action-btns" style="justify-content:flex-end;">
                            <button class="btn-icon danger btn-delete-user" data-id="${u.id}" title="Xóa tài khoản">
                                <i data-lucide="trash-2" style="width:14px;height:14px;"></i>
                            </button>
                        </div>
                    </td>
                `;

                tr.querySelector(".btn-delete-user").addEventListener("click", async () => {
                    // Custom confirm modal
                    const modal = document.getElementById("global-modal");
                    const content = document.getElementById("global-modal-content");
                    modal.classList.add("active");
                    content.innerHTML = `
                        <div class="modal-header">
                            <h2 style="color:var(--danger);">Xác nhận xóa tài khoản</h2>
                            <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
                        </div>
                        <div style="text-align:center;padding:12px 0 24px;">
                            <div style="width:56px;height:56px;border-radius:50%;background:rgba(239,68,68,0.15);display:flex;align-items:center;justify-content:center;margin:0 auto 16px;">
                                <i data-lucide="trash-2" style="width:24px;height:24px;color:var(--danger);"></i>
                            </div>
                            <p style="font-size:15px;font-weight:600;margin-bottom:8px;">Xóa tài khoản <strong>${u.fullName || u.username}</strong>?</p>
                            <p style="font-size:13px;color:var(--text-muted);">Hành động này không thể hoàn tác.</p>
                        </div>
                        <div class="flex-row" style="justify-content:flex-end;">
                            <button class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy bỏ</button>
                            <button class="btn btn-danger" id="confirm-delete-user">Xóa tài khoản</button>
                        </div>
                    `;
                    lucide.createIcons();
                    document.getElementById("confirm-delete-user").addEventListener("click", async () => {
                        try {
                            await api.delete(`/users/${u.id}`);
                            closeModal("global-modal");
                            loadUsers();
                        } catch (err) {
                            alert("Lỗi xóa tài khoản: " + err.message);
                        }
                    });
                });

                body.appendChild(tr);
            });
            lucide.createIcons();

            if (window.renderPaginationComponent) {
                window.renderPaginationComponent(
                    "pagination-admin-users",
                    allUsers.length,
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

    loadUsers();

    document.getElementById("btn-add-staff").addEventListener("click", () => {
        const modal   = document.getElementById("global-modal");
        const content = document.getElementById("global-modal-content");
        modal.classList.add("active");

        content.innerHTML = `
            <div class="modal-header">
                <h2>Thêm Nhân Viên / Người dùng</h2>
                <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
            </div>
            <form id="form-add-staff-action">
                <div class="grid grid-cols-2" style="gap:16px;">
                    <div class="form-group">
                        <label class="form-label">Tên đăng nhập *</label>
                        <input class="form-input" type="text" id="as-username" required placeholder="vd: nguyen_van_a">
                    </div>
                    <div class="form-group">
                        <label class="form-label">Mật khẩu *</label>
                        <input class="form-input" type="password" id="as-password" required placeholder="Tối thiểu 6 ký tự">
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label">Họ và tên *</label>
                    <input class="form-input" type="text" id="as-name" required placeholder="Nguyễn Văn A">
                </div>
                <div class="grid grid-cols-2" style="gap:16px;">
                    <div class="form-group">
                        <label class="form-label">Số điện thoại</label>
                        <input class="form-input" type="text" id="as-phone" placeholder="0912 345 678">
                    </div>
                    <div class="form-group">
                        <label class="form-label">Email</label>
                        <input class="form-input" type="email" id="as-email" placeholder="email@aurelia.vn">
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label">Chức vụ (Phân quyền) *</label>
                    <select class="form-input" id="as-role" required>
                        <option value="1">⚡ ADMIN — Quản trị viên</option>
                        <option value="2" selected>👤 STAFF — Nhân viên lễ tân</option>
                    </select>
                </div>
                <div id="form-add-error" style="color:var(--danger);font-size:13px;margin-bottom:12px;"></div>
                <div class="flex-row" style="justify-content: flex-end;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy</button>
                    <button type="submit" class="btn btn-primary">
                        <i data-lucide="user-plus" style="width:14px;height:14px;"></i> Tạo tài khoản
                    </button>
                </div>
            </form>
        `;
        lucide.createIcons();

        document.getElementById("form-add-staff-action").addEventListener("submit", async (e) => {
            e.preventDefault();
            const errDiv    = document.getElementById("form-add-error");
            const username  = document.getElementById("as-username").value.trim();
            const password  = document.getElementById("as-password").value;
            const fullName  = document.getElementById("as-name").value.trim();
            const phone     = document.getElementById("as-phone").value.trim();
            const email     = document.getElementById("as-email").value.trim();
            const roleId    = document.getElementById("as-role").value;

            try {
                await api.post("/users", { username, password, fullName, phone, email, roleId, status: "active" });
                closeModal("global-modal");
                loadUsers();
            } catch (err) {
                errDiv.innerHTML = `<i data-lucide="alert-circle" style="width:13px;height:13px;vertical-align:middle;"></i> ${err.message}`;
                lucide.createIcons();
            }
        });
    });
}
