import { api } from "../../api.js";

export async function renderPayments(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Hóa Đơn Doanh Thu</h1>
            <p>Báo cáo doanh số giao dịch thanh toán của khách hàng.</p>
        </div>
        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Hóa đơn ID</th>
                            <th>Mã phòng</th>
                            <th>Tổng tiền</th>
                            <th>Ngày thanh toán</th>
                            <th>Phương thức</th>
                        </tr>
                    </thead>
                    <tbody id="staff-payments-body">
                        <tr><td colspan="5" style="text-align: center;">Đang tải danh sách...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;

    try {
        const invoices = await api.get("/invoices");
        const body = document.getElementById("staff-payments-body");
        body.innerHTML = "";
        
        if (!invoices || invoices.length === 0) {
            body.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted);">Chưa phát sinh hóa đơn thanh toán nào.</td></tr>`;
            return;
        }

        invoices.forEach(i => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>#${i.id}</td>
                <td><strong>Phòng #${i.roomId}</strong></td>
                <td style="font-weight: 600; color: var(--success);">${Number(i.amount).toLocaleString('vi-VN')} đ</td>
                <td>${i.invoiceDate}</td>
                <td style="text-transform: uppercase;">${i.paymentMethod || 'cash'}</td>
            `;
            body.appendChild(tr);
        });
    } catch (e) {
        console.error(e);
    }
}
