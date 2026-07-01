import { api } from "../../api.js";

const METHOD_LABEL = {
    cash:         { label: 'Tiền mặt',    color: 'var(--success)' },
    card:         { label: 'Thẻ ngân hàng', color: 'var(--info)' },
    transfer:     { label: 'Chuyển khoản', color: 'var(--primary)' },
    online:       { label: 'Online',       color: 'var(--warning)' },
};

function skeletonRows(count = 6) {
    return Array.from({ length: count }, () =>
        `<tr>${Array.from({ length: 5 }, (_, i) =>
            `<td><div class="skeleton-block" style="width:${[40,70,90,80,80][i]}px;border-radius:4px;"></div></td>`
        ).join('')}</tr>`
    ).join('');
}

export async function renderPayments(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Hóa Đơn Thanh Toán</h1>
            <p>Tra cứu và quản lý các giao dịch thanh toán của khách hàng.</p>
        </div>

        <!-- Summary bar -->
        <div id="staff-pay-summary" style="display:flex;gap:16px;margin-bottom:24px;"></div>

        <!-- Search + filter -->
        <div class="card" style="padding:14px 20px;margin-bottom:0;">
            <div style="display:flex;align-items:center;gap:10px;">
                <div style="position:relative;flex:1;">
                    <i data-lucide="search" style="width:15px;height:15px;position:absolute;left:12px;top:50%;transform:translateY(-50%);color:var(--text-muted);pointer-events:none;"></i>
                    <input id="staff-pay-search" class="form-input" type="text"
                        placeholder="Tìm theo mã hóa đơn, phòng, phương thức..."
                        style="padding-left:36px;margin:0;">
                </div>
                <select id="staff-pay-method" class="form-input" style="width:180px;margin:0;">
                    <option value="">Tất cả phương thức</option>
                    <option value="cash">Tiền mặt</option>
                    <option value="card">Thẻ ngân hàng</option>
                    <option value="transfer">Chuyển khoản</option>
                    <option value="online">Online</option>
                </select>
                <button id="btn-refresh-pay" class="btn btn-secondary" style="padding:10px 14px;flex-shrink:0;" title="Làm mới">
                    <i data-lucide="refresh-cw" style="width:15px;height:15px;"></i>
                </button>
            </div>
        </div>

        <div class="card" style="margin-top:0;border-top:none;border-top-left-radius:0;border-top-right-radius:0;">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th style="width:70px;">Hóa đơn</th>
                            <th>Phòng</th>
                            <th>Tổng tiền</th>
                            <th>Ngày thanh toán</th>
                            <th>Phương thức</th>
                        </tr>
                    </thead>
                    <tbody id="staff-payments-body">
                        ${skeletonRows(6)}
                    </tbody>
                </table>
            </div>
            <div id="pagination-staff-payments"></div>
        </div>
    `;
    lucide.createIcons();

    let allInvoices = [];
    let currentDisplayList = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const applyFilter = () => {
        const q      = document.getElementById("staff-pay-search").value.toLowerCase().trim();
        const method = document.getElementById("staff-pay-method").value.toLowerCase();
        let list = allInvoices;
        if (method) list = list.filter(i => (i.paymentMethod || '').toLowerCase() === method);
        if (q) list = list.filter(i =>
            String(i.id).includes(q) ||
            String(i.roomId).includes(q) ||
            (i.paymentMethod || '').toLowerCase().includes(q)
        );
        currentPage = 1;
        renderTable(list);
    };

    const renderTable = (list = currentDisplayList) => {
        currentDisplayList = list;
        const body = document.getElementById("staff-payments-body");
        body.innerHTML = "";
        if (!list || list.length === 0) {
            body.innerHTML = `<tr><td colspan="5">
                <div class="empty-state">
                    <div class="empty-state-icon"><i data-lucide="receipt" style="width:26px;height:26px;"></i></div>
                    <p>Không tìm thấy hóa đơn nào</p>
                </div>
            </td></tr>`;
            lucide.createIcons();
            const paginationContainer = document.getElementById("pagination-staff-payments");
            if (paginationContainer) paginationContainer.innerHTML = '';
            return;
        }

        const start = (currentPage - 1) * itemsPerPage;
        const end = start + itemsPerPage;
        const paginatedList = currentDisplayList.slice(start, end);

        paginatedList.forEach((inv, i) => {
            const m = METHOD_LABEL[(inv.paymentMethod || '').toLowerCase()] || { label: inv.paymentMethod || 'cash', color: 'var(--text-muted)' };
            const tr = document.createElement("tr");
            tr.className = "table-row-animate";
            tr.style.animationDelay = `${i * 0.03}s`;
            tr.innerHTML = `
                <td style="font-weight:700;color:var(--primary);">#${inv.id}</td>
                <td><strong>Phòng ${inv.roomId}</strong></td>
                <td style="font-weight:700;color:var(--success);font-size:15px;">${Number(inv.amount || 0).toLocaleString('vi-VN')} đ</td>
                <td style="font-size:13px;color:var(--text-muted);">${inv.invoiceDate || '—'}</td>
                <td>
                    <span style="display:inline-flex;align-items:center;gap:5px;font-size:12px;font-weight:600;color:${m.color};background:${m.color}18;padding:4px 10px;border-radius:30px;text-transform:uppercase;">
                        ${m.label}
                    </span>
                </td>
            `;
            body.appendChild(tr);
        });
        lucide.createIcons();

        if (window.renderPaginationComponent) {
            window.renderPaginationComponent(
                "pagination-staff-payments",
                currentDisplayList.length,
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

    const loadPayments = async () => {
        document.getElementById("staff-payments-body").innerHTML = skeletonRows(6);
        try {
            allInvoices = await api.get("/invoices") || [];

            const total   = allInvoices.reduce((s, i) => s + Number(i.amount || 0), 0);
            const byCash  = allInvoices.filter(i => (i.paymentMethod || '').toLowerCase() === 'cash').length;
            const byCard  = allInvoices.filter(i => ['card','transfer','online'].includes((i.paymentMethod || '').toLowerCase())).length;

            const summaryBar = document.getElementById("staff-pay-summary");
            summaryBar.innerHTML = `
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--primary);">
                    <i data-lucide="receipt" style="width:18px;height:18px;color:var(--primary);"></i>
                    <div>
                        <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${allInvoices.length}</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tổng hóa đơn</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--success);">
                    <i data-lucide="banknote" style="width:18px;height:18px;color:var(--success);"></i>
                    <div>
                        <div style="font-size:16px;font-weight:700;font-family:var(--font-heading);">${(total/1e6).toFixed(1)}M đ</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tổng thu</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--success);">
                    <i data-lucide="coins" style="width:18px;height:18px;color:var(--success);"></i>
                    <div>
                        <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${byCash}</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tiền mặt</div>
                    </div>
                </div>
                <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--info);">
                    <i data-lucide="credit-card" style="width:18px;height:18px;color:var(--info);"></i>
                    <div>
                        <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${byCard}</div>
                        <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Thẻ / CK</div>
                    </div>
                </div>
            `;
            lucide.createIcons();
            renderTable(allInvoices);
        } catch (e) {
            document.getElementById("staff-payments-body").innerHTML =
                `<tr><td colspan="5" style="color:var(--danger);padding:20px;">${e.message}</td></tr>`;
        }
    };
    loadPayments();

    document.getElementById("staff-pay-search").addEventListener("input", applyFilter);
    document.getElementById("staff-pay-method").addEventListener("change", applyFilter);
    document.getElementById("btn-refresh-pay").addEventListener("click", () => {
        document.getElementById("staff-pay-search").value = "";
        document.getElementById("staff-pay-method").value = "";
        loadPayments();
    });
}
