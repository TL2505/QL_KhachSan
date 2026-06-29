let allRooms = [];
let roomModalInstance = null;
let currentUserRole = null;

document.addEventListener("DOMContentLoaded", function() {
    const user = checkAuth();
    if(user.role !== 'STAFF' && user.role !== 'ADMIN') {
        alert("Bạn không có quyền truy cập!");
        window.location.href = "/shared/login.html";
        return;
    }
    currentUserRole = user.role;
    document.getElementById("welcomeMessage").textContent = `Xin chào, ${user.fullName}`;
    
    // Chỉ Admin mới được quyền thêm/xóa phòng
    if (user.role === 'ADMIN') {
        document.getElementById("btnAddRoom").style.display = "block";
        document.querySelectorAll('.admin-only').forEach(el => el.style.display = "table-cell");
    }

    roomModalInstance = new bootstrap.Modal(document.getElementById('roomModal'));
    loadRoomsData();
});

async function loadRoomsData() {
    try {
        const res = await fetchApi('/rooms');
        if (res.status === "success") {
            allRooms = res.data;
            renderRooms(allRooms);
        } else {
            alert("Lỗi tải danh sách phòng");
        }
    } catch (e) {
        console.error(e);
        alert("Lỗi máy chủ!");
    }
}

function renderRooms(list) {
    const tbody = document.getElementById("roomTableBody");
    tbody.innerHTML = "";
    
    if (list.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center text-muted">Chưa có phòng nào</td></tr>`;
        return;
    }
    
    list.forEach(r => {
        let statusBadge = '';
        let st = r.status.toLowerCase();
        if (st === 'available') statusBadge = '<span class="badge bg-success">Trống</span>';
        else if (st === 'occupied') statusBadge = '<span class="badge bg-danger">Đang ở</span>';
        else if (st === 'cleaning') statusBadge = '<span class="badge bg-warning text-dark">Đang dọn</span>';
        else if (st === 'maintenance') statusBadge = '<span class="badge bg-secondary">Bảo trì</span>';
        else statusBadge = `<span class="badge bg-dark">${st}</span>`;

        let typeName = 'Standard';
        if (r.roomTypeId === 2) typeName = 'VIP';
        if (r.roomTypeId === 3) typeName = 'Family';

        const tr = document.createElement("tr");
        let html = `
            <td>#${r.id}</td>
            <td class="fw-bold text-primary">${r.roomNumber}</td>
            <td>${typeName}</td>
            <td>${r.price.toLocaleString('vi-VN')} đ</td>
            <td>${statusBadge}</td>
        `;

        if (currentUserRole === 'ADMIN') {
            html += `
            <td class="text-end">
                <button class="btn btn-sm btn-outline-danger" onclick="deleteRoom(${r.id}, '${r.roomNumber}')"><i class="fas fa-trash"></i> Xóa</button>
            </td>
            `;
        } else {
            html += `<td style="display:none;"></td>`;
        }

        tr.innerHTML = html;
        tbody.appendChild(tr);
    });
}

function showAddRoomModal() {
    document.getElementById("roomForm").reset();
    roomModalInstance.show();
}

async function saveRoom() {
    const data = {
        roomNumber: document.getElementById("roomNumber").value,
        roomTypeId: parseInt(document.getElementById("roomTypeId").value),
        price: parseFloat(document.getElementById("roomPrice").value),
        status: "available"
    };

    if (!data.roomNumber || !data.price) {
        alert("Vui lòng nhập đủ thông tin!");
        return;
    }

    try {
        const res = await fetchApi('/rooms', 'POST', data);
        if (res.status === "success") {
            alert("Thêm phòng thành công!");
            roomModalInstance.hide();
            loadRoomsData();
        } else {
            alert("Lỗi: " + res.message);
        }
    } catch (e) {
        console.error(e);
        alert("Lỗi máy chủ!");
    }
}

async function deleteRoom(id, roomNum) {
    if (confirm(`Bạn có chắc muốn xóa phòng ${roomNum} không? Hành động này không thể hoàn tác.`)) {
        try {
            const res = await fetchApi(`/rooms/${id}`, 'DELETE');
            if (res.status === "success") {
                alert("Đã xóa phòng thành công!");
                loadRoomsData();
            } else {
                alert("Lỗi: " + res.message);
            }
        } catch (e) {
            console.error(e);
            alert("Lỗi máy chủ! Không thể xóa phòng đang có lịch sử đặt.");
        }
    }
}
