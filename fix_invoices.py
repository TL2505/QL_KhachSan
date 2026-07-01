import re
import subprocess

def fix_file(file_path, prefix):
    # Get original content
    result = subprocess.run(['git', 'show', f'HEAD~1:{file_path}'], capture_output=True)
    old_content = result.stdout.decode('utf-8')
    
    match = re.search(r'(import .*?export async function renderInvoices[^{]*{\s*container\.innerHTML = `.*?`;\s*)', old_content, re.DOTALL)
    if not match:
        print(f'Could not extract HTML from {file_path}')
        return
        
    html_part = match.group(1)
    
    summary_bar_html = f'''
        <!-- Summary bar -->
        <div id="{prefix}-invoices-summary" style="display:flex;gap:16px;margin-bottom:24px;"></div>
'''
    html_part = html_part.replace('<!-- Lọc dữ liệu -->', summary_bar_html + '        <!-- Lọc dữ liệu -->')
    
    html_part = re.sub(
        r'(<button id="btn-search" class="btn btn-primary"[^>]*>.*?Lọc dữ liệu\s*</button>)',
        r'\1\n                    <button id="btn-refresh" class="btn btn-secondary" style="height: 42px; padding: 10px 14px; display: flex; align-items: center; justify-content: center;" title="Làm mới"><i data-lucide="refresh-cw" style="width: 16px; height: 16px;"></i></button>',
        html_part,
        flags=re.DOTALL
    )
    html_part = re.sub(
        r'(<div style="flex-shrink: 0;">\s*<button id="btn-search")',
        r'<div style="flex-shrink: 0; display: flex; gap: 8px;">\n                    <button id="btn-search"',
        html_part
    )
    html_part = html_part.replace(
        '<div class="card" style="margin-top: 0; padding: 0; overflow: hidden;">',
        '<div class="card" style="margin-top: 0; padding: 0;">\n            <div class="table-container" style="overflow-x: hidden;">'
    )
    html_part = html_part.replace(
        '<div class="card" style="margin-top: 0; padding: 0;">\n            <table class="table"',
        '<div class="card" style="margin-top: 0; padding: 0;">\n            <div class="table-container" style="overflow-x: hidden;">\n                <table class="table"'
    )
    html_part = html_part.replace(
        '</table>\n        </div>',
        '</table>\n            </div>\n            <div id="pagination-invoices"></div>\n        </div>'
    )
    html_part = re.sub(r'<div class="flex-row" style="justify-content: flex-end; margin-top: 15px;">\s*<div class="flex-row" style="gap: 15px;">\s*<button id="btn-prev-page".*?</button>\s*</div>\s*</div>', '', html_part, flags=re.DOTALL)
    html_part = re.sub(r'<td[^>]*>#\$\{i\.id\}</td>', r'<td><strong style="color: var(--warning);">#${i.id}</strong></td>', html_part)

    html_part += '    lucide.createIcons();\n\n'

    with open(file_path, 'r', encoding='utf-8') as f:
        new_content = f.read()

    idx = new_content.find('    let allInvoices = [];')
    if idx == -1:
        idx = new_content.find('        let allInvoices = [];')
        if idx == -1:
            print(f'Could not find allInvoices in {file_path}')
            return
            
    rest_of_file = new_content[idx:]
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(html_part + rest_of_file)
        
    print(f'Successfully fixed {file_path}')

fix_file('web-ui/public/js/admin/invoices.js', 'admin')
fix_file('web-ui/public/js/staff/invoices.js', 'staff')
