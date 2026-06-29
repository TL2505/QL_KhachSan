document.addEventListener("DOMContentLoaded", function() {
    // 1. Kiểm tra đăng nhập
    const user = checkAuth();
    document.getElementById("welcomeMessage").textContent = `Xin chào, ${user.fullName || user.username} (${user.role === 'ADMIN' ? 'Admin' : 'Staff'})`;

    // 2. Load danh sách phòng và cài đặt Polling (Tự làm mới mỗi 5 giây)
    loadRooms();
    setInterval(loadRooms, 5000);

    // 3. Xử lý form Thêm Khách Hàng
    const customerForm = document.getElementById("customerForm");
    if (customerForm) {
        customerForm.addEventListener("submit", async function(e) {
            e.preventDefault();
            
            const btnCustomer = document.getElementById("btnCustomer");
            const msgBox = document.getElementById("customer-msg");
            
            const name = document.getElementById("custName").value;
            const phone = document.getElementById("custPhone").value;
            const cccd = document.getElementById("custCccd").value;

            msgBox.classList.add("d-none");
            btnCustomer.disabled = true;
            btnCustomer.textContent = "Đang xử lý...";

            try {
                const result = await fetchApi('/customers', 'POST', {
                    name: name,
                    phone: phone,
                    cccd: cccd
                });

                if (result.status === "success") {
                    msgBox.className = "alert alert-success mt-3";
                    msgBox.textContent = "Thêm khách hàng thành công!";
                    customerForm.reset();
                } else {
                    msgBox.className = "alert alert-danger mt-3";
                    msgBox.textContent = result.message || "Lỗi khi thêm khách hàng.";
                }
            } catch (error) {
                msgBox.className = "alert alert-danger mt-3";
                msgBox.textContent = "Lỗi kết nối Máy chủ!";
            } finally {
                btnCustomer.disabled = false;
                btnCustomer.textContent = "Tạo Khách Hàng";
            }
        });
    }
});

// Biến lưu Modal để tái sử dụng
let roomModalInstance = null;

// Hàm lấy dữ liệu phòng từ Backend
async function loadRooms() {
    const roomListDiv = document.getElementById("roomList");
    const filterFloor = document.getElementById("filterFloor").value;
    const filterStatus = document.getElementById("filterStatus").value;

    try {
        const response = await fetchApi('/rooms', 'GET');
        
        if (response.status === "success" && Array.isArray(response.data)) {
            let rooms = response.data;
            
            // Lọc theo tầng (VD: 101 -> Tầng 1)
            if (filterFloor !== "all") {
                rooms = rooms.filter(r => r.roomNumber.toString().startsWith(filterFloor));
            }
            // Lọc theo trạng thái
            if (filterStatus !== "all") {
                rooms = rooms.filter(r => r.status === filterStatus);
            }

            roomListDiv.innerHTML = ""; // Xóa loading
            
            if (rooms.length === 0) {
                roomListDiv.innerHTML = `<div class="col-12 text-center text-muted mt-3">Không có phòng nào thỏa mãn điều kiện lọc.</div>`;
                return;
            }

            rooms.forEach(room => {
                let statusClass = "room-available";
                let statusText = "Trống";
                let iconHtml = `<i class="fa-solid fa-bed text-success"></i>`;
                
                if (room.status === "occupied") {
                    statusClass = "room-occupied";
                    statusText = "Đang ở";
                    iconHtml = `<i class="fa-solid fa-user text-danger"></i>`;
                } else if (room.status === "cleaning") {
                    statusClass = "room-cleaning";
                    statusText = "Đang dọn";
                    iconHtml = `<i class="fa-solid fa-broom text-warning"></i>`;
                } else if (room.status === "maintenance") {
                    statusClass = "room-cleaning"; // Dùng tạm màu vàng cho class CSS
                    statusText = "Bảo trì";
                    iconHtml = `<i class="fa-solid fa-wrench text-secondary"></i>`;
                } else if (room.status === "booked") {
                    statusClass = "room-available";
                    statusText = "Đã đặt";
                    iconHtml = `<i class="fa-solid fa-calendar-check text-info"></i>`;
                }

                // Cấu hình Tooltip cho phòng đang ở
                let tooltipAttr = "";
                if (room.status === "occupied" || room.status === "booked") {
                    tooltipAttr = `data-bs-toggle="tooltip" data-bs-placement="top" title="Click để xem chi tiết"`;
                }

                const roomHtml = `
                    <div class="col-md-4 col-sm-6">
                        <div class="room-box ${statusClass}" style="cursor: pointer;" onclick="openRoomModal(${room.id}, '${room.status}', '${room.roomNumber}')" ${tooltipAttr}>
                            <div class="d-flex justify-content-between align-items-start">
                                <h5 class="fw-bold mb-1">${room.roomNumber}</h5>
                                ${iconHtml}
                            </div>
                            <small class="text-muted d-block mb-2">${room.roomTypeName || 'Standard'}</small>
                            <span class="badge bg-secondary">${statusText}</span>
                        </div>
                    </div>
                `;
                roomListDiv.insertAdjacentHTML('beforeend', roomHtml);
            });

            // Kích hoạt lại Tooltips của Bootstrap
            const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
            [...tooltipTriggerList].map(tooltipTriggerEl => new bootstrap.Tooltip(tooltipTriggerEl));

        } else {
            if(roomListDiv.innerHTML.includes("Đang tải")) {
                roomListDiv.innerHTML = `<div class="col-12 text-danger">Không tải được dữ liệu phòng.</div>`;
            }
        }
    } catch (error) {
        if(roomListDiv.innerHTML.includes("Đang tải")) {
            roomListDiv.innerHTML = `<div class="col-12 text-danger">Lỗi kết nối khi lấy danh sách phòng.</div>`;
        }
    }
}

