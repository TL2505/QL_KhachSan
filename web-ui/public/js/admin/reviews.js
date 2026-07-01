import { api } from "../../api.js";

export async function renderReviews(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Quản Lý Đánh Giá Khách Hàng</h1>
            <p>Kiểm duyệt các ý kiến đóng góp và xếp hạng sao phòng nghỉ.</p>
        </div>
        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Mã phòng</th>
                            <th>Xếp hạng sao</th>
                            <th>Nội dung bình luận</th>
                            <th>Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="admin-reviews-body">
                        <tr><td colspan="5" style="text-align: center;">Đang tải đánh giá...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;

    const loadReviews = async () => {
        const body = document.getElementById("admin-reviews-body");
        try {
            const list = await api.get("/reviews");
            body.innerHTML = "";
            if (!list || list.length === 0) {
                body.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted);">Không có bài đánh giá nào.</td></tr>`;
                return;
            }
            list.forEach(r => {
                const tr = document.createElement("tr");
                tr.innerHTML = `
                    <td>${r.id}</td>
                    <td>Phòng #${r.roomId}</td>
                    <td style="color: var(--primary); font-weight: bold;">${'★'.repeat(r.rating)}${'☆'.repeat(5 - r.rating)}</td>
                    <td>${r.comment || ''}</td>
                    <td>
                        <button class="btn btn-danger btn-sm btn-delete-review" data-id="${r.id}" style="padding: 4px 8px; font-size: 12px;">Xóa</button>
                    </td>
                `;
                
                tr.querySelector(".btn-delete-review").addEventListener("click", async () => {
                    if (await window.showCustomConfirm(`Bạn chắc chắn muốn xóa bài đánh giá này khỏi danh mục hiển thị công cộng?`)) {
                        try {
                            await api.delete(`/reviews/${r.id}`);
                            window.showCustomAlert("Đã xóa bài đánh giá thành công!");
                            loadReviews();
                        } catch (err) {
                            window.showCustomAlert("Lỗi xóa đánh giá: " + err.message);
                        }
                    }
                });
                
                body.appendChild(tr);
            });
        } catch (e) {
            body.innerHTML = `<tr><td colspan="5" style="color: var(--danger);">${e.message}</td></tr>`;
        }
    };
    loadReviews();
}
