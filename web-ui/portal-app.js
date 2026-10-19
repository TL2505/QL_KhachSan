import { api, getAuthSession, setAuthSession } from './public/api.js';
import { loadAdminView } from './public/js/view-admin.js';
import { loadStaffView } from './public/js/view-staff.js';
import { loadCustomerView } from './public/js/view-customer.js';
import { loadProfileView } from './public/js/view-shared.js';
import './public/js/shared/theme.js';

const closeGlobalModal = (modalId = 'global-modal') => {
  const modal = document.getElementById(modalId);
  if (!modal) return;
  modal.classList.remove('active');
  modal.setAttribute('aria-hidden', 'true');
  const content = modal.querySelector('.modal-content');
  if (content) content.replaceChildren();
};

window.closeModal = closeGlobalModal;

window.showCustomConfirm = (message, options = {}) => new Promise(resolve => {
  const modal = document.getElementById('global-modal');
  const content = document.getElementById('global-modal-content');
  if (!modal || !content) { resolve(window.confirm(message)); return; }
  content.replaceChildren();
  const heading = document.createElement('div');
  heading.className = 'modal-header';
  const title = document.createElement('h2');
  title.textContent = options.title || 'Xác nhận thao tác';
  const close = document.createElement('button');
  close.type = 'button'; close.className = 'modal-close'; close.setAttribute('aria-label', 'Đóng'); close.textContent = '×';
  heading.append(title, close);
  const text = document.createElement('p');
  text.textContent = message;
  text.style.cssText = 'margin:0;color:var(--portal-muted);line-height:1.65';
  const actions = document.createElement('div');
  actions.className = 'flex-row'; actions.style.justifyContent = 'flex-end'; actions.style.marginTop = '22px';
  const cancel = document.createElement('button');
  cancel.type = 'button'; cancel.className = 'btn btn-secondary'; cancel.textContent = 'Hủy';
  const accept = document.createElement('button');
  accept.type = 'button'; accept.className = `btn ${options.kind === 'primary' ? 'btn-primary' : 'btn-danger'}`; accept.textContent = options.confirmLabel || 'Xác nhận';
  actions.append(cancel, accept); content.append(heading, text, actions);
  const finish = value => { closeGlobalModal(); resolve(value); };
  close.addEventListener('click', () => finish(false));
  cancel.addEventListener('click', () => finish(false));
  accept.addEventListener('click', () => finish(true));
  modal.classList.add('active'); modal.setAttribute('aria-hidden', 'false'); accept.focus();
});

// Shared feedback dialog: modules can show API errors instead of failing silently.
window.showCustomAlert = (message, options = {}) => new Promise(resolve => {
  const modal = document.getElementById('global-modal');
  const content = document.getElementById('global-modal-content');
  if (!modal || !content) { window.alert(message); resolve(); return; }
  content.replaceChildren();
  const heading = document.createElement('div');
  heading.className = 'modal-header';
  const title = document.createElement('h2');
  title.textContent = options.title || (options.kind === 'success' ? 'Thành công' : 'Không thể thực hiện');
  const close = document.createElement('button');
  close.type = 'button'; close.className = 'modal-close'; close.setAttribute('aria-label', 'Đóng'); close.textContent = '×';
  heading.append(title, close);
  const text = document.createElement('p');
  text.textContent = message || 'Đã xảy ra lỗi không xác định.';
  text.style.cssText = 'margin:0;color:var(--portal-muted);line-height:1.65';
  const actions = document.createElement('div');
  actions.className = 'flex-row'; actions.style.justifyContent = 'flex-end'; actions.style.marginTop = '22px';
  const acknowledge = document.createElement('button');
  acknowledge.type = 'button'; acknowledge.className = `btn ${options.kind === 'success' ? 'btn-primary' : 'btn-secondary'}`; acknowledge.textContent = 'Đã hiểu';
  actions.append(acknowledge); content.append(heading, text, actions);
  const finish = () => { closeGlobalModal(); resolve(); };
  close.addEventListener('click', finish);
  acknowledge.addEventListener('click', finish);
  modal.classList.add('active'); modal.setAttribute('aria-hidden', 'false'); acknowledge.focus();
});

