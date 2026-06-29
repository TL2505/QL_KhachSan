document.addEventListener("DOMContentLoaded", function() {
    const user = checkAuth();
    if(user.role !== 'ADMIN') { // Chỉ Admin mới được vào đây
        alert("Truy cập trái phép!");
        window.location.href = "/shared/login.html";
    }
    document.getElementById("welcomeMessage").textContent = `Xin chào Sếp, ${user.fullName || user.username}!`;

    loadRevenueChart();
    
    // Bind sự kiện Add User
    const addUserForm = document.getElementById("addUserForm");
    if(addUserForm) {
        addUserForm.addEventListener("submit", handleAddUser);
    }
});

let revenueChart = null;

// Hàm tải Biểu đồ doanh thu
async function loadRevenueChart() {
    try {
        const response = await fetchApi('/reports/monthly-revenue', 'GET');
        if (response.status === "success" && Array.isArray(response.data)) {
            const data = response.data;
            
            // Xử lý dữ liệu
            const labels = [];
            const revenueData = [];
            let totalRevenue = 0;

            data.forEach(item => {
                labels.push(`Tháng ${item.month}/${item.year}`);
                revenueData.push(item.totalRevenue);
                totalRevenue += item.totalRevenue;
            });

            // Hiển thị tổng doanh thu
            document.getElementById("totalRevenue").textContent = totalRevenue.toLocaleString('vi-VN') + " ₫";

            // Vẽ biểu đồ Chart.js
            const ctx = document.getElementById('revenueChart').getContext('2d');
            
            if (revenueChart) revenueChart.destroy();
            
            revenueChart = new Chart(ctx, {
                type: 'bar', // Biểu đồ Cột
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'Doanh thu (VNĐ)',
                        data: revenueData,
                        backgroundColor: 'rgba(220, 53, 69, 0.7)', // Màu đỏ (Danger)
                        borderColor: 'rgba(220, 53, 69, 1)',
                        borderWidth: 1
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: true
                        }
                    }
                }
            });

        } else {
            alert("Không tải được dữ liệu doanh thu.");
        }
    } catch (error) {
        console.error("Lỗi vẽ biểu đồ:", error);
    }
}

// ================= Quản lý Nhân sự =================
async function loadUsers() {
    const tableBody = document.getElementById("userTableBody");
    tableBody.innerHTML = `<tr><td colspan="5" class="text-center">Đang tải...</td></tr>`;
    
    try {
        const response = await fetchApi('/users', 'GET');
        if (response.status === "success" && Array.isArray(response.data)) {
            tableBody.innerHTML = "";
            response.data.forEach(user => {
                let roleName = "Customer";
                let roleColor = "secondary";
                if(user.roleId === 1) { roleName = "Admin"; roleColor = "danger"; }
                else if(user.roleId === 2) { roleName = "Staff"; roleColor = "primary"; }

                const statusColor = user.status === 'active' ? 'success' : 'danger';

                tableBody.innerHTML += `
                    <tr>
                        <td class="fw-bold">${user.id}</td>
                        <td>${user.username}</td>
                        <td>${user.fullName || '-'}</td>
                        <td><span class="badge bg-${roleColor}">${roleName}</span></td>
                        <td><span class="badge bg-${statusColor}">${user.status || 'active'}</span></td>
                    </tr>
                `;
            });
        } else {
            tableBody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">Lỗi tải danh sách user</td></tr>`;
        }
    } catch(e) {
        tableBody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">Lỗi kết nối máy chủ</td></tr>`;
    }
}

async function handleAddUser(e) {
    e.preventDefault();
    const btn = document.getElementById("btnAddUser");
    
    const username = document.getElementById("addUsername").value;
    const password = document.getElementById("addPassword").value;
    const fullName = document.getElementById("addFullName").value;
    const roleId = parseInt(document.getElementById("addRole").value);

    btn.disabled = true;
    btn.textContent = "Đang xử lý...";

    try {
        const result = await fetchApi('/users', 'POST', {
            username: username,
            password: password,
            fullName: fullName,
            roleId: roleId
        });

        if(result.status === "success") {
            alert("Tạo nhân sự mới thành công!");
            document.getElementById("addUserForm").reset();
            // Đóng modal Thêm User
            var addModal = bootstrap.Modal.getInstance(document.getElementById('addUserModal'));
            addModal.hide();
            // Load lại bảng
            loadUsers();
        } else {
            alert("Lỗi: " + result.message);
        }
    } catch(err) {
        alert("Lỗi kết nối máy chủ");
    } finally {
        btn.disabled = false;
        btn.textContent = "Tạo Tài Khoản";
    }
}
