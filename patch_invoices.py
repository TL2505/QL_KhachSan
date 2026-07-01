import re

def process_file(filepath, prefix):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Add Summary Bar below the filter toolbar
    summary_bar_html = f'''
        <!-- Summary bar -->
        <div id="{prefix}-invoices-summary" style="display:flex;gap:16px;margin-bottom:24px;"></div>
'''
    content = content.replace('<!-- Lọc dữ liệu -->', summary_bar_html + '        <!-- Lọc dữ liệu -->')

    # 2. Add sync button to the filter row
    content = re.sub(
        r'(<button id="btn-search" class="btn btn-primary"[^>]*>.*?Lọc dữ liệu\s*</button>)',
        r'\1\n                    <button id="btn-refresh" class="btn btn-secondary" style="height: 42px; padding: 10px 14px; display: flex; align-items: center; justify-content: center;" title="Làm mới"><i data-lucide="refresh-cw" style="width: 16px; height: 16px;"></i></button>',
        content,
        flags=re.DOTALL
    )
    content = re.sub(
        r'(<div style="flex-shrink: 0;">\s*<button id="btn-search")',
        r'<div style="flex-shrink: 0; display: flex; gap: 8px;">\n                    <button id="btn-search"',
        content
    )

    # 3. Modify table container to hide scrollbar
    content = content.replace(
        '<div class="card" style="margin-top: 0; padding: 0; overflow: hidden;">',
        '<div class="card" style="margin-top: 0; padding: 0;">\n            <div class="table-container" style="overflow-x: hidden;">'
    )
    # for staff which doesn't have overflow: hidden
    content = content.replace(
        '<div class="card" style="margin-top: 0; padding: 0;">\n            <table class="table"',
        '<div class="card" style="margin-top: 0; padding: 0;">\n            <div class="table-container" style="overflow-x: hidden;">\n                <table class="table"'
    )
    
    content = content.replace(
        '</table>\n        </div>',
        '</table>\n            </div>\n            <div id="pagination-invoices"></div>\n        </div>'
    )

    content = content.replace(
        '</table>\n            </div>',
        '</table>\n            </div>\n            <div id="pagination-invoices"></div>'
    )

    # Remove old pagination HTML
    content = re.sub(r'<div class="flex-row"[^>]*>\s*<div class="flex-row"[^>]*>\s*<button id="btn-prev-page".*?</button>\s*</div>\s*</div>', '', content, flags=re.DOTALL)
    content = re.sub(r'<div class="pagination".*?</button>\s*</div>', '', content, flags=re.DOTALL)

    # Remove the global variables: `let currentPage = 1; \n const limit = 10; \n let currentInvoices = [];`
    # We will redefine them inside `renderInvoices` to be scoped properly!
    content = re.sub(r'let currentPage = 1;\s*const limit = \d+;\s*let currentInvoices = \[\];', '', content)

    # Inject new variables right after `const btnSearch = document.getElementById("btn-search");`
    # Let's find `const btnSearch = document.getElementById("btn-search");`
    
    new_vars = f'''
    let allInvoices = [];
    let currentDisplayList = [];
    let currentPage = 1;
    let itemsPerPage = 10;

    const renderTable = () => {{
        const list = currentDisplayList;
        const body = document.getElementById("{prefix}-invoices-body") || document.getElementById("{prefix}-payments-body");
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
                if(typeof openModal === 'function') openModal(e.target.dataset.id);
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
'''
    
    # We find `const loadData = async () => {` and replace it entirely up to `loadData(); \n }`
    loadData_idx = content.find('const loadData = async () => {')
    if loadData_idx == -1:
        print("Error: Could not find loadData in", filepath)
        return
        
    head = content[:loadData_idx]

    new_loadData = f'''const loadData = async () => {{
        showSkeleton();
        try {{
            const response = await api.get("/invoices");
            allInvoices = response || [];
            
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
            const body = document.getElementById("{prefix}-invoices-body") || document.getElementById("{prefix}-payments-body");
            if (body) body.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--danger);">Lỗi khi tải dữ liệu: ${{e.message}}</td></tr>`;
        }}
    }};

    const btnSearch = document.getElementById("btn-search");
    if(btnSearch) btnSearch.addEventListener("click", applyFilters);

    // Xử lý refresh
    const containerEl = document.getElementById("{prefix}-invoices-summary")?.parentElement;
    if (containerEl) {{
        containerEl.addEventListener("click", (e) => {{
            const btnRefresh = e.target.closest("#btn-refresh");
            if (btnRefresh) {{
                const kw = document.getElementById("filter-keyword");
                const st = document.getElementById("filter-status");
                const fd = document.getElementById("filter-from-date");
                const td = document.getElementById("filter-to-date");
                if(kw) kw.value = "";
                if(st) st.value = "all";
                if(fd) fd.value = "";
                if(td) td.value = "";
                applyFilters();
            }}
        }});
    }}

    loadData();
}}
'''

    # Clean up head: remove `btnPrev`, `btnNext`, `pageInfo`
    head = re.sub(r'const\s+(btnPrev|btnNext|pageInfo)\s+=\s+document\.getElementById\([^)]+\);', '', head)

    # Inject new_vars right before `const getFilters`
    getFilters_idx = head.find('const getFilters = () => {')
    if getFilters_idx != -1:
        head = head[:getFilters_idx] + new_vars + head[getFilters_idx:]
    else:
        print("Error: Could not find getFilters in", filepath)
        return
        
    # Replace `currentInvoices.find` with `allInvoices.find` in `openModal`
    head = head.replace('currentInvoices.find', 'allInvoices.find')

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(head + new_loadData)

process_file('web-ui/public/js/admin/invoices.js', 'admin')
process_file('web-ui/public/js/staff/invoices.js', 'staff')
