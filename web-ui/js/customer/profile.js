import { api, getAuthSession, setAuthSession } from "../../api.js";
import { identityScannerMarkup, mountIdentityScanner } from './identity-scan.js';
import { escapeHtml } from '../shared/ui.js';

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
    identityCard: '',
    dateOfBirth: '',
    address: ''
  };

  container.innerHTML = `
    <div class="page-header mb-6">
      <h1 class="text-2xl font-bold font-serif text-white tracking-wide">Cài Đặt Hồ Sơ Cá Nhân</h1>
      <p class="text-xs md:text-sm text-slate-400 mt-1">Quản lý và cập nhật thông tin định danh thành viên khách sạn Aurelia Grand Palace</p>
    </div>

    <div class="grid grid-cols-1 gap-8">
      <!-- Member information appears before the editable profile -->
      <div class="profile-member-panel">
        <div class="card p-6 rounded-3xl bg-slate-900/80 border border-amber-500/20 shadow-xl space-y-5">
          <div class="flex items-center gap-4">
            <div class="w-14 h-14 rounded-2xl bg-gradient-to-tr from-amber-600 to-amber-300 text-slate-950 font-bold flex items-center justify-center text-xl shadow-md shrink-0">
              ${escapeHtml((currentProfile.fullName || session.username || 'A').trim().charAt(0).toUpperCase())}
            </div>
            <div class="min-w-0">
              <h3 class="font-bold text-white text-base truncate" id="profile-preview-name">${escapeHtml(currentProfile.fullName || session.username || 'Hội Viên')}</h3>
              <span id="profile-member-code" class="text-xs text-amber-400 font-semibold block mt-0.5">Mã thành viên: #CUST-${escapeHtml(String(customerId).padStart(5, '0'))}</span>
            </div>
          </div>

          <div class="p-4 rounded-2xl bg-slate-950/60 border border-slate-800 text-xs space-y-2.5">
            <div class="flex items-center justify-between">
              <span class="text-slate-400">Tên đăng nhập:</span>
              <span class="font-mono text-white font-semibold">${escapeHtml(session.username || 'guest')}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-slate-400">Vai trò tài khoản:</span>
              <span class="text-emerald-400 font-semibold uppercase">Thành viên Aurelia</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-slate-400">Giấy tờ định danh:</span>
              <span class="text-amber-300 font-semibold flex items-center gap-1">
                <span id="profile-identity-status">Đang tải trạng thái</span>
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

      <!-- Profile information form -->
      <div class="profile-form-panel">
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

            ${identityScannerMarkup}

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

  let edited = false;
  let loaded = false;
  const report = (message, success = false) => {
    if (!statusBox) return;
    statusBox.className = `alert ${success ? 'alert-success' : 'alert-error'} mb-6`;
    statusBox.textContent = message;
    statusBox.classList.remove('hidden');
  };
  const identityState = saved => {
    const node = document.getElementById('profile-identity-status');
    if (node) node.textContent = saved ? 'Đã lưu giấy tờ · đối chiếu tại quầy' : 'Chưa lưu giấy tờ';
    if (idcardInput) idcardInput.placeholder = saved ? 'Đã lưu giấy tờ. Chỉ nhập khi cần thay đổi.' : 'Nhập số CCCD hoặc Passport';
  };
  form?.addEventListener('input', () => { edited = true; });
  mountIdentityScanner(document.getElementById('profile-identity-scan'), {
    apply: data => {
      edited = true;
      if (fullnameInput) fullnameInput.value = data.fullName;
      if (idcardInput) idcardInput.value = data.identityCard;
      if (dobInput) dobInput.value = data.dateOfBirth;
      if (addressInput) addressInput.value = data.address;
      idcardInput?.focus();
    },
    requestOcr: (body, signal) => api.post('/customer-portal/identity/scan', body, { signal })
  });
  const syncName = name => {
    for (const id of ['profile-preview-name','user-display-name']) {
      const node = document.getElementById(id);
      if (node) node.textContent = name;
    }
  };

  if (btn) btn.disabled = true;
  try {
    const profile = await api.get('/customer-portal/profile');
    if (!profile || !profile.customerId) throw new Error('Không tải được hồ sơ của tài khoản.');
    loaded = true;
    if (!edited) {
      if (fullnameInput) fullnameInput.value = profile.fullName || '';
      if (phoneInput) phoneInput.value = profile.phone || '';
      if (emailInput) emailInput.value = profile.email || '';
      if (dobInput) dobInput.value = profile.dateOfBirth || '';
      if (addressInput) addressInput.value = profile.address || '';
      syncName(profile.fullName || session.username || 'Hội viên');
    }
    identityState(profile.identitySaved);
    const storedSession = getAuthSession();
    if (storedSession) {
      for (const field of ['identityCard','idCard','dateOfBirth','dob','address']) delete storedSession[field];
      setAuthSession(storedSession);
    }
    const code = document.getElementById('profile-member-code');
    if (code) code.textContent = `Mã thành viên: #CUST-${String(profile.customerId).padStart(5,'0')}`;
    // Remove legacy plaintext caches; scans and identity numbers stay out of browser storage.
    for (const id of new Set([customerId, profile.customerId])) {
      try { localStorage.removeItem(`aurelia_profile_${id}`); sessionStorage.removeItem(`aurelia_profile_${id}`); } catch {}
    }
  } catch {
    report('Chưa tải được hồ sơ. Hãy tải lại trang trước khi lưu thay đổi.');
  } finally {
    if (btn) btn.disabled = !loaded;
  }

  if (form) form.addEventListener('submit', async e => {
    e.preventDefault();
    if (!loaded) { report('Chưa tải được hồ sơ. Vui lòng tải lại trang.'); return; }
    if (btn) { btn.disabled = true; btn.setAttribute('aria-busy','true'); }
    const updatedData = {
      fullName: fullnameInput?.value.trim() || '', phone: phoneInput?.value.trim() || '',
      email: emailInput?.value.trim() || '', identityCard: idcardInput?.value.trim() || null,
      dateOfBirth: dobInput?.value || null, address: addressInput?.value.trim() || null
    };
    try {
      const saved = await api.put('/customer-portal/profile', updatedData);
      if (!saved?.customerId) throw new Error('Máy chủ chưa xác nhận việc lưu hồ sơ.');
      const updatedSession = {...(getAuthSession() || session),fullName:saved.fullName,phone:saved.phone,email:saved.email,customerId:saved.customerId};
      for (const field of ['identityCard','idCard','dateOfBirth','dob','address']) delete updatedSession[field];
      setAuthSession(updatedSession);
      syncName(saved.fullName);
      if (idcardInput) idcardInput.value = '';
      identityState(saved.identitySaved);
      report('Đã lưu hồ sơ thành công. Thông tin giấy tờ sẽ được đối chiếu khi nhận phòng.',true);
    } catch (error) {
      report(error.message || 'Chưa thể lưu hồ sơ. Vui lòng thử lại.');
    } finally {
      if (btn) { btn.disabled = false; btn.removeAttribute('aria-busy'); }
      refreshIcons();
    }
  });
}
