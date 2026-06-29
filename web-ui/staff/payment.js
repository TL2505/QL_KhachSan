let allBookings = [];
let allCustomers = [];
let allRooms = [];
let selectedBooking = null;

let currentCalculatedDiscount = 0;
let currentServiceTotal = 0;
let redeemDiscount = 0;
let pointsToRedeem = 0;
let currentLoyaltyPoints = 0;
let finalTaxableAmount = 0;
let finalTotalAmount = 0;
let serviceUsages = [];

let printModalInstance = null;

document.addEventListener("DOMContentLoaded", function() {
    const user = checkAuth();
    if(user.role !== 'STAFF' && user.role !== 'ADMIN') {
        alert("Bạn không có quyền truy cập!");
        window.location.href = "/shared/login.html";
        return;
    }
    document.getElementById("welcomeMessage").textContent = `Xin chào, ${user.fullName}`;
    
    printModalInstance = new bootstrap.Modal(document.getElementById('invoicePrintModal'));
    loadPaymentData();
});

async function loadPaymentData() {
    document.getElementById("paymentTableBody").innerHTML = `<tr><td colspan="5" class="text-center py-4"><span class="spinner-border spinner-border-sm"></span> Đang tải dữ liệu...</td></tr>`;
    try {
        const [resB, resC, resR] = await Promise.all([
            fetchApi('/bookings'),
            fetchApi('/customers'),
            fetchApi('/rooms')
        ]);
        
        if (resB.status === "success" && resC.status === "success" && resR.status === "success") {
            allBookings = resB.data;
            allCustomers = resC.data;
            allRooms = resR.data;
            renderBookingList();
        } else {
            alert("Lỗi tải dữ liệu thanh toán");
        }
    } catch (e) {
        console.error(e);
        alert("Lỗi kết nối máy chủ!");
    }
}

function getCustomer(id) { return allCustomers.find(c => c.id === id); }
function getRoom(id) { return allRooms.find(r => r.id === id); }

function renderBookingList() {
    const tbody = document.getElementById("paymentTableBody");
    tbody.innerHTML = "";
    
    // Chỉ lấy phòng Đang ở (checked_in) hoặc Đã trả chờ thanh toán (checked_out)
    const unpaidBookings = allBookings.filter(b => b.status === "checked_in" || b.status === "checked_out");
    
    if (unpaidBookings.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-4">Không có hóa đơn nào cần thanh toán</td></tr>`;
        return;
    }
    
    unpaidBookings.forEach(b => {
        const cust = getCustomer(b.customerId);
        const room = getRoom(b.roomId);
        
        let statusBadge = b.status === "checked_in" ? 
            '<span class="badge bg-warning text-dark">Đang ở</span>' : 
            '<span class="badge bg-info text-dark">Chờ thanh toán</span>';
            
        const tr = document.createElement("tr");
        tr.style.cursor = "pointer";
        tr.onclick = () => selectBooking(b.id);
        
        tr.innerHTML = `
            <td class="ps-3">#${b.id}</td>
            <td class="fw-bold">${cust ? cust.fullName : "Không rõ"}</td>
            <td><span class="badge bg-secondary">${room ? room.roomNumber : "?"}</span></td>
            <td>${statusBadge}</td>
            <td class="pe-3 text-end"><button class="btn btn-sm btn-primary">Chọn</button></td>
        `;
        tbody.appendChild(tr);
    });
}

