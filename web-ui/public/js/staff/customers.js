import { api } from "../../api.js";

export async function renderCustomers(container, session) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Hồ Sơ Khách Hàng</h1>
                <p>Danh bạ thông tin khách hàng đăng ký lưu trú.</p>
            </div>
            <button id="btn-add-customer" class="btn btn-primary">
                <i data-lucide="plus" style="width: 14px; height: 14px;"></i> Thêm khách hàng mới
            </button>
        </div>
        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Họ và tên</th>
                            <th>Số CCCD/Passport</th>
                            <th>Số điện thoại</th>
                            <th>Email</th>
                            <th>Địa chỉ</th>
                        </tr>
                    </thead>
                    <tbody id="staff-customers-body">
                        <tr><td colspan="6" style="text-align: center;">Đang tải danh sách...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;
    lucide.createIcons();

    const loadCustomers = async () => {
        const body = document.getElementById("staff-customers-body");
        try {
            const list = await api.get("/customers");
            body.innerHTML = "";
            if (!list || list.length === 0) {
                body.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted);">Chưa có khách hàng nào.</td></tr>`;
                return;
            }
            list.forEach(c => {
                const tr = document.createElement("tr");
                tr.innerHTML = `
                    <td>${c.id}</td>
                    <td><strong>${c.fullName}</strong></td>
                    <td>${c.identityCard}</td>
                    <td>${c.phone || ''}</td>
                    <td>${c.email || ''}</td>
                    <td>${c.address || ''}</td>
                `;
                body.appendChild(tr);
            });
        } catch (e) {
            body.innerHTML = `<tr><td colspan="6" style="color: var(--danger);">${e.message}</td></tr>`;
        }
    };
    loadCustomers();

    document.getElementById("btn-add-customer").addEventListener("click", () => {
        const modal = document.getElementById("global-modal");
        const content = document.getElementById("global-modal-content");
        modal.classList.add("active");
        
        content.innerHTML = `
            <div class="modal-header">
                <h2>Thêm Khách Hàng</h2>
                <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
            </div>
            <form id="form-add-customer-staff">
                <div class="form-group">
                    <label class="form-label">Họ và tên *</label>
                    <input class="form-input" type="text" id="add-cust-name" required>
                </div>
                <div class="form-group">
                    <label class="form-label">Số CCCD / Hộ chiếu *</label>
                    <input class="form-input" type="text" id="add-cust-idcard" required>
                </div>
                <div class="form-group">
                    <label class="form-label">Số điện thoại</label>
                    <input class="form-input" type="text" id="add-cust-phone">
                </div>
                <div class="form-group">
                    <label class="form-label">Email</label>
                    <input class="form-input" type="email" id="add-cust-email">
                </div>
                <div class="form-group">
                    <label class="form-label">Địa chỉ</label>
                    <input class="form-input" type="text" id="add-cust-address">
                </div>
                <div class="flex-row mt-4" style="justify-content: flex-end;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy</button>
                    <button type="submit" class="btn btn-primary">Thêm mới</button>
                </div>
            </form>
        `;

        document.getElementById("form-add-customer-staff").addEventListener("submit", async (e) => {
            e.preventDefault();
            const fullName = document.getElementById("add-cust-name").value.trim();
            const identityCard = document.getElementById("add-cust-idcard").value.trim();
            const phone = document.getElementById("add-cust-phone").value.trim();
            const email = document.getElementById("add-cust-email").value.trim();
            const address = document.getElementById("add-cust-address").value.trim();

            try {
                await api.post("/customers", { fullName, identityCard, phone, email, address });
                window.showCustomAlert("Thêm khách hàng mới thành công!");
                closeModal("global-modal");
                loadCustomers();
            } catch (err) {
                window.showCustomAlert("Lỗi thêm khách hàng: " + err.message);
            }
        });
    });
}
