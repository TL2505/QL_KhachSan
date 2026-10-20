import { api, getApiBaseUrl } from '../../api.js';
import { escapeHtml } from '../shared/ui.js';

const customerFullName = customer =>
    [customer?.fullName, customer?.full_name].find(name => typeof name === 'string' && name.trim())?.trim();
const money = value => Number.isFinite(Number(value)) && value != null
    ? Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 2 }) + ' ₫' : 'Chưa có dữ liệu';
const hotelDate = value => {
    const date = new Date(value || '');
    if (!Number.isFinite(date.getTime())) return null;
    const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(date);
    const part = type => parts.find(item => item.type === type).value;
    return `${part('year')}-${part('month')}-${part('day')}`;
};
const dateLabel = value => {
    const date = new Date(value || '');
    return Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(date) : 'Chưa có thời gian';
};
export function departureState(booking, now = new Date()) {
    const due = hotelDate(booking.checkOutDate), today = hotelDate(now);
    if (!due) return { key: 'unknown', label: 'Chưa có ngày trả', tone: 'muted' };
    if (due < today) return { key: 'overdue', label: 'Quá ngày dự kiến', tone: 'red' };
    if (due === today) return { key: 'today', label: 'Trả hôm nay', tone: 'gold' };
    return { key: 'staying', label: 'Đang lưu trú', tone: 'green' };
}
const searchText = value => String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
const icons = () => globalThis.lucide?.createIcons();

