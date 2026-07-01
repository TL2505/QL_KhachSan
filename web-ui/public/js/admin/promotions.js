import { api } from "../../api.js";

export async function renderPromotions(container, session) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Danh Sách Khuyến Mãi</h1>
                <p>Quản lý các sự kiện giảm giá và Vouchers quy đổi.</p>
            </div>
            <button id="btn-add-promo" class="btn btn-primary">
                <i data-lucide="plus" style="width: 14px; height: 14px;"></i> Thêm khuyến mãi mới
            </button>
        </div>
        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Mã</th>
                            <th>Tên chiến dịch</th>
                            <th>Mức giảm</th>
                            <th>Ngày bắt đầu</th>
                            <th>Ngày kết thúc</th>
                        </tr>
                    </thead>
                    <tbody id="admin-promotions-body">
                        <tr><td colspan="5" style="text-align: center;">Đang tải danh sách...</td></tr>
                    </tbody>
                </table>
            </div>
            <div id="pagination-admin-promotions"></div>
        </div>
    `;
    lucide.createIcons();

    const loadPromotions = async () => {
        const body = document.getElementById("admin-promotions-body");
        try {
            const list = await api.get("/promotions");
            allPromos = list;
            currentPage = 1;
            renderTable();
        } catch (e) {
            const body = document.getElementById("admin-promotions-body");
            body.innerHTML = `<tr><td colspan="5" style="color: var(--danger);">${e.message}</td></tr>`;
        }
    };

    let allPromos = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const renderTable = () => {
        const body = document.getElementById("admin-promotions-body");
        body.innerHTML = "";
        
        if (!allPromos || allPromos.length === 0) {
            body.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted);">Không có chiến dịch khuyến mãi nào.</td></tr>`;
            const paginationContainer = document.getElementById("pagination-admin-promotions");
            if (paginationContainer) paginationContainer.innerHTML = '';
            return;
        }

        const start = (currentPage - 1) * itemsPerPage;
        const end = start + itemsPerPage;
        const paginatedList = allPromos.slice(start, end);

        paginatedList.forEach(p => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>${p.id}</td>
                <td><strong>${p.name}</strong></td>
                <td><span class="room-badge badge-cleaning">${p.discountType === 'percentage' ? p.discountValue + '%' : Number(p.discountValue).toLocaleString('vi-VN') + 'đ'}</span></td>
                <td>${p.startDate}</td>
                <td>${p.endDate || 'Vô thời hạn'}</td>
            `;
            body.appendChild(tr);
        });

        if (window.renderPaginationComponent) {
            window.renderPaginationComponent(
                "pagination-admin-promotions",
                allPromos.length,
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

    loadPromotions();

    document.getElementById("btn-add-promo").addEventListener("click", () => {
        const modal = document.getElementById("global-modal");
        const content = document.getElementById("global-modal-content");
        modal.classList.add("active");

        content.innerHTML = `
            <div class="modal-header">
                <h2>Tạo Chiến Dịch Khuyến Mãi</h2>
                <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
            </div>
            <form id="form-add-promo-action">
                <div class="form-group">
                    <label class="form-label">Tên khuyến mãi *</label>
                    <input class="form-input" type="text" id="ap-name" required placeholder="Ví dụ: Giảm giá hè">
                </div>
                <div class="form-group">
                    <label class="form-label">Loại giảm giá *</label>
                    <select class="form-input" id="ap-type" required>
                        <option value="fixed">Giảm số tiền cố định (VNĐ)</option>
                        <option value="percentage">Giảm theo tỷ lệ phần trăm (%)</option>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label">Giá trị giảm *</label>
                    <input class="form-input" type="number" id="ap-value" required placeholder="Ví dụ: 10% hoặc 50000đ">
                </div>
                <div class="form-group">
                    <label class="form-label">Ngày bắt đầu *</label>
                    <input class="form-input" type="date" id="ap-start" required>
                </div>
                <div class="form-group">
                    <label class="form-label">Ngày kết thúc *</label>
                    <input class="form-input" type="date" id="ap-end" required>
                </div>
                <div class="flex-row mt-4" style="justify-content: flex-end;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy</button>
                    <button type="submit" class="btn btn-primary">Tạo mới</button>
                </div>
            </form>
        `;

        document.getElementById("form-add-promo-action").addEventListener("submit", async (e) => {
            e.preventDefault();
            const name = document.getElementById("ap-name").value.trim();
            const discountType = document.getElementById("ap-type").value;
            const discountValue = document.getElementById("ap-value").value;
            const startDate = document.getElementById("ap-start").value;
            const endDate = document.getElementById("ap-end").value;

            try {
                await api.post("/promotions", { name, discountType, discountValue, startDate, endDate, status: "active" });
                window.showCustomAlert("Tạo chiến dịch khuyến mãi mới thành công!");
                closeModal("global-modal");
                loadPromotions();
            } catch (err) {
                window.showCustomAlert("Lỗi tạo khuyến mãi: " + err.message);
            }
        });
    });
}
