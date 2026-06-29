document.addEventListener("DOMContentLoaded", function() {
    const loginForm = document.getElementById("loginForm");
    
    if (loginForm) {
        loginForm.addEventListener("submit", async function(event) {
            event.preventDefault(); // Ngăn form reload trang

            const username = document.getElementById("username").value;
            const password = document.getElementById("password").value;
            const errorBox = document.getElementById("error-message");
            const btnLogin = document.getElementById("btnLogin");

            // Reset UI
            errorBox.classList.add("d-none");
            btnLogin.disabled = true;
            btnLogin.innerHTML = `<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Đang đăng nhập...`;

            try {
                // Gọi API qua file api.js dùng fetchApi()
                const data = await fetchApi('/auth/login', 'POST', {
                    username: username,
                    password: password
                });

                if (data.status === "success") {
                    const user = data.data;
                    localStorage.setItem("user", JSON.stringify(user));
                    localStorage.setItem("token", data.token);
                    
                    // Phân quyền Điều hướng (RBAC Routing)
                    if (user.role === 'ADMIN') { 
                        window.location.href = "/admin/admin.html";
                    } else if (user.role === 'CUSTOMER') { 
                        window.location.href = "/customer/customer.html";
                    } else { // STAFF
                        window.location.href = "/staff/reception.html";
                    }
                } else {
                    showError(data.message || "Đăng nhập thất bại.");
                }
            } catch (error) {
                showError("Không thể kết nối đến Máy chủ. Vui lòng kiểm tra Docker Nginx!");
            } finally {
                btnLogin.disabled = false;
                btnLogin.innerHTML = "Đăng nhập ngay";
            }
        });
    }

    function showError(message) {
        const errorBox = document.getElementById("error-message");
        errorBox.textContent = message;
        errorBox.classList.remove("d-none");
    }
});