const styles = `
body.staff-portal:has(.sc-workspace) :is(.portal-content,.portal-view,.portal-sidebar,.portal-nav) { min-width:0; }
body.staff-portal:has(.sc-workspace) .portal-shell { grid-template-columns:248px minmax(0,1fr); }
@media(max-width:1000px) { body.staff-portal:has(.sc-workspace) .portal-shell { grid-template-columns:minmax(0,1fr); } }
.sc-workspace { min-width:0; width:100%; --sc-green:#58d5ae; --sc-red:#f3a58f; color:var(--portal-text); }
.sc-workspace *, .sc-dialog * { box-sizing:border-box; }
#view-container .sc-intro { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-bottom:22px; }
#view-container .sc-intro p { margin:0; color:var(--portal-muted); font-size:14px; line-height:1.7; }
#view-container .sc-intro small { display:block; margin-bottom:5px; color:var(--portal-gold); letter-spacing:.12em; font-size:10px; font-weight:700; }
.sc-workspace button, .sc-dialog button { cursor:pointer; font:inherit; }
#view-container .sc-secondary, .sc-dialog .sc-secondary { display:inline-flex; align-items:center; justify-content:center; gap:8px; min-height:40px; padding:9px 14px; border:1px solid var(--portal-border); border-radius:9px; background:var(--portal-panel); color:var(--portal-text); font-size:12px; font-weight:600; white-space:nowrap; }
.sc-workspace svg, .sc-dialog svg { width:16px; height:16px; flex:none; }
.sc-workspace button:disabled, .sc-dialog button:disabled { cursor:wait; opacity:.55; }
.sc-workspace :is(button,input):focus-visible, .sc-dialog :is(button,select):focus-visible { outline:2px solid var(--portal-gold); outline-offset:3px; }
#view-container .sc-stats { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; margin-bottom:24px; }
#view-container .sc-stat { position:relative; display:flex; align-items:center; justify-content:space-between; gap:12px; min-height:108px; padding:20px 22px; border:1px solid var(--portal-border); border-radius:13px; background:var(--portal-panel); text-align:left; }
.sc-stat-label { margin:0 0 7px; color:var(--portal-muted); font-size:12px; }
.sc-stat-value { font-size:30px; font-weight:650; line-height:1; font-variant-numeric:tabular-nums; color:var(--portal-text); }
.sc-stat-icon { display:grid; place-items:center; width:42px; height:42px; border-radius:12px; background:color-mix(in srgb,var(--portal-gold) 10%,transparent); color:var(--portal-gold); }
.sc-stat-icon svg { width:20px; height:20px; }
.sc-stat[data-filter="overdue"] .sc-stat-icon { color:var(--sc-red); background:color-mix(in srgb,var(--sc-red) 9%,transparent); }
#view-container .sc-panel { border:1px solid var(--portal-border); border-radius:14px; background:var(--portal-panel); overflow:hidden; }
#view-container .sc-toolbar { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:16px; padding:18px 20px; border-bottom:1px solid var(--portal-border); }
#view-container .sc-search { position:relative; flex:1 1 260px; max-width:360px; }
.sc-search svg { position:absolute; left:13px; top:50%; transform:translateY(-50%); color:var(--portal-muted); pointer-events:none; }
#view-container .sc-search input { display:block; width:100%; min-height:41px; margin:0; padding:10px 12px 10px 39px; border:1px solid var(--portal-border); border-radius:9px; background:var(--portal-bg); color:var(--portal-text); font:inherit; font-size:12px; }
.sc-search input::placeholder { color:var(--portal-muted); opacity:.9; }
#view-container .sc-filters { display:flex; flex-wrap:wrap; gap:5px; }
#view-container .sc-filter { display:inline-flex; align-items:center; gap:6px; min-height:36px; padding:7px 10px; border:1px solid transparent; border-radius:8px; background:transparent; color:var(--portal-muted); font-size:12px; white-space:nowrap; }
#view-container .sc-filter[aria-pressed="true"] { background:color-mix(in srgb,var(--portal-gold) 12%,transparent); border-color:color-mix(in srgb,var(--portal-gold) 25%,transparent); color:var(--portal-soft); }
.sc-filter span { font-size:10px; padding:1px 5px; border-radius:5px; background:color-mix(in srgb,var(--portal-muted) 12%,transparent); font-variant-numeric:tabular-nums; }
#view-container .sc-columns, #view-container .checkout-card { display:grid; grid-template-columns:84px minmax(130px,1.25fr) minmax(165px,1fr) minmax(125px,.85fr) 176px; align-items:center; column-gap:20px; }
#view-container .sc-columns { padding:12px 22px; background:color-mix(in srgb,var(--portal-bg) 40%,transparent); color:var(--portal-muted); font-size:10px; font-weight:600; letter-spacing:.055em; text-transform:uppercase; }
#view-container .checkout-card { min-height:117px; padding:20px 22px; border-top:1px solid var(--portal-border); background:transparent; border-radius:0; box-shadow:none; transition:background .15s; }
#view-container .checkout-card:hover { transform:none; background:color-mix(in srgb,var(--portal-gold) 3%,transparent); }
.sc-room { display:flex; flex-direction:column; align-items:flex-start; gap:6px; }
.sc-room strong { color:var(--portal-soft); font-size:23px; font-weight:600; letter-spacing:-.04em; line-height:1.1; }
.sc-room small { color:var(--portal-muted); font-size:10px; }
.sc-guest { min-width:0; }
.sc-guest h3 { margin:0 0 7px; font-family:inherit; font-size:14px; font-weight:600; line-height:1.5; overflow-wrap:anywhere; }
.sc-guest p { margin:0 0 7px; color:var(--portal-muted); font-size:11px; }
.sc-pill { display:inline-flex; align-items:center; gap:5px; padding:4px 8px; border-radius:5px; background:color-mix(in srgb,var(--portal-muted) 8%,transparent); font-size:10px; line-height:1.4; color:var(--portal-muted); }
.sc-pill::before { content:''; width:5px; height:5px; border-radius:50%; background:currentColor; }
.sc-pill-green { color:var(--sc-green); background:color-mix(in srgb,var(--sc-green) 8%,transparent); }
.sc-pill-gold { color:var(--portal-soft); background:color-mix(in srgb,var(--portal-gold) 10%,transparent); }
.sc-pill-red { color:var(--sc-red); background:color-mix(in srgb,var(--sc-red) 8%,transparent); }
.sc-dates { display:grid; gap:9px; font-size:11px; line-height:1.5; font-variant-numeric:tabular-nums; }
.sc-dates div { display:flex; align-items:baseline; gap:10px; }
.sc-dates span { color:var(--portal-muted); width:30px; flex:none; }
.sc-dates time { color:var(--portal-text); }
.sc-amount strong { display:block; font-size:15px; font-weight:600; white-space:nowrap; font-variant-numeric:tabular-nums; }
.sc-amount small { display:block; margin-top:5px; color:var(--portal-muted); font-size:10px; line-height:1.5; }
#view-container .sc-primary, .sc-dialog .sc-primary { display:inline-flex; align-items:center; justify-content:center; gap:8px; min-height:40px; padding:10px 14px; border:1px solid var(--portal-gold); border-radius:8px; background:var(--portal-gold); color:#151511; font-size:12px; font-weight:700; white-space:nowrap; }
.sc-action { min-width:0; display:grid; gap:7px; text-align:center; }
.sc-action small { color:var(--portal-muted); font-size:10px; }
#view-container .sc-list-footer { display:flex; justify-content:space-between; flex-wrap:wrap; gap:8px; padding:14px 22px; border-top:1px solid var(--portal-border); color:var(--portal-muted); font-size:11px; line-height:1.6; }
.sc-empty { display:grid; justify-items:center; gap:10px; padding:50px 20px; text-align:center; color:var(--portal-muted); }
.sc-empty svg { width:30px; height:30px; color:var(--portal-gold); }
.sc-empty h3 { margin:0; color:var(--portal-text); font-size:16px; font-weight:600; }
.sc-empty p { margin:0; max-width:430px; font-size:12px; line-height:1.7; }
.sc-dialog { color:var(--portal-text); }
.sc-dialog .sc-modal-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; padding-bottom:18px; border-bottom:1px solid var(--portal-border); }
.sc-dialog h2 { margin:0; font-family:inherit; font-size:20px; line-height:1.4; font-weight:600; }
.sc-dialog .sc-modal-head p { margin:6px 0 0; color:var(--portal-muted); font-size:12px; }
.sc-dialog .sc-close { width:32px; height:32px; border:1px solid var(--portal-border); border-radius:8px; color:var(--portal-muted); background:transparent; font-size:22px; }
.sc-dialog .sc-modal-guest { display:flex; justify-content:space-between; gap:14px; margin:20px 0; padding:16px; border-radius:10px; background:var(--portal-surface); border:1px solid var(--portal-border); }
.sc-modal-guest strong { display:block; font-size:14px; overflow-wrap:anywhere; }
.sc-modal-guest small { display:block; color:var(--portal-muted); font-size:11px; margin-top:5px; }
.sc-dialog label { display:block; margin:18px 0 8px; font-size:12px; font-weight:600; }
.sc-dialog select { width:100%; min-height:44px; padding:10px 12px; border:1px solid var(--portal-border); border-radius:8px; background:var(--portal-bg); color:var(--portal-text); font:inherit; font-size:13px; }
.sc-dialog .sc-balance { margin:20px 0; padding:18px; border:1px solid color-mix(in srgb,var(--portal-gold) 30%,transparent); border-radius:10px; background:color-mix(in srgb,var(--portal-gold) 5%,transparent); }
.sc-balance span { font-size:12px; color:var(--portal-muted); }
.sc-balance strong { display:block; margin-top:9px; font-size:29px; line-height:1.2; font-weight:650; font-variant-numeric:tabular-nums; }
.sc-dialog .sc-balance label { margin:0 0 9px; color:var(--portal-muted); font-weight:400; }
.sc-dialog .sc-amount-input { width:100%; min-width:0; padding:0; border:0; background:transparent; color:var(--portal-text); font-family:inherit; font-size:29px; font-weight:650; outline-offset:5px; font-variant-numeric:tabular-nums; }
.sc-dialog .sc-qr { text-align:center; margin-block:18px; }
.sc-dialog .sc-qr img { display:block; max-width:100%; margin:10px auto; border-radius:10px; }
.sc-dialog .sc-note, .sc-dialog .sc-feedback { font-size:12px; line-height:1.7; color:var(--portal-muted); }
.sc-dialog .sc-feedback { color:var(--sc-red,#f3a58f); }
.sc-dialog .sc-modal-actions { display:flex; justify-content:flex-end; flex-wrap:wrap; gap:10px; padding-top:20px; margin-top:20px; border-top:1px solid var(--portal-border); }
.sc-dialog .sc-success { padding:25px 0 10px; text-align:center; }
.sc-success > svg { width:44px; height:44px; color:#58d5ae; margin-bottom:14px; }
.sc-success h3 { margin:0 0 10px; font-size:19px; }
.sc-success p { color:var(--portal-muted); font-size:13px; line-height:1.7; }
html[data-theme="light"] body.staff-portal:has(.sc-workspace) { background:var(--portal-bg); }
html[data-theme="light"] body.staff-portal:has(.sc-workspace) .portal-content { background:var(--portal-bg); }
html[data-theme="light"] :is(.sc-workspace,.sc-dialog) .sc-primary { color:#fff; }
html[data-theme="light"] .staff-portal #global-modal:has(.sc-dialog) .modal-content { background:var(--portal-panel); }
html[data-theme="light"] .sc-workspace { min-width:0; width:100%; --sc-green:#147a59; --sc-red:#b54d35; }
html[data-theme="light"] .sc-dialog .sc-feedback { color:#a33d2a; }
@media(min-width:1500px) { #view-container .sc-columns,#view-container .checkout-card { column-gap:28px; padding-inline:26px; } }
@media(max-width:1150px) { #view-container .sc-columns,#view-container .checkout-card { grid-template-columns:68px minmax(130px,1fr) minmax(150px,1fr) 176px; column-gap:14px; } .sc-amount { grid-column:4; grid-row:1; margin-bottom:8px; } #view-container .sc-columns span:nth-child(4) { display:none; } .sc-action { grid-column:4; grid-row:2; } #view-container .checkout-card { grid-template-rows:auto auto; row-gap:10px; } .sc-room,.sc-guest,.sc-dates { grid-row:1 / 3; } .sc-action small { display:none; } }
@media(max-width:900px) { #view-container .sc-intro { align-items:flex-start; } #view-container .sc-stats { gap:8px; margin-bottom:16px; } #view-container .sc-stat { padding:15px 12px; min-height:91px; } .sc-stat-icon { display:none; } .sc-stat-label { font-size:10px; line-height:1.5; } .sc-stat-value { font-size:25px; } #view-container .sc-toolbar { padding:14px; gap:12px; } #view-container .sc-search { max-width:none; flex-basis:100%; } #view-container .sc-filters { width:100%; } #view-container .sc-filter { padding:6px 8px; font-size:11px; } #view-container .sc-columns { display:none; } #view-container .checkout-card { position:relative; grid-template-columns:54px minmax(0,1fr); gap:14px; padding:18px 14px; } .sc-room { grid-row:1; grid-column:1; align-self:start; padding-top:3px; } .sc-guest { grid-row:1; grid-column:2; } .sc-dates { grid-row:2; grid-column:1 / -1; padding:12px 0; border-block:1px solid var(--portal-border); } .sc-dates div { gap:12px; } .sc-amount { grid-row:3; grid-column:1 / -1; padding-right:160px; min-height:42px; margin:0; display:flex; flex-direction:column; justify-content:center; } .sc-amount strong { font-size:14px; } .sc-amount small { font-size:9px; } .sc-action { position:absolute; bottom:18px; right:14px; width:148px; } #view-container .sc-primary { font-size:11px; padding-inline:9px; } #view-container .sc-list-footer { padding:13px 14px; } }
@media(prefers-reduced-motion:reduce) { #view-container .checkout-card { transition:none; } }
`;

