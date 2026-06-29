// Cấu hình URL Backend (Đi qua Nginx Reverse Proxy)
const BACKEND_URL = "/api";

/**
 * Hàm gọi API chung, tự động đính kèm Token bảo mật (JWT/Role)
 * @param {string} endpoint - Đường dẫn API (vd: '/rooms')
 * @param {string} method - Phương thức (GET, POST, PUT, DELETE)
 * @param {object} bodyData - Dữ liệu gửi lên (nếu có)
 * @returns Promise
 */
async function fetchApi(endpoint, method = 'GET', bodyData = null) {
    // Cấu hình Headers mặc định
    const headers = {
        "Content-Type": "application/json"
    };

    // Lấy Token từ LocalStorage (nếu đã đăng nhập)
    const token = localStorage.getItem("token");
    if (token) {
        headers["Authorization"] = "Bearer " + token;
    }

    // Cấu hình Request
    const config = {
        method: method,
        headers: headers
    };

    if (bodyData && (method === 'POST' || method === 'PUT')) {
        config.body = JSON.stringify(bodyData);
    }

    try {
        const response = await fetch(BACKEND_URL + endpoint, config);
        const data = await response.json();
        
        // Nếu Server báo lỗi 403 (Cấm truy cập do phân quyền) hoặc 401 (Hết hạn Token)
        if (response.status === 401 || response.status === 403) {
            if (endpoint !== '/auth/login') {
                alert("Bạn không có quyền truy cập chức năng này hoặc phiên đăng nhập đã hết hạn!");
                if(response.status === 401) {
                    localStorage.removeItem("user");
                    localStorage.removeItem("token");
                    window.location.href = "login.html";
                }
                throw new Error(data.message || "Forbidden");
            }
        }

        return data;
    } catch (error) {
        console.error("Lỗi gọi API:", error);
        throw error;
    }
}

// Kiểm tra trạng thái đăng nhập chung
function checkAuth() {
    const user = localStorage.getItem("user");
    if (!user) {
        window.location.href = "login.html";
    }
    return JSON.parse(user);
}

// Đăng xuất
function logout() {
    localStorage.removeItem("user");
    localStorage.removeItem("token");
    window.location.href = "login.html";
}
