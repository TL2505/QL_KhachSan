import { api, getApiBaseUrl } from "../../api.js";



export async function renderInvoices(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Tra Cứu Hóa Đơn</h1>
            <p>Hồ sơ chứng từ lưu trữ các giao dịch thanh toán thành công.</p>
        </div>
        
        
        <!-- Summary bar -->
        <div id="admin-invoices-summary" style="display:flex;gap:16px;margin-bottom:24px;"></div>
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
                <div style="flex-shrink: 0; display: flex; gap: 8px;">
                    <button id="btn-search" class="btn btn-primary" style="padding: 10px 20px; display: flex; align-items: center; gap: 8px; height: 42px;">
                        <i data-lucide="filter" style="width: 16px; height: 16px;"></i> Lọc dữ liệu
                    </button>
                    <button id="btn-refresh" class="btn btn-secondary" style="height: 42px; padding: 10px 14px; display: flex; align-items: center; justify-content: center;" title="Làm mới"><i data-lucide="refresh-cw" style="width: 16px; height: 16px;"></i></button>
                </div>
            </div>
        </div>

        <div class="card">
            <div class="table-container" style="overflow-x: hidden;">
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
            <div id="pagination-invoices"></div>
            
        </div>

    `;
    
    const body = document.getElementById("admin-invoices-body");
    
    
    
    
    
    
    let allInvoices = [];
    let currentDisplayList = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const renderTable = () => {
        const list = currentDisplayList;
        const tbody = document.getElementById("admin-invoices-body") || document.getElementById("admin-payments-body");
        tbody.innerHTML = "";
        if (!list || list.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 20px;">Không tìm thấy hóa đơn nào.</td></tr>`;
            const paginationContainer = document.getElementById("pagination-invoices");
            if (paginationContainer) paginationContainer.innerHTML = '';
            return;
        }

        const start = (currentPage - 1) * itemsPerPage;
        const end = start + itemsPerPage;
        const pageData = list.slice(start, end);

        pageData.forEach(i => {
            const tr = document.createElement("tr");
            
            let statusBadge = "";
            if (i.status === 'paid') statusBadge = `<span class="badge" style="background: rgba(16,185,129,0.1); color: #10b981; padding: 4px 8px; border-radius: 4px; font-size: 0.75rem;">Đã thanh toán</span>`;
            else if (i.status === 'pending') statusBadge = `<span class="badge" style="background: rgba(245,158,11,0.1); color: #f59e0b; padding: 4px 8px; border-radius: 4px; font-size: 0.75rem;">Chờ thanh toán</span>`;
            else statusBadge = `<span class="badge" style="background: rgba(239,68,68,0.1); color: #ef4444; padding: 4px 8px; border-radius: 4px; font-size: 0.75rem;">Đã hủy</span>`;
            
            const dateStr = i.issueDate ? new Date(i.issueDate).toLocaleDateString('vi-VN') : 'N/A';
            const methodStr = (i.paymentMethod || 'CASH').toUpperCase();
            
            const idColHTML = `admin` === 'admin' 
                ? `<td><strong style="color: var(--warning);">#${i.id}</strong></td>` 
                : `<td><strong style="color: var(--warning);">#${i.id}</strong><br><span style="font-size:0.75rem; color:var(--text-muted);">Đơn #${i.bookingId}</span></td>`;
            
            const bookingColHTML = `admin` === 'admin' 
                ? `<td>Đơn thuê<br>#${i.bookingId}</td>` 
                : ``;
                
            tr.innerHTML = `
                ${idColHTML}
                ${bookingColHTML}
                <td><strong>${i.roomNumber || 'Phòng undefined'}</strong></td>
                <td>${i.customerName || 'Khách vãng lai'}</td>
                <td style="font-weight: 600; color: var(--success);">${formatCurrency(i.finalTotal)}<br><span style="font-size:0.75rem;color:var(--text-muted);font-weight:normal;">${statusBadge}</span></td>
                <td>${dateStr}<br><span style="font-size:0.75rem;color:var(--text-muted);">${methodStr}</span></td>
                <td style="text-align: center;">
                    <button class="btn-icon btn-view-invoice" data-id="${i.id}" title="Chi tiết"><i data-lucide="file-text" style="width:14px;height:14px;pointer-events:none;"></i></button>
                </td>
            `;
            tbody.appendChild(tr);
        });
        if(window.lucide) lucide.createIcons();

        document.querySelectorAll('.btn-view-invoice').forEach(btn => {
            btn.addEventListener('click', (e) => {
                if(typeof openModal === 'function') openModal(e.target.dataset.id);
            });
        });

        if (window.renderPaginationComponent) {
            window.renderPaginationComponent(
                "pagination-invoices",
                list.length,
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

    const applyFilters = () => {
        const filters = getFilters();
        currentDisplayList = allInvoices.filter(i => {
            let match = true;
            if (filters.keyword) {
                const kw = filters.keyword.toLowerCase();
                const idStr = String(i.id);
                const bkStr = String(i.bookingId);
                const rmStr = String(i.roomNumber || '').toLowerCase();
                const custStr = String(i.customerName || '').toLowerCase();
                if (!idStr.includes(kw) && !bkStr.includes(kw) && !rmStr.includes(kw) && !custStr.includes(kw)) {
                    match = false;
                }
            }
            if (filters.status && filters.status !== 'all') {
                if (i.status !== filters.status) match = false;
            }
            if (filters.fromDate) {
                if (new Date(i.issueDate) < new Date(filters.fromDate)) match = false;
            }
            if (filters.toDate) {
                if (new Date(i.issueDate) > new Date(filters.toDate + 'T23:59:59')) match = false;
            }
            return match;
        });
        currentPage = 1;
        renderTable();
    };
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
        const inv = allInvoices.find(i => i.id == invoiceId);
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
            const response = await api.get("/invoices");
            allInvoices = response || [];
            
            try {
                const [rooms, customers] = await Promise.all([
                    api.get("/rooms"),
                    api.get("/customers")
                ]);
                allInvoices.forEach(inv => {
                    if (inv.roomId && !inv.roomNumber) {
                        const r = rooms.find(room => room.id === inv.roomId);
                        if (r) inv.roomNumber = r.roomNumber;
                    }
                    if (inv.customerId && !inv.customerName) {
                        const c = customers.find(cust => cust.id === inv.customerId);
                        if (c) inv.customerName = c.fullName;
                    }
                });
            } catch (e) { console.warn("Cannot fetch rooms/customers details", e); }

            // Calculate KPIs
            const totalInvoices = allInvoices.length;
            const totalRevenue = allInvoices.filter(i => i.status === 'paid').reduce((sum, i) => sum + (i.finalTotal || 0), 0);
            const cashTotal = allInvoices.filter(i => i.status === 'paid' && (i.paymentMethod || '').toLowerCase().includes('cash')).reduce((sum, i) => sum + (i.finalTotal || 0), 0);
            const transferTotal = allInvoices.filter(i => i.status === 'paid' && !(i.paymentMethod || '').toLowerCase().includes('cash')).reduce((sum, i) => sum + (i.finalTotal || 0), 0);

            const summaryBar = document.getElementById("admin-invoices-summary");
            if (summaryBar) {
                summaryBar.innerHTML = `
                    <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--warning);">
                        <i data-lucide="file-text" style="width:18px;height:18px;color:var(--warning);"></i>
                        <div>
                            <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${totalInvoices}</div>
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tổng Hóa Đơn</div>
                        </div>
                    </div>
                    <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--success);">
                        <i data-lucide="banknote" style="width:18px;height:18px;color:var(--success);"></i>
                        <div>
                            <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${formatCurrency(totalRevenue)}</div>
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tổng Thu</div>
                        </div>
                    </div>
                    <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--primary);">
                        <i data-lucide="coins" style="width:18px;height:18px;color:var(--primary);"></i>
                        <div>
                            <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${formatCurrency(cashTotal)}</div>
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tiền Mặt</div>
                        </div>
                    </div>
                    <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--info);">
                        <i data-lucide="credit-card" style="width:18px;height:18px;color:var(--info);"></i>
                        <div>
                            <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${formatCurrency(transferTotal)}</div>
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Thẻ / CK</div>
                        </div>
                    </div>
                `;
                if(window.lucide) lucide.createIcons();
            }

            applyFilters();
        } catch (e) {
            console.error(e);
            const errTbody = document.getElementById("admin-invoices-body") || document.getElementById("admin-payments-body");
            if (errTbody) errTbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--danger);">Lỗi khi tải dữ liệu: ${e.message}</td></tr>`;
        }
    };

    const btnSearch = document.getElementById("btn-search");
    if(btnSearch) btnSearch.addEventListener("click", applyFilters);

    // Xử lý refresh
    const containerEl = document.getElementById("admin-invoices-summary")?.parentElement;
    if (containerEl) {
        containerEl.addEventListener("click", (e) => {
            const btnRefresh = e.target.closest("#btn-refresh");
            if (btnRefresh) {
                const kw = document.getElementById("filter-keyword");
                const st = document.getElementById("filter-status");
                const fd = document.getElementById("filter-from-date");
                const td = document.getElementById("filter-to-date");
                if(kw) kw.value = "";
                if(st) st.value = "all";
                if(fd) fd.value = "";
                if(td) td.value = "";
                applyFilters();
            }
        });
    }

    loadData();
}