const menus = {
  ADMIN: [
    ['dashboard', 'Tổng quan', 'chart-no-axes-combined'], ['rooms', 'Quản lý phòng', 'bed-double'],
    ['bookings', 'Đặt phòng', 'calendar-check'], ['users', 'Tài khoản nhân viên', 'users'], ['customers', 'Khách hàng', 'contact-round'],
    ['promotions', 'Khuyến mãi', 'ticket-percent'], ['reviews', 'Đánh giá', 'star'], ['invoices', 'Hóa đơn', 'receipt-text'], ['profile', 'Hồ sơ & bảo mật', 'settings']
  ],
  STAFF: [
    ['room-map', 'Sơ đồ phòng', 'panels-top-left'], ['bookings', 'Đặt phòng', 'calendar-check'],
    ['customers', 'Khách hàng', 'contact-round'], ['checkout', 'Nhận & trả phòng', 'key-round'],
    ['invoices', 'Hóa đơn', 'receipt-text'], ['loyalty', 'Thành viên', 'award'], ['chat', 'Tin nhắn', 'messages-square'], ['profile', 'Hồ sơ & bảo mật', 'settings']
  ],
  CUSTOMER: [
    ['dashboard', 'Tổng quan', 'layout-dashboard'], ['discovery', 'Khám phá phòng', 'bed-double'], ['bookings', 'Đặt phòng của tôi', 'calendar-check'], ['invoices', 'Hóa đơn', 'receipt-text'],
    ['loyalty', 'Điểm thưởng', 'award'], ['promotions', 'Ưu đãi', 'ticket-percent'], ['chat', 'Hỗ trợ concierge', 'messages-square'], ['profile', 'Hồ sơ & bảo mật', 'settings']
  ]
};

const titles = Object.fromEntries(Object.values(menus).flat().map(([key, title]) => [key, title]));
const roleNames = { ADMIN: 'Quản trị viên', STAFF: 'Nhân viên khách sạn', CUSTOMER: 'Khách hàng' };
const roleOf = value => {
  const role = String(value || '').toUpperCase().replace(/[ -]/g, '_');
  if (role === 'ADMIN') return 'ADMIN';
  if (role === 'CUSTOMER' || role === 'GUEST') return 'CUSTOMER';
  if (['STAFF', 'FRONT_DESK', 'HOUSEKEEPING', 'MAINTENANCE', 'F&B', 'FB'].includes(role)) return 'STAFF';
  return null;
};
const byId = id => document.getElementById(id);
const clearAuthSession = () => setAuthSession(null);
let googleChallengeId = null;

// Only return to known hotel pages on this origin, never an arbitrary redirect.
function bookingReturnUrl() {
  const value = new URLSearchParams(location.search).get('returnTo');
  if (!value) return null;
  try {
    const url = new URL(value, location.origin);
    if (url.origin !== location.origin || !['/', '/index.html', '/booking/', '/booking/index.html'].includes(url.pathname)) return null;
    return url.pathname + url.search + url.hash;
  } catch { return null; }
}

function status(message, kind = 'error') {
  const node = byId('auth-status');
  node.textContent = message;
  node.className = `auth-status ${kind === 'success' ? 'success' : ''}`;
}

function showAuth(tab = 'login') {
  document.body.classList.remove('customer-portal');
  document.body.classList.remove('admin-portal');
  document.body.classList.remove('staff-portal');
  byId('auth-view').hidden = false;
  byId('app-view').hidden = true;
  const login = tab === 'login';
  byId('login-form').hidden = !login;
  byId('register-form').hidden = login;
  byId('google-otp-form').hidden = true;
  byId('show-login').classList.toggle('active', login);
  byId('show-login').classList.toggle('tab-active', login);
  byId('show-register').classList.toggle('active', !login);
  byId('show-register').classList.toggle('tab-active', !login);
  byId('show-login').setAttribute('aria-selected', String(login));
  byId('show-register').setAttribute('aria-selected', String(!login));
}

function renderNav(session, activeTab) {
  const role = roleOf(session.role);
  const items = menus[role];
  byId('sidebar-role').textContent = roleNames[role].toUpperCase();
  byId('user-display-name').textContent = session.fullName || session.username || 'Aurelia';
  byId('account-role').textContent = roleNames[role];
  byId('account-avatar').textContent = String(session.fullName || session.username || 'A').trim().charAt(0).toUpperCase();
  byId('portal-nav').innerHTML = items.map(([key, label, icon]) => `<button type="button" data-view="${key}" class="btn btn-ghost ${key === activeTab ? 'active' : ''}" aria-current="${key === activeTab ? 'page' : 'false'}"><i data-lucide="${icon}"></i><span>${label}</span></button>`).join('');
  byId('portal-nav').querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => openView(session, role, button.dataset.view)));
  if (window.lucide?.createIcons) window.lucide.createIcons();
}

