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
    content = content.replace(
        '<div class="card" style="margin-top: 0; padding: 0;">\n            <table class="table"',
        '<div class="card" style="margin-top: 0; padding: 0;">\n            <div class="table-container" style="overflow-x: hidden;">\n                <table class="table"'
    )
    
    content = content.replace(
        '</table>\n        </div>',
        '</table>\n            </div>\n            <div id="pagination-invoices"></div>\n        </div>'
    )

    # Remove old pagination HTML
    content = re.sub(r'<div class="flex-row" style="justify-content: flex-end; margin-top: 15px;">\s*<div class="flex-row" style="gap: 15px;">\s*<button id="btn-prev-page".*?</button>\s*</div>\s*</div>', '', content, flags=re.DOTALL)

    # 4. Orange color for invoice IDs (#ID). 
    content = re.sub(r'<td[^>]*>#\$\{i\.id\}</td>', r'<td><strong style="color: var(--warning);">#${i.id}</strong></td>', content)

    # 5. Fix Javascript for KPIs and client-side pagination
    # We will replace the whole loadData logic with a complete client-side load/filter/paginate logic
    # In voices.js, it's currently:
    """
    let currentInvoices = [];
    let currentPage = 1;
    let limit = 5;

    const loadData = async () => {
        showSkeleton();
        try {
            const filters = getFilters();
            let query = `?page=${currentPage}&limit=${limit}`;
            ...
            const response = await api.get(`/invoices${query}`);
    """
    # Wait, instead of rewriting python for complex JS replacement, I'll write the script to stop here
    # and then I'll manually modify the JS logic using replace_file_content or by writing a more targeted replace script.

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

process_file('web-ui/public/js/admin/invoices.js', 'admin')
process_file('web-ui/public/js/staff/invoices.js', 'staff')
