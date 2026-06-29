let allInvoices = [];
let allBookings = [];
let allCustomers = [];
let allRooms = [];

document.addEventListener("DOMContentLoaded", function() {
    const user = checkAuth();
    if(user.role !== 'ADMIN') {
        alert("Bạn không có quyền truy cập trang quản trị!");
        window.location.href = "/shared/login.html";
        return;
    }
    document.getElementById("welcomeMessage").textContent = `Xin chào, ${user.fullName}`;
    
    loadInvoices();
});

async function loadInvoices() {
    document.getElementById("invoiceTableBody").innerHTML = `<tr><td colspan="8" class="text-center py-4"><span class="spinner-border spinner-border-sm"></span> Đang tải dữ liệu...</td></tr>`;
    
    try {
        // Tải hóa đơn và các danh mục liên quan để lookup tên
        const [invRes, bookRes, custRes, roomRes] = await Promise.all([
            fetchApi('/invoices'),
            fetchApi('/bookings'),
            fetchApi('/customers'),
            fetchApi('/rooms')
        ]);
        
        // Backend Invoice trả về Array thay vì object {status, data}
        if (Array.isArray(invRes)) {
            allInvoices = invRes;
        } else if (invRes.status === "success") {
            allInvoices = invRes.data;
        } else {
            alert("Lỗi tải hóa đơn");
            return;
        }

        if (bookRes.status === "success") allBookings = bookRes.data;
        if (custRes.status === "success") allCustomers = custRes.data;
        if (roomRes.status === "success") allRooms = roomRes.data;

        filterInvoices();
        
    } catch (e) {
        console.error(e);
        alert("Lỗi kết nối máy chủ!");
    }
}

function filterInvoices() {
    const keyword = document.getElementById("searchInput").value.toLowerCase();
    
    const filtered = allInvoices.filter(inv => {
        const idStr = "hd" + inv.id;
        const custName = (inv.customerName || "").toLowerCase();
        const roomNum = (inv.roomNumber || "").toLowerCase();
        
        return idStr.includes(keyword) || custName.includes(keyword) || roomNum.includes(keyword);
    });
    
    renderInvoices(filtered);
}

function renderInvoices(list) {
    const tbody = document.getElementById("invoiceTableBody");
    tbody.innerHTML = "";
    
    if (list.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center text-muted py-4">Không tìm thấy hóa đơn nào</td></tr>`;
        return;
    }
    
    list.forEach(inv => {
        let statusBadge = '';
        if (inv.status === 'paid' || inv.status === 'PAID') {
            statusBadge = '<span class="badge bg-success">Đã thu</span>';
        } else {
            statusBadge = `<span class="badge bg-secondary">${inv.status}</span>`;
        }

        const dateStr = inv.issueDate ? new Date(inv.issueDate).toLocaleString('vi-VN') : "N/A";

        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td class="fw-bold">HD${inv.id}</td>
            <td>${inv.customerName || "N/A"}</td>
            <td><span class="badge bg-dark">${inv.roomNumber || "N/A"}</span></td>
            <td>${(inv.totalServiceFee || 0).toLocaleString('vi-VN')} đ</td>
            <td class="fw-bold text-danger">${(inv.finalTotal || 0).toLocaleString('vi-VN')} đ</td>
            <td>${dateStr}</td>
            <td>${statusBadge}</td>
            <td class="text-end">
                <button class="btn btn-sm btn-outline-primary" onclick="reprintInvoice(${inv.id})"><i class="fas fa-print"></i> In PDF</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

async function reprintInvoice(invoiceId) {
    const inv = allInvoices.find(i => i.id === invoiceId);
    if (!inv) return;

    // Lấy thông tin booking tương ứng
    const booking = allBookings.find(b => b.id === inv.bookingId);
    if (!booking) {
        alert("Không tìm thấy thông tin đơn đặt phòng liên kết!");
        return;
    }

    const cust = allCustomers.find(c => c.id === booking.customerId);
    const room = allRooms.find(r => r.id === booking.roomId);

    const dateStr = new Date(inv.issueDate).toLocaleString('vi-VN');
    
    // Tải dịch vụ đã dùng
    let serviceTotal = inv.totalServiceFee || 0;

    let html = `
        <div style="text-align: center; margin-bottom: 20px;">
            <h2 style="color: #0f172a; margin: 0;">KHÁCH SẠN NGỌC MAI</h2>
            <p style="color: #64748b; margin: 5px 0;">123 Nguyễn Văn Linh, Đà Nẵng | ĐT: 0123.456.789</p>
            <h3 style="margin-top: 20px; text-transform: uppercase;">BẢN SAO HÓA ĐƠN THANH TOÁN</h3>
            <p style="font-size: 12px; color: #64748b;">Mã HĐ: HD${inv.id} | Ngày in: ${dateStr}</p>
        </div>
        <hr style="border-top: 1px dashed #ccc; margin: 15px 0;">
        <table style="width: 100%; margin-bottom: 20px; font-size: 14px;">
            <tr><td style="padding: 5px 0;"><strong>Khách hàng:</strong></td><td>${cust ? cust.fullName : inv.customerName}</td></tr>
            <tr><td style="padding: 5px 0;"><strong>Phòng:</strong></td><td>${room ? room.roomNumber : inv.roomNumber}</td></tr>
            <tr><td style="padding: 5px 0;"><strong>Ngày nhận:</strong></td><td>${new Date(booking.checkInDate).toLocaleString('vi-VN')}</td></tr>
            <tr><td style="padding: 5px 0;"><strong>Ngày trả:</strong></td><td>${new Date(booking.checkOutDate).toLocaleString('vi-VN')}</td></tr>
        </table>
        <table style="width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px;">
            <tr style="border-bottom: 2px solid #000;"><th style="text-align: left; padding: 8px 0;">Diễn giải</th><th style="text-align: right; padding: 8px 0;">Thành tiền</th></tr>
            <tr style="border-bottom: 1px solid #eee;"><td style="padding: 8px 0;">Tiền dịch vụ</td><td style="text-align: right;">${serviceTotal.toLocaleString('vi-VN')} đ</td></tr>
        </table>
        <div style="text-align: right; margin-top: 20px;">
            <h3 style="color: #dc2626; margin: 0;">TỔNG CỘNG ĐÃ THU: ${inv.finalTotal.toLocaleString('vi-VN')} đ</h3>
            <p style="color: #64748b; font-size: 12px; margin-top: 5px;">(Đã bao gồm VAT)</p>
        </div>
        <div style="text-align: center; margin-top: 40px; color: #64748b; font-style: italic; font-size: 13px;">
            <p>Đây là bản sao hóa đơn, có giá trị lưu trữ.</p>
        </div>
    `;

    const el = document.getElementById("printableInvoice");
    el.innerHTML = html;
    
    // In
    const opt = {
        margin:       10,
        filename:     \`BanSao_HoaDon_HD\${inv.id}.pdf\`,
        image:        { type: 'jpeg', quality: 0.98 },
        html2canvas:  { scale: 2 },
        jsPDF:        { unit: 'mm', format: 'a5', orientation: 'portrait' }
    };

    html2pdf().set(opt).from(el).save();
}
