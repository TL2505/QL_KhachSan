import { api } from "../../api.js";

export async function renderUsers(container, session) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Quản Lý Nhân Viên & Người Dùng</h1>
                <p>Danh sách tài khoản quản lý và nhân viên vận hành khách sạn.</p>
            </div>
            <button id="btn-add-staff" class="btn btn-primary">
                <i data-lucide="plus" style="width: 14px; height: 14px;"></i> Thêm tài khoản mới
            </button>
        </div>
        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Username</th>
                            <th>Họ và tên</th>
                            <th>Email</th>
                            <th>Quyền hạn</th>
                            <th>Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="admin-users-body">
                        <tr><td colspan="6" style="text-align: center;">Đang tải danh sách...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;
    lucide.createIcons();

    const loadUsers = async () => {
        const body = document.getElementById("admin-users-body");
        try {
            const list = await api.get("/users");
            body.innerHTML = "";
            list.forEach(u => {
                const tr = document.createElement("tr");
                tr.innerHTML = `
                    <td>${u.id}</td>
                    <td>${u.username}</td>
                    <td><strong>${u.fullName || ''}</strong></td>
                    <td>${u.email || ''}</td>
                    <td><span class="room-badge badge-cleaning">${u.roleName || 'STAFF'}</span></td>
                    <td>
                        <button class="btn btn-danger btn-sm btn-delete-user" data-id="${u.id}" style="padding: 4px 8px; font-size: 12px;">Xóa</button>
                    </td>
                `;
                
                tr.querySelector(".btn-delete-user").addEventListener("click", async () => {
                    if (await window.showCustomConfirm(`Bạn chắc chắn muốn xóa tài khoản này khỏi hệ thống?`)) {
                        try {
                            await api.delete(`/users/${u.id}`);
                            window.showCustomAlert("Đã xóa tài khoản nhân viên thành công!");
                            loadUsers();
                        } catch (err) {
                            window.showCustomAlert("Lỗi xóa tài khoản: " + err.message);
                        }
                    }
                });
                
                body.appendChild(tr);
            });
        } catch (e) {
            body.innerHTML = `<tr><td colspan="6" style="color: var(--danger);">${e.message}</td></tr>`;
        }
    };
    loadUsers();

    document.getElementById("btn-add-staff").addEventListener("click", () => {
        const modal = document.getElementById("global-modal");
        const content = document.getElementById("global-modal-content");
        modal.classList.add("active");

        content.innerHTML = `
            <div class="modal-header">
                <h2>Thêm Nhân Viên / Người dùng</h2>
                <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
            </div>
            <form id="form-add-staff-action">
                <div class="form-group">
                    <label class="form-label">Tên đăng nhập *</label>
                    <input class="form-input" type="text" id="as-username" required>
                </div>
                <div class="form-group">
                    <label class="form-label">Mật khẩu *</label>
                    <input class="form-input" type="password" id="as-password" required>
                </div>
                <div class="form-group">
                    <label class="form-label">Họ và tên *</label>
                    <input class="form-input" type="text" id="as-name" required>
                </div>
                <div class="form-group">
                    <label class="form-label">Số điện thoại</label>
                    <input class="form-input" type="text" id="as-phone">
                </div>
                <div class="form-group">
                    <label class="form-label">Email</label>
                    <input class="form-input" type="email" id="as-email">
                </div>
                <div class="form-group">
                    <label class="form-label">Chức vụ (Phân quyền) *</label>
                    <select class="form-input" id="as-role" required>
                        <option value="1">ADMIN (Quản trị viên)</option>
                        <option value="2">STAFF (Nhân viên lễ tân)</option>
                    </select>
                </div>
                <div class="flex-row mt-4" style="justify-content: flex-end;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy</button>
                    <button type="submit" class="btn btn-primary">Thêm tài khoản</button>
                </div>
            </form>
        `;

        document.getElementById("form-add-staff-action").addEventListener("submit", async (e) => {
            e.preventDefault();
            const username = document.getElementById("as-username").value.trim();
            const password = document.getElementById("as-password").value;
            const fullName = document.getElementById("as-name").value.trim();
            const phone = document.getElementById("as-phone").value.trim();
            const email = document.getElementById("as-email").value.trim();
            const roleId = document.getElementById("as-role").value;

            try {
                await api.post("/users", { username, password, fullName, phone, email, roleId, status: "active" });
                window.showCustomAlert("Đã tạo tài khoản nhân viên mới!");
                closeModal("global-modal");
                loadUsers();
            } catch (err) {
                window.showCustomAlert("Lỗi tạo tài khoản: " + err.message);
            }
        });
    });
}
