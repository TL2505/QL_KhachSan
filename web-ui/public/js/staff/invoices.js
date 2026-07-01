import { api, getApiBaseUrl } from "../../api.js";

let currentPage = 1;
const limit = 10;
let currentInvoices = [];

export async function renderInvoices(container, session) {
    container.innerHTML = `
        <style>
            .filter-toolbar {
                display: flex; gap: 15px; margin-bottom: 20px; flex-wrap: wrap; background: var(--bg-card); padding: 15px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            }
            .filter-group {
                display: flex; flex-direction: column; gap: 5px; flex: 1; min-width: 200px;
            }
            .filter-group label {
                font-size: 0.85rem; font-weight: 500; color: var(--text-muted);
            }
            .filter-group input, .filter-group select {
                padding: 8px 12px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-body); color: var(--text-color);
            }
            .filter-group option {
                background-color: var(--bg-card, #1e293b);
                color: var(--text-color, #fff);
            }
            .skeleton-row td {
                padding: 15px; border-bottom: 1px solid var(--border-color);
            }
            .skeleton-box {
                height: 20px; background: linear-gradient(90deg, var(--border-color) 25%, var(--bg-body) 50%, var(--border-color) 75%);
                background-size: 200% 100%; animation: loading 1.5s infinite; border-radius: 4px;
            }
            @keyframes loading {
                0% { background-position: 200% 0; }
                100% { background-position: -200% 0; }
            }
            .btn-action {
                padding: 4px 10px; font-size: 0.85rem; background: var(--primary-color, #4f46e5); color: white; border: none; border-radius: 4px; cursor: pointer;
            }
            .btn-action:hover { opacity: 0.9; }

            /* Modal Styles */
            .modal-overlay {
                position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); 
                backdrop-filter: blur(8px); z-index: 9999; display: none; align-items: center; justify-content: center;
                opacity: 0; transition: opacity 0.3s ease; pointer-events: none;
            }
            .modal-overlay.active { display: flex; opacity: 1; pointer-events: auto; }
            .modal-glass {
                background: rgba(30, 41, 59, 0.85); backdrop-filter: blur(20px);
                border: 1px solid rgba(255,255,255,0.15); border-radius: 12px;
                width: 90%; max-width: 500px; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
                transform: translateY(20px); transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            }
            .modal-overlay.active .modal-glass { transform: translateY(0); }
            .modal-header { padding: 20px; border-bottom: 1px solid rgba(255,255,255,0.1); display: flex; justify-content: space-between; align-items: center; }
            .modal-header h2 { margin: 0; font-size: 1.25rem; color: #fff; }
            .btn-close { background: none; border: none; color: #94a3b8; font-size: 1.5rem; cursor: pointer; padding: 0; }
            .btn-close:hover { color: #fff; }
            .modal-body { padding: 20px; color: #cbd5e1; }
            .invoice-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 25px; }
            .invoice-meta .label { color: #64748b; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.5px; display: block; margin-bottom: 4px;}
            .invoice-meta strong { color: #f8fafc; font-size: 1rem; }
            .invoice-breakdown { background: rgba(15, 23, 42, 0.6); padding: 20px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.05); }
            .breakdown-row { display: flex; justify-content: space-between; margin-bottom: 12px; font-size: 0.95rem; }
            .breakdown-row:last-child { margin-bottom: 0; }
            .breakdown-row.text-success { color: #10b981; }
            .breakdown-row.final-total { font-size: 1.25rem; font-weight: 700; color: #fff; margin-top: 15px; }
            .modal-footer { padding: 15px 20px; border-top: 1px solid rgba(255,255,255,0.1); display: flex; justify-content: flex-end; gap: 12px; }
            .btn-secondary { background: rgba(255,255,255,0.1); color: #fff; border: 1px solid rgba(255,255,255,0.2); }
            .btn-secondary:hover { background: rgba(255,255,255,0.2); }
        </style>
        
        <div class="page-header">
            <h1>Hóa Đơn Doanh Thu</h1>
            <p>Báo cáo doanh số giao dịch thanh toán của khách hàng.</p>
        </div>
        
        <div class="filter-toolbar">
            <div class="filter-group">
                <label>Tìm kiếm (Mã HĐ, Tên, Phòng)</label>
                <input type="text" id="filter-keyword" placeholder="Nhập từ khóa...">
            </div>
            <div class="filter-group">
                <label>Trạng thái</label>
                <select id="filter-status">
                    <option value="all">Tất cả</option>
                    <option value="paid">Đã thanh toán</option>
                    <option value="pending">Chờ thanh toán</option>
                    <option value="cancelled">Đã hủy</option>
                </select>
            </div>
            <div class="filter-group">
                <label>Từ ngày</label>
                <input type="date" id="filter-from-date">
            </div>
            <div class="filter-group">
                <label>Đến ngày</label>
                <input type="date" id="filter-to-date">
            </div>
            <div class="filter-group" style="flex: 0; justify-content: flex-end;">
                <button id="btn-search" class="btn" style="padding: 9px 20px; white-space: nowrap;">Lọc dữ liệu</button>
            </div>
        </div>

        <div class="card">
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Hóa đơn ID</th>
                            <th>Mã phòng</th>
                            <th>Khách hàng</th>
                            <th>Tổng tiền</th>
                            <th>Ngày xuất</th>
                            <th style="text-align: center;">Hành động</th>
                        </tr>
                    </thead>
                    <tbody id="staff-payments-body">
                        <!-- Content loaded here -->
                    </tbody>
                </table>
            </div>
            <div class="pagination" style="display: flex; justify-content: space-between; padding: 15px; align-items: center; border-top: 1px solid var(--border-color, #eee);">
                <button id="btn-prev-page" class="btn" disabled style="padding: 5px 15px; cursor: pointer;">Trang trước</button>
                <span id="page-info" style="font-weight: 500;">Trang 1</span>
                <button id="btn-next-page" class="btn" style="padding: 5px 15px; cursor: pointer;">Trang tiếp</button>
            </div>
        </div>

        <!-- Modal Overlay -->
        <div id="payment-modal-overlay" class="modal-overlay">
            <div class="modal-glass">
                <div class="modal-header">
                    <h2 id="modal-title">Chi Tiết Hóa Đơn</h2>
                    <button class="btn-close" id="btn-close-modal">&times;</button>
                </div>
                <div class="modal-body">
                    <div class="invoice-meta">
                        <div><span class="label">Khách hàng</span> <strong id="md-customer"></strong></div>
                        <div><span class="label">Phòng</span> <strong id="md-room"></strong></div>
                        <div><span class="label">Mã đơn thuê</span> <strong id="md-booking"></strong></div>
                        <div><span class="label">Trạng thái</span> <strong id="md-status"></strong></div>
                        <div style="grid-column: span 2;"><span class="label">Ngày xuất</span> <strong id="md-date"></strong></div>
                    </div>
                    <div class="invoice-breakdown">
                        <div class="breakdown-row"><span>Tiền phòng</span><strong id="md-room-fee"></strong></div>
                        <div class="breakdown-row"><span>Tiền dịch vụ</span><strong id="md-service-fee"></strong></div>
                        <div class="breakdown-row text-success"><span>Khuyến mãi</span><strong id="md-discount"></strong></div>
                        <div class="breakdown-row"><span>Thuế VAT</span><strong id="md-tax"></strong></div>
                        <hr style="border:0; border-top:1px dashed rgba(255,255,255,0.2); margin:15px 0;">
                        <div class="breakdown-row final-total"><span>TỔNG THANH TOÁN</span><span id="md-final-total"></span></div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-secondary" id="btn-close-modal-2">Đóng</button>
                    <button class="btn btn-primary" id="btn-print-pdf-real">In Hóa Đơn PDF</button>
                </div>
            </div>
        </div>
    `;

    const body = document.getElementById("staff-payments-body");
    const btnPrev = document.getElementById("btn-prev-page");
    const btnNext = document.getElementById("btn-next-page");
    const pageInfo = document.getElementById("page-info");
    const btnSearch = document.getElementById("btn-search");
    
    const modalOverlay = document.getElementById("payment-modal-overlay");
    const btnCloseModal1 = document.getElementById("btn-close-modal");
    const btnCloseModal2 = document.getElementById("btn-close-modal-2");
    const btnPrintPdf = document.getElementById("btn-print-pdf-real");
    
    let currentModalInvoiceId = null;

    btnPrintPdf.addEventListener("click", () => {
        if (currentModalInvoiceId) {
            window.open(getApiBaseUrl() + "/invoices/" + currentModalInvoiceId + "/pdf", "_blank");
        }
    });

    const getFilters = () => {
        return {
            keyword: document.getElementById("filter-keyword").value.trim(),
            status: document.getElementById("filter-status").value,
            fromDate: document.getElementById("filter-from-date").value,
            toDate: document.getElementById("filter-to-date").value
        };
    };

    const formatCurrency = (amount) => {
        return amount != null ? Number(amount).toLocaleString('vi-VN') + ' đ' : '0 đ';
    };

    const closeModal = () => {
        modalOverlay.classList.remove('active');
        setTimeout(() => modalOverlay.style.display = 'none', 300);
    };

    btnCloseModal1.addEventListener('click', closeModal);
    btnCloseModal2.addEventListener('click', closeModal);
    modalOverlay.addEventListener('click', (e) => {
        if (e.target === modalOverlay) closeModal();
    });

    const openModal = (invoiceId) => {
        const inv = currentInvoices.find(i => i.id == invoiceId);
        if (!inv) return;

        document.getElementById('modal-title').textContent = `Hóa Đơn #${inv.id}`;
        document.getElementById('md-customer').textContent = inv.customerName || 'Khách vãng lai';
        document.getElementById('md-room').textContent = `#${inv.roomNumber || 'N/A'}`;
        document.getElementById('md-booking').textContent = `#${inv.bookingId}`;
        
        let statusText = "Khác";
        if (inv.status === 'paid') statusText = "Đã thanh toán";
        else if (inv.status === 'pending') statusText = "Chờ thanh toán";
        else if (inv.status === 'cancelled') statusText = "Đã hủy";
        document.getElementById('md-status').textContent = statusText;

        document.getElementById('md-date').textContent = inv.issueDate ? new Date(inv.issueDate).toLocaleString('vi-VN') : 'N/A';
        
        document.getElementById('md-room-fee').textContent = formatCurrency(inv.totalRoomFee);
        document.getElementById('md-service-fee').textContent = formatCurrency(inv.totalServiceFee);
        document.getElementById('md-discount').textContent = "-" + formatCurrency(inv.discount);
        document.getElementById('md-tax').textContent = formatCurrency(inv.taxAmount);
        document.getElementById('md-final-total').textContent = formatCurrency(inv.finalTotal);

        currentModalInvoiceId = inv.id;
        modalOverlay.style.display = 'flex';
        requestAnimationFrame(() => modalOverlay.classList.add('active'));
    };

    const showSkeleton = () => {
        let skeletonHTML = "";
        for (let i = 0; i < 5; i++) {
            skeletonHTML += `
                <tr class="skeleton-row">
                    <td><div class="skeleton-box" style="width: 50px;"></div></td>
                    <td><div class="skeleton-box" style="width: 80px;"></div></td>
                    <td><div class="skeleton-box" style="width: 150px;"></div></td>
                    <td><div class="skeleton-box" style="width: 120px;"></div></td>
                    <td><div class="skeleton-box" style="width: 100px;"></div></td>
                    <td><div class="skeleton-box" style="width: 80px; margin: auto;"></div></td>
                </tr>
            `;
        }
        body.innerHTML = skeletonHTML;
    };

    const loadData = async () => {
        showSkeleton();
        try {
            const filters = getFilters();
            let query = `?page=${currentPage}&limit=${limit}`;
            if (filters.keyword) query += `&keyword=${encodeURIComponent(filters.keyword)}`;
            if (filters.status && filters.status !== 'all') query += `&status=${encodeURIComponent(filters.status)}`;
            if (filters.fromDate) query += `&fromDate=${encodeURIComponent(filters.fromDate)}`;
            if (filters.toDate) query += `&toDate=${encodeURIComponent(filters.toDate)}`;

            const response = await api.get(`/invoices${query}`);
            currentInvoices = response || [];
            
            body.innerHTML = "";
            if (currentInvoices.length === 0) {
                body.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 20px;">Không tìm thấy hóa đơn nào.</td></tr>`;
                btnNext.disabled = true;
                return;
            }

            currentInvoices.forEach(i => {
                const tr = document.createElement("tr");
                
                let statusBadge = "";
                if (i.status === 'paid') statusBadge = `<span class="badge" style="background: rgba(16,185,129,0.1); color: #10b981; padding: 4px 8px; border-radius: 4px; font-size: 0.75rem;">Đã thanh toán</span>`;
                else if (i.status === 'pending') statusBadge = `<span class="badge" style="background: rgba(245,158,11,0.1); color: #f59e0b; padding: 4px 8px; border-radius: 4px; font-size: 0.75rem;">Chờ thanh toán</span>`;
                else statusBadge = `<span class="badge" style="background: rgba(239,68,68,0.1); color: #ef4444; padding: 4px 8px; border-radius: 4px; font-size: 0.75rem;">Đã hủy</span>`;
                
                const dateStr = i.issueDate ? new Date(i.issueDate).toLocaleDateString('vi-VN') : 'N/A';

                tr.innerHTML = `
                    <td><strong>#${i.id}</strong><br><span style="font-size:0.75rem; color:var(--text-muted);">Đơn #${i.bookingId}</span></td>
                    <td><strong>${i.roomNumber || 'N/A'}</strong></td>
                    <td>${i.customerName || 'Khách vãng lai'}</td>
                    <td style="font-weight: 600; color: var(--success);">${formatCurrency(i.finalTotal)}</td>
                    <td>${dateStr}<br>${statusBadge}</td>
                    <td style="text-align: center;">
                        <button class="btn-action btn-view-invoice" data-id="${i.id}">Chi tiết</button>
                    </td>
                `;
                body.appendChild(tr);
            });

            btnNext.disabled = currentInvoices.length < limit;

            document.querySelectorAll('.btn-view-invoice').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    openModal(e.target.dataset.id);
                });
            });

        } catch (e) {
            console.error(e);
            body.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--danger);">Lỗi khi tải dữ liệu: ${e.message}</td></tr>`;
        }
    };

    btnSearch.addEventListener("click", () => {
        currentPage = 1;
        pageInfo.textContent = `Trang ${currentPage}`;
        btnPrev.disabled = true;
        loadData();
    });

    btnPrev.addEventListener("click", () => {
        if (currentPage > 1) {
            currentPage--;
            pageInfo.textContent = `Trang ${currentPage}`;
            btnPrev.disabled = currentPage === 1;
            loadData();
        }
    });

    btnNext.addEventListener("click", () => {
        currentPage++;
        pageInfo.textContent = `Trang ${currentPage}`;
        btnPrev.disabled = false;
        loadData();
    });

    // Initial Load
    loadData();
}
