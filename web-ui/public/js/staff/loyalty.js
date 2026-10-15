export function renderLoyalty(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Chính Sách Hệ Thành Viên</h1>
            <p>Hệ thống xếp hạng khách hàng thân thiết dựa trên số điểm tích lũy phòng.</p>
        </div>
        
        <div class="grid grid-cols-3">
            <div class="card" style="border-top: 4px solid var(--text-muted);">
                <div style="font-weight: 700; font-size: 20px; margin-bottom: 8px;">Silver (Bạc)</div>
                <div style="font-size: 14px; color: var(--text-muted); margin-bottom: 20px;">Dành cho tài khoản mới đăng ký.</div>
                <div class="text-gold" style="font-weight: 600;">Mặc định</div>
            </div>
            
            <div class="card" style="border-top: 4px solid var(--color-booked);">
                <div style="font-weight: 700; font-size: 20px; margin-bottom: 8px;">Gold (Vàng)</div>
                <div style="font-size: 14px; color: var(--text-muted); margin-bottom: 20px;">Đạt từ 300 điểm tích lũy phòng.</div>
                <div class="text-gold" style="font-weight: 600;">Giảm 5% hóa đơn phòng</div>
            </div>
            
            <div class="card" style="border-top: 4px solid var(--color-cleaning);">
                <div style="font-weight: 700; font-size: 20px; margin-bottom: 8px;">VIP (Kim Cương)</div>
                <div style="font-size: 14px; color: var(--text-muted); margin-bottom: 20px;">Đạt từ 1.000 điểm tích lũy phòng.</div>
                <div class="text-gold" style="font-weight: 600;">Giảm 10% hóa đơn phòng</div>
            </div>
        </div>
    `;
}