// Hàm mở Modal tương tác
async function openRoomModal(roomId, status, roomNumber) {
    if (!roomModalInstance) {
        roomModalInstance = new bootstrap.Modal(document.getElementById('roomActionModal'));
    }
    
    document.getElementById("roomModalTitle").textContent = `Phòng ${roomNumber}`;
    const contentDiv = document.getElementById("roomModalContent");
    const footerDiv = document.getElementById("roomModalFooter");
    
    contentDiv.innerHTML = `<div class="text-center text-muted"><span class="spinner-border spinner-border-sm"></span> Đang tải thông tin...</div>`;
    footerDiv.innerHTML = `<button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Đóng</button>`;
    
    roomModalInstance.show();

    if (status === 'cleaning') {
        contentDiv.innerHTML = `<div class="alert alert-warning">Phòng đang được dọn dẹp. Chưa thể nhận khách.</div>`;
        return;
    }
    if (status === 'maintenance') {
        contentDiv.innerHTML = `<div class="alert alert-secondary">Phòng đang bảo trì.</div>`;
        return;
    }
    if (status === 'available') {
        contentDiv.innerHTML = `<div class="alert alert-success">Phòng hiện đang trống. Sẵn sàng đón khách.</div>`;
        footerDiv.innerHTML = `
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Đóng</button>
            <button type="button" class="btn btn-primary" onclick="alert('Tính năng Tạo Đặt Phòng Nhanh đang hoàn thiện!')">Tạo Đặt phòng</button>
        `;
        return;
    }

    // Nếu phòng Đang ở (Occupied) hoặc Đã đặt (Booked) -> Gọi API lấy Booking hiện tại
    try {
        // Thêm timestamp để vô hiệu hóa Cache của trình duyệt (tránh lỗi hiển thị data cũ)
        const res = await fetchApi(`/bookings/room/${roomId}?_t=${new Date().getTime()}`);
        if (res.status === "success" && res.data) {
            const b = res.data;
            const checkInDate = new Date(b.checkInDate).toLocaleDateString('vi-VN');
            const checkOutDate = new Date(b.checkOutDate).toLocaleDateString('vi-VN');
            
            contentDiv.innerHTML = `
                <ul class="list-group list-group-flush">
                    <li class="list-group-item"><strong>Mã Booking:</strong> #${b.id}</li>
                    <li class="list-group-item"><strong>Mã Khách hàng:</strong> ID ${b.customerId}</li>
                    <li class="list-group-item"><strong>Ngày Check-in:</strong> ${checkInDate}</li>
                    <li class="list-group-item"><strong>Ngày Check-out:</strong> ${checkOutDate}</li>
                    <li class="list-group-item text-danger fw-bold"><strong>Tổng chi phí:</strong> ${b.totalPrice.toLocaleString('vi-VN')} ₫</li>
                </ul>
            `;

            let actionBtn = "";
            if (b.status === "booked" || b.status === "pending" || b.status === "confirmed") {
                actionBtn = `<button type="button" class="btn btn-success fw-bold" onclick="handleCheckIn(${b.id})">Check-in Khách</button>`;
            } else if (b.status === "checked_in") {
                actionBtn = `<button type="button" class="btn btn-danger fw-bold" onclick="handleCheckOut(${b.id})">Thanh toán & Check-out</button>`;
            }

            footerDiv.innerHTML = `
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Đóng</button>
                ${actionBtn}
            `;
        } else {
            contentDiv.innerHTML = `<div class="alert alert-danger">Không tìm thấy dữ liệu đặt phòng! Có thể dữ liệu bị lỗi.</div>`;
        }
    } catch (e) {
        contentDiv.innerHTML = `<div class="alert alert-danger">Lỗi lấy thông tin Booking!</div>`;
    }
}

async function handleCheckIn(bookingId) {
    if (confirm("Xác nhận Check-in nhận phòng cho khách?")) {
        try {
            const res = await fetchApi(`/bookings/checkin/${bookingId}`, 'PUT');
            if (res.status === "success") {
                alert("Check-in thành công!");
                roomModalInstance.hide();
                loadRooms(); // Tải lại sơ đồ
            } else {
                alert("Lỗi: " + res.message);
            }
        } catch (e) {
            alert("Lỗi máy chủ!");
        }
    }
}

async function handleCheckOut(bookingId) {
    if (confirm("Xác nhận Trả phòng (Check-out) và thanh toán cho khách?")) {
        try {
            const res = await fetchApi(`/bookings/checkout/${bookingId}`, 'PUT');
            if (res.status === "success") {
                alert("Check-out và Thanh toán thành công! Phòng đã được chuyển sang trạng thái Đang Dọn.");
                roomModalInstance.hide();
                loadRooms(); // Tải lại sơ đồ
            } else {
                alert("Lỗi: " + res.message);
            }
        } catch (e) {
            alert("Lỗi máy chủ!");
        }
    }
}
