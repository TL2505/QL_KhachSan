import { api, getApiBaseUrl } from "../../api.js";

let currentPage = 1;
const limit = 10;
let currentInvoices = [];

export async function renderInvoices(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Tra Cứu Hóa Đơn</h1>
            <p>Hồ sơ chứng từ lưu trữ các giao dịch thanh toán thành công.</p>
        </div>
        
        <!-- Lọc dữ liệu -->
        <div class="card" style="padding: 20px; margin-bottom: 24px;">
            <div style="display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-end;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Tìm kiếm (Mã HĐ, Tên, Phòng)</label>
                    <div style="position: relative;">
                        <i data-lucide="search" style="width: 16px; height: 16px; position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); pointer-events: none;"></i>
                        <input id="filter-keyword" class="form-input" type="text" placeholder="Nhập từ khóa..." style="padding-left: 38px; width: 100%; margin: 0;">
                    </div>
                </div>
                <div style="flex: 1; min-width: 150px;">
                    <label style="display: block; margin-bottom: 8px; font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Trạng thái</label>
                    <div style="position: relative;">
                        <select id="filter-status" class="form-input" style="width: 100%; margin: 0; appearance: none; padding-right: 36px;">
                            <option value="all">Tất cả</option>
                            <option value="paid">Đã thanh toán</option>
                            <option value="pending">Chờ thanh toán</option>
                            <option value="cancelled">Đã hủy</option>
                        </select>
                        <i data-lucide="chevron-down" style="width: 16px; height: 16px; position: absolute; right: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); pointer-events: none;"></i>
                    </div>
                </div>
                <div style="flex: 1; min-width: 140px;">
                    <label style="display: block; margin-bottom: 8px; font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Từ ngày</label>
                    <div style="position: relative;">
                        <i data-lucide="calendar" style="width: 16px; height: 16px; position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); pointer-events: none;"></i>
                        <input type="date" id="filter-from-date" class="form-input" style="width: 100%; margin: 0; padding-left: 38px;">
                    </div>
                </div>
                <div style="flex: 1; min-width: 140px;">
                    <label style="display: block; margin-bottom: 8px; font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Đến ngày</label>
                    <div style="position: relative;">
                        <i data-lucide="calendar" style="width: 16px; height: 16px; position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted); pointer-events: none;"></i>
                        <input type="date" id="filter-to-date" class="form-input" style="width: 100%; margin: 0; padding-left: 38px;">
                    </div>
                </div>
                <div style="flex-shrink: 0;">
                    <button id="btn-search" class="btn btn-primary" style="padding: 10px 20px; display: flex; align-items: center; gap: 8px; height: 42px;">
                        <i data-lucide="filter" style="width: 16px; height: 16px;"></i> Lọc dữ liệu
                    </button>
                </div>
            </div>
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
                            <th style="text-align: center;">Hành động</th>
                        </tr>
                    </thead>
                    <tbody id="admin-invoices-body">
                        <!-- Content will be loaded here -->
                    </tbody>
                </table>
            </div>
            <div class="pagination" style="display: flex; justify-content: space-between; padding: 15px; align-items: center; border-top: 1px solid var(--border-color, #eee);">
                <button id="btn-prev-page" class="btn" disabled style="padding: 5px 15px; cursor: pointer;">Trang trước</button>
                <span id="page-info" style="font-weight: 500;">Trang 1</span>
                <button id="btn-next-page" class="btn" style="padding: 5px 15px; cursor: pointer;">Trang tiếp</button>
            </div>
        </div>

    `;
    
    const body = document.getElementById("admin-invoices-body");
    const btnPrev = document.getElementById("btn-prev-page");
    const btnNext = document.getElementById("btn-next-page");
    const pageInfo = document.getElementById("page-info");
    const btnSearch = document.getElementById("btn-search");
    
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

    const openModal = (invoiceId) => {
        const inv = currentInvoices.find(i => i.id == invoiceId);
        if (!inv) return;
        
        const modal = document.getElementById('global-modal');
        const content = document.getElementById('global-modal-content');
        
        let statusText = 'Khác';
        if (inv.status === 'paid') statusText = 'Đã thanh toán';
        else if (inv.status === 'pending') statusText = 'Chờ thanh toán';
        else if (inv.status === 'cancelled') statusText = 'Đã hủy';

        const dateStr = inv.issueDate ? new Date(inv.issueDate).toLocaleString('vi-VN') : 'N/A';

        content.innerHTML = `
            <div class="modal-header">
                <h2>Chi Tiết Hóa Đơn #${inv.id}</h2>
                <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
            </div>
            <div class="modal-body" style="padding-top:10px;">
                <div class="grid grid-cols-2" style="gap:15px; margin-bottom:25px;">
                    <div><span style="color:var(--text-muted);font-size:12px;text-transform:uppercase;">Khách hàng</span><br><strong>${inv.customerName || 'Khách vãng lai'}</strong></div>
                    <div><span style="color:var(--text-muted);font-size:12px;text-transform:uppercase;">Phòng</span><br><strong>#${inv.roomNumber || 'N/A'}</strong></div>
                    <div><span style="color:var(--text-muted);font-size:12px;text-transform:uppercase;">Mã đơn thuê</span><br><strong>#${inv.bookingId}</strong></div>
                    <div><span style="color:var(--text-muted);font-size:12px;text-transform:uppercase;">Trạng thái</span><br><strong>${statusText}</strong></div>
                    <div style="grid-column: span 2;"><span style="color:var(--text-muted);font-size:12px;text-transform:uppercase;">Ngày xuất</span><br><strong>${dateStr}</strong></div>
                </div>
                <div style="background:var(--bg-input); padding:20px; border-radius:8px; border:1px solid var(--border);">
                    <div class="flex-row" style="justify-content:space-between; margin-bottom:12px; font-size:14px;"><span>Tiền phòng</span><strong>${formatCurrency(inv.totalRoomFee)}</strong></div>
                    <div class="flex-row" style="justify-content:space-between; margin-bottom:12px; font-size:14px;"><span>Tiền dịch vụ</span><strong>${formatCurrency(inv.totalServiceFee)}</strong></div>
                    <div class="flex-row" style="justify-content:space-between; margin-bottom:12px; font-size:14px; color:var(--success);"><span>Khuyến mãi</span><strong>-${formatCurrency(inv.discount)}</strong></div>
                    <div class="flex-row" style="justify-content:space-between; margin-bottom:12px; font-size:14px;"><span>Thuế VAT</span><strong>${formatCurrency(inv.taxAmount)}</strong></div>
                    <hr style="border:0; border-top:1px dashed var(--border); margin:15px 0;">
                    <div class="flex-row" style="justify-content:space-between; font-size:18px; font-weight:700;"><span>TỔNG THANH TOÁN</span><span>${formatCurrency(inv.finalTotal)}</span></div>
                </div>
            </div>
            <div class="flex-row" style="justify-content:flex-end; margin-top:20px;">
                <button class="btn btn-secondary" onclick="closeModal('global-modal')">Đóng</button>
                <button class="btn btn-primary" id="btn-print-pdf-real">
                    <i data-lucide="printer" style="width:16px;height:16px;"></i> In Hóa Đơn PDF
                </button>
            </div>
        `;
        if(window.lucide) lucide.createIcons();
        modal.classList.add('active');
        
        document.getElementById('btn-print-pdf-real').addEventListener('click', () => {
            window.open(getApiBaseUrl() + '/invoices/' + inv.id + '/pdf', '_blank');
        });
    };

    const showSkeleton = () => {
        let skeletonHTML = "";
        for (let i = 0; i < 5; i++) {
            skeletonHTML += `
                <tr class="skeleton-row">
                    <td><div class="skeleton-block" style="width: 50px; height: 20px; border-radius: 4px;"></div></td>
                    <td><div class="skeleton-block" style="width: 80px; height: 20px; border-radius: 4px;"></div></td>
                    <td><div class="skeleton-block" style="width: 100px; height: 20px; border-radius: 4px;"></div></td>
                    <td><div class="skeleton-block" style="width: 150px; height: 20px; border-radius: 4px;"></div></td>
                    <td><div class="skeleton-block" style="width: 120px; height: 20px; border-radius: 4px;"></div></td>
                    <td><div class="skeleton-block" style="width: 100px; height: 20px; border-radius: 4px;"></div></td>
                    <td><div class="skeleton-block" style="width: 32px; height: 32px; margin: auto; border-radius: 6px;"></div></td>
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
            if (filters.status) query += `&status=${encodeURIComponent(filters.status)}`;
            if (filters.fromDate) query += `&fromDate=${encodeURIComponent(filters.fromDate)}`;
            if (filters.toDate) query += `&toDate=${encodeURIComponent(filters.toDate)}`;

            currentInvoices = await api.get(`/invoices${query}`);
            body.innerHTML = "";
            if (!currentInvoices || currentInvoices.length === 0) {
                body.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 30px;">Không tìm thấy bản ghi hóa đơn nào phù hợp.</td></tr>`;
                btnNext.disabled = true;
                return;
            }

            btnNext.disabled = currentInvoices.length < limit;

            currentInvoices.forEach(i => {
                const tr = document.createElement("tr");
                const dateStr = i.issueDate ? new Date(i.issueDate).toLocaleString('vi-VN') : 'N/A';
                const amountStr = formatCurrency(i.finalTotal);
                
                let statusColor = "var(--text-muted)";
                let statusText = "Khác";
                if (i.status === 'paid') { statusColor = "var(--success)"; statusText = "Đã thanh toán"; }
                else if (i.status === 'pending') { statusColor = "var(--warning, #f59e0b)"; statusText = "Chờ thanh toán"; }
                else if (i.status === 'cancelled') { statusColor = "var(--danger, #ef4444)"; statusText = "Đã hủy"; }

                tr.innerHTML = `
                    <td>#${i.id}</td>
                    <td>Đơn thuê #${i.bookingId}</td>
                    <td><strong>Phòng #${i.roomNumber || 'N/A'}</strong></td>
                    <td>${i.customerName || 'Khách vãng lai'}</td>
                    <td>
                        <div style="font-weight: 600; color: ${statusColor};">${amountStr}</div>
                        <div style="font-size: 0.8rem; color: var(--text-muted);">${statusText}</div>
                    </td>
                    <td>${dateStr}</td>
                    <td style="text-align: center;">
                        <button class="btn-icon btn-detail" data-id="${i.id}" title="Chi tiết"><i data-lucide="file-text" style="width:14px;height:14px;pointer-events:none;"></i></button>
                    </td>
                `;
                body.appendChild(tr);
            });
            if(window.lucide) lucide.createIcons();

            document.querySelectorAll('.btn-detail').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    openModal(e.target.getAttribute('data-id'));
                });
            });

        } catch (e) {
            console.error(e);
            body.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #ff4d4f; padding: 30px;">Lỗi kết nối đến máy chủ. Vui lòng thử lại sau.</td></tr>`;
        }
    };

    btnSearch.addEventListener('click', () => {
        currentPage = 1;
        pageInfo.textContent = `Trang ${currentPage}`;
        btnPrev.disabled = true;
        loadData();
    });

    btnPrev.addEventListener('click', () => {
        if (currentPage > 1) {
            currentPage--;
            pageInfo.textContent = `Trang ${currentPage}`;
            btnPrev.disabled = currentPage === 1;
            loadData();
        }
    });

    btnNext.addEventListener('click', () => {
        currentPage++;
        pageInfo.textContent = `Trang ${currentPage}`;
        btnPrev.disabled = false;
        loadData();
    });

    currentPage = 1;
    btnPrev.disabled = true;
    pageInfo.textContent = `Trang ${currentPage}`;
    loadData();
}
