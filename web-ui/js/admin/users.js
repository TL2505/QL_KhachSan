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
                <div class="card stat-card">
                    <div class="stat-title">Tổng tài khoản</div>
                    <div class="stat-value">${list.length}</div>
                </div>
                <div class="card stat-card">
                    <div class="stat-title">Admin</div>
                    <div class="stat-value">${admins}</div>
                </div>
                <div class="card stat-card">
                    <div class="stat-title">Nhân viên</div>
                    <div class="stat-value">${staff}</div>
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
                            <button class="btn-icon btn-edit-user" data-id="${u.id}" title="Sửa quyền" aria-label="Sửa quyền của ${u.username}">
                                <i data-lucide="shield-check" style="width:14px;height:14px;"></i>
                            </button>
                            <button class="btn-icon danger btn-delete-user" data-id="${u.id}" title="Xóa tài khoản">
                                <i data-lucide="trash-2" style="width:14px;height:14px;"></i>
                            </button>
                        </div>
                    </td>
                `;

                tr.querySelector(".btn-edit-user").addEventListener("click", () => {
                    const modal = document.getElementById("global-modal");
                    const content = document.getElementById("global-modal-content");
                    content.innerHTML = `
                        <div class="modal-header">
                            <h2>Sửa quyền tài khoản</h2>
                            <button type="button" class="modal-close" id="close-edit-user-role" aria-label="Đóng">&times;</button>
                        </div>
                        <div class="card bg-base-200 mb-4"><div class="card-body p-4">
                            <p id="edit-user-name" class="font-semibold"></p>
                            <p id="edit-user-meta" class="text-sm text-base-content/70"></p>
                        </div></div>
                        <label class="form-control w-full">
                            <span class="label"><span class="label-text font-semibold">Vai trò tài khoản</span></span>
                            <select id="edit-user-role" class="select select-bordered w-full">
                                <option value="2">STAFF — Nhân viên lễ tân</option>
                                <option value="1">ADMIN — Quản trị viên</option>
                            </select>
                        </label>
                        <div id="edit-user-role-error" class="alert alert-error mt-3" role="alert" hidden></div>
                        <div class="modal-action">
                            <button type="button" class="btn btn-ghost" id="cancel-edit-user-role">Hủy</button>
                            <button type="button" class="btn btn-primary" id="save-edit-user-role"><i data-lucide="save" aria-hidden="true"></i> Lưu quyền</button>
                        </div>
                    `;
                    document.getElementById("edit-user-name").textContent = u.fullName || u.username;
                    document.getElementById("edit-user-meta").textContent = `@${u.username} · ${u.email || "Không có email"} · Quyền hiện tại: ${String(u.roleName || "STAFF").toUpperCase()}`;
                    document.getElementById("edit-user-role").value = String(u.roleId);
                    modal.classList.add("active");
                    modal.setAttribute("aria-hidden", "false");
                    lucide.createIcons();
                    const closeEditor = () => {
                        modal.classList.remove("active");
                        modal.setAttribute("aria-hidden", "true");
                        content.replaceChildren();
                    };
                    document.getElementById("close-edit-user-role").addEventListener("click", closeEditor);
                    document.getElementById("cancel-edit-user-role").addEventListener("click", closeEditor);
                    document.getElementById("save-edit-user-role").addEventListener("click", async event => {
                        const button = event.currentTarget;
                        const error = document.getElementById("edit-user-role-error");
                        const roleId = Number(document.getElementById("edit-user-role").value);
                        if (roleId === Number(u.roleId)) { closeEditor(); return; }
                        button.disabled = true;
                        error.hidden = true;
                        try {
                            await api.put(`/users/${encodeURIComponent(u.id)}`, {
                                fullName: u.fullName || u.username,
                                email: u.email || "",
                                phone: u.phone || "",
                                roleId,
                                status: u.status || "active"
                            });
                            closeEditor();
                            await loadUsers();
                            await window.showCustomAlert?.("Quyền tài khoản đã được cập nhật.", { title: "Cập nhật thành công", kind: "success" });
                        } catch (err) {
                            error.textContent = err.message || "Không thể cập nhật quyền tài khoản.";
                            error.hidden = false;
                            button.disabled = false;
                        }
                    });
                });

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
                            window.showCustomAlert("Lỗi xóa tài khoản: " + err.message);
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
        modal.setAttribute("aria-hidden", "false");

        content.innerHTML = `
            <div class="modal-header">
                <h2>Thêm Nhân Viên / Người dùng</h2>
                <button type="button" class="modal-close" id="close-add-user" aria-label="Đóng">&times;</button>
            </div>
            <div role="tablist" class="tabs tabs-box mb-5" aria-label="Cách thêm nhân viên">
                <button type="button" role="tab" class="tab tab-active" id="tab-create-account" aria-selected="true">Tạo tài khoản mới</button>
                <button type="button" role="tab" class="tab" id="tab-existing-account" aria-selected="false">Cấp quyền tài khoản có sẵn</button>
            </div>
            <section id="create-account-panel" role="tabpanel" aria-labelledby="tab-create-account">
            <form id="form-add-staff-action">
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <label class="form-control w-full"><span class="label"><span class="label-text font-semibold">Tên đăng nhập *</span></span><input class="input input-bordered w-full" type="text" id="as-username" required autocomplete="username" placeholder="vd: nguyen_van_a"></label>
                    <label class="form-control w-full"><span class="label"><span class="label-text font-semibold">Mật khẩu *</span></span><input class="input input-bordered w-full" type="password" id="as-password" required minlength="6" autocomplete="new-password" placeholder="Tối thiểu 6 ký tự"></label>
                </div>
                <label class="form-control w-full"><span class="label"><span class="label-text font-semibold">Họ và tên *</span></span><input class="input input-bordered w-full" type="text" id="as-name" required autocomplete="name" placeholder="Nguyễn Văn A"></label>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <label class="form-control w-full"><span class="label"><span class="label-text font-semibold">Số điện thoại</span></span><input class="input input-bordered w-full" type="tel" id="as-phone" autocomplete="tel" placeholder="0912 345 678"></label>
                    <label class="form-control w-full"><span class="label"><span class="label-text font-semibold">Email</span></span><input class="input input-bordered w-full" type="email" id="as-email" autocomplete="email" placeholder="email@aurelia.vn"></label>
                </div>
                <label class="form-control w-full"><span class="label"><span class="label-text font-semibold">Chức vụ (Phân quyền) *</span></span>
                    <select class="select select-bordered w-full" id="as-role" required>
                        <option value="1">⚡ ADMIN — Quản trị viên</option>
                        <option value="2" selected>👤 STAFF — Nhân viên lễ tân</option>
                    </select>
                </label>
                <div id="form-add-error" class="alert alert-error mt-3" role="alert" hidden></div>
                <div class="modal-action">
                    <button type="button" class="btn btn-ghost" data-close-add-user>Hủy</button>
                    <button type="submit" class="btn btn-primary">
                        <i data-lucide="user-plus" aria-hidden="true"></i> Tạo tài khoản
                    </button>
                </div>
            </form>
            </section>
            <section id="existing-account-panel" role="tabpanel" aria-labelledby="tab-existing-account" hidden>
                <p class="text-sm text-base-content/70 mb-4">Tìm tài khoản khách hàng đã đăng ký để cấp quyền nhân viên. Hồ sơ và trạng thái tài khoản sẽ được giữ nguyên.</p>
                <label class="form-control w-full"><span class="label"><span class="label-text font-semibold">Tìm tài khoản</span></span><input class="input input-bordered w-full" type="search" id="existing-account-search" autocomplete="off" placeholder="Tên, tên đăng nhập, email hoặc số điện thoại"></label>
                <div id="existing-account-results" class="mt-3 max-h-56 overflow-y-auto" aria-live="polite"></div>
                <div id="selected-account-panel" class="card bg-base-200 mt-4" hidden><div class="card-body gap-3 p-4"><div><p class="text-xs uppercase tracking-wide text-base-content/60">Tài khoản được chọn</p><h3 id="selected-account-name" class="font-semibold"></h3><p id="selected-account-meta" class="text-sm text-base-content/70"></p></div>
                    <label class="form-control w-full"><span class="label"><span class="label-text font-semibold">Cấp vai trò</span></span><select class="select select-bordered w-full" id="existing-account-role"><option value="2" selected>STAFF — Nhân viên lễ tân</option><option value="1">ADMIN — Quản trị viên</option></select></label>
                </div></div>
                <div id="grant-account-error" class="alert alert-error mt-3" role="alert" hidden></div>
                <div class="modal-action"><button type="button" class="btn btn-ghost" data-close-add-user>Đóng</button><button type="button" class="btn btn-primary" id="grant-existing-role" disabled><i data-lucide="user-round-check" aria-hidden="true"></i> Cấp quyền</button></div>
            </section>
        `;
        lucide.createIcons();

        let eligibleAccounts = null;
        let selectedAccount = null;
        const createPanel = document.getElementById("create-account-panel");
        const existingPanel = document.getElementById("existing-account-panel");
        const createTab = document.getElementById("tab-create-account");
        const existingTab = document.getElementById("tab-existing-account");
        const closeAddUser = () => {
            modal.classList.remove("active");
            modal.setAttribute("aria-hidden", "true");
            content.replaceChildren();
        };
        const showPanel = existing => {
            createPanel.hidden = existing;
            existingPanel.hidden = !existing;
            createTab.classList.toggle("tab-active", !existing);
            existingTab.classList.toggle("tab-active", existing);
            createTab.setAttribute("aria-selected", String(!existing));
            existingTab.setAttribute("aria-selected", String(existing));
        };
        const renderAccountMatches = query => {
            const results = document.getElementById("existing-account-results");
            if (!eligibleAccounts) return;
            const normalizedQuery = query.trim().toLocaleLowerCase("vi");
            const matches = eligibleAccounts.filter(user => [user.username, user.fullName, user.email, user.phone]
                .some(value => String(value || "").toLocaleLowerCase("vi").includes(normalizedQuery)))
                .slice(0, 8);
            results.replaceChildren();
            if (!matches.length) {
                const empty = document.createElement("div");
                empty.className = "alert alert-info";
                empty.textContent = normalizedQuery ? "Không tìm thấy tài khoản khách hàng phù hợp." : "Chưa có tài khoản khách hàng phù hợp để cấp quyền.";
                results.append(empty);
                return;
            }
            const list = document.createElement("ul");
            list.className = "menu rounded-box bg-base-200 w-full p-2";
            matches.forEach(user => {
                const item = document.createElement("li");
                const choose = document.createElement("button");
                choose.type = "button";
                choose.className = "flex-col items-start gap-0";
                const name = document.createElement("strong");
                name.textContent = user.fullName || user.username;
                const detail = document.createElement("span");
                detail.className = "text-xs text-base-content/60";
                detail.textContent = `@${user.username}${user.email ? ` · ${user.email}` : ""}`;
                choose.append(name, detail);
                choose.addEventListener("click", () => {
                    selectedAccount = user;
                    document.getElementById("selected-account-panel").hidden = false;
                    document.getElementById("selected-account-name").textContent = user.fullName || user.username;
                    document.getElementById("selected-account-meta").textContent = `@${user.username} · ${user.email || user.phone || "Không có email hoặc số điện thoại"}`;
                    document.getElementById("grant-existing-role").disabled = false;
                    document.getElementById("grant-account-error").hidden = true;
                });
                item.append(choose);
                list.append(item);
            });
            results.append(list);
        };
        document.getElementById("close-add-user").addEventListener("click", closeAddUser);
        content.querySelectorAll("[data-close-add-user]").forEach(button => button.addEventListener("click", closeAddUser));
        createTab.addEventListener("click", () => showPanel(false));
        existingTab.addEventListener("click", async () => {
            showPanel(true);
            document.getElementById("existing-account-search").focus();
            if (eligibleAccounts) return;
            const results = document.getElementById("existing-account-results");
            results.textContent = "Đang tải danh sách tài khoản…";
            try {
                const users = await api.get("/users");
                eligibleAccounts = (users || []).filter(user => String(user.roleName || "").toUpperCase() === "CUSTOMER");
                renderAccountMatches(document.getElementById("existing-account-search").value);
            } catch (error) {
                results.textContent = error.message || "Không tải được danh sách tài khoản.";
                eligibleAccounts = null;
            }
        });
        document.getElementById("existing-account-search").addEventListener("input", event => {
            selectedAccount = null;
            document.getElementById("selected-account-panel").hidden = true;
            document.getElementById("grant-existing-role").disabled = true;
            renderAccountMatches(event.target.value);
        });

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
                closeAddUser();
                loadUsers();
            } catch (err) {
                errDiv.textContent = err.message || "Không thể tạo tài khoản.";
                errDiv.hidden = false;
            }
        });

        document.getElementById("grant-existing-role").addEventListener("click", async event => {
            if (!selectedAccount) return;
            const button = event.currentTarget;
            const errorBox = document.getElementById("grant-account-error");
            button.disabled = true;
            errorBox.hidden = true;
            try {
                await api.put(`/users/${encodeURIComponent(selectedAccount.id)}`, {
                    fullName: selectedAccount.fullName || selectedAccount.username,
                    email: selectedAccount.email || "",
                    phone: selectedAccount.phone || "",
                    roleId: Number(document.getElementById("existing-account-role").value),
                    status: selectedAccount.status || "active"
                });
                closeAddUser();
                await loadUsers();
                await window.showCustomAlert?.("Đã cấp quyền cho tài khoản hiện có.", { title: "Cấp quyền thành công", kind: "success" });
            } catch (error) {
                errorBox.textContent = error.message || "Không thể cấp quyền cho tài khoản này.";
                errorBox.hidden = false;
                button.disabled = false;
            }
        });
    });
}