async function selectBooking(bookingId) {
    selectedBooking = allBookings.find(b => b.id === bookingId);
    if (!selectedBooking) return;
    
    document.getElementById("paymentActionSection").style.display = "block";
    document.getElementById("btnConfirmPay").disabled = false;
    document.getElementById("btnConfirmPay").innerHTML = "XÁC NHẬN THANH TOÁN";
    
    // Đặt lại các biến tính toán
    currentCalculatedDiscount = 0;
    currentServiceTotal = 0;
    redeemDiscount = 0;
    pointsToRedeem = 0;
    currentLoyaltyPoints = 0;
    serviceUsages = [];
    
    renderInvoicePreviewLoading();
    
    try {
        // Tải Dịch vụ, Khuyến mãi và Loyalty song song
        const [resServices, resPromo, resCustomer] = await Promise.all([
            fetchApi(`/service-usages/booking/${bookingId}`),
            fetchApi(`/promotions/best?bookingId=${bookingId}`),
            fetchApi(`/customers/${selectedBooking.customerId}`)
        ]);

        if (resServices.status === "success") {
            serviceUsages = resServices.data;
            currentServiceTotal = serviceUsages.reduce((sum, item) => sum + item.totalPrice, 0);
        }

        let promoName = "Không có";
        if (resPromo.status === "success" && resPromo.data.calculatedDiscount) {
            currentCalculatedDiscount = resPromo.data.calculatedDiscount;
            promoName = resPromo.data.promotion ? resPromo.data.promotion.name : "Khuyến mãi tự động";
        }

        if (resCustomer.status === "success") {
            currentLoyaltyPoints = resCustomer.data.loyaltyPoints || 0;
        }

        renderInvoicePreview(promoName, resCustomer.data);
        toggleQR(); // Cập nhật QR nếu đang chọn Chuyển khoản
        
    } catch (e) {
        console.error(e);
        alert("Lỗi tải thông tin chi tiết hóa đơn!");
    }
}

function renderInvoicePreviewLoading() {
    document.getElementById("invoiceContent").innerHTML = `<div class="text-center py-4"><span class="spinner-border spinner-border-sm"></span> Đang tính toán...</div>`;
}

function renderInvoicePreview(promoName, customerDetails) {
    const cust = getCustomer(selectedBooking.customerId);
    const room = getRoom(selectedBooking.roomId);
    
    const checkIn = new Date(selectedBooking.checkInDate);
    const checkOut = new Date(selectedBooking.checkOutDate);
    let diffDays = Math.ceil((checkOut - checkIn) / (1000 * 60 * 60 * 24));
    if (diffDays <= 0) diffDays = 1;
    
    const roomPrice = room ? room.price : 0;
    const roomTotal = roomPrice * diffDays;
    
    // Box đổi điểm
    let loyaltyHtml = '';
    if (customerDetails) {
        let tierColor = "#64748b";
        if (customerDetails.loyaltyLevel === "VIP") tierColor = "#dc2626";
        else if (customerDetails.loyaltyLevel === "GOLD") tierColor = "#d97706";
        
        loyaltyHtml = `
        <div class="loyalty-box">
            <div class="d-flex justify-content-between align-items-center mb-2">
                <span class="badge" style="background-color: ${tierColor}">${customerDetails.loyaltyLevel || 'MEMBER'}</span>
                <strong class="text-primary">${currentLoyaltyPoints.toLocaleString('vi-VN')} điểm</strong>
            </div>
            <p class="small text-muted mb-2" id="redeemStatusText">Đổi điểm tích lũy để nhận giảm giá:</p>
            <div class="d-flex gap-2">
                <button class="btn btn-sm btn-outline-primary" id="btnR100" onclick="applyRedeem(100, 50000)" ${currentLoyaltyPoints < 100 ? 'disabled' : ''}>100đ (-50k)</button>
                <button class="btn btn-sm btn-outline-primary" id="btnR500" onclick="applyRedeem(500, 300000)" ${currentLoyaltyPoints < 500 ? 'disabled' : ''}>500đ (-300k)</button>
                <button class="btn btn-sm btn-danger" id="btnRCancel" onclick="applyRedeem(0, 0)" style="display:none;">Hủy đổi</button>
            </div>
        </div>
        `;
    }

    // HTML Nội dung
    let html = `
        <div class="invoice-row"><span class="text-muted">Khách hàng:</span> <span class="fw-bold">${cust ? cust.fullName : "N/A"}</span></div>
        <div class="invoice-row"><span class="text-muted">Phòng:</span> <span class="fw-bold">${room ? room.roomNumber : "N/A"} (${roomPrice.toLocaleString('vi-VN')} đ/đêm)</span></div>
        <div class="invoice-row"><span class="text-muted">Lưu trú:</span> <span class="fw-bold">${diffDays} đêm</span></div>
        <hr style="border-style: dashed;">
        <div class="invoice-row"><span class="text-muted">Tiền phòng:</span> <span class="fw-bold">${roomTotal.toLocaleString('vi-VN')} đ</span></div>
        <div class="invoice-row"><span class="text-muted">Tiền dịch vụ:</span> <span class="fw-bold">${currentServiceTotal.toLocaleString('vi-VN')} đ</span></div>
        <div class="invoice-row"><span class="text-muted">Khuyến mãi (${promoName}):</span> <span class="fw-bold text-success">- ${currentCalculatedDiscount.toLocaleString('vi-VN')} đ</span></div>
        <div class="invoice-row"><span class="text-muted">Trừ điểm thưởng:</span> <span class="fw-bold text-success" id="lblRedeemDiscount">- ${redeemDiscount.toLocaleString('vi-VN')} đ</span></div>
        
        ${loyaltyHtml}
        
        <div class="invoice-row"><span class="text-muted">Thuế VAT (10%):</span> <span class="fw-bold" id="lblTax">0 đ</span></div>
        <hr>
        <div class="invoice-row fw-bold mt-3"><span class="text-danger fs-5">TỔNG CỘNG:</span> <span class="text-danger fs-5" id="lblFinalTotal">0 đ</span></div>
    `;
    
    document.getElementById("invoiceContent").innerHTML = html;
    updateFinalTotal(roomTotal);
}