export async function renderCheckout(container, session) {
    container.innerHTML = `<style>${styles}</style><section class="sc-workspace" aria-label="Danh sách khách đang lưu trú">
        <div class="sc-intro"><div><small>QUẢN LÝ LƯU TRÚ</small><p>Theo dõi phòng đang lưu trú và thanh toán cho khách.</p></div>
            <button type="button" class="sc-secondary" id="sc-refresh"><i data-lucide="refresh-cw"></i>Làm mới</button></div>
        <div class="sc-stats">
            <button type="button" class="sc-stat" data-filter="all"><div><p class="sc-stat-label">Phòng đang lưu trú</p><strong class="sc-stat-value" id="sc-total">—</strong></div><span class="sc-stat-icon"><i data-lucide="bed-double"></i></span></button>
            <button type="button" class="sc-stat" data-filter="today"><div><p class="sc-stat-label">Dự kiến trả hôm nay</p><strong class="sc-stat-value" id="sc-today">—</strong></div><span class="sc-stat-icon"><i data-lucide="calendar-check"></i></span></button>
            <button type="button" class="sc-stat" data-filter="overdue"><div><p class="sc-stat-label">Quá ngày trả dự kiến</p><strong class="sc-stat-value" id="sc-overdue">—</strong></div><span class="sc-stat-icon"><i data-lucide="clock-3"></i></span></button>
        </div>
        <div class="sc-panel"><div class="sc-toolbar">
            <label class="sc-search"><i data-lucide="search"></i><input id="sc-search" type="search" aria-label="Tìm phòng, tên khách hoặc mã đặt phòng" placeholder="Tìm phòng, tên khách, mã đặt phòng…" autocomplete="off"></label>
            <div class="sc-filters" role="group" aria-label="Lọc theo ngày trả phòng">
                ${[['all','Tất cả'],['today','Trả hôm nay'],['overdue','Quá dự kiến']].map(([key,label]) => `<button type="button" class="sc-filter" id="sc-filter-${key}" data-filter="${key}" aria-pressed="${key === 'all'}">${label}<span id="sc-count-${key}">—</span></button>`).join('')}
            </div></div>
            <div class="sc-columns" aria-hidden="true"><span>Phòng</span><span>Khách lưu trú</span><span>Thời gian lưu trú</span><span>Giá trị đặt phòng</span><span>Thao tác</span></div>
            <div id="checkout-grid" aria-live="polite" aria-busy="true"></div>
            <div class="sc-list-footer"><span id="sc-results">Đang tải danh sách…</span><span>Ưu tiên phòng có ngày trả sớm nhất</span></div>
        </div></section>`;
    const byId = id => document.getElementById(id);
    const grid = byId('checkout-grid');
    let activeBookings = [], filter = 'all', requestVersion = 0;
    let currentProcessBooking = null;
    const empty = (title, detail, icon = 'search') => `<div class="sc-empty"><i data-lucide="${icon}"></i><h3>${title}</h3><p>${escapeHtml(detail)}</p></div>`;

    const renderList = () => {
        const now = new Date();
        const counts = { all: activeBookings.length, today: 0, overdue: 0 };
        for (const booking of activeBookings) {
            const state = departureState(booking, now).key;
            if (state in counts) counts[state]++;
        }
        byId('sc-total').textContent = counts.all;
        byId('sc-today').textContent = counts.today;
        byId('sc-overdue').textContent = counts.overdue;
        for (const key of Object.keys(counts)) {
            byId(`sc-count-${key}`).textContent = counts[key];
            byId(`sc-filter-${key}`).setAttribute('aria-pressed', String(filter === key));
        }
        const query = searchText(byId('sc-search').value).trim();
        const list = activeBookings.filter(b => (filter === 'all' || departureState(b, now).key === filter)
            && searchText(`${b.roomNumber} ${b.customerName} ${b.customerPhone} ${b.id}`).includes(query));
        grid.innerHTML = '';
        if (!list.length) grid.innerHTML = activeBookings.length
            ? empty('Không tìm thấy phòng phù hợp', 'Thử tìm tên khách, số phòng hoặc chọn bộ lọc khác.')
            : empty('Chưa có khách đang lưu trú', 'Các phòng đã nhận khách sẽ xuất hiện tại đây để theo dõi và thanh toán.', 'bed-double');
        for (const b of list) {
            const state = departureState(b, now);
            const row = document.createElement('article');
            row.className = 'checkout-card';
            row.setAttribute('aria-label', `Phòng ${b.roomNumber}, ${b.customerName}`);
            row.innerHTML = `<div class="sc-room"><strong aria-label="Phòng ${escapeHtml(b.roomNumber)}">${escapeHtml(b.roomNumber)}</strong><small>ĐP #${escapeHtml(b.id)}</small></div>
                <div class="sc-guest"><h3>${escapeHtml(b.customerName)}</h3>${b.customerPhone ? `<p>${escapeHtml(b.customerPhone)}</p>` : ''}<span class="sc-pill sc-pill-${state.tone}">${state.label}</span></div>
                <div class="sc-dates"><div><span>Nhận</span><time>${dateLabel(b.checkInDate)}</time></div><div><span>Trả</span><time>${dateLabel(b.checkOutDate)}</time></div></div>
                <div class="sc-amount"><strong>${money(b.totalPrice)}</strong><small>Giá trị đơn đặt phòng</small></div>
                <div class="sc-action"><button type="button" class="sc-primary btn-pay" data-id="${escapeHtml(b.id)}" aria-label="Thanh toán phòng ${escapeHtml(b.roomNumber)}">Thanh toán<i data-lucide="arrow-up-right"></i></button><small>Xem & xác nhận thanh toán</small></div>`;
            grid.appendChild(row);
        }
        byId('sc-results').textContent = `Hiển thị ${list.length} / ${activeBookings.length} phòng đang lưu trú`;
        document.querySelectorAll('.btn-pay').forEach(button => button.addEventListener('click', event => openModal(event.currentTarget?.dataset.id ?? event.target.dataset.id)));
        icons();
    };

    const loadData = async () => {
        const version = ++requestVersion;
        byId('sc-refresh').disabled = true;
        grid.setAttribute('aria-busy', 'true');
        grid.innerHTML = empty('Đang tải danh sách lưu trú', 'Vui lòng chờ trong giây lát.', 'loader-circle');
        try {
            const [bookings, rooms, customers] = await Promise.all([api.get('/bookings'), api.get('/rooms'), api.get('/customers')]);
            const checkedIn = bookings.filter(b => String(b.status || '').toLowerCase() === 'checked_in');
            const customersById = new Map(customers.map(c => [String(c.id), c]));
            const missingIds = [...new Set(checkedIn.map(b => String(b.customerId)))].filter(id => Number.isInteger(Number(id)) && Number(id) > 0 && !customerFullName(customersById.get(id)));
            await Promise.all(missingIds.map(async id => {
                try {
                    const customer = await api.get(`/customers/${encodeURIComponent(id)}`);
                    if (String(customer?.id) === id) customersById.set(id, customer);
                } catch (error) { if (error.status !== 404) throw error; }
            }));
            if (version !== requestVersion || grid.isConnected === false) return;
            activeBookings = checkedIn.map(b => {
                const customer = customersById.get(String(b.customerId));
                const room = rooms.find(r => String(r.id) === String(b.roomId));
                return { ...b, roomNumber: room?.roomNumber || '—', customerPhone: customer?.phone || '',
                    customerName: customerFullName(customer) || (Number(b.customerId) > 0 ? `Chưa có thông tin khách hàng (KH #${b.customerId})` : 'Đơn đặt phòng thiếu thông tin khách hàng') };
            }).sort((a,b) => (new Date(a.checkOutDate).getTime() || Infinity) - (new Date(b.checkOutDate).getTime() || Infinity) || Number(a.id) - Number(b.id));
            renderList();
        } catch (error) {
            if (version !== requestVersion || grid.isConnected === false) return;
            grid.innerHTML = empty('Chưa tải được danh sách', `Lỗi tải dữ liệu: ${error.message}. Chọn Làm mới để thử lại.`, 'circle-alert');
            for (const id of ['sc-total','sc-today','sc-overdue','sc-count-all','sc-count-today','sc-count-overdue']) byId(id).textContent = '—';
            byId('sc-results').textContent = 'Chưa có dữ liệu để hiển thị';
        } finally {
            if (version === requestVersion && grid.isConnected !== false) { grid.setAttribute('aria-busy','false'); byId('sc-refresh').disabled = false; icons(); }
        }
    };

    const openModal = bookingId => {
        const b = activeBookings.find(booking => String(booking.id) === String(bookingId));
        if (!b) return;
        currentProcessBooking = b;
        const modal = byId('global-modal'), content = byId('global-modal-content');
        const close = () => { modal.classList.remove('active'); modal.setAttribute('aria-hidden', 'true'); };
        content.innerHTML = `<section class="sc-dialog">
            <div class="sc-modal-head"><div><h2>Xác nhận thanh toán</h2><p>Kiểm tra thông tin và số tiền trước khi xác nhận</p></div><button type="button" class="sc-close" id="sc-modal-close" aria-label="Đóng">×</button></div>
            <div class="sc-modal-guest"><div><strong>${escapeHtml(b.customerName)}</strong><small>Đặt phòng #${escapeHtml(b.id)}</small></div><div><strong>Phòng ${escapeHtml(b.roomNumber)}</strong><small>Trả dự kiến: ${dateLabel(b.checkOutDate)}</small></div></div>
            <div id="modal-checkout-form">
                <div class="sc-balance"><label for="pay-amount">Số tiền thu (VNĐ)</label><input class="sc-amount-input" id="pay-amount" type="number" min="0" value="${Number(b.totalPrice) || 0}" required></div>
                <label for="pay-method">Phương thức thanh toán</label><select id="pay-method"><option value="Cash">Tiền mặt</option><option value="Credit Card">Thẻ tín dụng (POS)</option><option value="Chuyển khoản">Chuyển khoản ngân hàng</option></select>
                <div id="qr-code-section" class="sc-qr" style="display:none"><p class="sc-note">Quét mã VietQR để thanh toán</p><img id="qr-image" src="" alt="Mã VietQR thanh toán" width="200" height="200"></div>
                <p class="sc-note">Đối chiếu số tiền và giao dịch của khách trước khi hoàn tất thanh toán.</p>
            </div>
            <div id="modal-success-screen" class="sc-success" style="display:none"><i data-lucide="circle-check"></i><h3>Đã ghi nhận thanh toán</h3><p>Bạn có thể xem hoặc in hóa đơn cho khách.</p><div class="sc-modal-actions"><button type="button" class="sc-secondary" id="btn-print-invoice"><i data-lucide="printer"></i>In hóa đơn PDF</button><button type="button" class="sc-primary" id="sc-finish">Hoàn tất</button></div></div>
            <div class="sc-modal-actions" id="modal-footer-form"><button type="button" class="sc-secondary" id="sc-cancel-pay">Quay lại</button><button type="button" class="sc-primary" id="btn-confirm-pay">Hoàn tất thanh toán</button></div>
        </section>`;
        byId('sc-modal-close').addEventListener('click', close);
        byId('sc-cancel-pay').addEventListener('click', close);
        byId('sc-finish').addEventListener('click', () => { close(); loadData(); });
        modal.classList.add('active'); modal.setAttribute('aria-hidden','false');
        byId('sc-modal-close').focus();
        icons();
        // Bind events inside modal
        const payMethodSelect = document.getElementById("pay-method");
        const payAmountInput = document.getElementById("pay-amount");
        const qrCodeSection = document.getElementById("qr-code-section");
        const qrImage = document.getElementById("qr-image");
        const btnConfirm = document.getElementById("btn-confirm-pay");
        
        const updateQRCode = () => {
            if (payMethodSelect.value === "Chuyển khoản") {
                const amount = payAmountInput.value || 0;
                const bookingId = currentProcessBooking ? currentProcessBooking.id : '';
                qrImage.src = `https://img.vietqr.io/image/970436-1012345678-compact2.png?amount=${amount}&addInfo=Thanh toan don ${bookingId}&accountName=QUAN LY KHACH SAN`;
                qrCodeSection.style.display = "block";
            } else {
                qrCodeSection.style.display = "none";
            }
        };

        payMethodSelect.addEventListener("change", updateQRCode);
        payAmountInput.addEventListener("input", updateQRCode);
        
        btnConfirm.addEventListener('click', async () => {
            if (!currentProcessBooking) return;
            
            const amount = document.getElementById("pay-amount").value;
            const method = document.getElementById("pay-method").value;
            
            if (!amount || amount < 0) {
                alert("Vui lòng nhập số tiền hợp lệ!");
                return;
            }

            const payload = {
                bookingId: currentProcessBooking.id,
                customerId: currentProcessBooking.customerId,
                amount: parseFloat(amount),
                paymentMethod: method
            };

            try {
                btnConfirm.disabled = true;
                btnConfirm.textContent = "Đang xử lý...";
                
                await api.post("/payments", payload);
                
                // Handle success
                document.getElementById("modal-checkout-form").style.display = 'none';
                document.getElementById("modal-footer-form").style.display = 'none';
                document.getElementById("modal-success-screen").style.display = 'block';
                if (window.lucide) lucide.createIcons();

                document.getElementById("btn-print-invoice").onclick = async () => {
                    try {
                        const invoices = await api.get("/invoices");
                        const invoice = invoices.find(i => i.bookingId == currentProcessBooking.id);
                        if (invoice) {
                            window.open(getApiBaseUrl() + "/invoices/" + invoice.id + "/pdf", "_blank");
                        } else {
                            alert("Không tìm thấy hóa đơn cho đơn đặt phòng này!");
                        }
                    } catch (err) {
                        console.error(err);
                    }
                    document.getElementById('global-modal').classList.remove('active');
                    loadData();
                };
            } catch (e) {
                console.error(e);
                alert("Lỗi thanh toán: " + e.message);
            } finally {
                btnConfirm.disabled = false;
                btnConfirm.textContent = "Hoàn tất Thanh toán";
            }
        });
    };

    byId('sc-refresh').addEventListener('click', loadData);
    byId('sc-search').addEventListener('input', renderList);
    for (const key of ['all','today','overdue']) {
        byId(`sc-filter-${key}`).addEventListener('click', () => { filter = key; renderList(); });
    }
    container.querySelectorAll?.('.sc-stat').forEach(button => button.addEventListener('click', () => { filter = button.dataset.filter; renderList(); }));
    icons();
    await loadData();
}
