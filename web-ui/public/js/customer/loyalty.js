import { api } from "../../api.js";

export async function renderLoyalty(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Khách Hàng Thân Thiết</h1>
            <p>Theo dõi điểm tích lũy và đổi điểm lấy ưu đãi độc quyền dành cho thành viên.</p>
        </div>

        <!-- Hero Points Card (populated after data fetch) -->
        <div id="loyalty-hero-wrap"></div>

        <div class="grid grid-cols-3">
            <!-- Left: Redeem Panel -->
            <div style="grid-column: span 1;">
                <div class="card" style="margin-bottom: 0;">
                    <h3 style="font-size: 16px; font-weight: 700; margin-bottom: 4px;">Đổi Điểm Lấy Voucher</h3>
                    <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 20px;">Chọn gói đổi điểm phù hợp với bạn</p>

                    <div id="loyalty-redeem-msg" style="font-size: 13px; margin-bottom: 12px;"></div>

                    <!-- Redeem Card 100pt -->
                    <div class="redeem-card" id="redeem-card-100">
                        <div class="redeem-card-icon">
                            <i data-lucide="ticket" style="width: 20px; height: 20px;"></i>
                        </div>
                        <div class="redeem-card-title">Gói Cơ Bản</div>
                        <div class="redeem-card-desc">Đổi điểm tích lũy lấy Voucher giảm giá trực tiếp vào hóa đơn phòng tiếp theo.</div>
                        <div class="redeem-card-cost">100 Điểm</div>
                        <div style="font-size: 12px; color: var(--success); font-weight: 600; margin-bottom: 12px;">→ Nhận Voucher 50.000 VNĐ</div>
                        <button class="btn btn-primary btn-sm" id="btn-redeem-100" style="width: 100%;">Đổi ngay</button>
                    </div>

                    <!-- Redeem Card 500pt -->
                    <div class="redeem-card" id="redeem-card-500">
                        <div class="redeem-card-icon" style="background: rgba(6,182,212,0.15); color: #22d3ee;">
                            <i data-lucide="crown" style="width: 20px; height: 20px;"></i>
                        </div>
                        <div class="redeem-card-title">Gói Cao Cấp</div>
                        <div class="redeem-card-desc">Dành cho thành viên tích cực. Giá trị Voucher cao hơn đáng kể so với gói cơ bản.</div>
                        <div class="redeem-card-cost" style="color: #22d3ee;">500 Điểm</div>
                        <div style="font-size: 12px; color: var(--success); font-weight: 600; margin-bottom: 12px;">→ Nhận Voucher 300.000 VNĐ</div>
                        <button class="btn btn-sm" id="btn-redeem-500" style="width: 100%; background: rgba(6,182,212,0.2); color: #22d3ee; border: 1px solid rgba(6,182,212,0.4);">Đổi ngay</button>
                    </div>
                </div>
            </div>

            <!-- Right: History Table -->
            <div style="grid-column: span 2;">
                <div class="card" style="margin-bottom: 0;">
                    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px;">
                        <div>
                            <h3 style="font-size: 16px; font-weight: 700; margin-bottom: 2px;">Lịch Sử Giao Dịch Điểm</h3>
                            <p style="font-size: 13px; color: var(--text-muted);">Toàn bộ lịch sử tích lũy và đổi điểm của bạn</p>
                        </div>
                        <i data-lucide="history" style="width: 20px; height: 20px; color: var(--text-muted);"></i>
                    </div>
                    <div class="table-container">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>Thời gian</th>
                                    <th>Mô tả</th>
                                    <th style="text-align: right;">Biến động điểm</th>
                                </tr>
                            </thead>
                            <tbody id="loyalty-history-body">
                                <tr>
                                    <td colspan="3" style="text-align: center; padding: 40px; color: var(--text-muted);">
                                        <div style="display:flex;align-items:center;justify-content:center;gap:8px;">
                                            <i data-lucide="loader" style="width:16px;height:16px;"></i> Đang tải lịch sử...
                                        </div>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                    <div id="pagination-loyalty-history"></div>
                </div>
            </div>
        </div>
    `;
    lucide.createIcons();

    // Helper: tính tier từ điểm tích lũy ròng
    const getTierInfo = (pts) => {
        if (pts >= 1000) return { name: 'VIP',    cls: 'tier-vip',    icon: '👑', next: null,   nextPts: 0,    progress: 100 };
        if (pts >= 300)  return { name: 'Gold',   cls: 'tier-gold',   icon: '⭐', next: 'VIP',  nextPts: 1000, progress: Math.round((pts - 300) / 700 * 100) };
        return              { name: 'Silver', cls: 'tier-silver', icon: '🥈', next: 'Gold', nextPts: 300,  progress: Math.round(pts / 300 * 100) };
    };

    // Helper: màu avatar ngẫu nhiên theo hash
    const avatarColors = ['#d97706','#10b981','#3b82f6','#8b5cf6','#06b6d4','#ef4444'];
    const strColor = (str) => avatarColors[(str || 'U').charCodeAt(0) % avatarColors.length];

    const loadHistory = async () => {
        try {
            const history = await api.get(`/loyalty/history/${session.customerId}`);
            const body = document.getElementById("loyalty-history-body");
            body.innerHTML = "";

            // Tính tổng điểm hiện tại
            let totalPoints = 0;
            if (history && history.length > 0) {
                history.forEach(log => {
                    if (log.changeType === 'earn') totalPoints += log.pointsChanged;
                    else totalPoints -= log.pointsChanged;
                });
            }

            // Render hero card
            const tier = getTierInfo(Math.max(0, totalPoints));
            const heroWrap = document.getElementById("loyalty-hero-wrap");
            heroWrap.innerHTML = `
                <div class="loyalty-hero">
                    <div class="loyalty-hero-label">Điểm tích lũy của bạn</div>
                    <div class="loyalty-hero-points">${Math.max(0, totalPoints).toLocaleString('vi-VN')}<span>Điểm</span></div>
                    <div>
                        <span class="tier-badge ${tier.cls}">${tier.icon} Hạng ${tier.name}</span>
                    </div>
                    ${tier.next ? `
                    <div class="loyalty-progress-wrap">
                        <div class="loyalty-progress-info">
                            <span>Tiến độ lên hạng ${tier.next}</span>
                            <span>${Math.max(0, totalPoints).toLocaleString()} / ${tier.nextPts.toLocaleString()} điểm</span>
                        </div>
                        <div class="loyalty-progress-bar">
                            <div class="loyalty-progress-fill" style="width: 0%;" data-target="${tier.progress}"></div>
                        </div>
                    </div>` : `<div style="font-size: 13px; color: #22d3ee; margin-top: 8px;">🎉 Bạn đã đạt hạng thành viên cao nhất!</div>`}
                </div>
            `;

            // Animate progress bar
            setTimeout(() => {
                const fill = heroWrap.querySelector('.loyalty-progress-fill');
                if (fill) fill.style.width = fill.dataset.target + '%';
            }, 100);

            // Render history
        let allHistory = history || [];
        let currentPage = 1;
        let itemsPerPage = 10;

        const renderTable = () => {
            const body = document.getElementById("loyalty-history-body");
            body.innerHTML = "";

            if (!allHistory || allHistory.length === 0) {
                body.innerHTML = `
                    <tr>
                        <td colspan="3">
                            <div class="empty-state">
                                <div class="empty-state-icon"><i data-lucide="inbox" style="width:28px;height:28px;"></i></div>
                                <p>Chưa có giao dịch điểm nào</p>
                                <small>Đặt phòng để bắt đầu tích lũy điểm thưởng!</small>
                            </div>
                        </td>
                    </tr>`;
                lucide.createIcons();
                const paginationContainer = document.getElementById("pagination-loyalty-history");
                if (paginationContainer) paginationContainer.innerHTML = '';
                return;
            }

            const start = (currentPage - 1) * itemsPerPage;
            const end = start + itemsPerPage;
            const paginatedList = allHistory.slice(start, end);

            paginatedList.forEach((log, i) => {
                const isEarn = log.changeType === 'earn';
                const tr = document.createElement("tr");
                tr.className = "history-row";
                tr.style.animationDelay = `${i * 0.05}s`;
                tr.innerHTML = `
                    <td style="color: var(--text-muted); font-size: 13px;">${log.changeDate || 'N/A'}</td>
                    <td>
                        <div style="display:flex;align-items:center;gap:8px;">
                            <div style="width:28px;height:28px;border-radius:50%;background:${isEarn ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)'};display:flex;align-items:center;justify-content:center;flex-shrink:0;">
                                <i data-lucide="${isEarn ? 'arrow-up' : 'arrow-down'}" style="width:14px;height:14px;color:${isEarn ? 'var(--success)' : 'var(--danger)'};"></i>
                            </div>
                            <span style="font-size:14px;">${log.reason}</span>
                        </div>
                    </td>
                    <td style="text-align: right;">
                        <span class="${isEarn ? 'history-earn' : 'history-redeem'}" style="font-size:16px;">
                            ${isEarn ? '+' : '-'}${log.pointsChanged}
                        </span>
                        <span style="font-size:12px;color:var(--text-muted);margin-left:2px;">Điểm</span>
                    </td>
                `;
                body.appendChild(tr);
            });
            lucide.createIcons();

            if (window.renderPaginationComponent) {
                window.renderPaginationComponent(
                    "pagination-loyalty-history",
                    allHistory.length,
                    itemsPerPage,
                    currentPage,
                    (newPage) => {
                        currentPage = newPage;
                        renderTable();
                    },
                    (newItemsPerPage) => {
                        itemsPerPage = newItemsPerPage;
                        currentPage = 1;
                        renderTable();
                    }
                );
            }
        };

        renderTable();
        } catch (e) {
            console.error(e);
        }
    };
    loadHistory();

    const redeem = async (points, discount) => {
        const msgDiv = document.getElementById("loyalty-redeem-msg");
        msgDiv.innerHTML = "";
        try {
            await api.post("/loyalty/redeem", {
                customerId: session.customerId,
                pointsToRedeem: points,
                discountAmount: discount
            });
            msgDiv.innerHTML = `<span style="color:var(--success);display:flex;align-items:center;gap:6px;"><i data-lucide="check-circle" style="width:14px;height:14px;"></i> Đổi điểm thành công!</span>`;
            lucide.createIcons();
            loadHistory();
        } catch (e) {
            msgDiv.innerHTML = `<span style="color:var(--danger);display:flex;align-items:center;gap:6px;"><i data-lucide="x-circle" style="width:14px;height:14px;"></i> ${e.message}</span>`;
            lucide.createIcons();
        }
    };

    document.getElementById("btn-redeem-100").addEventListener("click", () => redeem(100, 50000));
    document.getElementById("btn-redeem-500").addEventListener("click", () => redeem(500, 300000));
}