function applyRedeem(points, discount) {
    pointsToRedeem = points;
    redeemDiscount = discount;
    
    const btnR100 = document.getElementById("btnR100");
    const btnR500 = document.getElementById("btnR500");
    const btnCancel = document.getElementById("btnRCancel");
    const statusText = document.getElementById("redeemStatusText");
    
    if (points > 0) {
        btnR100.disabled = true;
        btnR500.disabled = true;
        btnCancel.style.display = "inline-block";
        statusText.innerHTML = `<span class="text-success fw-bold"><i class="fas fa-check-circle"></i> Đã áp dụng đổi ${points} điểm</span>`;
    } else {
        btnR100.disabled = currentLoyaltyPoints < 100;
        btnR500.disabled = currentLoyaltyPoints < 500;
        btnCancel.style.display = "none";
        statusText.innerHTML = `Đổi điểm tích lũy để nhận giảm giá:`;
    }
    
    document.getElementById("lblRedeemDiscount").textContent = `- ${redeemDiscount.toLocaleString('vi-VN')} đ`;
    
    const room = getRoom(selectedBooking.roomId);
    const checkIn = new Date(selectedBooking.checkInDate);
    const checkOut = new Date(selectedBooking.checkOutDate);
    let diffDays = Math.ceil((checkOut - checkIn) / (1000 * 60 * 60 * 24));
    if (diffDays <= 0) diffDays = 1;
    updateFinalTotal((room ? room.price : 0) * diffDays);
    toggleQR(); // Cập nhật lại QR code do tổng tiền thay đổi
}

function updateFinalTotal(roomTotal) {
    let subtotal = roomTotal + currentServiceTotal - currentCalculatedDiscount - redeemDiscount;
    if (subtotal < 0) subtotal = 0;
    
    const tax = subtotal * 0.1; // 10%
    finalTotalAmount = subtotal + tax;
    
    document.getElementById("lblTax").textContent = `${tax.toLocaleString('vi-VN')} đ`;
    document.getElementById("lblFinalTotal").textContent = `${finalTotalAmount.toLocaleString('vi-VN')} đ`;
}

