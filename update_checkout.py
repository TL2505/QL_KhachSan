import re

def update_checkout():
    with open('web-ui/public/js/staff/checkout.js', 'r', encoding='utf-8') as f:
        content = f.read()

    # Find the start of the logic block
    start_index = content.find('const grid = document.getElementById("checkout-grid");')
    if start_index == -1:
        print("ERROR: could not find anchor in checkout.js")
        return

    # Build the new file content: keep the template, replace the logic
    header = content[:start_index]

    new_logic = r"""const grid = document.getElementById("checkout-grid");
    
    let activeBookings = [];
    let currentProcessBooking = null;

    const formatCurrency = (amount) => {
        return amount != null ? Number(amount).toLocaleString('vi-VN') + ' đ' : '0 đ';
    };
    
    const formatDate = (dateStr) => {
        if (!dateStr) return 'N/A';
        const d = new Date(dateStr);
        return d.toLocaleDateString('vi-VN') + ' ' + d.toLocaleTimeString('vi-VN', {hour: '2-digit', minute:'2-digit'});
    };

    const loadData = async () => {
        grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--text-muted);">Đang tải danh sách phòng...</p>';
        try {
            const [response, rooms, customers] = await Promise.all([
                api.get("/bookings"),
                api.get("/rooms"),
                api.get("/customers")
            ]);
            
            // Only get checked_in bookings
            activeBookings = response.filter(b => b.status === "checked_in").map(b => {
                const r = rooms.find(room => room.id === b.roomId);
                const c = customers.find(cust => cust.id === b.customerId);
                return {
                    ...b,
                    roomNumber: r ? r.roomNumber : 'N/A',
                    customerName: c ? c.fullName : 'Khách vãng lai'
                };
            });
            
            grid.innerHTML = "";
            if (activeBookings.length === 0) {
                grid.innerHTML = `
                    <div style="grid-column: 1/-1; text-align: center; padding: 40px; background: var(--bg-card); border-radius: 12px;">
                        <i data-lucide="check-circle" style="width: 48px; height: 48px; color: var(--success); margin-bottom: 15px;"></i>
                        <h3>Tất cả đã hoàn tất</h3>
                        <p style="color: var(--text-muted);">Hiện không có khách nào đang lưu trú cần thanh toán.</p>
                    </div>
                `;
                if(window.lucide) lucide.createIcons();
                return;
            }

            activeBookings.forEach(b => {
                const card = document.createElement("div");
                card.className = "checkout-card";
                card.innerHTML = `
                    <div class="card-header">
                        <div class="room-badge">Phòng ${b.roomNumber || 'N/A'}</div>
                        <div class="booking-id">#${b.id}</div>
                    </div>
                    <div class="card-body">
                        <p><span>Khách hàng</span> <strong>${b.customerName || 'Khách vãng lai'}</strong></p>
                        <p><span>Nhận phòng</span> <strong>${formatDate(b.checkInDate)}</strong></p>
                        <p><span>Trả phòng</span> <strong>${formatDate(b.checkOutDate)}</strong></p>
                    </div>
                    <div class="card-footer">
                        <div class="total-price">${formatCurrency(b.totalPrice)}</div>
                        <button class="btn-pay" data-id="${b.id}">Thanh Toán</button>
                    </div>
                `;
                grid.appendChild(card);
            });

            document.querySelectorAll('.btn-pay').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    openModal(e.target.dataset.id);
                });
            });

        } catch (e) {
            console.error(e);
            grid.innerHTML = `<p style="grid-column: 1/-1; text-align: center; color: var(--danger);">Lỗi tải dữ liệu: ${e.message}</p>`;
        }
    };

    const openModal = (bookingId) => {
        const b = activeBookings.find(x => x.id == bookingId);
        if (!b) return;
        currentProcessBooking = b;

        const modal = document.getElementById('global-modal');
        const content = document.getElementById('global-modal-content');

        content.innerHTML = `
            <div class="modal-header">
                <h2>Xác nhận Thanh toán</h2>
                <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
            </div>
            <div class="modal-body" id="modal-checkout-form" style="padding-top:10px;">
                <div style="background: var(--bg-input); padding: 15px; border-radius: 8px; margin-bottom: 20px;">
                    <p style="margin: 0 0 8px 0; color: var(--text-muted); font-size: 14px;">Phòng: <strong style="color: var(--text-color); font-size: 16px; float: right;">${b.roomNumber || 'N/A'}</strong></p>
                    <p style="margin: 0; color: var(--text-muted); font-size: 14px;">Khách hàng: <strong style="color: var(--text-color); font-size: 16px; float: right;">${b.customerName || 'Khách vãng lai'}</strong></p>
                </div>
                <div class="form-group" style="margin-bottom: 15px;">
                    <label style="display:block;margin-bottom:5px;font-size:14px;color:var(--text-muted);">Số tiền thu (VNĐ)</label>
                    <input type="number" class="form-control" id="pay-amount" value="${b.totalPrice || 0}" required style="width:100%;">
                </div>
                <div class="form-group" style="margin-bottom: 15px;">
                    <label style="display:block;margin-bottom:5px;font-size:14px;color:var(--text-muted);">Phương thức thanh toán</label>
                    <select class="form-control" id="pay-method" style="width:100%;">
                        <option value="Cash">Tiền mặt</option>
                        <option value="Credit Card">Thẻ tín dụng (POS)</option>
                        <option value="Chuyển khoản">Chuyển khoản ngân hàng</option>
                    </select>
                </div>
                <div id="qr-code-section" style="display: none; text-align: center; margin-top: 15px;">
                    <p style="font-size: 14px; color: var(--success); margin-bottom: 10px;">Quét mã VietQR để thanh toán</p>
                    <img id="qr-image" src="" alt="VietQR" style="width: 200px; height: 200px; border-radius: 8px; border: 2px solid var(--border);">
                </div>
            </div>
            <div class="modal-body" id="modal-success-screen" style="display: none; text-align: center; padding: 40px 20px;">
                <i data-lucide="check-circle" style="width: 64px; height: 64px; color: var(--success); margin-bottom: 20px; display: inline-block;"></i>
                <h3 style="margin-bottom: 10px;">Thanh Toán Thành Công!</h3>
                <p style="color: var(--text-muted); margin-bottom: 25px;">Hệ thống đã ghi nhận doanh thu và chuyển phòng sang trạng thái Đang Dọn Dẹp.</p>
                <button class="btn btn-primary" id="btn-print-invoice">
                    <i data-lucide="printer" style="width:16px;height:16px;"></i> In Hóa Đơn PDF
                </button>
            </div>
            <div class="modal-footer" id="modal-footer-form" style="margin-top:20px; display:flex; justify-content:flex-end; gap:10px;">
                <button class="btn btn-secondary" onclick="closeModal('global-modal')">Hủy</button>
                <button class="btn btn-primary" id="btn-confirm-pay">Hoàn tất Thanh toán</button>
            </div>
        `;
        if(window.lucide) lucide.createIcons();
        modal.classList.add('active');

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
"""

    new_content = header + new_logic + "\n    loadData();\n}\n"
    
    with open('web-ui/public/js/staff/checkout.js', 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("staff/checkout.js updated successfully")

update_checkout()
