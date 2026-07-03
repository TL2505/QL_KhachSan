// Application Shell Controller & Router
import { api, getApiBaseUrl, setApiBaseUrl, getAuthSession, setAuthSession } from "./api.js";

// Core View Modules
import { loadAdminView } from "./js/view-admin.js";
import { loadStaffView } from "./js/view-staff.js";
import { loadCustomerView } from "./js/view-customer.js";
import { loadProfileView } from "./js/view-shared.js";
import { renderAdminCustomers } from "./js/admin/customers.js";

document.addEventListener("DOMContentLoaded", () => {
    initApp();
});

// Define global pagination component renderer
window.renderPaginationComponent = (containerId, totalItems, itemsPerPage, currentPage, onPageChange, onItemsPerPageChange) => {
    const container = document.getElementById(containerId);
    if (!container) return;

    const totalPages = Math.ceil(totalItems / itemsPerPage) || 1;
    
    // Safety boundaries
    if (currentPage < 1) currentPage = 1;
    if (currentPage > totalPages) currentPage = totalPages;

    let html = `
        <div style="display:flex; justify-content:space-between; align-items:center; padding: 16px; border-top: 1px solid var(--border); font-size: 13px;">
            <div style="display:flex; align-items:center; gap: 8px; color: var(--text-muted);">
                <span>Hiển thị</span>
                <select class="form-input" style="padding: 4px 8px; width: auto; font-size: 13px; height: 32px;" id="sel-per-page-${containerId}">
                    <option value="5" ${itemsPerPage === 5 ? 'selected' : ''}>5</option>
                    <option value="10" ${itemsPerPage === 10 ? 'selected' : ''}>10</option>
                    <option value="20" ${itemsPerPage === 20 ? 'selected' : ''}>20</option>
                    <option value="50" ${itemsPerPage === 50 ? 'selected' : ''}>50</option>
                </select>
                <span>bản ghi / trang (Tổng: ${totalItems})</span>
            </div>
            
            <div style="display:flex; align-items:center; gap: 4px;">
                <button class="btn btn-secondary" style="padding: 4px 8px; min-width: 32px; height: 32px; font-size: 13px;" id="btn-prev-${containerId}" ${currentPage === 1 ? 'disabled' : ''}>&lt;</button>
    `;

    for (let i = 1; i <= totalPages; i++) {
        if (totalPages > 5) {
            if (i === 1 || i === totalPages || (i >= currentPage - 1 && i <= currentPage + 1)) {
                html += `<button class="btn ${i === currentPage ? 'btn-primary' : 'btn-secondary'}" style="padding: 4px 8px; min-width: 32px; height: 32px; font-size: 13px;" data-page="${i}">${i}</button>`;
            } else if (i === currentPage - 2 || i === currentPage + 2) {
                html += `<span style="padding: 4px; color: var(--text-muted);">...</span>`;
            }
        } else {
            html += `<button class="btn ${i === currentPage ? 'btn-primary' : 'btn-secondary'}" style="padding: 4px 8px; min-width: 32px; height: 32px; font-size: 13px;" data-page="${i}">${i}</button>`;
        }
    }

    html += `
                <button class="btn btn-secondary" style="padding: 4px 8px; min-width: 32px; height: 32px; font-size: 13px;" id="btn-next-${containerId}" ${currentPage === totalPages ? 'disabled' : ''}>&gt;</button>
            </div>
        </div>
    `;

    container.innerHTML = html;

    // Events
    document.getElementById(`sel-per-page-${containerId}`).addEventListener("change", (e) => {
        onItemsPerPageChange(parseInt(e.target.value));
    });

    if (currentPage > 1) {
        document.getElementById(`btn-prev-${containerId}`).addEventListener("click", () => {
            onPageChange(currentPage - 1);
        });
    }

    if (currentPage < totalPages) {
        document.getElementById(`btn-next-${containerId}`).addEventListener("click", () => {
            onPageChange(currentPage + 1);
        });
    }

    const pageBtns = container.querySelectorAll("button[data-page]");
    pageBtns.forEach(btn => {
        btn.addEventListener("click", () => {
            const page = parseInt(btn.getAttribute("data-page"));
            if (page !== currentPage) {
                onPageChange(page);
            }
        });
    });
};