function toggleQR() {
    const method = document.getElementById("paymentMethod").value;
    const qrContainer = document.getElementById("qrCodeContainer");
    
    if (method === "Chuyển khoản" && selectedBooking) {
        const room = getRoom(selectedBooking.roomId);
        const addInfo = encodeURIComponent("Thanh toan phong " + (room ? room.roomNumber : ""));
        const amountStr = Math.round(finalTotalAmount);
        
        // Cấu hình VietQR Demo (Mã NH 970422 - MBBank)
        const qrUrl = `https://img.vietqr.io/image/970422-0987654321-compact.png?amount=${amountStr}&addInfo=${addInfo}&accountName=KHACH%20SAN%20NGOC%20MAI`;
        
        document.getElementById("vietQrImg").src = qrUrl;
        qrContainer.style.display = "block";
    } else {
        qrContainer.style.display = "none";
    }
}

async function processPayment() {
    if (!selectedBooking) return;
    if (!confirm("Xác nhận thanh toán đơn đặt phòng #" + selectedBooking.id + "?")) return;
    
    const method = document.getElementById("paymentMethod").value;
    const btn = document.getElementById("btnConfirmPay");
    btn.disabled = true;
    btn.innerHTML = `<span class="spinner-border spinner-border-sm"></span> Đang xử lý...`;
    
    try {
        // 1. Nếu có đổi điểm, gọi API đổi điểm trước (Tương đương logic trên Desktop)
        if (pointsToRedeem > 0) {
            // Giả lập gọi API redeem (Trong dự án backend có LoyaltyService.redeemPoints nhưng có thể cần tạo endpoint riêng nếu thiếu. 
            // Nếu API backend chỉ xử lý cộng điểm trong PaymentController, ta cần API đổi điểm hoặc tự động hóa. 
            // Do đó tạm thời gửi kèm thông tin này vào Payment hoặc assume backend tự trừ nếu có API).
            // Ghi chú: Desktop dùng LoyaltyAPI.redeemPoints(customerId, points, ...). 
            await fetchApi('/loyalty/redeem', 'POST', {
                customerId: selectedBooking.customerId,
                points: pointsToRedeem,
                discountAmount: redeemDiscount,
                description: `Đổi điểm tại hóa đơn #${selectedBooking.id}`
            });
        }
        
        // 2. Gọi API Thanh toán chính thức
        const payload = {
            bookingId: selectedBooking.id.toString(),
            amount: finalTotalAmount.toString(),
            paymentMethod: method,
            customerId: selectedBooking.customerId.toString()
        };
        
        const res = await fetchApi('/payments', 'POST', payload);
        if (res.status === "success") {
            // Prepare PDF Data before cleaning UI
            preparePDFContent();
            
            // Clean UI
            document.getElementById("paymentActionSection").style.display = "none";
            document.getElementById("invoiceContent").innerHTML = `
                <div class="text-center text-success py-5">
                    <i class="fas fa-check-circle fa-4x mb-3"></i>
                    <h4>Thanh toán hoàn tất!</h4>
                    <p>Phòng đã được chuyển sang trạng thái trả và ghi nhận doanh thu.</p>
                </div>
            `;
            loadPaymentData(); // Refresh list
            selectedBooking = null;
            
            // Hiện Modal In PDF
            printModalInstance.show();
        } else {
            alert("Lỗi thanh toán: " + res.message);
            btn.disabled = false;
            btn.innerHTML = "XÁC NHẬN THANH TOÁN";
        }
    } catch (e) {
        console.error(e);
        alert("Lỗi hệ thống khi thanh toán!");
        btn.disabled = false;
        btn.innerHTML = "XÁC NHẬN THANH TOÁN";
    }
}

