// Application Shell Controller & Router
import { api, getApiBaseUrl, setApiBaseUrl, getAuthSession, setAuthSession } from "./api.js";

// Core View Modules
import { loadAdminView } from "./js/view-admin.js";
import { loadStaffView } from "./js/view-staff.js";
import { loadCustomerView } from "./js/view-customer.js";
import { loadProfileView } from "./js/view-shared.js";

document.addEventListener("DOMContentLoaded", () => {
    initApp();
});

function initApp() {
    // 1. Setup API Connection Display
    const apiBaseInput = document.getElementById("config-api-input");
    const activeApiDisplay = document.getElementById("active-api-url");
    const currentBase = getApiBaseUrl();
    
    apiBaseInput.value = currentBase;
    activeApiDisplay.textContent = currentBase;

    // 2. Event Listener for Saving API Configuration
    document.getElementById("btn-save-api-config").addEventListener("click", () => {
        const val = apiBaseInput.value.trim();
        if (val) {
            setApiBaseUrl(val);
            activeApiDisplay.textContent = getApiBaseUrl();
            alert("Đã lưu địa chỉ kết nối API!");
            closeModal("modal-api-config");
            
            // Re-check api health
            checkApiHealth();
        }
    });

    document.getElementById("btn-config-api").addEventListener("click", (e) => {
        e.preventDefault();
        openModal("modal-api-config");
    });

    // 3. Routing toggle between login/register cards
    document.getElementById("to-register").addEventListener("click", (e) => {
        e.preventDefault();
        document.getElementById("login-card").style.display = "none";
        document.getElementById("register-card").style.display = "block";
    });
    document.getElementById("to-login").addEventListener("click", (e) => {
        e.preventDefault();
        document.getElementById("register-card").style.display = "none";
        document.getElementById("login-card").style.display = "block";
    });

    // 4. Submit Authentication forms
    document.getElementById("login-form").addEventListener("submit", handleLogin);
    document.getElementById("register-form").addEventListener("submit", handleRegister);
    document.getElementById("btn-logout").addEventListener("click", handleLogout);

    // Check backend connection health
    checkApiHealth();

    // 5. Load Session state
    const session = getAuthSession();
    if (session) {
        showAppShell(session);
    } else {
        showAuthPage();
    }
}

async function checkApiHealth() {
    const statusText = document.getElementById("connection-status");
    try {
        await api.get("/health");
        statusText.textContent = "Đang kết nối API Backend";
        statusText.style.color = "var(--success)";
    } catch (e) {
        statusText.textContent = "Mất kết nối API Backend";
        statusText.style.color = "var(--danger)";
    }
}

async function handleLogin(e) {
    e.preventDefault();
    const errorDiv = document.getElementById("login-error");
    errorDiv.style.display = "none";

    const username = document.getElementById("login-username").value.trim();
    const password = document.getElementById("login-password").value;

    try {
        const data = await api.post("/auth/login", { username, password });
        // Expected data structure: { userId, username, role, fullName, email, phone, customerId }
        setAuthSession(data);
        showAppShell(data);
    } catch (err) {
        errorDiv.textContent = err.message || "Lỗi đăng nhập!";
        errorDiv.style.display = "block";
    }
}

async function handleRegister(e) {
    e.preventDefault();
    const errorDiv = document.getElementById("register-error");
    const successDiv = document.getElementById("register-success");
    errorDiv.style.display = "none";
    successDiv.style.display = "none";

    const username = document.getElementById("reg-username").value.trim();
    const password = document.getElementById("reg-password").value;
    const fullName = document.getElementById("reg-name").value.trim();
    const identityCard = document.getElementById("reg-identity").value.trim();
    const phone = document.getElementById("reg-phone").value.trim();
    const email = document.getElementById("reg-email").value.trim();
    const address = document.getElementById("reg-address").value.trim();

    try {
        await api.post("/auth/register", {
            username, password, fullName, identityCard, phone, email, address
        });
        successDiv.textContent = "Đăng ký thành công! Hãy chuyển sang Đăng nhập.";
        successDiv.style.display = "block";
        document.getElementById("register-form").reset();
    } catch (err) {
        errorDiv.textContent = err.message || "Lỗi đăng ký!";
        errorDiv.style.display = "block";
    }
}

