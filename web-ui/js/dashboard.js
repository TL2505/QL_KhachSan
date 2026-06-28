document.addEventListener("DOMContentLoaded", function() {
    // 1. Kiểm tra đăng nhập
    const user = checkAuth();
    document.getElementById("welcomeMessage").textContent = `Xin chào, ${user.fullName || user.username} (${user.roleId === 1 ? 'Admin' : 'Staff'})`;

    // 2. Load danh sách phòng
    loadRooms();

    // 3. Xử lý form Thêm Khách Hàng
    const customerForm = document.getElementById("customerForm");
    if (customerForm) {
        customerForm.addEventListener("submit", async function(e) {
            e.preventDefault();
            
            const btnCustomer = document.getElementById("btnCustomer");
            const msgBox = document.getElementById("customer-msg");
            
            const name = document.getElementById("custName").value;
            const phone = document.getElementById("custPhone").value;
            const email = document.getElementById("custEmail").value;

            msgBox.classList.add("d-none");
            btnCustomer.disabled = true;
            btnCustomer.textContent = "Đang xử lý...";

            try {
                const result = await fetchApi('/customers', 'POST', {
                    fullName: name,
                    phone: phone,
                    email: email
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

// Hàm lấy dữ liệu phòng từ Backend
async function loadRooms() {
    const roomListDiv = document.getElementById("roomList");
    try {
        const response = await fetchApi('/rooms', 'GET');
        
        if (response.status === "success" && Array.isArray(response.data)) {
            roomListDiv.innerHTML = ""; // Xóa loading
            
            response.data.forEach(room => {
                let statusClass = "room-available";
                let statusText = "Trống";
                
                if (room.status === "occupied") {
                    statusClass = "room-occupied";
                    statusText = "Đang ở";
                } else if (room.status === "cleaning") {
                    statusClass = "room-cleaning";
                    statusText = "Đang dọn";
                } else if (room.status === "maintenance") {
                    statusClass = "room-cleaning"; // Dùng tạm màu vàng
                    statusText = "Bảo trì";
                }

                const roomHtml = `
                    <div class="col-md-4 col-sm-6">
                        <div class="room-box ${statusClass}">
                            <h5 class="fw-bold mb-1">${room.roomNumber}</h5>
                            <small class="text-muted d-block mb-2">${room.roomTypeName || 'Standard'}</small>
                            <span class="badge bg-secondary">${statusText}</span>
                        </div>
                    </div>
                `;
                roomListDiv.insertAdjacentHTML('beforeend', roomHtml);
            });
        } else {
            roomListDiv.innerHTML = `<div class="col-12 text-danger">Không tải được dữ liệu phòng.</div>`;
        }
    } catch (error) {
        roomListDiv.innerHTML = `<div class="col-12 text-danger">Lỗi kết nối khi lấy danh sách phòng.</div>`;
    }
}
