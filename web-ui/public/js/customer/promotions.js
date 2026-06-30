import { api } from "../../api.js";

export async function renderPromotions(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Ưu Đãi & Khuyến Mãi Của Tôi</h1>
            <p>Xem danh sách các chương trình khuyến mãi hiện hành áp dụng tại khách sạn Aurelia.</p>
        </div>
        <div class="grid grid-cols-3" id="promotions-list-grid">Loading active promotions...</div>
    `;

    try {
        const promos = await api.get("/promotions/active");
        const grid = document.getElementById("promotions-list-grid");
        grid.innerHTML = "";
        if (!promos || promos.length === 0) {
            grid.innerHTML = "<div class='card' style='grid-column: span 3; text-align: center; color: var(--text-muted);'>Hiện tại không có chương trình khuyến mãi nào khả dụng.</div>";
            return;
        }

        promos.forEach(p => {
            const card = document.createElement("div");
            card.className = "card";
            card.style.borderTop = "4px solid var(--primary)";
            card.innerHTML = `
                <div style="font-weight: 700; font-size: 18px; margin-bottom: 8px;">${p.name}</div>
                <div style="font-size: 13px; color: var(--text-muted); margin-bottom: 12px; height: 40px; overflow: hidden;">${p.description || ''}</div>
                <div class="flex-row justify-between">
                    <div>
                        <span class="room-badge badge-cleaning" style="font-size: 12px; font-weight: 700;">
                            Giảm ${p.discountType === 'percentage' ? p.discountValue + '%' : p.discountValue.toLocaleString('vi-VN') + 'đ'}
                        </span>
                    </div>
                    <div style="font-size: 11px; color: var(--text-muted);">
                        Hết hạn: ${p.endDate || 'Vĩnh viễn'}
                    </div>
                </div>
            `;
            grid.appendChild(card);
        });
    } catch (e) {
        console.error(e);
    }
}
