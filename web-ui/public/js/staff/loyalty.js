
import { MEMBERSHIP_TIERS } from '../customer/tier-helper.js';

export function renderLoyalty(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Chính Sách Hệ Thành Viên</h1>
            <p>Xếp hạng theo tổng điểm đã tích lũy: mỗi 1.000 VNĐ được 1 điểm. Đổi điểm không làm giảm hạng.</p>
        </div>
        <div class="membership-policy-grid" aria-label="Các hạng thành viên">
            ${MEMBERSHIP_TIERS.map(tier => `
                <section class="card membership-policy-card" data-membership-tier="${tier.name.toLowerCase()}">
                    <h2 class="card-title">${tier.name}</h2>
                    <p class="membership-policy-threshold">Từ ${tier.minPoints.toLocaleString('vi-VN')} điểm tích lũy</p>
                    <span class="badge membership-policy-badge">${{ Member: 'Hạng tiêu chuẩn', Silver: 'Hạng Bạc', Gold: 'Hạng Vàng', VIP: 'Hạng VIP' }[tier.name]}</span>
                </section>
            `).join('')}

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
