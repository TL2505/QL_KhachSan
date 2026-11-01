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

  container.classList.add('customer-profile-view');
  container.innerHTML = `
    <div class="page-header mb-6">
      <h1 class="text-2xl font-bold font-serif customer-text tracking-wide">Cài Đặt Hồ Sơ Cá Nhân</h1>
      <p class="text-xs md:text-sm customer-muted mt-1">Quản lý và cập nhật thông tin định danh thành viên khách sạn Aurelia Grand Palace</p>
    </div>

    <div class="grid grid-cols-1 gap-8">
      <!-- Member information appears before the editable profile -->
      <div class="profile-member-panel">
        <div class="card p-6 rounded-3xl customer-secondary-surface border border-amber-500/20 shadow-xl space-y-5">
          <div class="flex items-center gap-4">
            <div class="w-14 h-14 rounded-2xl bg-gradient-to-tr from-amber-600 to-amber-300 text-slate-950 font-bold flex items-center justify-center text-xl shadow-md shrink-0">
              ${escapeHtml((currentProfile.fullName || session.username || 'A').trim().charAt(0).toUpperCase())}
            </div>
            <div class="min-w-0">
              <h3 class="font-bold customer-text text-base truncate" id="profile-preview-name">${escapeHtml(currentProfile.fullName || session.username || 'Hội Viên')}</h3>
              <span id="profile-member-code" class="text-xs customer-accent font-semibold block mt-0.5">Mã thành viên: #CUST-${escapeHtml(String(customerId).padStart(5, '0'))}</span>
            </div>
          </div>

          <div class="customer-profile-summary p-4 rounded-2xl customer-secondary-surface border customer-border text-xs space-y-2.5">
            <div class="flex items-center justify-between">
              <span class="customer-muted">Tên đăng nhập:</span>
              <span class="font-mono customer-text font-semibold">${escapeHtml(session.username || 'guest')}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="customer-muted">Vai trò tài khoản:</span>
              <span class="customer-success font-semibold uppercase">Thành viên Aurelia</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="customer-muted">Giấy tờ định danh:</span>
              <span class="customer-accent font-semibold flex items-center gap-1">
                <span id="profile-identity-status">Đang tải trạng thái</span>
              </span>
            </div>
          </div>

          <div class="customer-profile-note p-4 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-xs customer-accent leading-relaxed">
            <div class="flex items-center gap-2 font-bold customer-accent mb-1">
              <i data-lucide="info" class="w-4 h-4"></i>
              <span>Lưu ý quan trọng</span>
            </div>
            Thông tin Họ tên và Số CCCD/Passport được dùng trực tiếp cho thủ tục nhận phòng nhanh (Fast Check-in) tại quầy lễ tân Aurelia.
          </div>
        </div>
      </div>

      <!-- Profile information form -->
      <div class="profile-form-panel">
        <div class="card p-6 md:p-8 rounded-3xl customer-secondary-surface border border-amber-500/20 shadow-2xl">
          <!-- Notification Status Box -->
          <div id="profile-status-box" class="hidden mb-6 p-4 rounded-xl text-xs flex items-center gap-3" role="status" aria-live="polite"></div>

          <form id="profile-form" class="space-y-5" novalidate>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <!-- Full Name -->
              <div class="profile-field">
                <label for="profile-fullname" class="block text-xs font-semibold customer-muted mb-1.5 uppercase tracking-wider">
                  Họ và tên <span class="customer-accent">*</span>
                </label>
                <div class="relative">
                  <input type="text" id="profile-fullname" name="fullName" required maxlength="100" autocomplete="name"
                         placeholder="Nhập họ và tên đầy đủ"
                         aria-describedby="profile-fullname-hint profile-fullname-error"
                         class="input w-full customer-secondary-surface border customer-border focus:border-amber-400 customer-text text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="user" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
                <p id="profile-fullname-hint" class="profile-field-hint customer-muted">Bắt buộc, tối đa 100 ký tự.</p>
                <p id="profile-fullname-error" class="validator-hint profile-field-error text-error" hidden aria-live="polite"></p>
              </div>

              <!-- Phone Number -->
              <div class="profile-field">
                <label for="profile-phone" class="block text-xs font-semibold customer-muted mb-1.5 uppercase tracking-wider">
                  Số điện thoại <span class="customer-accent">*</span>
                </label>
                <div class="relative">
                  <input type="tel" id="profile-phone" name="phone" required maxlength="20" autocomplete="tel" inputmode="tel"
                         placeholder="0912345678"
                         aria-describedby="profile-phone-hint profile-phone-error"
                         class="input w-full customer-secondary-surface border customer-border focus:border-amber-400 customer-text text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="phone" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
                <p id="profile-phone-hint" class="profile-field-hint customer-muted">Giữ nguyên số đang lưu được phép. Khi đổi số: nhập 10 số bắt đầu 03/05/07/08/09 hoặc dạng +84912345678; tối đa 20 ký tự.</p>
                <p id="profile-phone-error" class="validator-hint profile-field-error text-error" hidden aria-live="polite"></p>
              </div>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <!-- Email -->
              <div class="profile-field">
                <label for="profile-email" class="block text-xs font-semibold customer-muted mb-1.5 uppercase tracking-wider">
                  Địa chỉ Email <span class="customer-accent">*</span>
                </label>
                <div class="relative">
                  <input type="email" id="profile-email" name="email" required maxlength="100" autocomplete="email"
                         placeholder="guest@example.com"
                         aria-describedby="profile-email-hint profile-email-error"
                         class="input w-full customer-secondary-surface border customer-border focus:border-amber-400 customer-text text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="mail" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
                <p id="profile-email-hint" class="profile-field-hint customer-muted">Bắt buộc, ví dụ ten@domain.vn; tối đa 100 ký tự.</p>
                <p id="profile-email-error" class="validator-hint profile-field-error text-error" hidden aria-live="polite"></p>
              </div>

              <!-- National ID / Passport Number -->
              <div class="profile-field">
                <label for="profile-idcard" class="block text-xs font-semibold customer-muted mb-1.5 uppercase tracking-wider">
                  CCCD / Hộ chiếu
                </label>
                <div class="relative">
                  <input type="password" id="profile-idcard" name="identityCard" maxlength="20" autocomplete="off"
                         placeholder="12 số CCCD hoặc số hộ chiếu"
                         aria-describedby="profile-idcard-hint profile-idcard-error"
                         class="input w-full customer-secondary-surface border customer-border focus:border-amber-400 customer-text text-xs sm:text-sm rounded-xl pl-4 pr-12 py-3 outline-none transition-colors">
                  <button type="button" id="profile-idcard-visibility" class="btn btn-ghost btn-square customer-muted absolute right-1.5 top-1/2 -translate-y-1/2" aria-label="Hiển thị CCCD / Hộ chiếu" aria-controls="profile-idcard" aria-pressed="false" title="Hiển thị CCCD / Hộ chiếu">
                    <i data-lucide="eye" class="w-4 h-4"></i>
                  </button>
                </div>
                <p id="profile-idcard-hint" class="profile-field-hint customer-muted">Không bắt buộc: CCCD đúng 12 số hoặc hộ chiếu 5–20 ký tự, bắt đầu bằng chữ và theo sau là chữ/số.</p>
                <p id="profile-idcard-error" class="validator-hint profile-field-error text-error" hidden aria-live="polite"></p>
              </div>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <!-- Date of Birth -->
              <div class="profile-field">
                <label for="profile-dob" class="block text-xs font-semibold customer-muted mb-1.5 uppercase tracking-wider">
                  Ngày sinh
                </label>
                <div class="relative">
                  <input type="date" id="profile-dob" name="dateOfBirth" min="1900-01-01"
                         aria-describedby="profile-dob-hint profile-dob-error"
                         class="input w-full customer-secondary-surface border customer-border focus:border-amber-400 customer-text text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="calendar" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
                <p id="profile-dob-hint" class="profile-field-hint customer-muted">Không bắt buộc, từ 01/01/1900 đến hôm nay.</p>
                <p id="profile-dob-error" class="validator-hint profile-field-error text-error" hidden aria-live="polite"></p>
              </div>

              <!-- Permanent Address -->
              <div class="profile-field">
                <label for="profile-address" class="block text-xs font-semibold customer-muted mb-1.5 uppercase tracking-wider">
                  Địa chỉ thường trú
                </label>
                <div class="relative">
                  <input type="text" id="profile-address" name="address" maxlength="255" autocomplete="street-address"
                         placeholder="Số nhà, Tên đường, Tỉnh / Thành phố"
                         aria-describedby="profile-address-hint profile-address-error"
                         class="input w-full customer-secondary-surface border customer-border focus:border-amber-400 customer-text text-xs sm:text-sm rounded-xl px-4 py-3 outline-none transition-colors">
                  <i data-lucide="map-pin" class="w-4 h-4 text-slate-500 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none"></i>
                </div>
                <p id="profile-address-hint" class="profile-field-hint customer-muted">Không bắt buộc, tối đa 255 ký tự.</p>
                <p id="profile-address-error" class="validator-hint profile-field-error text-error" hidden aria-live="polite"></p>
              </div>
            </div>

            ${identityScannerMarkup}

            <div class="pt-4 border-t customer-border flex items-center justify-between">
              <span class="text-xs customer-muted flex items-center gap-1.5">
                <i data-lucide="lock" class="w-3.5 h-3.5 customer-accent"></i>
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
  const idcardVisibilityButton = document.getElementById('profile-idcard-visibility');
  const idcardHint = document.getElementById('profile-idcard-hint');
  const dobInput = document.getElementById('profile-dob');
  const addressInput = document.getElementById('profile-address');
  const statusBox = document.getElementById('profile-status-box');
  const form = document.getElementById('profile-form');
  const btn = document.getElementById('btn-save-profile');
  let originalPhoneValue = (phoneInput?.value || '').trim();
  let originalIdentityValue = null;
  let identitySaved = false;
  let identityLoaded = false;
  let identityDirty = false;

  const todayInVietnam = (() => {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit'
    }).formatToParts(new Date());
    const datePart = Object.fromEntries(parts.map(part => [part.type, part.value]));
    return `${datePart.year}-${datePart.month}-${datePart.day}`;
  })();
  if (dobInput) dobInput.max = todayInVietnam;

  const profileFieldRules = [
    {
      input: fullnameInput,
      error: document.getElementById('profile-fullname-error'),
      validate: value => !value ? 'Hãy nhập họ và tên.' : value.length > 100 ? 'Họ và tên tối đa 100 ký tự.' : ''
    },
    {
      input: phoneInput,
      error: document.getElementById('profile-phone-error'),
      validate: value => {
        if (!value) return 'Hãy nhập số điện thoại.';
        if (value.length > 20) return 'Số điện thoại tối đa 20 ký tự.';
        if (value === originalPhoneValue) return '';
        return /^(?:0|\+84)[35789]\d{8}$/.test(value)
          ? ''
          : 'Nhập 10 số bắt đầu 03/05/07/08/09 hoặc dạng +84912345678, không có dấu cách.';
      }
    },
    {
      input: emailInput,
      error: document.getElementById('profile-email-error'),
      validate: value => {
        if (!value) return 'Hãy nhập địa chỉ email.';
        if (value.length > 100) return 'Email tối đa 100 ký tự.';
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) ? '' : 'Nhập email theo dạng ten@domain.vn.';
      }
    },
    {
      input: idcardInput,
      error: document.getElementById('profile-idcard-error'),
      validate: value => {
        if (!value) return '';
        if (identitySaved && value === originalIdentityValue) return '';
        if (value.length > 20) return 'Số CCCD / hộ chiếu tối đa 20 ký tự.';
        return /^(?:\d{12}|[A-Za-z][A-Za-z0-9]{4,19})$/.test(value)
          ? ''
          : 'CCCD phải có đúng 12 số; hộ chiếu gồm 5–20 ký tự và bắt đầu bằng chữ.';
      }
    },
    {
      input: dobInput,
      error: document.getElementById('profile-dob-error'),
      validate: value => value && (value < '1900-01-01' || value > todayInVietnam)
        ? 'Chọn ngày sinh từ 01/01/1900 đến hôm nay.'
        : ''
    },
    {
      input: addressInput,
      error: document.getElementById('profile-address-error'),
      validate: value => value.length > 255 ? 'Địa chỉ tối đa 255 ký tự.' : ''
    }
  ].filter(field => field.input && field.error);

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
  const setFieldError = (field, message = '') => {
    field.error.textContent = message;
    field.error.hidden = !message;
    field.input.classList.toggle('input-error', Boolean(message));
    field.input.setAttribute('aria-invalid', message ? 'true' : 'false');
  };
  const validateField = field => {
    const message = field.validate(field.input.value.trim());
    setFieldError(field, message);
    return Boolean(message);
  };
  const setIdentityVisibility = visible => {
    if (idcardInput) idcardInput.type = visible ? 'text' : 'password';
    if (idcardVisibilityButton) {
      const label = visible ? 'Ẩn CCCD / Hộ chiếu' : 'Hiển thị CCCD / Hộ chiếu';
      idcardVisibilityButton.setAttribute('aria-label', label);
      idcardVisibilityButton.setAttribute('title', label);
      idcardVisibilityButton.setAttribute('aria-pressed', String(visible));
      idcardVisibilityButton.innerHTML = `<i data-lucide="${visible ? 'eye-off' : 'eye'}" class="w-4 h-4"></i>`;
      refreshIcons();
    }
  };
  idcardVisibilityButton?.addEventListener('click', async () => {
    if (!idcardInput) return;
    if (idcardInput.type === 'text') {
      setIdentityVisibility(false);
      return;
    }
    if (identitySaved && !identityLoaded && !identityDirty) {
      idcardVisibilityButton.disabled = true;
      try {
        const result = await api.get('/customer-portal/profile/identity');
        if (typeof result?.identityCard !== 'string' || !result.identityCard) {
          throw new Error('Không tải được giấy tờ đã lưu. Vui lòng thử lại.');
        }
        idcardInput.value = result.identityCard;
        originalIdentityValue = result.identityCard;
        identityLoaded = true;
      } catch (error) {
        report(error?.message || 'Không thể hiển thị giấy tờ đã lưu. Vui lòng thử lại.');
        return;
      } finally {
        idcardVisibilityButton.disabled = false;
      }
    }
    setIdentityVisibility(true);
  });
  let submitAttempted = false;
  profileFieldRules.forEach(field => {
    field.touched = false;
    field.input.setAttribute('aria-invalid', 'false');
  });
  const identityState = saved => {
    identitySaved = Boolean(saved);
    const node = document.getElementById('profile-identity-status');
    if (node) node.textContent = identitySaved ? 'Đã lưu & mã hóa · số được ẩn' : 'Chưa lưu giấy tờ';
    if (idcardInput) idcardInput.placeholder = identitySaved ? '••••••••••••' : '12 số CCCD hoặc số hộ chiếu';
    if (idcardHint) idcardHint.textContent = identitySaved
      ? 'Giấy tờ đã lưu được ẩn như mật khẩu. Bấm biểu tượng con mắt để xem hoặc chỉnh sửa; nhập số mới để thay thế.'
      : 'Không bắt buộc: CCCD đúng 12 số hoặc hộ chiếu 5–20 ký tự, bắt đầu bằng chữ và theo sau là chữ/số.';
    setIdentityVisibility(false);
  };
  const fieldForInput = target => profileFieldRules.find(field => field.input === target);
  form?.addEventListener('input', event => {
    edited = true;
    if (event.target === idcardInput) identityDirty = true;
    const field = fieldForInput(event.target);
    if (field && (field.touched || submitAttempted)) validateField(field);
  });
  form?.addEventListener('change', event => {
    const field = fieldForInput(event.target);
    if (field && (field.touched || submitAttempted)) validateField(field);
  });
  form?.addEventListener('focusout', event => {
    const field = fieldForInput(event.target);
    if (!field) return;
    field.touched = true;
    validateField(field);
  });
  mountIdentityScanner(document.getElementById('profile-identity-scan'), {
    apply: data => {
      edited = true;
      if (fullnameInput) fullnameInput.value = data.fullName;
      if (idcardInput) idcardInput.value = data.identityCard;
      originalIdentityValue = null;
      identityDirty = true;
      identityLoaded = true;
      if (dobInput) dobInput.value = data.dateOfBirth;
      if (addressInput) addressInput.value = data.address;
      profileFieldRules.forEach(field => {
        if (field.touched || submitAttempted) validateField(field);
      });
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
    originalPhoneValue = (profile.phone || '').trim();
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
    submitAttempted = true;
    let firstInvalidField = null;
    profileFieldRules.forEach(field => {
      field.touched = true;
      if (validateField(field) && !firstInvalidField) firstInvalidField = field;
    });
    if (firstInvalidField) {
      statusBox?.classList.add('hidden');
      firstInvalidField.input.focus();
      return;
    }
    if (btn) { btn.disabled = true; btn.setAttribute('aria-busy','true'); }
    const updatedData = {
      fullName: fullnameInput?.value.trim() || '', phone: phoneInput?.value.trim() || '',
      email: emailInput?.value.trim() || '',
      identityCard: idcardInput?.value.trim() && idcardInput.value.trim() !== originalIdentityValue
        ? idcardInput.value.trim() : null,
      dateOfBirth: dobInput?.value || null, address: addressInput?.value.trim() || null
    };
    try {
      const saved = await api.put('/customer-portal/profile', updatedData);
      if (!saved?.customerId) throw new Error('Máy chủ chưa xác nhận việc lưu hồ sơ.');
      originalPhoneValue = (saved.phone || '').trim();
      if (phoneInput) phoneInput.value = saved.phone || '';
      const updatedSession = {...(getAuthSession() || session),fullName:saved.fullName,phone:saved.phone,email:saved.email,customerId:saved.customerId};
      for (const field of ['identityCard','idCard','dateOfBirth','dob','address']) delete updatedSession[field];
      setAuthSession(updatedSession);
      syncName(saved.fullName);
      if (idcardInput) idcardInput.value = '';
      originalIdentityValue = null;
      identityLoaded = false;
      identityDirty = false;
      profileFieldRules.forEach(field => {
        field.touched = false;
        setFieldError(field);
      });
      submitAttempted = false;
      identityState(saved.identitySaved);
      report('Đã lưu hồ sơ thành công. Thông tin giấy tờ sẽ được đối chiếu khi nhận phòng.',true);
      window.dispatchEvent?.(new (window.Event || Event)('hotel:profile-updated'));
    } catch (error) {
      report(error?.status === 400
        ? 'Dữ liệu chưa được máy chủ chấp nhận. Hãy kiểm tra gợi ý và giới hạn dưới từng trường.'
        : error.message || 'Chưa thể lưu hồ sơ. Vui lòng thử lại.');
    } finally {
      if (btn) { btn.disabled = false; btn.removeAttribute('aria-busy'); }
      refreshIcons();
    }
  });
}
