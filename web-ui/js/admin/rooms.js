import { api } from "../../api.js";

export async function renderRooms(container, session) {
    container.innerHTML = `
        <div class="page-header flex-row justify-between">
            <div>
                <h1>Cấu Hình Phòng Khách Sạn</h1>
                <p>Danh sách cấu hình phòng trống và đơn giá phòng nghỉ.</p>
            </div>
            <button id="btn-add-room-admin" class="btn btn-primary">
                <i data-lucide="plus" style="width: 14px; height: 14px;"></i> Thêm phòng mới
            </button>
        </div>
        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Mã ID</th>
                            <th>Số phòng</th>
                            <th>Hạng phòng</th>
                            <th>Đơn giá đêm</th>
                            <th>Trạng thái</th>
                            <th>Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="admin-rooms-body">
                        <tr><td colspan="6" style="text-align: center;">Đang tải danh sách phòng...</td></tr>
                    </tbody>
                </table>
            </div>
            <div id="pagination-admin-rooms"></div>
        </div>
    `;
    lucide.createIcons();

    const loadRooms = async () => {
        const body = document.getElementById("admin-rooms-body");
        try {
            const list = await api.get("/rooms");
            allRooms = list;
            currentPage = 1;
            renderTable();
        } catch (e) {
            const body = document.getElementById("admin-rooms-body");
            body.innerHTML = `<tr><td colspan="6" style="color: var(--danger);">${e.message}</td></tr>`;
        }
    };

    let allRooms = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const renderTable = () => {
        const body = document.getElementById("admin-rooms-body");
        body.innerHTML = "";
        
        if (!allRooms || allRooms.length === 0) {
            body.innerHTML = `<tr><td colspan="6" style="text-align:center;">Chưa có dữ liệu phòng.</td></tr>`;
            const paginationContainer = document.getElementById("pagination-admin-rooms");
            if (paginationContainer) paginationContainer.innerHTML = '';
            return;
        }

        const start = (currentPage - 1) * itemsPerPage;
        const end = start + itemsPerPage;
        const paginatedList = allRooms.slice(start, end);

        paginatedList.forEach(r => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>${r.id}</td>
                <td><strong>Phòng ${r.roomNumber}</strong></td>
                <td>${r.typeName || 'Standard'}</td>
                <td>${Number(r.price).toLocaleString('vi-VN')} đ</td>
                <td><span class="room-badge badge-${r.status}">${r.status}</span></td>
                <td>
                    <button class="btn btn-danger btn-sm btn-delete-room" data-id="${r.id}" style="padding: 4px 8px; font-size: 12px;">Xóa</button>
                </td>
            `;
            
            tr.querySelector(".btn-delete-room").addEventListener("click", async () => {
                if (confirm(`Bạn chắc chắn muốn xóa phòng này khỏi danh mục hệ thống?`)) {
                    try {
                        await api.delete(`/rooms/${r.id}`);
                        alert("Đã xóa phòng thành công!");
                        loadRooms();
                    } catch (err) {
                        alert("Lỗi xóa phòng: " + err.message);
                    }
                }
            });
            
            body.appendChild(tr);
        });

        if (window.renderPaginationComponent) {
            window.renderPaginationComponent(
                "pagination-admin-rooms",
                allRooms.length,
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

    loadRooms();

    document.getElementById("btn-add-room-admin").addEventListener("click", () => {
        const modal = document.getElementById("global-modal");
        const content = document.getElementById("global-modal-content");
        modal.classList.add("active");

        content.innerHTML = `
            <div class="modal-header">
                <h2>Thêm Phòng Mới</h2>
                <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
            </div>
            <form id="form-add-room-admin-action">
                <div class="form-group">
                    <label class="form-label">Số phòng *</label>
                    <input class="form-input" type="text" id="ar-number" required placeholder="Ví dụ: 101">
                </div>
                <div class="form-group">
                    <label class="form-label">Loại phòng (Hạng phòng) *</label>
                    <select class="form-input" id="ar-type" required>
                        <option value="1">Standard (Standard Room)</option>
                        <option value="2">Superior (Superior Room)</option>
                        <option value="3">Deluxe (Deluxe Room)</option>
                        <option value="4">Suite (Luxury Suite)</option>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label">Đơn giá / Đêm *</label>
                    <input class="form-input" type="number" id="ar-price" required placeholder="Ví dụ: 500000">
                </div>
                <div class="flex-row mt-4" style="justify-content: flex-end;">
                    <button type="button" class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy</button>
                    <button type="submit" class="btn btn-primary">Thêm</button>
                </div>
            </form>
        `;

        document.getElementById("form-add-room-admin-action").addEventListener("submit", async (e) => {
            e.preventDefault();
            const roomNumber = document.getElementById("ar-number").value.trim();
            const roomTypeId = document.getElementById("ar-type").value;
            const price = document.getElementById("ar-price").value;

            try {
                await api.post("/rooms", { roomNumber, roomTypeId, price, status: "available" });
                window.showCustomAlert("Đã tạo phòng mới thành công!");
                closeModal("global-modal");
                loadRooms();
            } catch (err) {
                window.showCustomAlert("Lỗi tạo phòng: " + err.message);
            }
        });
    });
}
