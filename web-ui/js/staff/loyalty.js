import { api } from '../../api.js';
import { escapeHtml } from '../shared/ui.js';

export function memberTier(value) {
    const raw = typeof value === 'string' ? value.trim() : '';
    return new Map([['silver','Silver'],['gold','Gold'],['vip','VIP']]).get(raw.toLowerCase()) || raw || 'Chưa xếp hạng';
}
const standardTiers = ['Silver', 'Gold', 'VIP'];
const fold = value => String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
export function filterMembers(members, query = '', tier = '') {
    const text = fold(query).trim(), digits = query.replace(/\D/g, '');
    const phoneQuery = digits && /^[\d\s+().-]+$/.test(query);
    return members.filter(member => {
        const rank = memberTier(member.loyaltyLevel);
        const matchesTier = !tier || (tier === 'other' ? !standardTiers.includes(rank) : rank === tier);
        return matchesTier && (!text || fold(`${member.fullName || ''} ${member.phone || ''} ${member.id}`).includes(text)
            || (phoneQuery && String(member.phone || '').replace(/\D/g, '').includes(digits)));
    });
}
const points = value => value != null && Number.isFinite(Number(value)) ? Number(value).toLocaleString('vi-VN') : '—';
export function loyaltyDate(value, withTime = false) {
    if (!value) return 'Chưa có dữ liệu';
    // Database DATETIME values have hotel-local calendar semantics.
    const local = String(value).match(/^(\d{4})-(\d{2})-(\d{2})(?:T| )(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/);
    if (local) return `${local[3]}/${local[2]}/${local[1]}${withTime ? ` · ${local[4]}:${local[5]}` : ''}`;
    const date = new Date(value);
    if (!Number.isFinite(date.getTime())) return 'Chưa có dữ liệu';
    return new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric', ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}) }).format(date);
}
const tierClass = value => new Map([['Silver','silver'],['Gold','gold'],['VIP','vip']]).get(memberTier(value)) || 'other';
const badge = value => `<span class="sl-tier sl-tier-${tierClass(value)}">${escapeHtml(memberTier(value))}</span>`;
const memberName = member => member.fullName?.trim() || 'Chưa có họ tên';
const icons = () => globalThis.lucide?.createIcons();

const styles = `
body.staff-portal:has(.sl-view) .portal-shell { grid-template-columns:248px minmax(0,1fr); }
body.staff-portal:has(.sl-view) :is(.portal-sidebar,.portal-nav) { min-width:0; }
.sl-view { min-width:0; color:var(--portal-text); }
.sl-view *, .sl-history-dialog * { box-sizing:border-box; }
.sl-view svg, .sl-history-dialog svg { width:16px; height:16px; flex:none; }
.sl-intro { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-bottom:22px; }
.sl-intro small { display:block; margin-bottom:6px; color:var(--portal-gold); font-size:10px; font-weight:650; letter-spacing:.1em; }
.sl-intro p { margin:0; color:var(--portal-muted); font-size:13px; line-height:1.7; }
#view-container .sl-button, .sl-history-dialog .sl-button { display:inline-flex; align-items:center; justify-content:center; gap:8px; min-height:39px; padding:9px 13px; border:1px solid var(--portal-border); border-radius:8px; background:var(--portal-panel); color:var(--portal-text); font:inherit; font-size:12px; font-weight:600; cursor:pointer; white-space:nowrap; }
#view-container .sl-button:disabled { opacity:.45; cursor:default; }
.sl-view :is(button,input,select,summary):focus-visible, .sl-history-dialog button:focus-visible { outline:2px solid var(--portal-gold); outline-offset:3px; }
.sl-stats { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:14px; margin-bottom:12px; }
#view-container .sl-stat { display:flex; align-items:center; justify-content:space-between; gap:12px; min-height:108px; padding:21px; border:1px solid var(--portal-border); border-radius:12px; background:var(--portal-panel); color:var(--portal-text); text-align:left; cursor:pointer; font:inherit; }
.sl-stat-label { display:block; margin-bottom:10px; color:var(--portal-muted); font-size:11px; }
.sl-stat-value { display:block; font-size:28px; line-height:1; font-weight:650; font-variant-numeric:tabular-nums; }
.sl-stat-icon { display:grid; place-items:center; width:38px; height:38px; border-radius:10px; background:var(--portal-surface); color:var(--portal-soft); }
.sl-stat-icon svg { width:19px; height:19px; }
.sl-stat[data-tier="Silver"] .sl-stat-icon { color:#b7c6d9; }.sl-stat[data-tier="Gold"] .sl-stat-icon { color:var(--portal-gold); }.sl-stat[data-tier="VIP"] .sl-stat-icon { color:#c4a4ef; }
.sl-count-note { min-height:18px; margin:0 0 16px; color:var(--portal-muted); font-size:11px; line-height:1.6; }
.sl-panel { border:1px solid var(--portal-border); border-radius:13px; background:var(--portal-panel); overflow:hidden; }
.sl-toolbar { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; padding:18px 20px; border-bottom:1px solid var(--portal-border); }
.sl-search { position:relative; display:block; flex:1 1 260px; max-width:440px; }
.sl-search svg { position:absolute; left:12px; top:50%; transform:translateY(-50%); color:var(--portal-muted); }
#view-container .sl-search input, #view-container .sl-filter select { display:block; width:100%; min-height:41px; margin:0; padding:10px 12px; border:1px solid var(--portal-border); border-radius:8px; color:var(--portal-text); background:var(--portal-bg); font:inherit; font-size:12px; }
#view-container .sl-search input { padding-left:38px; }
.sl-search input::placeholder { color:var(--portal-muted); opacity:.9; }
.sl-filter { display:flex; align-items:center; gap:10px; font-size:12px; color:var(--portal-muted); }
#view-container .sl-filter select { width:200px; }
.sl-table-scroll { max-width:100%; overflow:auto; scrollbar-width:thin; }
#view-container .sl-table { width:100%; min-width:780px; border-collapse:collapse; }
#view-container .sl-table th { padding:13px 20px; background:color-mix(in srgb,var(--portal-bg) 40%,transparent); color:var(--portal-muted); font-size:10px; letter-spacing:.035em; font-weight:600; white-space:nowrap; }
#view-container .sl-table td { padding:16px 20px; border-bottom:1px solid var(--portal-border); vertical-align:middle; font-size:12px; line-height:1.6; }
#view-container .sl-table tbody tr:last-child td { border-bottom:0; }
#view-container .sl-table tbody tr:hover { background:color-mix(in srgb,var(--portal-gold) 3%,transparent); }
.sl-name { display:flex; align-items:center; gap:11px; min-width:180px; }
.sl-avatar { display:grid; place-items:center; width:34px; height:34px; flex:none; border:1px solid var(--portal-border); border-radius:10px; background:var(--portal-surface); color:var(--portal-soft); font-size:11px; font-weight:650; }
.sl-name strong { display:block; font-weight:600; overflow-wrap:anywhere; }
.sl-name small { display:block; margin-top:3px; color:var(--portal-muted); font-size:10px; }
.sl-number { font-variant-numeric:tabular-nums; white-space:nowrap; }
.sl-number strong { display:block; font-size:14px; font-weight:600; }
.sl-number small { display:block; margin-top:3px; color:var(--portal-muted); font-size:10px; }
.sl-tier { display:inline-flex; padding:4px 9px; border:1px solid var(--portal-border); border-radius:6px; color:var(--portal-muted); background:var(--portal-surface); font-size:11px; font-weight:600; white-space:nowrap; }
.sl-tier-silver { color:#c3cddd; background:rgba(148,163,184,.09); border-color:rgba(148,163,184,.25); }.sl-tier-gold { color:var(--portal-soft); background:color-mix(in srgb,var(--portal-gold) 10%,transparent); border-color:color-mix(in srgb,var(--portal-gold) 25%,transparent); }.sl-tier-vip { color:#cfb1f5; background:rgba(168,120,233,.08); border-color:rgba(168,120,233,.23); }
.sl-pager { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; padding:15px 20px; border-top:1px solid var(--portal-border); color:var(--portal-muted); font-size:11px; }
.sl-page-actions { display:flex; align-items:center; gap:12px; }
#view-container .sl-page-actions .sl-button { min-height:32px; padding:6px 9px; }
.sl-empty { padding:40px 20px; text-align:center; color:var(--portal-muted); }
.sl-empty svg { width:27px; height:27px; margin-bottom:12px; color:var(--portal-gold); }
.sl-empty h3 { margin:0 0 8px; color:var(--portal-text); font-family:inherit; font-size:15px; font-weight:600; }
.sl-empty p { margin:0; font-size:12px; line-height:1.8; }
.sl-policy { margin-top:22px; border:1px solid var(--portal-border); border-radius:12px; background:var(--portal-panel); }
.sl-policy summary { display:flex; align-items:center; gap:9px; padding:17px 20px; color:var(--portal-soft); font-size:12px; font-weight:600; cursor:pointer; list-style:none; }
.sl-policy summary::-webkit-details-marker { display:none; }
.sl-policy summary svg:last-child { margin-left:auto; }
.sl-policy-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; padding:0 18px 18px; }
.sl-policy-tier { padding:18px; border:1px solid var(--portal-border); border-top:3px solid var(--portal-gold); border-radius:9px; }
.sl-policy-tier:first-child { border-top-color:#b7c6d9; }.sl-policy-tier:last-child { border-top-color:#bca0e4; }
.sl-policy-tier h3 { margin:0 0 9px; font-family:inherit; font-size:14px; }.sl-policy-tier p { margin:0 0 14px; color:var(--portal-muted); font-size:11px; line-height:1.7; }.sl-policy-tier strong { color:var(--portal-soft); font-size:11px; font-weight:600; }
.sl-history-dialog { color:var(--portal-text); }
.sl-history-head { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-bottom:18px; padding-bottom:18px; border-bottom:1px solid var(--portal-border); }
.sl-history-head h2 { margin:0; font-family:inherit; font-size:20px; line-height:1.4; font-weight:600; }
.sl-history-head p { margin:5px 0 0; color:var(--portal-muted); font-size:12px; }
.sl-history-dialog .sl-close { display:grid; place-items:center; width:32px; height:32px; border:1px solid var(--portal-border); border-radius:8px; background:transparent; color:var(--portal-muted); font-size:22px; cursor:pointer; }
.sl-history-member { display:flex; flex-wrap:wrap; align-items:center; gap:12px; padding:14px; border:1px solid var(--portal-border); border-radius:9px; background:var(--portal-surface); }
.sl-history-member strong { display:block; font-size:14px; }.sl-history-member small { display:block; margin-top:5px; font-size:11px; color:var(--portal-muted); }.sl-history-member .sl-tier { margin-left:auto; }
.sl-history-balances { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; margin:16px 0; }
.sl-history-balances > div { padding:14px; border:1px solid var(--portal-border); border-radius:9px; }.sl-history-balances span { color:var(--portal-muted); font-size:11px; }.sl-history-balances strong { display:block; margin-top:8px; font-size:22px; font-weight:650; font-variant-numeric:tabular-nums; }
.sl-history-scroll { max-height:330px; overflow:auto; border:1px solid var(--portal-border); border-radius:9px; }
.sl-history-table { width:100%; min-width:500px; border-collapse:collapse; }
.sl-history-table th { text-align:left; padding:11px 13px; background:var(--portal-surface); color:var(--portal-muted); font-size:10px; font-weight:600; }.sl-history-table td { padding:13px; border-top:1px solid var(--portal-border); font-size:11px; line-height:1.7; vertical-align:top; }.sl-history-table td:last-child { min-width:180px; overflow-wrap:anywhere; }.sl-positive { color:#5cdbb4; font-weight:600; }.sl-negative { color:#f2a28d; font-weight:600; }
.sl-history-footer { display:flex; justify-content:flex-end; margin-top:18px; }.sl-history-dialog .sl-error { padding:18px 0; color:var(--portal-muted); font-size:12px; line-height:1.8; }
html[data-theme="light"] body.staff-portal:has(.sl-view) .portal-content { background:var(--portal-bg); }
html[data-theme="light"] body.staff-portal #global-modal:has(.sl-history-dialog) .modal-content { background:var(--portal-panel); }
html[data-theme="light"] .sl-tier-silver { color:#526174; }html[data-theme="light"] .sl-tier-vip { color:#7952a3; }html[data-theme="light"] .sl-positive { color:#16734f; }html[data-theme="light"] .sl-negative { color:#a84c34; }
@media(max-width:1000px) { body.staff-portal:has(.sl-view) .portal-shell { grid-template-columns:minmax(0,1fr); } }
@media(max-width:700px) { .sl-stats { grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; } #view-container .sl-stat { padding:16px; min-height:94px; } .sl-toolbar { padding:14px; }.sl-search { max-width:none; flex-basis:100%; }.sl-filter { width:100%; justify-content:space-between; } #view-container .sl-filter select { flex:1; max-width:250px; }.sl-policy-grid { grid-template-columns:minmax(0,1fr); }.sl-intro { align-items:flex-start; }.sl-pager { padding:14px; }.sl-history-head h2 { font-size:18px; } }
`;

export async function renderLoyalty(container, session) {
    container.innerHTML = `<style>${styles}</style><section class="sl-view" aria-label="Danh sách thành viên khách sạn">
        <div class="sl-intro"><div><small>KHÁCH HÀNG THÂN THIẾT</small><p>Tra cứu hạng thành viên, điểm tích lũy và lịch sử điểm của khách.</p></div><button type="button" class="sl-button" id="sl-refresh"><i data-lucide="refresh-cw" aria-hidden="true"></i>Làm mới</button></div>
        <div class="sl-stats">${[['','Tổng khách','users'],['Silver','Silver · Bạc','medal'],['Gold','Gold · Vàng','award'],['VIP','VIP · Kim Cương','gem']].map(([tier,label,icon]) => `<button type="button" class="sl-stat" data-tier="${tier}" aria-label="Lọc ${label}"><div><span class="sl-stat-label">${label}</span><strong class="sl-stat-value" id="sl-count-${tier || 'total'}">—</strong></div><span class="sl-stat-icon"><i data-lucide="${icon}" aria-hidden="true"></i></span></button>`).join('')}</div>
        <p class="sl-count-note" id="sl-other-note"></p>
        <div class="sl-panel"><div class="sl-toolbar"><label class="sl-search"><i data-lucide="search" aria-hidden="true"></i><input type="search" id="sl-search" placeholder="Tìm họ tên, số điện thoại hoặc mã khách…" aria-label="Tìm thành viên" autocomplete="off"></label><label class="sl-filter" for="sl-tier">Hạng thành viên<select id="sl-tier"><option value="">Tất cả hạng</option><option value="Silver">Silver</option><option value="Gold">Gold</option><option value="VIP">VIP</option><option value="other">Khác / chưa xếp hạng</option></select></label></div>
            <div class="sl-table-scroll"><table class="sl-table"><thead><tr><th>Họ và tên</th><th>Số điện thoại</th><th>Hạng thành viên</th><th>Điểm tích lũy</th><th>Ngày tham gia</th><th>Thao tác</th></tr></thead><tbody id="sl-members" aria-live="polite" aria-busy="true"></tbody></table></div>
            <div class="sl-pager"><span id="sl-results">Đang tải danh sách…</span><div class="sl-page-actions"><button type="button" class="sl-button" id="sl-prev" aria-label="Trang trước"><i data-lucide="chevron-left" aria-hidden="true"></i></button><span id="sl-page">—</span><button type="button" class="sl-button" id="sl-next" aria-label="Trang sau"><i data-lucide="chevron-right" aria-hidden="true"></i></button></div></div>
        </div>
        <details class="sl-policy"><summary><i data-lucide="badge-check" aria-hidden="true"></i>Chính sách hệ thành viên<i data-lucide="chevron-down" aria-hidden="true"></i></summary><div class="sl-policy-grid">
            <div class="sl-policy-tier"><h3>Silver (Bạc)</h3><p>Dành cho tài khoản mới đăng ký.</p><strong>Mặc định</strong></div>
            <div class="sl-policy-tier"><h3>Gold (Vàng)</h3><p>Đạt từ 300 điểm tích lũy phòng.</p><strong>Giảm 5% hóa đơn phòng</strong></div>
            <div class="sl-policy-tier"><h3>VIP (Kim Cương)</h3><p>Đạt từ 1.000 điểm tích lũy phòng.</p><strong>Giảm 10% hóa đơn phòng</strong></div>
        </div></details>
    </section>`;
    const byId = id => container.querySelector(`#${id}`);
    const view = container.querySelector('.sl-view'), body = byId('sl-members');
    let members = null, page = 1, loadVersion = 0, historyVersion = 0;
    const pageSize = 10;
    const messageRow = (title, description, icon = 'search') => `<tr><td colspan="6"><div class="sl-empty"><i data-lucide="${icon}" aria-hidden="true"></i><h3>${escapeHtml(title)}</h3><p>${escapeHtml(description)}</p></div></td></tr>`;

    const renderTable = () => {
        if (!members) return;
        const filtered = filterMembers(members, byId('sl-search').value, byId('sl-tier').value);
        const pages = Math.max(1, Math.ceil(filtered.length / pageSize)); page = Math.min(page, pages);
        const start = (page - 1) * pageSize, visible = filtered.slice(start, start + pageSize);
        body.innerHTML = visible.length ? visible.map(member => {
            const name = memberName(member), initial = name.split(/\s+/).slice(-2).map(part => part.charAt(0)).join('').toUpperCase();
            return `<tr><td><div class="sl-name"><span class="sl-avatar" aria-hidden="true">${escapeHtml(initial)}</span><div><strong>${escapeHtml(name)}</strong><small>KH #${escapeHtml(member.id)}</small></div></div></td><td class="sl-number">${member.phone ? `<a href="tel:${escapeHtml(member.phone)}" style="color:inherit;text-decoration:none">${escapeHtml(member.phone)}</a>` : '—'}</td><td>${badge(member.loyaltyLevel)}</td><td class="sl-number"><strong>${points(member.totalLoyaltyPoints)}</strong><small>Khả dụng: ${points(member.loyaltyPoints)}</small></td><td class="sl-number">${loyaltyDate(member.joinedAt)}</td><td><button type="button" class="sl-button" data-member-id="${escapeHtml(member.id)}" aria-label="Xem lịch sử điểm của ${escapeHtml(name)}"><i data-lucide="history" aria-hidden="true"></i>Lịch sử điểm</button></td></tr>`;
        }).join('') : messageRow(members.length ? 'Không tìm thấy thành viên phù hợp' : 'Chưa có khách hàng', members.length ? 'Thử tìm tên, số điện thoại hoặc chọn hạng khác.' : 'Khách hàng có trong hệ thống sẽ xuất hiện tại đây.', 'users');
        byId('sl-results').textContent = filtered.length ? `Hiển thị ${start + 1}–${start + visible.length} / ${filtered.length} khách${filtered.length !== members.length ? ` · Tổng ${members.length} khách` : ''}` : `0 / ${members.length} khách`;
        byId('sl-page').textContent = `${page} / ${pages}`;
        byId('sl-prev').disabled = page === 1; byId('sl-next').disabled = page >= pages;
        body.querySelectorAll('[data-member-id]').forEach(button => button.addEventListener('click', () => openHistory(button.dataset.memberId)));
        icons();
    };

    const load = async () => {
        const version = ++loadVersion; members = null;
        byId('sl-refresh').disabled = true; body.setAttribute('aria-busy', 'true');
        byId('sl-prev').disabled = true; byId('sl-next').disabled = true;
        for (const tier of ['total', ...standardTiers]) byId(`sl-count-${tier}`).textContent = '—';
        byId('sl-other-note').textContent = ''; byId('sl-results').textContent = 'Đang tải danh sách…'; byId('sl-page').textContent = '—';
        body.innerHTML = messageRow('Đang tải thành viên', 'Vui lòng chờ trong giây lát.', 'loader-circle'); icons();
        try {
            const data = await api.get('/loyalty/members');
            if (version !== loadVersion || !view.isConnected) return;
            if (!Array.isArray(data?.members) || !data.summary || ['total', ...standardTiers, 'other'].some(key => !Number.isSafeInteger(data.summary[key]) || data.summary[key] < 0) || data.summary.total !== data.members.length
                || data.summary.Silver + data.summary.Gold + data.summary.VIP + data.summary.other !== data.summary.total
                || data.members.some(member => !Number.isSafeInteger(member.id) || member.id < 1)) throw new Error('Phản hồi danh sách thành viên chưa đầy đủ');
            members = data.members;
            for (const tier of ['total', ...standardTiers]) byId(`sl-count-${tier}`).textContent = points(data.summary[tier]);
            byId('sl-other-note').textContent = data.summary.other ? `${points(data.summary.other)} khách có hạng khác hoặc chưa xếp hạng. Hạng hiển thị theo hồ sơ hiện tại.` : 'Thống kê theo hạng thành viên đang lưu trong hồ sơ khách hàng.';
            page = 1; renderTable();
        } catch (error) {
            if (version !== loadVersion || !view.isConnected) return;
            body.innerHTML = messageRow('Chưa tải được danh sách thành viên', `${error.message} Chọn Làm mới để thử lại.`, 'circle-alert');
            byId('sl-results').textContent = 'Chưa có dữ liệu để hiển thị';
        } finally {
            if (version === loadVersion && view.isConnected) { body.setAttribute('aria-busy', 'false'); byId('sl-refresh').disabled = false; icons(); }
        }
    };

    const openHistory = async customerId => {
        const member = members?.find(item => String(item.id) === String(customerId)); if (!member) return;
        const version = ++historyVersion;
        const modal = document.getElementById('global-modal'), content = document.getElementById('global-modal-content');
        content.innerHTML = `<section class="sl-history-dialog"><div class="sl-history-head"><div><h2>Lịch sử tích điểm</h2><p>Chi tiết giao dịch điểm của khách hàng</p></div><button type="button" class="sl-close" id="sl-close-history" aria-label="Đóng lịch sử">×</button></div><div id="sl-history-body"><p class="sl-error" role="status">Đang tải lịch sử điểm…</p></div></section>`;
        const pane = content.querySelector('.sl-history-dialog'), historyBody = content.querySelector('#sl-history-body');
        const close = () => { historyVersion++; modal.classList.remove('active'); modal.setAttribute('aria-hidden', 'true'); };
        content.querySelector('#sl-close-history').addEventListener('click', close);
        modal.classList.add('active'); modal.setAttribute('aria-hidden', 'false'); content.querySelector('#sl-close-history').focus();
        try {
            const data = await api.get(`/loyalty/members/${encodeURIComponent(customerId)}/history`);
            if (version !== historyVersion || !pane.isConnected || !view.isConnected) return;
            if (String(data?.member?.id) !== String(customerId) || !Array.isArray(data.history)) throw new Error('Phản hồi lịch sử điểm chưa đầy đủ');
            const current = data.member;
            historyBody.innerHTML = `<div class="sl-history-member"><div><strong>${escapeHtml(memberName(current))}</strong><small>KH #${escapeHtml(current.id)}${current.phone ? ` · ${escapeHtml(current.phone)}` : ''}</small></div>${badge(current.loyaltyLevel)}</div><div class="sl-history-balances"><div><span>Điểm tích lũy</span><strong>${points(current.totalLoyaltyPoints)}</strong></div><div><span>Điểm khả dụng</span><strong>${points(current.loyaltyPoints)}</strong></div></div>
                ${data.history.length ? `<div class="sl-history-scroll"><table class="sl-history-table"><thead><tr><th>Thời gian</th><th>Giao dịch</th><th>Thay đổi</th><th>Nội dung</th></tr></thead><tbody>${data.history.map(entry => `<tr><td class="sl-number">${loyaltyDate(entry.createdAt, true)}</td><td>${escapeHtml(new Map([['earn','Tích điểm'],['redeem','Đổi điểm']]).get(entry.type) || entry.type || '—')}</td><td class="sl-number ${Number(entry.pointsChange) > 0 ? 'sl-positive' : Number(entry.pointsChange) < 0 ? 'sl-negative' : ''}">${entry.pointsChange != null && Number(entry.pointsChange) > 0 ? '+' : ''}${points(entry.pointsChange)}</td><td>${escapeHtml(entry.description || '—')}</td></tr>`).join('')}</tbody></table></div>` : '<div class="sl-empty"><h3>Chưa có lịch sử điểm</h3><p>Chưa có giao dịch điểm nào được ghi nhận cho khách hàng này.</p></div>'}
                <div class="sl-history-footer"><button type="button" class="sl-button" id="sl-history-done">Đóng</button></div>`;
            content.querySelector('#sl-history-done').addEventListener('click', close); icons();
        } catch (error) {
            if (version !== historyVersion || !pane.isConnected || !view.isConnected) return;
            historyBody.innerHTML = `<p class="sl-error" role="alert">${escapeHtml(error.message)} Vui lòng thử tải lại lịch sử.</p><button type="button" class="sl-button" id="sl-history-retry">Thử lại</button>`;
            content.querySelector('#sl-history-retry').addEventListener('click', () => openHistory(customerId));
        }
    };

    byId('sl-search').addEventListener('input', () => { page = 1; renderTable(); });
    byId('sl-tier').addEventListener('change', () => { page = 1; renderTable(); });
    byId('sl-refresh').addEventListener('click', load);
    byId('sl-prev').addEventListener('click', () => { page--; renderTable(); });
    byId('sl-next').addEventListener('click', () => { page++; renderTable(); });
    container.querySelectorAll('.sl-stat').forEach(button => button.addEventListener('click', () => { byId('sl-tier').value = button.dataset.tier; page = 1; renderTable(); }));
    await load();
}
