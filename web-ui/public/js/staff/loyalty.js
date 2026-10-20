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
    `;
}
