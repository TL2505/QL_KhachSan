import { api } from "../../api.js";

export async function renderInvoices(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Tra Cứu Hóa Đơn</h1>
            <p>Hồ sơ chứng từ lưu trữ các giao dịch thanh toán thành công.</p>
        </div>
        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Hóa đơn ID</th>
                            <th>Mã đơn thuê</th>
                            <th>Phòng</th>
                            <th>Khách hàng</th>
                            <th>Thanh toán</th>
                            <th>Ngày xuất</th>
                        </tr>
                    </thead>
                    <tbody id="admin-invoices-body">
                        <tr><td colspan="6" style="text-align: center;">Đang tải danh sách...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;

    try {
        const list = await api.get("/invoices");
        const body = document.getElementById("admin-invoices-body");
        body.innerHTML = "";
        if (!list || list.length === 0) {
            body.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted);">Không tìm thấy bản ghi hóa đơn nào.</td></tr>`;
            return;
        }

        list.forEach(i => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>#${i.id}</td>
                <td>Đơn thuê #${i.bookingId}</td>
                <td><strong>Phòng #${i.roomId}</strong></td>
                <td>Khách hàng #${i.customerId || 'Guest'}</td>
                <td style="font-weight: 600; color: var(--success);">${Number(i.amount).toLocaleString('vi-VN')} đ</td>
                <td>${i.invoiceDate}</td>
            `;
            body.appendChild(tr);
        });
    } catch (e) {
        console.error(e);
    }
}
