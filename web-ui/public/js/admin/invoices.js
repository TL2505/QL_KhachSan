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
            <div id="pagination-admin-invoices"></div>
        </div>
    `;

    let allInvoices = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const renderTable = () => {
        const body = document.getElementById("admin-invoices-body");
        body.innerHTML = "";
        
        if (!allInvoices || allInvoices.length === 0) {
            body.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted);">Không tìm thấy bản ghi hóa đơn nào.</td></tr>`;
            const paginationContainer = document.getElementById("pagination-admin-invoices");
            if (paginationContainer) paginationContainer.innerHTML = '';
            return;
        }

        const start = (currentPage - 1) * itemsPerPage;
        const end = start + itemsPerPage;
        const paginatedList = allInvoices.slice(start, end);

        paginatedList.forEach(i => {
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

        if (window.renderPaginationComponent) {
            window.renderPaginationComponent(
                "pagination-admin-invoices",
                allInvoices.length,
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

    try {
        const list = await api.get("/invoices");
        allInvoices = list;
        currentPage = 1;
        renderTable();
    } catch (e) {
        console.error(e);
        const body = document.getElementById("admin-invoices-body");
        if (body) body.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--danger);">${e.message}</td></tr>`;
    }
}
