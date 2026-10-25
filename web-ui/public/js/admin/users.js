import { api } from "../../api.js";

const ROLE_LABELS = {
    admin: "Quản trị viên",
    staff: "Nhân viên",
    front_desk: "Lễ tân",
    customer: "Khách hàng",
    housekeeping: "Buồng phòng",
    maintenance: "Bảo trì",
    "f&b": "Ẩm thực & nhà hàng",
    manager: "Quản lý",
    gm: "Tổng quản lý",
    revenue_manager: "Quản lý doanh thu"
};

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, char => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[char]);
}

function normalizedRole(user) {
    return String(user?.roleName || "unknown").trim().toLowerCase();
}

function roleLabel(role) {
    const name = String(role?.name || role || "unknown").trim().toLowerCase();
    return ROLE_LABELS[name] || String(role?.name || role || "Chưa xác định");
}

function roleClass(role) {
    return String(role || "unknown").toLowerCase().replace(/[^a-z0-9_-]/g, "-");
}

function roleBadge(user) {
    const role = normalizedRole(user);
    return `<span class="badge badge-outline user-role-badge user-role-badge--${roleClass(role)}">${escapeHtml(roleLabel(role))}</span>`;
}

function statusBadge(status) {
    const value = String(status || "active").trim().toLowerCase();
    if (["inactive", "disabled", "locked", "suspended", "deleted"].includes(value)) {
        return `<span class="badge badge-error badge-soft">${escapeHtml(value === "inactive" ? "Ngưng hoạt động" : "Đã khóa")}</span>`;
    }
    return `<span class="badge badge-success badge-soft">Đang hoạt động</span>`;
}

function avatarColor(str) {
    const colors = ["#d97706", "#10b981", "#3b82f6", "#8b5cf6", "#06b6d4", "#ef4444", "#f59e0b"];
    let hash = 0;
    for (const char of (str || "U")) hash = char.charCodeAt(0) + ((hash << 5) - hash);
    return colors[Math.abs(hash) % colors.length];
}

function getInitials(name) {
    const parts = String(name || "?").trim().split(/\s+/).filter(Boolean);
    return parts.length > 1
        ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
        : (parts[0]?.[0] || "?").toUpperCase();
}

function roleOption(role, selectedId, user = null) {
    const unavailableCustomerRole = role.name?.toLowerCase() === "customer"
        && user?.customerId == null
        && Number(user?.roleId) !== Number(role.id);
    const selected = Number(role.id) === Number(selectedId) ? " selected" : "";
    const disabled = unavailableCustomerRole ? " disabled" : "";
    const suffix = unavailableCustomerRole ? " — cần hồ sơ khách hàng" : "";
    return `<option value="${Number(role.id)}"${selected}${disabled}>${escapeHtml(roleLabel(role))}${escapeHtml(suffix)}</option>`;
}

