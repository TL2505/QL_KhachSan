import { api } from '../../api.js';
import { escapeHtml as esc } from '../shared/ui.js';
import { renderPaginationComponent } from '../shared/pagination.js';
import { MEMBERSHIP_TIERS, getLegacyTierInfo, normalizeLoyaltyHistory } from '../customer/tier-helper.js';

const num = value => Number(value || 0).toLocaleString('vi-VN');
const date = value => {
    const parsed = new Date(String(value).replace(/[\u00a0\u202f]/g, ' '));
    return Number.isNaN(parsed.getTime()) ? String(value || '—') : parsed.toLocaleString('vi-VN');
};
const icon = name => `<i data-lucide="${name}" aria-hidden="true"></i>`;
const icons = () => window.lucide?.createIcons?.();
const tierOf = member => getLegacyTierInfo(member.totalLoyaltyPoints).name;
const badge = member => `<span class="badge badge-outline membership-tier membership-tier-${tierOf(member).toLowerCase()}">${tierOf(member)}</span>`;
const fold = value => String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase();

export async function renderAdminMembership(container, { readOnly = false } = {}) {
    container.innerHTML = `<section class="admin-membership" aria-label="Quản lý thành viên">
        <div class="page-header membership-heading"><div><h1>${readOnly ? 'Tra cứu thành viên' : 'Quản lý thành viên'}</h1><p>Theo dõi hạng thành viên, điểm thưởng và lịch sử tích lũy.</p></div><button class="btn" id="membership-refresh">${icon('refresh-cw')} Làm mới</button></div>
        <div class="membership-summary" id="membership-summary" aria-label="Thống kê thành viên"></div>
        <p id="membership-notice" role="status" aria-live="polite"></p>
        <section class="card membership-list" aria-label="Danh sách thành viên">
            <div class="membership-filters">
                <label class="membership-search"><span>Tìm thành viên</span><input class="input form-input" id="membership-search" type="search" placeholder="Tên, mã thành viên, điện thoại, email…"></label>
                <label><span>Hạng thành viên</span><select class="select form-input" id="membership-tier"><option value="">Tất cả hạng</option>${MEMBERSHIP_TIERS.map(t => `<option>${t.name}</option>`).join('')}</select></label>
                <label><span>Sắp xếp</span><select class="select form-input" id="membership-sort"><option value="total">Điểm tích lũy cao nhất</option><option value="balance">Điểm khả dụng cao nhất</option><option value="name">Tên A – Z</option></select></label>
            </div>
            <div class="table-container overflow-x-auto"><table class="table"><thead><tr><th>Thành viên</th><th>Liên hệ</th><th>Hạng</th><th>Điểm khả dụng</th><th>Tổng tích lũy</th><th>Thao tác</th></tr></thead><tbody id="membership-body"></tbody></table></div>
            <div id="pagination-membership"></div>
        </section>
        <section class="card membership-policy" aria-label="Quy tắc xếp hạng"><h3>${icon('award')} Quy tắc thành viên</h3><p>Mỗi 1.000 VNĐ chi tiêu được 1 điểm. Hạng dựa trên tổng điểm tích lũy; trừ hoặc đổi điểm không làm giảm hạng.</p><div>${MEMBERSHIP_TIERS.map(t => `<span><strong>${t.name}</strong> · từ ${num(t.minPoints)} điểm</span>`).join('')}</div></section>
        <dialog class="modal membership-dialog" id="membership-dialog" aria-labelledby="membership-dialog-title"><div class="modal-box"></div></dialog>
    </section>`;
    const root = container.querySelector('.admin-membership');
    const $ = selector => root.querySelector(selector);
    const dialog = $('#membership-dialog'), box = dialog.querySelector('.modal-box');
    let members = [], page = 1, pageSize = 10, loadVersion = 0, dialogVersion = 0, saving = false;
    dialog.addEventListener('cancel', e => { if (saving) e.preventDefault(); });
    dialog.addEventListener('close', () => { dialogVersion++; });

    function openDialog(title, content) {
        dialogVersion++;
        box.innerHTML = `<div class="membership-dialog-heading"><h2 id="membership-dialog-title">${title}</h2><form method="dialog" class="membership-dialog-close"><button class="btn btn-circle btn-sm" data-close aria-label="Đóng">${icon('x')}</button></form></div>${content}`;
        box.querySelectorAll('[data-close]').forEach(b => b.onclick = e => { e.preventDefault(); if (!saving) dialog.close(); });
        dialog.showModal();
        icons();
    }
    function renderSummary() {
        const values = [
            ['users', 'Tổng thành viên', members.length, 'Tất cả hồ sơ khách hàng'],
            ['award', 'Hạng Gold & VIP', members.filter(m => ['Gold', 'VIP'].includes(tierOf(m))).length, 'Thành viên hạng cao'],
            ['wallet', 'Điểm khả dụng', members.reduce((s, m) => s + Number(m.loyaltyPoints || 0), 0), 'Số dư điểm của thành viên'],
            ['trending-up', 'Tổng điểm tích lũy', members.reduce((s, m) => s + Number(m.totalLoyaltyPoints || 0), 0), 'Cơ sở xác định hạng']
        ];
        $('#membership-summary').innerHTML = values.map(([glyph, title, value, desc]) => `<div class="card"><div class="stat"><div class="stat-title">${icon(glyph)} ${title}</div><div class="stat-value">${num(value)}</div><div class="stat-desc">${desc}</div></div></div>`).join('');
        icons();
    }
    function renderTable() {
        const query = fold($('#membership-search').value.trim()), tier = $('#membership-tier').value, sort = $('#membership-sort').value;
        const list = members.filter(m => (!tier || tierOf(m) === tier) && [m.fullName, m.id, `TV${m.id}`, m.phone, m.email].some(v => fold(v).includes(query)));
        const key = sort === 'balance' ? 'loyaltyPoints' : 'totalLoyaltyPoints';
        list.sort((a, b) => sort === 'name' ? String(a.fullName || '').localeCompare(String(b.fullName || ''), 'vi') : Number(b[key] || 0) - Number(a[key] || 0) || a.id - b.id);
        const pages = Math.max(1, Math.ceil(list.length / pageSize));
        page = Math.min(page, pages);
        const offset = (page - 1) * pageSize;
        $('#membership-body').innerHTML = list.length ? list.slice(offset, offset + pageSize).map(m => `<tr>
            <td><strong>${esc(m.fullName || 'Chưa có tên')}</strong><small>Mã TV${esc(m.id)}</small></td><td>${esc(m.phone || 'Chưa có số điện thoại')}<small>${esc(m.email || 'Chưa có email')}</small></td>
            <td>${badge(m)}</td><td class="membership-points">${num(m.loyaltyPoints)}</td><td>${num(m.totalLoyaltyPoints)}</td>
            <td><div class="membership-actions"><button class="btn btn-sm" data-history="${Number(m.id)}" aria-label="Lịch sử của ${esc(m.fullName)}">${icon('history')} Lịch sử</button>${readOnly ? '' : `<button class="btn btn-sm" data-adjust="${Number(m.id)}" aria-label="Điều chỉnh điểm của ${esc(m.fullName)}">${icon('sliders-horizontal')} Điều chỉnh</button>`}</div></td></tr>`).join('') : `<tr><td colspan="6"><div class="empty-state">${icon('users')}<p>${members.length ? 'Không tìm thấy thành viên phù hợp' : 'Chưa có thành viên'}</p><small>${members.length ? 'Thử từ khóa khác hoặc chọn tất cả hạng.' : 'Hồ sơ được tạo trong mục Khách hàng sẽ xuất hiện tại đây.'}</small></div></td></tr>`;
        renderPaginationComponent('pagination-membership', list.length, pageSize, page,
            nextPage => { page = nextPage; renderTable(); },
            nextSize => { pageSize = nextSize; page = 1; renderTable(); });
        root.querySelectorAll('[data-history]').forEach(b => b.onclick = () => showHistory(members.find(m => m.id === Number(b.dataset.history))));
        root.querySelectorAll('[data-adjust]').forEach(b => b.onclick = () => showAdjustment(members.find(m => m.id === Number(b.dataset.adjust))));
        icons();
    }
    async function loadMembers() {
        const version = ++loadVersion;
        $('#membership-refresh').disabled = true;
        $('#membership-body').innerHTML = '<tr><td colspan="6" role="status">Đang tải danh sách thành viên…</td></tr>';
        $('#pagination-membership').innerHTML = '';
        try {
            const data = await api.get('/loyalty/customers');
            if (!root.isConnected || version !== loadVersion) return;
            if (!Array.isArray(data)) throw new Error('Dữ liệu thành viên không hợp lệ.');
            members = data;
            renderSummary();
            renderTable();
        } catch (error) {
            if (!root.isConnected || version !== loadVersion) return;
            $('#membership-body').innerHTML = `<tr><td colspan="6"><div class="empty-state"><p>Không thể tải danh sách thành viên</p><small>${esc(error.message)}</small><button class="btn btn-sm" id="membership-retry">Thử lại</button></div></td></tr>`;
            $('#membership-retry').onclick = loadMembers;
        } finally {
            if (root.isConnected && version === loadVersion) $('#membership-refresh').disabled = false;
        }
    }
    async function showHistory(member) {
        openDialog('Lịch sử điểm', `<div class="membership-member"><strong>${esc(member.fullName)}</strong> · TV${esc(member.id)} ${badge(member)}<p>Khả dụng: <strong>${num(member.loyaltyPoints)}</strong> · Tổng tích lũy: <strong>${num(member.totalLoyaltyPoints)}</strong></p></div><div id="membership-history" aria-live="polite">Đang tải lịch sử…</div>`);
        const version = dialogVersion;
        async function loadHistory() {
            try {
                const data = await api.get(`/loyalty/history/${member.id}`);
                if (!root.isConnected || version !== dialogVersion || !dialog.open) return;
                if (!Array.isArray(data)) throw new Error('Dữ liệu lịch sử không hợp lệ.');
                const logs = normalizeLoyaltyHistory(data);
                let historyPage = 1, historyPageSize = 10;
                function renderHistory() {
                    box.querySelector('#membership-history').innerHTML = (logs.length ? `<div class="table-container overflow-x-auto"><table class="table"><thead><tr><th>Thời gian</th><th>Nội dung</th><th>Biến động</th></tr></thead><tbody>${logs.slice((historyPage - 1) * historyPageSize, historyPage * historyPageSize).map(log => `<tr><td>${esc(date(log.changeDate))}</td><td>${esc(log.reason)}</td><td class="${log.changeType === 'earn' ? 'membership-earned' : 'membership-deducted'}">${log.changeType === 'earn' ? '+' : '−'}${num(log.pointsChanged)}</td></tr>`).join('')}</tbody></table></div>` : '<div class="empty-state"><p>Chưa có giao dịch điểm</p></div>') + '<div id="pagination-membership-history"></div>';
                    renderPaginationComponent('pagination-membership-history', logs.length, historyPageSize, historyPage,
                        nextPage => { historyPage = nextPage; renderHistory(); },
                        nextSize => { historyPageSize = nextSize; historyPage = 1; renderHistory(); });
                }
                renderHistory();
            } catch (error) {
                if (!root.isConnected || version !== dialogVersion || !dialog.open) return;
                box.querySelector('#membership-history').innerHTML = `<p role="alert">${esc(error.message)}</p><button class="btn" id="history-retry">Thử lại</button>`;
                box.querySelector('#history-retry').onclick = loadHistory;
            }
        }
        await loadHistory();
    }
    function showAdjustment(member) {
        if (readOnly) return;
        openDialog('Điều chỉnh điểm', `<div class="membership-member"><strong>${esc(member.fullName)}</strong> · TV${esc(member.id)} ${badge(member)}<p>Số dư hiện tại: <strong>${num(member.loyaltyPoints)} điểm</strong></p></div>
            <form id="membership-adjust-form" class="membership-adjust-form">
                <label><span>Loại điều chỉnh</span><select class="select form-input" name="direction"><option value="1">Cộng điểm</option><option value="-1">Trừ điểm</option></select></label>
                <label><span>Số điểm</span><span class="membership-points-field"><span class="membership-points-sign" aria-hidden="true" hidden>−</span><input class="input form-input" name="points" type="number" min="1" max="2147483647" step="1" required placeholder="Nhập số điểm"></span></label>
                <label><span>Lý do điều chỉnh</span><input class="input form-input" name="reason" required maxlength="200" placeholder="Ví dụ: Bổ sung điểm cho lần lưu trú"></label>
                <p class="membership-help">Cộng điểm tăng tổng tích lũy và tự động cập nhật hạng. Trừ điểm chỉ giảm số dư khả dụng.</p>
                <p id="membership-preview" role="status"></p><p id="membership-adjust-error" role="alert"></p>
                <button class="btn btn-primary" type="submit">${icon('check')} Lưu điều chỉnh</button>
            </form>`);
        const form = box.querySelector('#membership-adjust-form'), fields = form.elements;
        function preview() {
            const isDeduction = fields.direction.value === '-1';
            form.querySelector('.membership-points-sign').hidden = !isDeduction;
            form.querySelector('.membership-points-field').classList.toggle('is-deduction', isDeduction);
            const points = Number(fields.points.value), direction = Number(fields.direction.value);
            const valid = Number.isInteger(points) && points > 0 && points <= 2147483647;
            const balance = Number(member.loyaltyPoints || 0) + points * direction;
            const total = Number(member.totalLoyaltyPoints || 0) + (direction > 0 ? points : 0);
            box.querySelector('#membership-preview').textContent = valid ? `Sau điều chỉnh: ${num(balance)} điểm khả dụng · Hạng ${getLegacyTierInfo(total).name}` : '';
            return valid && balance >= 0 && balance <= 2147483647 && total <= 2147483647;
        }
        form.addEventListener('input', preview);
        fields.direction.addEventListener('change', preview);
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (saving) return;
            const error = box.querySelector('#membership-adjust-error');
            error.textContent = '';
            if (!preview() || !fields.reason.value.trim()) {
                error.textContent = 'Nhập số điểm nguyên dương và lý do; số điểm trừ không được vượt quá số dư.';
                return;
            }
            const payload = { customerId: member.id, pointsChange: Number(fields.points.value) * Number(fields.direction.value), reason: fields.reason.value.trim() };
            saving = true;
            const controls = Array.from(box.querySelectorAll('button, input, select'));
            controls.forEach(c => c.disabled = true);
            const submit = form.querySelector('[type="submit"]');
            submit.textContent = 'Đang lưu…';
            try {
                const updated = await api.post('/loyalty/adjust', payload, { retryOnNetworkError: false, timeoutMs: 10000 });
                if (!root.isConnected) return;
                member.loyaltyPoints = updated.loyaltyPoints;
                member.totalLoyaltyPoints = updated.totalLoyaltyPoints;
                member.loyaltyLevel = updated.loyaltyLevel;
                saving = false;
                dialog.close();
                $('#membership-notice').textContent = `Đã ${payload.pointsChange > 0 ? 'cộng' : 'trừ'} ${num(Math.abs(payload.pointsChange))} điểm cho ${member.fullName}.`;
                renderSummary();
                renderTable();
            } catch (cause) {
                if (root.isConnected) error.textContent = cause.message || 'Không thể lưu điều chỉnh. Vui lòng thử lại.';
            } finally {
                saving = false;
                controls.forEach(c => c.disabled = false);
                submit.innerHTML = `${icon('check')} Lưu điều chỉnh`;
                icons();
            }
        });
    }
    $('#membership-search').addEventListener('input', () => { page = 1; renderTable(); });
    $('#membership-tier').addEventListener('change', () => { page = 1; renderTable(); });
    $('#membership-sort').addEventListener('change', () => { page = 1; renderTable(); });
    $('#membership-refresh').onclick = () => { $('#membership-notice').textContent = ''; loadMembers(); };
    icons();
    await loadMembers();
}
