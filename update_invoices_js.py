import re

def process_file(filepath, prefix):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    new_logic = f'''    let allInvoices = [];
    let currentDisplayList = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const renderTable = () => {{
        const list = currentDisplayList;
        body.innerHTML = "";
        if (!list || list.length === 0) {{
            body.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 20px;">Không tìm thấy hóa đơn nào.</td></tr>`;
            const paginationContainer = document.getElementById("pagination-invoices");
            if (paginationContainer) paginationContainer.innerHTML = '';
            return;
        }}

        const start = (currentPage - 1) * itemsPerPage;
        const end = start + itemsPerPage;
        const pageData = list.slice(start, end);

        pageData.forEach(i => {{
            const tr = document.createElement("tr");
            
            let statusBadge = "";
            if (i.status === 'paid') statusBadge = `<span class="badge" style="background: rgba(16,185,129,0.1); color: #10b981; padding: 4px 8px; border-radius: 4px; font-size: 0.75rem;">Đã thanh toán</span>`;
            else if (i.status === 'pending') statusBadge = `<span class="badge" style="background: rgba(245,158,11,0.1); color: #f59e0b; padding: 4px 8px; border-radius: 4px; font-size: 0.75rem;">Chờ thanh toán</span>`;
            else statusBadge = `<span class="badge" style="background: rgba(239,68,68,0.1); color: #ef4444; padding: 4px 8px; border-radius: 4px; font-size: 0.75rem;">Đã hủy</span>`;
            
            const dateStr = i.issueDate ? new Date(i.issueDate).toLocaleDateString('vi-VN') : 'N/A';
            const methodStr = (i.paymentMethod || 'CASH').toUpperCase();
            const idColHTML = `{prefix}` === 'admin' 
                ? `<td><strong style="color: var(--warning);">#${{i.id}}</strong></td>` 
                : `<td><strong style="color: var(--warning);">#${{i.id}}</strong><br><span style="font-size:0.75rem; color:var(--text-muted);">Đơn #${{i.bookingId}}</span></td>`;
            const bookingColHTML = `{prefix}` === 'admin' 
                ? `<td>Đơn thuê<br>#${{i.bookingId}}</td>` 
                : ``;
                
            tr.innerHTML = `
                ${{idColHTML}}
                ${{bookingColHTML}}
                <td><strong>${{i.roomNumber || 'Phòng undefined'}}</strong></td>
                <td>${{i.customerName || 'Khách vãng lai'}}</td>
                <td style="font-weight: 600; color: var(--success);">${{formatCurrency(i.finalTotal)}}<br><span style="font-size:0.75rem;color:var(--text-muted);font-weight:normal;">${{statusBadge}}</span></td>
                <td>${{dateStr}}<br><span style="font-size:0.75rem;color:var(--text-muted);">${{methodStr}}</span></td>
                <td style="text-align: center;">
                    <button class="btn-icon btn-view-invoice" data-id="${{i.id}}" title="Chi tiết"><i data-lucide="file-text" style="width:14px;height:14px;pointer-events:none;"></i></button>
                </td>
            `;
            body.appendChild(tr);
        }});
        if(window.lucide) lucide.createIcons();

        document.querySelectorAll('.btn-view-invoice').forEach(btn => {{
            btn.addEventListener('click', (e) => {{
                openModal(e.target.dataset.id);
            }});
        }});

        if (window.renderPaginationComponent) {{
            window.renderPaginationComponent(
                "pagination-invoices",
                list.length,
                itemsPerPage,
                currentPage,
                (newPage) => {{
                    currentPage = newPage;
                    renderTable();
                }},
                (newItemsPerPage) => {{
                    itemsPerPage = newItemsPerPage;
                    currentPage = 1;
                    renderTable();
                }}
            );
        }}
    }};

    const applyFilters = () => {{
        const filters = getFilters();
        currentDisplayList = allInvoices.filter(i => {{
            let match = true;
            if (filters.keyword) {{
                const kw = filters.keyword.toLowerCase();
                const idStr = String(i.id);
                const bkStr = String(i.bookingId);
                const rmStr = String(i.roomNumber || '').toLowerCase();
                const custStr = String(i.customerName || '').toLowerCase();
                if (!idStr.includes(kw) && !bkStr.includes(kw) && !rmStr.includes(kw) && !custStr.includes(kw)) {{
                    match = false;
                }}
            }}
            if (filters.status && filters.status !== 'all') {{
                if (i.status !== filters.status) match = false;
            }}
            if (filters.fromDate) {{
                if (new Date(i.issueDate) < new Date(filters.fromDate)) match = false;
            }}
            if (filters.toDate) {{
                if (new Date(i.issueDate) > new Date(filters.toDate + 'T23:59:59')) match = false;
            }}
            return match;
        }});
        currentPage = 1;
        renderTable();
    }};

    const loadData = async () => {{
        showSkeleton();
        try {{
            const response = await api.get("/invoices");
            allInvoices = response || [];
            
            // Lấy thông tin phòng và khách hàng để map vào hóa đơn
            try {{
                const [rooms, customers] = await Promise.all([
                    api.get("/rooms"),
                    api.get("/customers")
                ]);
                allInvoices.forEach(inv => {{
                    if (inv.roomId && !inv.roomNumber) {{
                        const r = rooms.find(room => room.id === inv.roomId);
                        if (r) inv.roomNumber = r.roomNumber;
                    }}
                    if (inv.customerId && !inv.customerName) {{
                        const c = customers.find(cust => cust.id === inv.customerId);
                        if (c) inv.customerName = c.fullName;
                    }}
                }});
            }} catch (e) {{ console.warn("Cannot fetch rooms/customers details", e); }}

            // Calculate KPIs
            const totalInvoices = allInvoices.length;
            const totalRevenue = allInvoices.filter(i => i.status === 'paid').reduce((sum, i) => sum + (i.finalTotal || 0), 0);
            const cashTotal = allInvoices.filter(i => i.status === 'paid' && (i.paymentMethod || '').toLowerCase().includes('cash')).reduce((sum, i) => sum + (i.finalTotal || 0), 0);
            const transferTotal = allInvoices.filter(i => i.status === 'paid' && !(i.paymentMethod || '').toLowerCase().includes('cash')).reduce((sum, i) => sum + (i.finalTotal || 0), 0);

            const summaryBar = document.getElementById("{prefix}-invoices-summary");
            if (summaryBar) {{
                summaryBar.innerHTML = `
                    <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--warning);">
                        <i data-lucide="file-text" style="width:18px;height:18px;color:var(--warning);"></i>
                        <div>
                            <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${{totalInvoices}}</div>
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tổng Hóa Đơn</div>
                        </div>
                    </div>
                    <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--success);">
                        <i data-lucide="banknote" style="width:18px;height:18px;color:var(--success);"></i>
                        <div>
                            <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${{formatCurrency(totalRevenue)}}</div>
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tổng Thu</div>
                        </div>
                    </div>
                    <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--primary);">
                        <i data-lucide="coins" style="width:18px;height:18px;color:var(--primary);"></i>
                        <div>
                            <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${{formatCurrency(cashTotal)}}</div>
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Tiền Mặt</div>
                        </div>
                    </div>
                    <div class="card" style="margin:0;flex:1;padding:14px 18px;display:flex;align-items:center;gap:10px;border-left:3px solid var(--info);">
                        <i data-lucide="credit-card" style="width:18px;height:18px;color:var(--info);"></i>
                        <div>
                            <div style="font-size:20px;font-weight:700;font-family:var(--font-heading);">${{formatCurrency(transferTotal)}}</div>
                            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;">Thẻ / CK</div>
                        </div>
                    </div>
                `;
                if(window.lucide) lucide.createIcons();
            }}

            applyFilters();
        }} catch (e) {{
            console.error(e);
            body.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--danger);">Lỗi khi tải dữ liệu: ${{e.message}}</td></tr>`;
        }}
    }};

    btnSearch.addEventListener("click", applyFilters);

    const btnRefresh = document.getElementById("btn-refresh");
    if (btnRefresh) {{
        btnRefresh.addEventListener("click", () => {{
            document.getElementById("filter-keyword").value = "";
            document.getElementById("filter-status").value = "all";
            document.getElementById("filter-from-date").value = "";
            document.getElementById("filter-to-date").value = "";
            applyFilters();
        }});
    }}'''

    # Replace from "let currentInvoices" until the end of btnNext.addEventListener
    pattern = r'let currentInvoices = \[\];.*?btnNext\.addEventListener[^}]+}\);'
    
    head = content[:content.find('let currentInvoices = [];')]
    head = head.replace('currentInvoices.find', 'allInvoices.find')

    content = re.sub(pattern, new_logic, content, flags=re.DOTALL)

    # Some variables like btnPrev, btnNext, pageInfo, btnSearch are still retrieved above but they don't exist anymore
    # (except btnSearch and btnRefresh). So we need to remove the dead variables.
    content = content.replace('const btnPrev = document.getElementById("btn-prev-page");', '')
    content = content.replace('const btnNext = document.getElementById("btn-next-page");', '')
    content = content.replace('const pageInfo = document.getElementById("page-info");', '')
    content = content.replace("const btnPrev = document.getElementById('btn-prev-page');", '')
    content = content.replace("const btnNext = document.getElementById('btn-next-page');", '')
    content = content.replace("const pageInfo = document.getElementById('page-info');", '')

    # Apply back to file
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

process_file('web-ui/public/js/admin/invoices.js', 'admin')
process_file('web-ui/public/js/staff/invoices.js', 'staff')