export async function renderUsers(container, session) {
    container.innerHTML = `
        <section class="users-management-page">
            <header class="users-page-header">
                <div class="users-page-heading">
                    <span class="users-page-icon" aria-hidden="true"><i data-lucide="users-round"></i></span>
                    <div>
                    <p class="users-eyebrow">QUẢN TRỊ TRUY CẬP</p>
                        <h1>Quản lý phân quyền</h1>
                        <p>Quản lý người dùng và vai trò truy cập các khu vực trong khách sạn.</p>
                    </div>
                </div>
                <button id="btn-add-staff" class="btn btn-primary">
                    <i data-lucide="user-plus" aria-hidden="true"></i> Thêm tài khoản
                </button>
            </header>

            <div id="users-summary-bar" class="users-summary-grid" aria-label="Tổng quan tài khoản"></div>

            <section class="users-directory card card-border" aria-label="Danh sách tài khoản">
                <div class="users-directory-header">
                    <div>
                        <h2>Danh sách tài khoản</h2>
                        <p id="users-result-count" aria-live="polite">Đang tải tài khoản…</p>
                    </div>
                    <button id="btn-refresh-users" class="btn btn-ghost btn-sm" type="button">
                        <i data-lucide="refresh-cw" aria-hidden="true"></i> Làm mới
                    </button>
                </div>

                <div class="users-toolbar">
                    <label class="users-search-field">
                        <i data-lucide="search" aria-hidden="true"></i>
                        <input id="users-search" class="input input-bordered" type="search" placeholder="Tìm theo tên, tên đăng nhập hoặc email" autocomplete="off" aria-label="Tìm tài khoản">
                    </label>
                    <label class="users-filter-field">
                        <span>Vai trò</span>
                        <select id="users-role-filter" class="select select-bordered" aria-label="Lọc theo vai trò">
                            <option value="">Tất cả vai trò</option>
                        </select>
                    </label>
                    <label class="users-filter-field">
                        <span>Trạng thái</span>
                        <select id="users-status-filter" class="select select-bordered" aria-label="Lọc theo trạng thái">
                            <option value="">Tất cả trạng thái</option>
                            <option value="active">Đang hoạt động</option>
                            <option value="inactive">Ngưng hoạt động</option>
                        </select>
                    </label>
                </div>

                <div class="overflow-x-auto users-table-wrap">
                    <table class="table table-zebra users-table">
                        <thead>
                            <tr>
                                <th>Tài khoản</th>
                                <th>Email</th>
                                <th>Vai trò</th>
                                <th>Trạng thái</th>
                                <th class="users-actions-heading">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody id="admin-users-body">
                            <tr><td colspan="5"><div class="users-loading"><span class="loading loading-spinner loading-sm"></span> Đang tải dữ liệu…</div></td></tr>
                        </tbody>
                    </table>
                </div>
                <div id="users-empty-state" class="users-empty-state" hidden>
                    <span class="users-empty-icon"><i data-lucide="user-round-search" aria-hidden="true"></i></span>
                    <strong>Không tìm thấy tài khoản</strong>
                    <span>Thử đổi từ khóa hoặc bộ lọc.</span>
                </div>
                <div id="pagination-admin-users" class="users-pagination"></div>
            </section>

            <dialog id="user-role-dialog" class="modal modal-middle users-role-dialog" aria-labelledby="user-role-title">
                <div class="modal-box">
                    <div class="users-dialog-heading">
                        <span class="users-dialog-icon"><i data-lucide="shield-check" aria-hidden="true"></i></span>
                        <div>
                            <p class="users-eyebrow">CẬP NHẬT TRUY CẬP</p>
                            <h2 id="user-role-title">Cấp quyền tài khoản</h2>
                        </div>
                        <button class="btn btn-sm btn-circle btn-ghost" type="button" data-close-role-dialog aria-label="Đóng">×</button>
                    </div>
                    <div class="users-target-account" id="user-role-target"></div>
                    <div role="alert" class="alert alert-warning users-role-warning">
                        <i data-lucide="triangle-alert" aria-hidden="true"></i>
                        <span>Quyền mới áp dụng ngay. Phiên đăng nhập hiện có sẽ hết hiệu lực và người dùng cần đăng nhập lại.</span>
                    </div>
                    <form id="user-role-form">
                        <label class="users-form-label" for="user-role-select">Vai trò mới</label>
                        <select id="user-role-select" class="select select-bordered w-full" required></select>
                        <p id="user-role-description" class="users-role-description"></p>
                        <p id="user-role-error" class="users-form-error" role="alert" hidden></p>
                        <div class="modal-action">
                            <button class="btn btn-ghost" type="button" data-close-role-dialog>Hủy</button>
                            <button id="user-role-save" class="btn btn-primary" type="submit">Lưu quyền</button>
                        </div>
                    </form>
                </div>
            </dialog>

            <dialog id="user-create-dialog" class="modal modal-middle users-role-dialog users-create-dialog" aria-labelledby="user-create-title">
                <div class="modal-box">
                    <div class="users-dialog-heading">
                        <span class="users-dialog-icon"><i data-lucide="user-round-plus" aria-hidden="true"></i></span>
                        <div>
                            <p class="users-eyebrow">TÀI KHOẢN MỚI</p>
                            <h2 id="user-create-title">Thêm tài khoản</h2>
                        </div>
                        <button class="btn btn-sm btn-circle btn-ghost" type="button" data-close-create-dialog aria-label="Đóng">×</button>
                    </div>
                    <p class="users-create-intro">Thông tin đăng nhập và vai trò sẽ quyết định khu vực người dùng được phép truy cập.</p>
                    <form id="user-create-form" class="users-create-form" autocomplete="off">
                        <div class="users-create-grid">
                            <label class="users-create-field" for="user-create-username">
                                <span>Tên đăng nhập <b>*</b></span>
                                <input id="user-create-username" class="input input-bordered" type="text" required maxlength="100" autocomplete="off" placeholder="vd: le_tan_01">
                            </label>
                            <label class="users-create-field" for="user-create-password">
                                <span>Mật khẩu ban đầu <b>*</b></span>
                                <input id="user-create-password" class="input input-bordered" type="password" required minlength="6" maxlength="200" autocomplete="new-password" placeholder="Tối thiểu 6 ký tự">
                            </label>
                            <label class="users-create-field users-create-field--wide" for="user-create-name">
                                <span>Họ và tên <b>*</b></span>
                                <input id="user-create-name" class="input input-bordered" type="text" required maxlength="100" autocomplete="name" placeholder="Tên hiển thị">
                            </label>
                            <label class="users-create-field" for="user-create-phone">
                                <span>Số điện thoại</span>
                                <input id="user-create-phone" class="input input-bordered" type="tel" maxlength="20" autocomplete="off" placeholder="Không bắt buộc">
                            </label>
                            <label class="users-create-field" for="user-create-email">
                                <span>Email</span>
                                <input id="user-create-email" class="input input-bordered" type="email" maxlength="100" autocomplete="off" placeholder="Không bắt buộc">
                            </label>
                            <label class="users-create-field users-create-field--wide" for="user-create-role">
                                <span>Vai trò ban đầu <b>*</b></span>
                                <select id="user-create-role" class="select select-bordered" required></select>
                                <small>Chọn quyền truy cập phù hợp với công việc của tài khoản.</small>
                            </label>
                        </div>
                        <p id="user-create-error" class="users-form-error" role="alert" hidden></p>
                        <div class="modal-action">
                            <button class="btn btn-ghost" type="button" data-close-create-dialog>Hủy</button>
                            <button id="user-create-submit" class="btn btn-primary" type="submit"><i data-lucide="user-plus" aria-hidden="true"></i> Tạo tài khoản</button>
                        </div>
                    </form>
                </div>
            </dialog>

            <dialog id="user-delete-dialog" class="modal modal-middle users-role-dialog" aria-labelledby="user-delete-title">
                <div class="modal-box">
                    <div class="users-dialog-heading">
                        <span class="users-dialog-icon users-dialog-icon--danger"><i data-lucide="trash-2" aria-hidden="true"></i></span>
                        <div>
                            <p class="users-eyebrow">THAO TÁC TÀI KHOẢN</p>
                            <h2 id="user-delete-title">Xóa tài khoản</h2>
                        </div>
                        <button class="btn btn-sm btn-circle btn-ghost" type="button" data-close-delete-dialog aria-label="Đóng">×</button>
                    </div>
                    <p id="user-delete-message" class="users-delete-message"></p>
                    <p id="user-delete-error" class="users-form-error" role="alert" hidden></p>
                    <div class="modal-action">
                        <button class="btn btn-ghost" type="button" data-close-delete-dialog>Hủy</button>
                        <button id="user-delete-confirm" class="btn btn-error" type="button">Xóa tài khoản</button>
                    </div>
                </div>
            </dialog>
        </section>
    `;
    lucide.createIcons();

    const body = document.getElementById("admin-users-body");
    const summaryBar = document.getElementById("users-summary-bar");
    const resultCount = document.getElementById("users-result-count");
    const searchInput = document.getElementById("users-search");
    const roleFilter = document.getElementById("users-role-filter");
    const statusFilter = document.getElementById("users-status-filter");
    const emptyState = document.getElementById("users-empty-state");
    const roleDialog = document.getElementById("user-role-dialog");
    const createDialog = document.getElementById("user-create-dialog");
    const deleteDialog = document.getElementById("user-delete-dialog");
    [roleDialog, createDialog, deleteDialog].forEach(dialog => {
        dialog.addEventListener("click", event => {
            if (event.target === dialog) dialog.close();
        });
    });

    let allUsers = [];
    let roles = [];
    let currentPage = 1;
    let itemsPerPage = 10;
    let selectedUser = null;
    let deleteUser = null;

    function visibleUsers() {
        const term = searchInput.value.trim().toLocaleLowerCase("vi");
        const selectedRole = roleFilter.value;
        const selectedStatus = statusFilter.value;
        return allUsers.filter(user => {
            const searchable = [user.fullName, user.username, user.email].join(" ").toLocaleLowerCase("vi");
            const currentStatus = ["inactive", "disabled", "locked", "suspended", "deleted"].includes(String(user.status || "").toLowerCase())
                ? "inactive" : "active";
            return (!term || searchable.includes(term))
                && (!selectedRole || String(user.roleId) === selectedRole)
                && (!selectedStatus || currentStatus === selectedStatus);
        });
    }

    function renderSummary() {
        const admins = allUsers.filter(user => normalizedRole(user) === "admin").length;
        const customers = allUsers.filter(user => normalizedRole(user) === "customer").length;
        const employees = allUsers.length - admins - customers;
        summaryBar.innerHTML = `
            <div class="stat users-stat"><div class="stat-title">Tổng tài khoản</div><div class="stat-value">${allUsers.length}</div><div class="stat-desc">Toàn bộ người dùng trong hệ thống</div></div>
            <div class="stat users-stat"><div class="stat-title">Quản trị viên</div><div class="stat-value">${admins}</div><div class="stat-desc">Quản lý cấu hình và phân quyền</div></div>
            <div class="stat users-stat"><div class="stat-title">Nhân viên</div><div class="stat-value">${employees}</div><div class="stat-desc">Vận hành các bộ phận khách sạn</div></div>
            <div class="stat users-stat"><div class="stat-title">Khách hàng</div><div class="stat-value">${customers}</div><div class="stat-desc">Tài khoản thành viên</div></div>
        `;
    }

    function buildRoleFilters() {
        roleFilter.innerHTML = `<option value="">Tất cả vai trò</option>` + roles
            .map(role => `<option value="${Number(role.id)}">${escapeHtml(roleLabel(role))}</option>`).join("");
    }

    function renderTable() {
        const filtered = visibleUsers();
        const pages = Math.max(1, Math.ceil(filtered.length / itemsPerPage));
        currentPage = Math.min(currentPage, pages);
        const start = (currentPage - 1) * itemsPerPage;
        const paginated = filtered.slice(start, start + itemsPerPage);
        resultCount.textContent = filtered.length === allUsers.length
            ? `${allUsers.length} tài khoản`
            : `${filtered.length} / ${allUsers.length} tài khoản`;
        emptyState.hidden = filtered.length !== 0;
        body.innerHTML = "";

        for (const user of paginated) {
            const name = user.fullName || user.username || "Chưa có tên";
            const tr = document.createElement("tr");
            tr.className = "users-table-row";
            tr.innerHTML = `
                <td>
                    <div class="user-avatar-wrap">
                        <div class="user-avatar" style="background:${avatarColor(user.username)};">${escapeHtml(getInitials(name))}</div>
                        <div class="users-account-copy">
                            <strong>${escapeHtml(name)}</strong>
                            <span>@${escapeHtml(user.username || "-")}</span>
                        </div>
                    </div>
                </td>
                <td class="users-email-cell">${escapeHtml(user.email || "—")}</td>
                <td>${roleBadge(user)}</td>
                <td>${statusBadge(user.status)}</td>
                <td>
                    <div class="users-row-actions">
                        <button class="btn btn-sm btn-outline btn-role-edit" type="button" data-id="${Number(user.id)}" aria-label="Cấp quyền cho ${escapeHtml(name)}">
                            <i data-lucide="shield-plus" aria-hidden="true"></i><span>Cấp quyền</span>
                        </button>
                        <button class="btn btn-sm btn-ghost btn-delete-user" type="button" data-id="${Number(user.id)}" aria-label="Xóa tài khoản ${escapeHtml(name)}" title="Xóa tài khoản">
                            <i data-lucide="trash-2" aria-hidden="true"></i>
                        </button>
                    </div>
                </td>
            `;
            body.appendChild(tr);
        }
        lucide.createIcons();

        body.querySelectorAll(".btn-role-edit").forEach(button => {
            button.addEventListener("click", () => openRoleEditor(Number(button.dataset.id)));
        });
        body.querySelectorAll(".btn-delete-user").forEach(button => {
            button.addEventListener("click", () => openDeleteDialog(Number(button.dataset.id)));
        });

        const pagination = document.getElementById("pagination-admin-users");
        if (window.renderPaginationComponent && filtered.length) {
            window.renderPaginationComponent("pagination-admin-users", filtered.length, itemsPerPage, currentPage,
                page => { currentPage = page; renderTable(); },
                pageSize => { itemsPerPage = pageSize; currentPage = 1; renderTable(); });
        } else if (pagination) {
            pagination.innerHTML = "";
        }
    }

    function openRoleEditor(userId) {
        selectedUser = allUsers.find(user => Number(user.id) === userId);
        if (!selectedUser) return;
        const currentRole = roles.find(role => Number(role.id) === Number(selectedUser.roleId));
        const isSelf = Number(selectedUser.id) === Number(session?.userId ?? session?.id);
        document.getElementById("user-role-target").innerHTML = `
            <div class="user-avatar" style="background:${avatarColor(selectedUser.username)};">${escapeHtml(getInitials(selectedUser.fullName || selectedUser.username))}</div>
            <div><strong>${escapeHtml(selectedUser.fullName || selectedUser.username)}</strong><span>@${escapeHtml(selectedUser.username)}</span></div>
            <div class="users-current-role"><span>Hiện tại</span>${roleBadge(selectedUser)}</div>
        `;
        const select = document.getElementById("user-role-select");
        select.innerHTML = roles.map(role => roleOption(role, selectedUser.roleId, selectedUser)).join("");
        select.disabled = isSelf;
        document.getElementById("user-role-description").textContent = currentRole?.description || roleLabel(currentRole);
        document.getElementById("user-role-error").hidden = true;
        updateRoleEditorState();
        roleDialog.showModal();
        lucide.createIcons();
    }

    function updateRoleEditorState() {
        const roleId = Number(document.getElementById("user-role-select").value);
        const role = roles.find(item => Number(item.id) === roleId);
        const error = document.getElementById("user-role-error");
        const sameRole = Number(selectedUser?.roleId) === roleId;
        const isSelf = Number(selectedUser?.id) === Number(session?.userId ?? session?.id);
        document.getElementById("user-role-description").textContent = role?.description || roleLabel(role);
        const saveButton = document.getElementById("user-role-save");
        saveButton.disabled = sameRole || isSelf;
        saveButton.textContent = sameRole ? "Đang giữ vai trò này" : "Lưu quyền";
        if (isSelf) {
            error.textContent = "Không thể thay đổi vai trò của tài khoản đang đăng nhập.";
            error.hidden = false;
        }
    }

    function openDeleteDialog(userId) {
        deleteUser = allUsers.find(user => Number(user.id) === userId);
        if (!deleteUser) return;
        document.getElementById("user-delete-message").textContent = `Xóa tài khoản ${deleteUser.fullName || deleteUser.username}? Thao tác này có thể ảnh hưởng đến các nghiệp vụ liên quan.`;
        document.getElementById("user-delete-error").hidden = true;
        deleteDialog.showModal();
    }

    async function loadUsers() {
        body.innerHTML = `<tr><td colspan="5"><div class="users-loading"><span class="loading loading-spinner loading-sm"></span> Đang tải dữ liệu…</div></td></tr>`;
        try {
            const [rawUsers, rawRoles] = await Promise.all([api.get("/users"), api.get("/roles")]);
            allUsers = Array.isArray(rawUsers) ? rawUsers : [];
            roles = Array.isArray(rawRoles) ? rawRoles : [];
            buildRoleFilters();
            renderSummary();
            currentPage = 1;
            renderTable();
        } catch (error) {
            body.innerHTML = `<tr><td colspan="5"><div role="alert" class="alert alert-error">${escapeHtml(error.message || "Không thể tải tài khoản")}</div></td></tr>`;
            summaryBar.innerHTML = "";
            resultCount.textContent = "Chưa tải được danh sách";
        }
    }

    searchInput.addEventListener("input", () => { currentPage = 1; renderTable(); });
    roleFilter.addEventListener("change", () => { currentPage = 1; renderTable(); });
    statusFilter.addEventListener("change", () => { currentPage = 1; renderTable(); });
    document.getElementById("btn-refresh-users").addEventListener("click", loadUsers);
    document.getElementById("user-role-select").addEventListener("change", updateRoleEditorState);
    document.querySelectorAll("[data-close-role-dialog]").forEach(button => button.addEventListener("click", () => roleDialog.close()));
    document.querySelectorAll("[data-close-create-dialog]").forEach(button => button.addEventListener("click", () => createDialog.close()));
    document.querySelectorAll("[data-close-delete-dialog]").forEach(button => button.addEventListener("click", () => deleteDialog.close()));

    document.getElementById("user-create-form").addEventListener("submit", async event => {
        event.preventDefault();
        const errorBox = document.getElementById("user-create-error");
        const submit = document.getElementById("user-create-submit");
        const payload = {
            username: document.getElementById("user-create-username").value.trim(),
            password: document.getElementById("user-create-password").value,
            fullName: document.getElementById("user-create-name").value.trim(),
            phone: document.getElementById("user-create-phone").value.trim(),
            email: document.getElementById("user-create-email").value.trim(),
            roleId: Number(document.getElementById("user-create-role").value),
            status: "active"
        };
        submit.disabled = true;
        errorBox.hidden = true;
        try {
            await api.post("/users", payload);
            createDialog.close();
            document.getElementById("user-create-form").reset();
            const createdRole = roles.find(role => Number(role.id) === payload.roleId);
            const accountDetails = [
                { label: "Họ và tên", value: payload.fullName || "—" },
                { label: "Tên đăng nhập", value: payload.username },
                ...(payload.email ? [{ label: "Email", value: payload.email }] : []),
                ...(payload.phone ? [{ label: "Số điện thoại", value: payload.phone }] : []),
                { label: "Vai trò", value: roleLabel(createdRole) },
                { label: "Trạng thái", value: "Đang hoạt động" }
            ];
            await window.showCustomAlert?.("Tài khoản đã được tạo thành công.", {
                kind: "success",
                details: accountDetails
            });
            await loadUsers();
        } catch (error) {
            errorBox.textContent = error.message || "Không thể tạo tài khoản.";
            errorBox.hidden = false;
        } finally {
            submit.disabled = false;
        }
    });

    document.getElementById("user-role-form").addEventListener("submit", async event => {
        event.preventDefault();
        if (!selectedUser) return;
        const error = document.getElementById("user-role-error");
        const button = document.getElementById("user-role-save");
        button.disabled = true;
        button.textContent = "Đang lưu…";
        error.hidden = true;
        try {
            await api.put(`/users/${selectedUser.id}/role`, { roleId: Number(document.getElementById("user-role-select").value) });
            roleDialog.close();
            window.showCustomAlert?.("Đã cập nhật vai trò tài khoản.");
            await loadUsers();
        } catch (err) {
            error.textContent = err.message || "Không thể cập nhật quyền truy cập.";
            error.hidden = false;
            updateRoleEditorState();
            button.disabled = false;
            button.textContent = "Lưu quyền";
        }
    });

    document.getElementById("user-delete-confirm").addEventListener("click", async () => {
        if (!deleteUser) return;
        const button = document.getElementById("user-delete-confirm");
        const error = document.getElementById("user-delete-error");
        button.disabled = true;
        error.hidden = true;
        try {
            await api.delete(`/users/${deleteUser.id}`);
            deleteDialog.close();
            await loadUsers();
        } catch (err) {
            error.textContent = err.message || "Không thể xóa tài khoản.";
            error.hidden = false;
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById("btn-add-staff").addEventListener("click", () => {
        const defaultRole = roles.find(role => role.name?.toLowerCase() === "front_desk")
            || roles.find(role => role.name?.toLowerCase() === "staff")
            || roles.find(role => role.name?.toLowerCase() === "customer");
        const form = document.getElementById("user-create-form");
        form.reset();
        const roleSelect = document.getElementById("user-create-role");
        roleSelect.innerHTML = roles.map(role => roleOption(role, defaultRole?.id)).join("");
        document.getElementById("user-create-error").hidden = true;
        createDialog.showModal();
        lucide.createIcons();
    });

    await loadUsers();
}
