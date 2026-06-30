// Shared Views Module
import { api, getAuthSession, setAuthSession } from "../api.js";

export function loadProfileView() {
    const container = document.getElementById("view-container");
    const session = getAuthSession();

    container.innerHTML = `
        <div class="page-header">
            <h1>Hồ Sơ & Bảo Mật</h1>
            <p>Quản lý thông tin tài khoản cá nhân và mật khẩu của bạn</p>
        </div>

        <div class="grid grid-cols-2">
            <!-- 1. Edit Profile Info Card -->
            <div class="card">
                <h3 style="font-size: 18px; margin-bottom: 20px; border-bottom: 1px solid var(--border); padding-bottom: 10px;">
                    Thông tin cá nhân
                </h3>
                <div id="profile-success" style="color: var(--success); font-size: 14px; margin-bottom: 15px; display: none;"></div>
                <div id="profile-error" style="color: var(--danger); font-size: 14px; margin-bottom: 15px; display: none;"></div>

                <form id="form-update-profile">
                    <div class="form-group">
                        <label class="form-label">Tên đăng nhập</label>
                        <input class="form-input" type="text" value="${session.username}" disabled style="opacity: 0.6; cursor: not-allowed;">
                    </div>
                    <div class="form-group">
                        <label class="form-label">Họ và tên</label>
                        <input class="form-input" type="text" id="prof-fullname" value="${session.fullName || ''}" required>
                    </div>
                    <div class="form-group">
                        <label class="form-label">Số điện thoại</label>
                        <input class="form-input" type="text" id="prof-phone" value="${session.phone || ''}">
                    </div>
                    <div class="form-group">
                        <label class="form-label">Email</label>
                        <input class="form-input" type="email" id="prof-email" value="${session.email || ''}">
                    </div>
                    <button type="submit" class="btn btn-primary">Lưu thay đổi</button>
                </form>
            </div>

            <!-- 2. Change Password Card -->
            <div class="card">
                <h3 style="font-size: 18px; margin-bottom: 20px; border-bottom: 1px solid var(--border); padding-bottom: 10px;">
                    Đổi mật khẩu bảo mật
                </h3>
                <div id="pass-success" style="color: var(--success); font-size: 14px; margin-bottom: 15px; display: none;"></div>
                <div id="pass-error" style="color: var(--danger); font-size: 14px; margin-bottom: 15px; display: none;"></div>

                <form id="form-change-password">
                    <div class="form-group">
                        <label class="form-label">Mật khẩu hiện tại</label>
                        <input class="form-input" type="password" id="pass-old" required>
                    </div>
                    <div class="form-group">
                        <label class="form-label">Mật khẩu mới</label>
                        <input class="form-input" type="password" id="pass-new" required>
                    </div>
                    <div class="form-group">
                        <label class="form-label">Xác nhận mật khẩu mới</label>
                        <input class="form-input" type="password" id="pass-confirm" required>
                    </div>
                    <button type="submit" class="btn btn-primary">Đổi mật khẩu</button>
                </form>
            </div>
        </div>
    `;

    // Add submit event listeners
    document.getElementById("form-update-profile").addEventListener("submit", async (e) => {
        e.preventDefault();
        const successDiv = document.getElementById("profile-success");
        const errorDiv = document.getElementById("profile-error");
        successDiv.style.display = "none";
        errorDiv.style.display = "none";

        const fullName = document.getElementById("prof-fullname").value.trim();
        const phone = document.getElementById("prof-phone").value.trim();
        const email = document.getElementById("prof-email").value.trim();

        try {
            await api.post("/users/update-profile", {
                username: session.username,
                fullName,
                phone,
                email
            });
            
            // Update current localStorage session
            session.fullName = fullName;
            session.phone = phone;
            session.email = email;
            setAuthSession(session);
            
            // Update display name at the bottom sidebar
            document.getElementById("user-display-name").textContent = fullName;
            
            successDiv.textContent = "Cập nhật thông tin cá nhân thành công!";
            successDiv.style.display = "block";
        } catch (err) {
            errorDiv.textContent = err.message || "Lỗi cập nhật hồ sơ!";
            errorDiv.style.display = "block";
        }
    });

    document.getElementById("form-change-password").addEventListener("submit", async (e) => {
        e.preventDefault();
        const successDiv = document.getElementById("pass-success");
        const errorDiv = document.getElementById("pass-error");
        successDiv.style.display = "none";
        errorDiv.style.display = "none";

        const oldPassword = document.getElementById("pass-old").value;
        const newPassword = document.getElementById("pass-new").value;
        const confirmPassword = document.getElementById("pass-confirm").value;

        if (newPassword !== confirmPassword) {
            errorDiv.textContent = "Mật khẩu xác nhận không khớp!";
            errorDiv.style.display = "block";
            return;
        }

        try {
            await api.post("/users/change-password", {
                username: session.username,
                oldPassword,
                newPassword
            });
            successDiv.textContent = "Thay đổi mật khẩu thành công!";
            successDiv.style.display = "block";
            document.getElementById("form-change-password").reset();
        } catch (err) {
            errorDiv.textContent = err.message || "Lỗi thay đổi mật khẩu!";
            errorDiv.style.display = "block";
        }
    });
}