function openView(session, role, tab) {
  if (!menus[role]?.some(([key]) => key === tab)) tab = menus[role][0][0];
  renderNav(session, tab);
  byId('portal-heading').textContent = titles[tab] || 'Tổng quan';
  byId('portal-message').hidden = true;
  try {
    if (tab === 'profile') loadProfileView();
    else if (role === 'ADMIN') loadAdminView(tab);
    else if (role === 'CUSTOMER') loadCustomerView(tab);
    else loadStaffView(tab);
    const view = byId('view-container');
    view.querySelectorAll('table').forEach(table => table.classList.add('table', 'table-zebra'));
    view.querySelectorAll('input:not([type="checkbox"]):not([type="radio"]), select, textarea').forEach(field => {
      field.classList.add(field.tagName === 'SELECT' ? 'select' : field.tagName === 'TEXTAREA' ? 'textarea' : 'input');
    });
    view.querySelectorAll('.btn-primary, .btn-secondary').forEach(button => button.classList.add('btn'));
  } catch (error) {
    byId('portal-message').textContent = error.message || 'Không thể mở mục này.';
    byId('portal-message').hidden = false;
  }
  const url = new URL(window.location.href);
  url.searchParams.set('view', tab);
  history.replaceState(null, '', url);
}

function enterPortal(session) {
  const role = roleOf(session?.role);
  if (!role) {
    clearAuthSession();
    showAuth();
    status('Tài khoản này chưa được cấp quyền truy cập cổng web.');
    return;
  }
  const isCustomer = role === 'CUSTOMER';
  document.body.classList.toggle('customer-portal', isCustomer);
  document.body.classList.toggle('admin-portal', role === 'ADMIN');
  document.body.classList.toggle('staff-portal', role === 'STAFF');
  byId('app-view').setAttribute('aria-label', role === 'CUSTOMER' ? 'Cổng khách hàng Aurelia' : 'Cổng quản lý Aurelia');
  byId('auth-view').hidden = true;
  byId('app-view').hidden = false;
  const requested = new URLSearchParams(location.search).get('view');
  const defaultTab = role === 'STAFF' ? 'room-map' : 'dashboard';
  openView(session, role, requested || defaultTab);
}

function completeLogin(result) {
  if (!result || !result.token || !roleOf(result.role)) throw new Error('Máy chủ không trả về quyền truy cập hợp lệ cho tài khoản này.');
  const session = {
    ...result,
    userId: result.userId ?? result.id,
    role: roleOf(result.role) === 'STAFF' ? String(result.role).toUpperCase() : roleOf(result.role)
  };
  setAuthSession(session);
  const returnUrl = bookingReturnUrl();
  if (returnUrl) { window.location.replace(returnUrl); return; }
  if (roleOf(result.role) === 'CUSTOMER' && new URLSearchParams(location.search).get('resumeBooking') === '1') {
    window.location.replace('portal.html?view=discovery&resumeBooking=1'); return;
  }
  const destination = roleOf(result.role) === 'STAFF' ? 'room-map' : 'dashboard';
  window.location.replace(`portal.html?view=${encodeURIComponent(destination)}`);
}

async function startGoogleOtp(googleResponse) {
  if (!googleResponse?.credential) { status('Google không trả về thông tin xác thực. Vui lòng thử lại.'); return; }
  status('Đang xác thực tài khoản Google…', 'success');
  try {
    const data = await api.post('/auth/google/request-otp', { credential: googleResponse.credential });
    googleChallengeId = data.challenge_id;
    byId('login-form').hidden = true;
    byId('register-form').hidden = true;
    byId('google-otp-form').hidden = false;
    byId('google-otp-email').textContent = data.email || 'email đã xác minh';
    byId('google-otp-code').value = '';
    byId('google-otp-code').focus();
    status('Mã xác thực đã được gửi. Mã có hiệu lực trong 5 phút.', 'success');
  } catch (error) { status(error.message || 'Không thể bắt đầu xác thực Google. Vui lòng thử lại.'); }
}

