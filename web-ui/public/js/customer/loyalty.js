import { api } from "../../api.js";

export async function renderLoyalty(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Điểm Thưởng Thành Viên</h1>
            <p>Theo dõi lịch sử tích lũy điểm và quy đổi điểm lấy các phần quà ưu đãi giảm giá phòng.</p>
        </div>

        <div class="grid grid-cols-3">
            <div class="card" style="grid-column: span 1;">
                <h3 style="font-size: 18px; margin-bottom: 20px;">Đổi điểm lấy Vouchers</h3>
                <div id="loyalty-redeem-msg" style="font-size: 14px; margin-bottom: 15px;"></div>
                
                <div class="form-group" style="background-color: var(--bg-card); padding: 15px; border-radius: var(--radius-sm); border: 1px solid var(--border); margin-bottom: 15px;">
                    <div style="font-weight: 600; font-size: 15px;">Gói đổi điểm 100 điểm</div>
                    <div style="font-size: 13px; color: var(--text-muted); margin-bottom: 10px;">Đổi 100 điểm tích lũy lấy Voucher giảm giá 50.000 VNĐ trực tiếp.</div>
                    <button class="btn btn-primary btn-sm" id="btn-redeem-100">Đổi ngay</button>
                </div>
                
                <div class="form-group" style="background-color: var(--bg-card); padding: 15px; border-radius: var(--radius-sm); border: 1px solid var(--border);">
                    <div style="font-weight: 600; font-size: 15px;">Gói đổi điểm 500 điểm</div>
                    <div style="font-size: 13px; color: var(--text-muted); margin-bottom: 10px;">Đổi 500 điểm tích lũy lấy Voucher giảm giá 300.000 VNĐ trực tiếp.</div>
                    <button class="btn btn-primary btn-sm" id="btn-redeem-500">Đổi ngay</button>
                </div>
            </div>

            <div class="card" style="grid-column: span 2;">
                <h3 style="font-size: 18px; margin-bottom: 20px;">Lịch sử giao dịch điểm</h3>
                <div class="table-container">
                    <table class="table">
                        <thead>
                            <tr>
                                <th>Thời gian</th>
                                <th>Hành động</th>
                                <th>Biến động</th>
                            </tr>
                        </thead>
                        <tbody id="loyalty-history-body">
                            <tr><td colspan="3" style="text-align: center;">Đang tải lịch sử...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    `;

    const loadHistory = async () => {
        try {
            const history = await api.get(`/loyalty/history/${session.customerId}`);
            const body = document.getElementById("loyalty-history-body");
            body.innerHTML = "";
            if (!history || history.length === 0) {
                body.innerHTML = `<tr><td colspan="3" style="text-align: center; color: var(--text-muted);">Không có giao dịch điểm tích lũy nào.</td></tr>`;
                return;
            }
            history.forEach(log => {
                const tr = document.createElement("tr");
                const isEarn = log.changeType === 'earn';
                tr.innerHTML = `
                    <td>${log.changeDate || 'N/A'}</td>
                    <td>${log.reason}</td>
                    <td style="font-weight: 600; color: ${isEarn ? 'var(--success)' : 'var(--danger)'}">
                        ${isEarn ? '+' : '-'}${log.pointsChanged} Điểm
                    </td>
                `;
                body.appendChild(tr);
            });
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
            alert("Đổi điểm tích lũy lấy ưu đãi giảm giá thành công!");
            loadHistory();
        } catch (e) {
            msgDiv.innerHTML = `<span style="color: var(--danger);">${e.message}</span>`;
        }
    };

    document.getElementById("btn-redeem-100").addEventListener("click", () => redeem(100, 50000));
    document.getElementById("btn-redeem-500").addEventListener("click", () => redeem(500, 300000));
}
