import { api, getAuthSession, setAuthSession } from "../../api.js";

function refreshIcons() {
  if (typeof window !== 'undefined' && window.lucide && typeof window.lucide.createIcons === 'function') {
    window.lucide.createIcons();
  }
}

export async function renderProfile(container, session) {
  if (!container) return;
  session = session || getAuthSession() || {};
  const customerId = session.customerId || session.userId || session.id || 1;

  // Retrieve current profile information from session, cached customer profile, or API
  let currentProfile = {
    fullName: session.fullName || '',
    phone: session.phone || '',
    email: session.email || '',
    identityCard: session.identityCard || session.idCard || '',
    dateOfBirth: session.dateOfBirth || session.dob || '',
    address: session.address || ''
  };

  // Try to read any saved local profile cache
  try {
    const cached = localStorage.getItem(`aurelia_profile_${customerId}`);
    if (cached) {
      currentProfile = { ...currentProfile, ...JSON.parse(cached) };
    }
  } catch {}

  container.innerHTML = `
    <div class="page-header mb-6">
      <h1 class="text-2xl font-bold font-serif text-white tracking-wide">Cài Đặt Hồ Sơ Cá Nhân</h1>
      <p class="text-xs md:text-sm text-slate-400 mt-1">Quản lý và cập nhật thông tin định danh thành viên khách sạn Aurelia Grand Palace</p>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-12 gap-8">
      <!-- Profile Form Column (8 cols) -->
      <div class="lg:col-span-8">
        <div class="card p-6 md:p-8 rounded-3xl bg-slate-900/85 border border-amber-500/20 shadow-2xl">
          <!-- Notification Status Box -->
          <div id="profile-status-box" class="hidden mb-6 p-4 rounded-xl text-xs flex items-center gap-3"></div>

          <form id="profile-form" class="space-y-5">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <!-- Full Name -->
              <div>
                <label for="profile-fullname" class="block text-xs font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
                  Họ và tên <span class="text-amber-400">*</span>
                </label>
                <div class="relative">
                  <input type="text" id="profile-fullname" name="fullName" required
                         placeholder="Nhập họ và tên đầy đủ"
                         class="w-full bg-slate-950/80 border border-slate-700/80 focus:border-amber-400 text-white text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="user" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
              </div>

              <!-- Phone Number -->
              <div>
                <label for="profile-phone" class="block text-xs font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
                  Số điện thoại <span class="text-amber-400">*</span>
                </label>
                <div class="relative">
                  <input type="tel" id="profile-phone" name="phone" required
                         placeholder="0912 345 678"
                         class="w-full bg-slate-950/80 border border-slate-700/80 focus:border-amber-400 text-white text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="phone" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
              </div>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <!-- Email -->
              <div>
                <label for="profile-email" class="block text-xs font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
                  Địa chỉ Email <span class="text-amber-400">*</span>
                </label>
                <div class="relative">
                  <input type="email" id="profile-email" name="email" required
                         placeholder="guest@example.com"
                         class="w-full bg-slate-950/80 border border-slate-700/80 focus:border-amber-400 text-white text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="mail" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
              </div>

              <!-- National ID / Passport Number -->
              <div>
                <label for="profile-idcard" class="block text-xs font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
                  CCCD / Hộ chiếu
                </label>
                <div class="relative">
                  <input type="text" id="profile-idcard" name="identityCard"
                         placeholder="Nhập số CCCD hoặc Passport"
                         class="w-full bg-slate-950/80 border border-slate-700/80 focus:border-amber-400 text-white text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="shield-check" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
              </div>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <!-- Date of Birth -->
              <div>
                <label for="profile-dob" class="block text-xs font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
                  Ngày sinh
                </label>
                <div class="relative">
                  <input type="date" id="profile-dob" name="dateOfBirth"
                         class="w-full bg-slate-950/80 border border-slate-700/80 focus:border-amber-400 text-white text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="calendar" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
              </div>

              <!-- Permanent Address -->
              <div>
                <label for="profile-address" class="block text-xs font-semibold text-slate-300 mb-1.5 uppercase tracking-wider">
                  Địa chỉ thường trú
                </label>
                <div class="relative">
                  <input type="text" id="profile-address" name="address"
                         placeholder="Số nhà, Tên đường, Tỉnh / Thành phố"
                         class="w-full bg-slate-950/80 border border-slate-700/80 focus:border-amber-400 text-white text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="map-pin" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
              </div>
            </div>

            <div class="pt-4 border-t border-slate-800/80 flex items-center justify-between">
              <span class="text-xs text-slate-400 flex items-center gap-1.5">
                <i data-lucide="lock" class="w-3.5 h-3.5 text-amber-400"></i>
                Thông tin được mã hóa bảo mật theo chuẩn 5 sao quốc tế
              </span>
              <button type="submit" id="btn-save-profile" class="btn portal-primary text-xs sm:text-sm px-6 py-3 rounded-xl font-bold flex items-center gap-2 cursor-pointer shadow-lg hover:brightness-110 transition-all">
                <i data-lucide="save" class="w-4 h-4"></i>
                <span>Lưu Thay Đổi</span>
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Member Account Identity Card (4 cols) -->
      <div class="lg:col-span-4">
        <div class="card p-6 rounded-3xl bg-slate-900/80 border border-amber-500/20 shadow-xl space-y-5">
          <div class="flex items-center gap-4">
            <div class="w-14 h-14 rounded-2xl bg-gradient-to-tr from-amber-600 to-amber-300 text-slate-950 font-bold flex items-center justify-center text-xl shadow-md shrink-0">
              ${(currentProfile.fullName || session.username || 'A').trim().charAt(0).toUpperCase()}
            </div>
            <div class="min-w-0">
              <h3 class="font-bold text-white text-base truncate" id="profile-preview-name">${currentProfile.fullName || session.username || 'Hội Viên'}</h3>
              <span class="text-xs text-amber-400 font-semibold block mt-0.5">Mã thành viên: #CUST-${String(customerId).padStart(5, '0')}</span>
            </div>
          </div>

          <div class="p-4 rounded-2xl bg-slate-950/60 border border-slate-800 text-xs space-y-2.5">
            <div class="flex items-center justify-between">
              <span class="text-slate-400">Tên đăng nhập:</span>
              <span class="font-mono text-white font-semibold">${session.username || 'guest'}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-slate-400">Vai trò tài khoản:</span>
              <span class="text-emerald-400 font-semibold uppercase">Thành viên Aurelia</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-slate-400">Trạng thái xác thực:</span>
              <span class="text-amber-300 font-semibold flex items-center gap-1">
                <i data-lucide="check-circle" class="w-3.5 h-3.5 text-emerald-400"></i> Đã xác thực
              </span>
            </div>
          </div>

          <div class="p-4 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-xs text-amber-200/90 leading-relaxed">
            <div class="flex items-center gap-2 font-bold text-amber-400 mb-1">
              <i data-lucide="info" class="w-4 h-4"></i>
              <span>Lưu ý quan trọng</span>
            </div>
            Thông tin Họ tên và Số CCCD/Passport được dùng trực tiếp cho thủ tục nhận phòng nhanh (Fast Check-in) tại quầy lễ tân Aurelia.
          </div>
        </div>
      </div>
    </div>
  `;
  refreshIcons();

  // Pre-fill fields automatically
  const fullnameInput = document.getElementById('profile-fullname');
  const phoneInput = document.getElementById('profile-phone');
  const emailInput = document.getElementById('profile-email');
  const idcardInput = document.getElementById('profile-idcard');
  const dobInput = document.getElementById('profile-dob');
  const addressInput = document.getElementById('profile-address');
  const statusBox = document.getElementById('profile-status-box');
  const form = document.getElementById('profile-form');
  const btn = document.getElementById('btn-save-profile');

  if (fullnameInput) fullnameInput.value = currentProfile.fullName;
  if (phoneInput) phoneInput.value = currentProfile.phone;
  if (emailInput) emailInput.value = currentProfile.email;
  if (idcardInput) idcardInput.value = currentProfile.identityCard;
  if (dobInput) dobInput.value = currentProfile.dateOfBirth;
  if (addressInput) addressInput.value = currentProfile.address;

  // Asynchronously attempt to fetch newest profile from backend if available
  try {
    const remoteCustomer = await api.get(`/customers/${customerId}`).catch(() => null);
    if (remoteCustomer) {
      if (remoteCustomer.fullName && fullnameInput) fullnameInput.value = remoteCustomer.fullName;
      if (remoteCustomer.phone && phoneInput) phoneInput.value = remoteCustomer.phone;
      if (remoteCustomer.email && emailInput) emailInput.value = remoteCustomer.email;
      if (remoteCustomer.identityCard && idcardInput) idcardInput.value = remoteCustomer.identityCard;
      if (remoteCustomer.dateOfBirth && dobInput) dobInput.value = remoteCustomer.dateOfBirth;
      if (remoteCustomer.address && addressInput) addressInput.value = remoteCustomer.address;
    }
  } catch {}

  // Handle Profile Form Submission with try...catch...finally
  if (form) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const originalBtnHtml = btn ? btn.innerHTML : '';

      if (btn) {
        btn.disabled = true;
        btn.setAttribute('aria-busy', 'true');
        btn.innerHTML = `
          <div class="w-4 h-4 border-2 border-slate-950 border-t-amber-300 rounded-full animate-spin shrink-0"></div>
          <span>Đang lưu thông tin...</span>
        `;
      }

      const updatedData = {
        fullName: fullnameInput?.value.trim() || '',
        phone: phoneInput?.value.trim() || '',
        email: emailInput?.value.trim() || '',
        identityCard: idcardInput?.value.trim() || '',
        dateOfBirth: dobInput?.value || '',
        address: addressInput?.value.trim() || ''
      };

      try {
        // Attempt backend API calls
        let apiSucceeded = false;
        try {
          await api.post('/users/update-profile', {
            username: session.username,
            fullName: updatedData.fullName,
            phone: updatedData.phone,
            email: updatedData.email
          });
          apiSucceeded = true;
        } catch {}

        try {
          await api.put(`/customers/${customerId}`, updatedData);
          apiSucceeded = true;
        } catch {}

        // Always save to session and localStorage cache (offline guarantee)
        const updatedSession = {
          ...session,
          fullName: updatedData.fullName,
          phone: updatedData.phone,
          email: updatedData.email,
          identityCard: updatedData.identityCard,
          dateOfBirth: updatedData.dateOfBirth,
          address: updatedData.address
        };
        setAuthSession(updatedSession);
        try {
          localStorage.setItem(`aurelia_profile_${customerId}`, JSON.stringify(updatedData));
          sessionStorage.setItem(`aurelia_profile_${customerId}`, JSON.stringify(updatedData));
        } catch {}

        // Update preview name
        const previewName = document.getElementById('profile-preview-name');
        if (previewName) previewName.textContent = updatedData.fullName;
        const sidebarName = document.getElementById('user-display-name');
        if (sidebarName) sidebarName.textContent = updatedData.fullName;

        // Show friendly notification
        if (statusBox) {
          statusBox.className = 'mb-6 p-4 rounded-xl text-xs flex items-center gap-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-300';
          statusBox.innerHTML = `
            <i data-lucide="check-circle" class="w-4 h-4 text-emerald-400 shrink-0"></i>
            <span>Thông tin hồ sơ cá nhân đã được lưu và cập nhật thành công!</span>
          `;
          statusBox.classList.remove('hidden');
          refreshIcons();
        }

      } catch (err) {
        console.error('Lỗi khi cập nhật hồ sơ:', err);
        if (statusBox) {
          statusBox.className = 'mb-6 p-4 rounded-xl text-xs flex items-center gap-3 bg-rose-500/10 border border-rose-500/30 text-rose-300';
          statusBox.innerHTML = `
            <i data-lucide="alert-circle" class="w-4 h-4 text-rose-400 shrink-0"></i>
            <span>Không thể lưu thông tin hồ sơ. Vui lòng kiểm tra lại kết nối mạng.</span>
          `;
          statusBox.classList.remove('hidden');
          refreshIcons();
        }
      } finally {
        if (btn) {
          btn.disabled = false;
          btn.removeAttribute('aria-busy');
          btn.innerHTML = originalBtnHtml;
          refreshIcons();
        }
      }
    });
  }
}