function preparePDFContent() {
    const cust = getCustomer(selectedBooking.customerId);
    const room = getRoom(selectedBooking.roomId);
    const dateStr = new Date().toLocaleString('vi-VN');
    
    let html = `
        <div style="text-align: center; margin-bottom: 20px;">
            <h2 style="color: #0f172a; margin: 0;">KHÁCH SẠN NGỌC MAI</h2>
            <p style="color: #64748b; margin: 5px 0;">123 Nguyễn Văn Linh, Đà Nẵng | ĐT: 0123.456.789</p>
            <h3 style="margin-top: 20px; text-transform: uppercase;">Hóa Đơn Thanh Toán</h3>
            <p style="font-size: 12px; color: #64748b;">Mã HĐ: HD-${Date.now()} | Ngày in: ${dateStr}</p>
        </div>
        <hr style="border-top: 1px dashed #ccc; margin: 15px 0;">
        <table style="width: 100%; margin-bottom: 20px; font-size: 14px;">
            <tr><td style="padding: 5px 0;"><strong>Khách hàng:</strong></td><td>${cust ? cust.fullName : "N/A"}</td></tr>
            <tr><td style="padding: 5px 0;"><strong>Phòng:</strong></td><td>${room ? room.roomNumber : "N/A"}</td></tr>
            <tr><td style="padding: 5px 0;"><strong>Ngày nhận:</strong></td><td>${new Date(selectedBooking.checkInDate).toLocaleString('vi-VN')}</td></tr>
            <tr><td style="padding: 5px 0;"><strong>Ngày trả:</strong></td><td>${new Date(selectedBooking.checkOutDate).toLocaleString('vi-VN')}</td></tr>
        </table>
        <table style="width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px;">
            <tr style="border-bottom: 2px solid #000;"><th style="text-align: left; padding: 8px 0;">Diễn giải</th><th style="text-align: right; padding: 8px 0;">Thành tiền</th></tr>
            <tr style="border-bottom: 1px solid #eee;"><td style="padding: 8px 0;">Tiền phòng</td><td style="text-align: right;">${(room ? room.price : 0).toLocaleString('vi-VN')} đ</td></tr>
            <tr style="border-bottom: 1px solid #eee;"><td style="padding: 8px 0;">Tiền dịch vụ</td><td style="text-align: right;">${currentServiceTotal.toLocaleString('vi-VN')} đ</td></tr>
            <tr style="border-bottom: 1px solid #eee;"><td style="padding: 8px 0;">Khuyến mãi/Giảm giá</td><td style="text-align: right;">- ${(currentCalculatedDiscount + redeemDiscount).toLocaleString('vi-VN')} đ</td></tr>
            <tr style="border-bottom: 1px solid #eee;"><td style="padding: 8px 0;">Thuế VAT (10%)</td><td style="text-align: right;">${(finalTotalAmount - (finalTotalAmount / 1.1)).toLocaleString('vi-VN')} đ</td></tr>
        </table>
        <div style="text-align: right; margin-top: 20px;">
            <h3 style="color: #dc2626; margin: 0;">TỔNG CỘNG: ${finalTotalAmount.toLocaleString('vi-VN')} đ</h3>
            <p style="color: #64748b; font-size: 12px; margin-top: 5px;">(Đã bao gồm VAT)</p>
        </div>
        <div style="text-align: center; margin-top: 40px; color: #64748b; font-style: italic; font-size: 13px;">
            <p>Cảm ơn quý khách đã sử dụng dịch vụ và hẹn gặp lại!</p>
        </div>
    `;
    document.getElementById("printableInvoice").innerHTML = html;
}

function closePrintModal() {
    printModalInstance.hide();
}

function exportPDF() {
    const element = document.getElementById('printableInvoice');
    const opt = {
        margin:       10,
        filename:     'HoaDon_KhachSan.pdf',
        image:        { type: 'jpeg', quality: 0.98 },
        html2canvas:  { scale: 2 },
        jsPDF:        { unit: 'mm', format: 'a5', orientation: 'portrait' }
    };

    // New Promise-based usage:
    html2pdf().set(opt).from(element).save().then(() => {
        console.log("PDF xuất thành công!");
    });
}