async function checkApiHealth() {
    try {
        const data = await api.get("/health");
        console.log("[App] API Health Status:", data);
    } catch (err) {
        console.error("[App] API Health Check Failed:", err.message);
    }
}

function initApp() {
    // 1. Setup API Connection Display
    const apiBaseInput = document.getElementById("config-api-input");
    const activeApiDisplay = document.getElementById("active-api-url");
    const currentBase = getApiBaseUrl();
    
    apiBaseInput.value = currentBase;
    if (activeApiDisplay) {
        activeApiDisplay.textContent = currentBase;
    }

    // 2. Event Listener for Saving API Configuration
    document.getElementById("btn-save-api-config").addEventListener("click", async () => {
        const val = apiBaseInput.value.trim();
        if (val) {
            setApiBaseUrl(val);
            if (activeApiDisplay) {
                activeApiDisplay.textContent = getApiBaseUrl();
            }
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

    // 5. Theme Toggle Logic
    const themeToggleBtn = document.getElementById("theme-toggle");
    const themeIcon = document.getElementById("theme-icon");
    const savedTheme = localStorage.getItem("theme") || "dark";
    if (savedTheme === "light") {
        document.documentElement.classList.add("light-theme");
        if (themeIcon) themeIcon.setAttribute("data-lucide", "sun");
    } else {
        if (themeIcon) themeIcon.setAttribute("data-lucide", "moon");
    }

    if (themeToggleBtn) {
        themeToggleBtn.addEventListener("click", () => {
            const isLight = document.documentElement.classList.toggle("light-theme");
            localStorage.setItem("theme", isLight ? "light" : "dark");
            
            if (themeIcon) {
                themeIcon.setAttribute("data-lucide", isLight ? "sun" : "moon");
                lucide.createIcons();
            }
        });
    }

    // 6. Sidebar Toggle Logic
    const sidebar = document.getElementById("app-sidebar");
    const btnToggleSidebar = document.getElementById("btn-toggle-sidebar");
    const sidebarToggleIcon = document.getElementById("sidebar-toggle-icon");
    const isCompact = localStorage.getItem("sidebarCompact") === "true";
    
    if (sidebar && isCompact) {
        sidebar.classList.add("compact");
    }

    if (btnToggleSidebar && sidebar) {
        btnToggleSidebar.addEventListener("click", () => {
            const compact = sidebar.classList.toggle("compact");
            localStorage.setItem("sidebarCompact", compact);
        });
    }

    // 7. Load Session state
    const session = getAuthSession();
    if (session) {
        showAppShell(session);
    } else {
        showAuthPage();
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
    const errorDiv   = document.getElementById("register-error");
    const successDiv = document.getElementById("register-success");
    const submitBtn  = e.target.querySelector("button[type='submit']");

    errorDiv.style.display   = "none";
    successDiv.style.display = "none";

    const username    = document.getElementById("reg-username").value.trim();
    const password    = document.getElementById("reg-password").value;
    const fullName    = document.getElementById("reg-name").value.trim();
    const identityCard = document.getElementById("reg-identity").value.trim();
    const phone       = document.getElementById("reg-phone").value.trim();
    const email       = document.getElementById("reg-email").value.trim();
    const address     = document.getElementById("reg-address").value.trim();

    // Disable button while submitting
    if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = "Đang xử lý..."; }

    try {
        await api.post("/auth/register", {
            username, password, fullName, identityCard, phone, email, address
        });

        document.getElementById("register-form").reset();

        // Hiển thị thông báo thành công với countdown
        let countdown = 3;
        const updateMsg = () => {
            successDiv.innerHTML = `
                <div style="display:flex;align-items:center;gap:8px;justify-content:center;">
                    <i data-lucide="check-circle" style="width:16px;height:16px;color:var(--success);flex-shrink:0;"></i>
                    <span>Đăng ký thành công! Chuyển về đăng nhập sau <strong>${countdown}s</strong>...</span>
                </div>`;
            successDiv.style.display = "block";
            lucide.createIcons();
        };
        updateMsg();

        const timer = setInterval(() => {
            countdown--;
            if (countdown <= 0) {
                clearInterval(timer);
                // Chuyển sang form đăng nhập và điền sẵn username
                successDiv.style.display = "none";
                document.getElementById("register-card").style.display = "none";
                document.getElementById("login-card").style.display    = "block";
                // Pre-fill username để người dùng chỉ cần nhập password
                const loginUsernameField = document.getElementById("login-username");
                if (loginUsernameField) {
                    loginUsernameField.value = username;
                    document.getElementById("login-password").focus();
                }
            } else {
                updateMsg();
            }
        }, 1000);

    } catch (err) {
        errorDiv.innerHTML = `
            <div style="display:flex;align-items:center;gap:8px;justify-content:center;">
                <i data-lucide="x-circle" style="width:16px;height:16px;flex-shrink:0;"></i>
                <span>${err.message || "Lỗi đăng ký!"}</span>
            </div>`;
        errorDiv.style.display = "block";
        lucide.createIcons();
        if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = "Đăng ký tài khoản"; }
    }
}


function handleLogout(e) {
    e.preventDefault();

    // Show styled confirmation modal instead of native confirm()
    const modal   = document.getElementById("global-modal");
    const content = document.getElementById("global-modal-content");
    modal.classList.add("active");

    content.innerHTML = `
        <div class="modal-header">
            <h2>Xác nhận đăng xuất</h2>
            <button class="modal-close" onclick="closeModal('global-modal')">&times;</button>
        </div>
        <div style="text-align:center; padding: 12px 0 28px;">
            <div style="width:60px;height:60px;border-radius:50%;background:rgba(217,119,6,0.12);border:1px solid rgba(217,119,6,0.3);display:flex;align-items:center;justify-content:center;margin:0 auto 16px;">
                <i data-lucide="log-out" style="width:26px;height:26px;color:var(--primary);"></i>
            </div>
            <p style="font-size:16px;font-weight:600;margin-bottom:8px;">Bạn muốn đăng xuất?</p>
            <p style="font-size:13px;color:var(--text-muted);">Phiên làm việc hiện tại sẽ kết thúc.</p>
        </div>
        <div class="flex-row" style="justify-content:flex-end;">
            <button class="btn btn-secondary" onclick="closeModal('global-modal')">Ở lại</button>
            <button class="btn btn-primary" id="confirm-logout-btn">
                <i data-lucide="log-out" style="width:14px;height:14px;"></i> Đăng xuất
            </button>
        </div>
    `;
    lucide.createIcons();

    document.getElementById("confirm-logout-btn").addEventListener("click", () => {
        closeModal("global-modal");
        setAuthSession(null);
        showAuthPage();
    });
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
            { id: "dashboard",  label: "Trang chủ",          icon: "layout-dashboard", view: loadAdminView },
            { id: "rooms",      label: "Quản lý phòng",       icon: "key",              view: () => loadAdminView("rooms") },
            { id: "users",      label: "Quản lý nhân viên",   icon: "users",            view: () => loadAdminView("users") },
            { id: "customers",  label: "Quản lý khách hàng",  icon: "contact",          view: () => {
                const container = document.getElementById("view-container");
                const session   = JSON.parse(localStorage.getItem("hotel_auth_session"));
                renderAdminCustomers(container, session);
            }},
            { id: "promotions", label: "Khuyến mãi",          icon: "percent",          view: () => loadAdminView("promotions") },
            { id: "reviews",    label: "Quản lý đánh giá",    icon: "star",             view: () => loadAdminView("reviews") },
            { id: "invoices",   label: "Hóa đơn",             icon: "receipt",          view: () => loadAdminView("invoices") }
        );
    } else if (role === "STAFF") {
        menus.push(
            { id: "room-map", label: "Sơ đồ phòng", icon: "grid", view: loadStaffView },
            { id: "bookings", label: "Đơn đặt phòng", icon: "calendar-days", view: () => loadStaffView("bookings") },
            { id: "customers", label: "Khách hàng", icon: "users", view: () => loadStaffView("customers") },
            { id: "checkout", label: "Thanh toán", icon: "wallet", view: () => loadStaffView("checkout") },
            { id: "invoices", label: "Hóa đơn", icon: "receipt", view: () => loadStaffView("invoices") },
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