async function initialiseGoogleLogin() {
  try {
    const config = await api.get('/auth/google/config');
    if (!config?.enabled || !config.client_id) return;
    for (let tries = 0; tries < 100 && !window.google?.accounts?.id; tries += 1) await new Promise(resolve => setTimeout(resolve, 50));
    if (!window.google?.accounts?.id) return;
    window.google.accounts.id.initialize({ client_id: config.client_id, callback: startGoogleOtp, auto_select: false, cancel_on_tap_outside: true });
    window.google.accounts.id.renderButton(byId('google-signin-button'), { theme: 'outline', size: 'large', text: 'signin_with', locale: 'vi', width: 300 });
    byId('google-login-area').hidden = false;
  } catch { /* Configuration is intentionally silent until the hotel enables Google login. */ }
}

byId('show-login').addEventListener('click', () => { status(''); showAuth('login'); });
byId('show-register').addEventListener('click', () => { status(''); showAuth('register'); });

byId('login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.currentTarget.querySelector('button[type="submit"]');
  const timeoutController = new AbortController();
  const timeoutId = setTimeout(() => {
    timeoutController.abort(new DOMException('Login request timed out.', 'TimeoutError'));
  }, 15000);
  button.disabled = true;
  status('Đang xác thực tài khoản…', 'success');
  try {
    const result = await api.post('/auth/login', {
      username: byId('login-username').value.trim(),
      password: byId('login-password').value
    }, { signal: timeoutController.signal });
    completeLogin(result);
  } catch (error) {
    const timedOut = error.name === 'TimeoutError' || error.name === 'AbortError';
    status(timedOut
      ? 'Máy chủ đăng nhập phản hồi quá lâu. Vui lòng kiểm tra MySQL và máy chủ rồi thử lại.'
      : error.message || 'Không thể đăng nhập. Vui lòng kiểm tra kết nối và thử lại.');
  } finally {
    clearTimeout(timeoutId);
    button.disabled = false;
  }
});

byId('google-otp-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.currentTarget.querySelector('button[type="submit"]');
  button.disabled = true;
  try {
    const result = await api.post('/auth/google/verify-otp', { challenge_id: googleChallengeId, otp: byId('google-otp-code').value.trim() });
    completeLogin(result);
  } catch (error) { status(error.message || 'Không thể xác nhận mã. Vui lòng thử lại.'); }
  finally { button.disabled = false; }
});

byId('google-otp-resend').addEventListener('click', async event => {
  event.currentTarget.disabled = true;
  try {
    const data = await api.post('/auth/google/resend-otp', { challenge_id: googleChallengeId });
    byId('google-otp-email').textContent = data.email || byId('google-otp-email').textContent;
    status('Mã mới đã được gửi. Mã cũ không còn hiệu lực.', 'success');
  } catch (error) { status(error.message || 'Không thể gửi lại mã.'); }
  finally { event.currentTarget.disabled = false; }
});

byId('google-otp-cancel').addEventListener('click', () => { googleChallengeId = null; status(''); showAuth('login'); });

byId('register-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.currentTarget.querySelector('button[type="submit"]');
  button.disabled = true;
  try {
    await api.post('/auth/register', {
      username: byId('register-username').value.trim(),
      password: byId('register-password').value,
      fullName: byId('register-name').value.trim(),
      identityCard: byId('register-identity').value.trim(),
      phone: byId('register-phone').value.trim(),
      email: byId('register-email').value.trim()
    });
    status('Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.', 'success');
    showAuth('login');
    byId('login-username').value = byId('register-username').value.trim();
    byId('login-password').focus();
  } catch (error) {
    status(error.message || 'Không thể tạo tài khoản. Vui lòng kiểm tra thông tin.');
  } finally {
    button.disabled = false;
  }
});

byId('logout-button').addEventListener('click', () => {
  clearAuthSession();
  history.replaceState(null, '', 'portal.html');
  byId('view-container').replaceChildren();
  showAuth();
  status('Bạn đã đăng xuất thành công.', 'success');
  byId('login-username').focus();
});

// apiFetch clears an invalid JWT and dispatches this event. Return to the
// existing login screen so protected views cannot remain in a loading state.
window.addEventListener('hotel:session-expired', () => {
  clearAuthSession();
  history.replaceState(null, '', 'portal.html');
  byId('view-container').replaceChildren();
  showAuth();
  status('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
  byId('login-username').focus();
});

if (window.lucide?.createIcons) window.lucide.createIcons();
const currentSession = getAuthSession();
if (currentSession?.token && bookingReturnUrl()) window.location.replace(bookingReturnUrl());
else if (currentSession?.token) enterPortal(currentSession);
else showAuth();
initialiseGoogleLogin();
