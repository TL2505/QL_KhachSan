import { api, clearAuthSession, getAuthSession, setAuthSession } from './public/api.js';
import { loadAdminView } from './public/js/view-admin.js';
import { loadStaffView } from './public/js/view-staff.js';
import { loadCustomerView } from './public/js/view-customer.js';
import { loadProfileView } from './public/js/view-shared.js';
import './public/js/shared/theme.js';

const menus = {
  ADMIN: [
    ['dashboard', 'Tổng quan', 'chart-no-axes-combined'], ['rooms', 'Quản lý phòng', 'bed-double'],
    ['users', 'Tài khoản nhân viên', 'users'], ['customers', 'Khách hàng', 'contact-round'],
    ['promotions', 'Khuyến mãi', 'ticket-percent'], ['reviews', 'Đánh giá', 'star'], ['invoices', 'Hóa đơn', 'receipt-text'], ['profile', 'Hồ sơ & bảo mật', 'settings']
  ],
  STAFF: [
    ['room-map', 'Sơ đồ phòng', 'panels-top-left'], ['bookings', 'Đặt phòng', 'calendar-check'],
    ['customers', 'Khách hàng', 'contact-round'], ['checkout', 'Nhận & trả phòng', 'key-round'],
    ['invoices', 'Hóa đơn', 'receipt-text'], ['loyalty', 'Thành viên', 'award'], ['chat', 'Tin nhắn', 'messages-square'], ['profile', 'Hồ sơ & bảo mật', 'settings']
  ],
  CUSTOMER: [
    ['dashboard', 'Tổng quan', 'layout-dashboard'], ['discovery', 'Khám phá phòng', 'bed-double'],
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

function status(message, kind = 'error') {
  const node = byId('auth-status');
  node.textContent = message;
  node.className = `auth-status ${kind === 'success' ? 'success' : ''}`;
}

function showAuth(tab = 'login') {
  document.body.classList.remove('customer-portal');
  byId('auth-view').hidden = false;
  byId('app-view').hidden = true;
  const login = tab === 'login';
  byId('login-form').hidden = !login;
  byId('register-form').hidden = login;
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
  byId('app-view').setAttribute('aria-label', role === 'CUSTOMER' ? 'Cổng khách hàng Aurelia' : 'Cổng quản lý Aurelia');
  byId('auth-view').hidden = true;
  byId('app-view').hidden = false;
  const requested = new URLSearchParams(location.search).get('view');
  const defaultTab = role === 'STAFF' ? 'room-map' : 'dashboard';
  openView(session, role, requested || defaultTab);

  if (session?.isGuestPreview) {
    const msg = byId('portal-message');
    if (msg) {
      msg.className = 'portal-message alert mb-4 bg-amber-500/10 border-amber-500/30 text-amber-200 text-xs flex items-center justify-between';
      msg.innerHTML = `
        <div class="flex items-center gap-2">
          <i data-lucide="info" class="w-4 h-4 text-amber-400"></i>
          <span>Bạn đang xem Cổng thành viên dưới chế độ Khách. Đăng nhập để đồng bộ lịch sử đặt phòng thực tế của bạn.</span>
        </div>
        <button type="button" id="btn-guest-login-now" class="btn btn-xs bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold border-0 ml-3">Đăng nhập</button>
      `;
      msg.hidden = false;
      byId('btn-guest-login-now')?.addEventListener('click', () => {
        clearAuthSession();
        history.replaceState(null, '', 'portal.html');
        byId('view-container').replaceChildren();
        showAuth('login');
      });
      if (window.lucide?.createIcons) window.lucide.createIcons();
    }
  }
}

byId('show-login').addEventListener('click', () => { status(''); showAuth('login'); });
byId('show-register').addEventListener('click', () => { status(''); showAuth('register'); });

byId('login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = event.currentTarget.querySelector('button[type="submit"]');
  button.disabled = true;
  status('Đang xác thực tài khoản…', 'success');
  try {
    const result = await api.post('/auth/login', {
      username: byId('login-username').value.trim(),
      password: byId('login-password').value
    });
    if (!result || !result.token || !roleOf(result.role)) throw new Error('Máy chủ không trả về quyền truy cập hợp lệ cho tài khoản này.');
    const session = {
      ...result,
      userId: result.userId ?? result.id,
      role: roleOf(result.role) === 'STAFF' ? String(result.role).toUpperCase() : roleOf(result.role)
    };
    setAuthSession(session);
    const destination = roleOf(result.role) === 'STAFF' ? 'room-map' : 'dashboard';
    window.location.replace(`portal.html?view=${encodeURIComponent(destination)}`);
  } catch (error) {
    status(error.message || 'Không thể đăng nhập. Vui lòng kiểm tra kết nối và thử lại.');
  } finally {
    button.disabled = false;
  }
});

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

if (window.lucide?.createIcons) window.lucide.createIcons();
const requestedView = new URLSearchParams(location.search).get('view');
const currentSession = getAuthSession();
if (currentSession?.token) {
  enterPortal(currentSession);
} else if (requestedView === 'dashboard') {
  const guestSession = {
    token: 'guest-preview',
    role: 'CUSTOMER',
    userId: 0,
    username: 'guest',
    fullName: 'Quý Khách (Guest)',
    isGuestPreview: true
  };
  enterPortal(guestSession);
} else {
  showAuth();
}
