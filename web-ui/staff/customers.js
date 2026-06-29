let allCustomers = [];
let customerModalInstance = null;

document.addEventListener("DOMContentLoaded", function() {
    const user = checkAuth();
    if(user.role !== 'STAFF' && user.role !== 'ADMIN') {
        alert("Bạn không có quyền truy cập!");
        window.location.href = "/shared/login.html";
        return;
    }
    document.getElementById("welcomeMessage").textContent = `Xin chào, ${user.fullName}`;
    
    customerModalInstance = new bootstrap.Modal(document.getElementById('customerModal'));
    loadCustomers();
});

async function loadCustomers() {
    try {
        const res = await fetchApi('/customers');
        if (res.status === "success") {
            allCustomers = res.data;
            renderCustomers(allCustomers);
        } else {
            alert("Lỗi tải danh sách khách hàng");
        }
    } catch (e) {
        console.error(e);
        alert("Lỗi máy chủ!");
    }
}

function renderCustomers(list) {
    const tbody = document.getElementById("customerTableBody");
    tbody.innerHTML = "";
    
    if (list.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center text-muted">Không có khách hàng nào</td></tr>`;
        return;
    }
    
    list.forEach(c => {
        let badgeColor = "bg-secondary";
        if (c.loyaltyLevel === "VIP") badgeColor = "bg-danger";
        else if (c.loyaltyLevel === "GOLD") badgeColor = "bg-warning text-dark";
        else if (c.loyaltyLevel === "SILVER") badgeColor = "bg-info text-dark";

        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>#${c.id}</td>
            <td class="fw-bold text-primary">${c.fullName}</td>
            <td>${c.phone}</td>
            <td>${c.identityCard || '---'}</td>
            <td><span class="badge ${badgeColor}">${c.loyaltyLevel || 'MEMBER'}</span></td>
            <td>${c.loyaltyPoints || 0} đ</td>
            <td class="text-end">
                <button class="btn btn-sm btn-outline-primary" onclick='editCustomer(${JSON.stringify(c)})'><i class="fas fa-edit"></i> Sửa</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function filterCustomers() {
    const text = document.getElementById("searchInput").value.toLowerCase();
    const filtered = allCustomers.filter(c => 
        c.fullName.toLowerCase().includes(text) || 
        c.phone.includes(text) ||
        (c.identityCard && c.identityCard.includes(text))
    );
    renderCustomers(filtered);
}

function showAddCustomerModal() {
    document.getElementById("customerForm").reset();
    document.getElementById("customerId").value = "";
    document.getElementById("customerModalTitle").textContent = "Thêm Khách hàng mới";
    customerModalInstance.show();
}

function editCustomer(c) {
    document.getElementById("customerId").value = c.id;
    document.getElementById("fullName").value = c.fullName;
    document.getElementById("phone").value = c.phone;
    document.getElementById("identityCard").value = c.identityCard || '';
    document.getElementById("email").value = c.email || '';
    document.getElementById("address").value = c.address || '';
    
    document.getElementById("customerModalTitle").textContent = "Cập nhật Khách hàng";
    customerModalInstance.show();
}

async function saveCustomer() {
    const id = document.getElementById("customerId").value;
    const data = {
        fullName: document.getElementById("fullName").value,
        phone: document.getElementById("phone").value,
        identityCard: document.getElementById("identityCard").value,
        email: document.getElementById("email").value,
        address: document.getElementById("address").value
    };

    if (!data.fullName || !data.phone) {
        alert("Vui lòng nhập đủ Tên và Số điện thoại!");
        return;
    }

    try {
        let res;
        if (id) {
            // Update
            data.id = parseInt(id);
            res = await fetchApi(`/customers/${id}`, 'PUT', data);
        } else {
            // Create
            res = await fetchApi('/customers', 'POST', data);
        }

        if (res.status === "success") {
            customerModalInstance.hide();
            loadCustomers();
        } else {
            alert("Lỗi: " + res.message);
        }
    } catch (e) {
        console.error(e);
        alert("Lỗi máy chủ!");
    }
}