function handleLogout(e) {
    e.preventDefault();
    if (confirm("Bạn có muốn đăng xuất khỏi hệ thống không?")) {
        setAuthSession(null);
        showAuthPage();
    }
}

function showAuthPage() {
    document.getElementById("app-page").style.display = "none";
    document.getElementById("auth-page").style.display = "flex";
    document.getElementById("login-form").reset();
    document.getElementById("register-form").reset();
}

// Build Layout Shell & Render Dynamic Menus
function showAppShell(session) {
    document.getElementById("auth-page").style.display = "none";
    document.getElementById("app-page").style.display = "flex";

    document.getElementById("user-display-name").textContent = session.fullName || session.username;
    document.getElementById("user-display-role").textContent = session.role;

    // Load dynamic navigation sidebar based on Role
    buildSidebarMenu(session.role);
}

function buildSidebarMenu(role) {
    const menuList = document.getElementById("sidebar-menu");
    menuList.innerHTML = ""; // Clear
    
    // Define navigation items based on Role
    const menus = [];
    
    if (role === "ADMIN") {
        menus.push(
            { id: "dashboard", label: "Trang chủ", icon: "layout-dashboard", view: loadAdminView },
            { id: "rooms", label: "Quản lý phòng", icon: "key", view: () => loadAdminView("rooms") },
            { id: "users", label: "Quản lý nhân viên", icon: "users", view: () => loadAdminView("users") },
            { id: "promotions", label: "Khuyến mãi", icon: "percent", view: () => loadAdminView("promotions") },
            { id: "reviews", label: "Quản lý đánh giá", icon: "star", view: () => loadAdminView("reviews") },
            { id: "invoices", label: "Hóa đơn", icon: "receipt", view: () => loadAdminView("invoices") }
        );
    } else if (role === "STAFF") {
        menus.push(
            { id: "room-map", label: "Sơ đồ phòng", icon: "grid", view: loadStaffView },
            { id: "bookings", label: "Đơn đặt phòng", icon: "calendar-days", view: () => loadStaffView("bookings") },
            { id: "customers", label: "Khách hàng", icon: "users", view: () => loadStaffView("customers") },
            { id: "payments", label: "Thanh toán", icon: "credit-card", view: () => loadStaffView("payments") },
            { id: "loyalty", label: "Hệ thành viên", icon: "award", view: () => loadStaffView("loyalty") },
            { id: "chat", label: "Hỗ trợ khách", icon: "message-square", view: () => loadStaffView("chat") }
        );
    } else if (role === "CUSTOMER") {
        menus.push(
            { id: "customer-home", label: "Bảng điều khiển", icon: "layout-dashboard", view: loadCustomerView },
            { id: "room-discovery", label: "Tìm phòng", icon: "search", view: () => loadCustomerView("discovery") },
            { id: "loyalty-rank", label: "Điểm tích lũy", icon: "award", view: () => loadCustomerView("loyalty") },
            { id: "promotions-view", label: "Ưu đãi của tôi", icon: "gift", view: () => loadCustomerView("promotions") },
            { id: "support-chat", label: "Hỗ trợ trực tuyến", icon: "message-circle", view: () => loadCustomerView("chat") }
        );
    }
    
    // Shared Menu Item: Profile settings
    menus.push({ id: "profile", label: "Hồ sơ cá nhân", icon: "user-cog", view: loadProfileView });

    // Render HTML nodes for menus
    menus.forEach(m => {
        const li = document.createElement("li");
        li.className = "menu-item";
        li.dataset.id = m.id;
        
        li.innerHTML = `
            <a href="#" class="menu-link">
                <i data-lucide="${m.icon}" style="width: 18px; height: 18px;"></i>
                <span>${m.label}</span>
            </a>
        `;
        
        li.addEventListener("click", (e) => {
            e.preventDefault();
            
            // Set active state styling
            document.querySelectorAll(".menu-item").forEach(item => item.classList.remove("active"));
            li.classList.add("active");
            
            // Load the corresponding view
            m.view();
        });
        
        menuList.appendChild(li);
    });

    // Initialize lucide icons for dynamic sidebar elements
    lucide.createIcons();

    // Load the first view by default
    if (menus.length > 0) {
        menuList.firstChild.click();
    }
}
