import { api, clearAuthSession, getAuthSession, setAuthSession } from './api.js';
import { loadAdminView } from './js/view-admin.js';
import { loadStaffView } from './js/view-staff.js';
import { loadCustomerView } from './js/view-customer.js';
import { loadProfileView } from './js/view-shared.js';
import './js/shared/theme.js';

const menus = {
  ADMIN: [
    ['dashboard', 'Tổng quan', 'chart-no-axes-combined'],
    ['rooms', 'Quản lý phòng', 'bed-double'],
    ['users', 'Tài khoản nhân viên', 'users'],
    ['customers', 'Khách hàng', 'contact-round'],
    ['promotions', 'Khuyến mãi', 'ticket-percent'],
    ['reviews', 'Đánh giá', 'star'],
    ['invoices', 'Hóa đơn', 'receipt-text'],
    ['profile', 'Hồ sơ & bảo mật', 'settings']
  ],
  STAFF: [
    ['room-map', 'Sơ đồ phòng', 'panels-top-left'],
    ['bookings', 'Đặt phòng', 'calendar-check'],
    ['customers', 'Khách hàng', 'contact-round'],
    ['checkout', 'Nhận & trả phòng', 'key-round'],
    ['invoices', 'Hóa đơn', 'receipt-text'],
    ['loyalty', 'Thành viên', 'award'],
    ['chat', 'Tin nhắn', 'messages-square'],
    ['profile', 'Hồ sơ & bảo mật', 'settings']
  ],
  CUSTOMER: [
    ['dashboard', 'Tổng quan & Hạng thẻ', 'layout-dashboard'],
    ['bookings', 'Lịch sử đặt phòng', 'calendar-check'],
    ['profile', 'Thông tin cá nhân', 'user-cog'],
    ['security', 'Bảo mật & Mật khẩu', 'shield-check']
  ]
};

const titles = Object.fromEntries(Object.values(menus).flat().map(([key, title]) => [key, title]));
titles['dashboard'] = 'Tổng quan & Hạng thẻ';
titles['bookings'] = 'Lịch sử đặt phòng';
titles['profile'] = 'Thông tin cá nhân';
titles['security'] = 'Bảo mật & Mật khẩu';

const roleNames = {
  ADMIN: 'Quản trị viên',
  STAFF: 'Nhân viên khách sạn',
  CUSTOMER: 'Khách hàng'
};

const roleOf = value => {
  const role = String(value || '').toUpperCase().replace(/[ -]/g, '_');
  if (role === 'ADMIN') return 'ADMIN';
  if (role === 'CUSTOMER' || role === 'GUEST') return 'CUSTOMER';
  if (['STAFF', 'FRONT_DESK', 'HOUSEKEEPING', 'MAINTENANCE', 'F&B', 'FB'].includes(role)) return 'STAFF';
  return null;
};

const byId = id => document.getElementById(id);

function status(message, kind = 'error') {
  try {
    const node = byId('auth-status');
    if (node) {
      node.textContent = message;
      node.className = `auth-status ${kind === 'success' ? 'success' : ''}`;
    }
  } catch {}
}

function showAuth(tab = 'login') {
  try {
    document.body.classList.remove('customer-portal');
    const authView = byId('auth-view');
    const appView = byId('app-view');
    if (authView) authView.hidden = false;
    if (appView) appView.hidden = true;

    const login = tab === 'login';
    const loginForm = byId('login-form');
    const registerForm = byId('register-form');
    if (loginForm) loginForm.hidden = !login;
    if (registerForm) registerForm.hidden = login;

    const showLoginBtn = byId('show-login');
    const showRegisterBtn = byId('show-register');
    if (showLoginBtn) {
      showLoginBtn.classList.toggle('active', login);
      showLoginBtn.classList.toggle('tab-active', login);
      showLoginBtn.setAttribute('aria-selected', String(login));
    }
    if (showRegisterBtn) {
      showRegisterBtn.classList.toggle('active', !login);
      showRegisterBtn.classList.toggle('tab-active', !login);
      showRegisterBtn.setAttribute('aria-selected', String(!login));
    }
  } catch (err) {
    console.error('Lỗi khi mở giao diện đăng nhập:', err);
  }
}

function renderNav(session, activeTab) {
  try {
    const role = roleOf(session.role) || 'CUSTOMER';
    const items = menus[role] || menus.CUSTOMER;

    const sidebarRole = byId('sidebar-role');
    if (sidebarRole) sidebarRole.textContent = (roleNames[role] || 'Thành viên').toUpperCase();

    const userDisplayName = byId('user-display-name');
    if (userDisplayName) userDisplayName.textContent = session.fullName || session.username || 'Aurelia';

    const accountRole = byId('account-role');
    if (accountRole) accountRole.textContent = roleNames[role] || 'Thành viên';

    const accountAvatar = byId('account-avatar');
    if (accountAvatar) {
      accountAvatar.textContent = String(session.fullName || session.username || 'A').trim().charAt(0).toUpperCase();
    }

    const portalNav = byId('portal-nav');
    if (portalNav) {
      portalNav.setAttribute('role', 'tablist');
      portalNav.setAttribute('aria-label', 'Điều hướng cổng');
      portalNav.innerHTML = items.map(([key, label, icon]) => {
        const isCurrent = key === activeTab;
        return `
          <button type="button" role="tab" id="tab-${key}" aria-controls="view-container" aria-selected="${isCurrent ? 'true' : 'false'}" data-view="${key}" class="btn btn-ghost ${isCurrent ? 'active tab-active' : ''}" aria-current="${isCurrent ? 'page' : 'false'}">
            <i data-lucide="${icon}"></i>
            <span>${label}</span>
          </button>
        `;
      }).join('');

      portalNav.querySelectorAll('[data-view]').forEach(button => {
        button.addEventListener('click', () => openView(session, role, button.dataset.view));
      });
    }

    if (window.lucide?.createIcons) window.lucide.createIcons();
  } catch (err) {
    console.error('Lỗi render thanh điều hướng:', err);
  }
}

function openView(session, role, tab) {
  try {
    if (!menus[role]?.some(([key]) => key === tab)) {
      tab = menus[role]?.[0]?.[0] || 'dashboard';
    }
    renderNav(session, tab);

    const heading = byId('portal-heading');
    if (heading) heading.textContent = titles[tab] || 'Tổng quan';

    const msg = byId('portal-message');
    if (msg) msg.hidden = true;

    const view = byId('view-container');
    if (view) {
      view.setAttribute('role', 'tabpanel');
      view.setAttribute('aria-labelledby', `tab-${tab}`);
    }

    if (role === 'CUSTOMER') {
      loadCustomerView(tab);
    } else if (tab === 'profile') {
      loadProfileView();
    } else if (role === 'ADMIN') {
      loadAdminView(tab);
    } else {
      loadStaffView(tab);
    }

    if (view) {
      view.querySelectorAll('table').forEach(table => table.classList.add('table', 'table-zebra'));
      view.querySelectorAll('input:not([type="checkbox"]):not([type="radio"]), select, textarea').forEach(field => {
        field.classList.add(field.tagName === 'SELECT' ? 'select' : field.tagName === 'TEXTAREA' ? 'textarea' : 'input');
      });
      view.querySelectorAll('.btn-primary, .btn-secondary').forEach(button => button.classList.add('btn'));
    }
  } catch (error) {
    console.error('Lỗi khi tải giao diện tab:', error);
    const msg = byId('portal-message');
    if (msg) {
      msg.textContent = error.message || 'Không thể mở mục này.';
      msg.hidden = false;
    }
  }

  try {
    const url = new URL(window.location.href);
    url.searchParams.set('view', tab);
    history.replaceState(null, '', url);
  } catch {}
}

function enterPortal(session) {
  try {
    const role = roleOf(session?.role);
    if (!role) {
      clearAuthSession();
      localStorage.removeItem('auth_token');
      alert('Tài khoản này chưa được cấp quyền truy cập cổng web.');
      window.location.href = 'index.html#login';
      return;
    }

    const isCustomer = role === 'CUSTOMER';
    document.body.classList.toggle('customer-portal', isCustomer);

    const appView = byId('app-view');
    const authView = byId('auth-view');

    // Remove hidden and opacity-0 immediately to prevent hanging
    if (appView) {
      appView.hidden = false;
      appView.classList.remove('hidden', 'opacity-0');
      appView.setAttribute('aria-label', isCustomer ? 'Cổng khách hàng Aurelia' : 'Cổng quản lý Aurelia');
    }
    if (authView) {
      authView.hidden = true;
    }

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
          localStorage.removeItem('auth_token');
          window.location.href = 'index.html#login';
        });
        if (window.lucide?.createIcons) window.lucide.createIcons();
      }
    }
  } catch (err) {
    console.error('Lỗi khởi tạo cổng:', err);
  }
}

// Initial DOM events setup wrapped in try...catch
try {
  byId('show-login')?.addEventListener('click', () => { status(''); showAuth('login'); });
  byId('show-register')?.addEventListener('click', () => { status(''); showAuth('register'); });

  byId('login-form')?.addEventListener('submit', async event => {
    event.preventDefault();
    const button = event.currentTarget.querySelector('button[type="submit"]');
    if (button) button.disabled = true;
    status('Đang xác thực tài khoản…', 'success');
    try {
      const usernameInput = byId('login-username');
      const passwordInput = byId('login-password');
      const result = await api.post('/auth/login', {
        username: usernameInput ? usernameInput.value.trim() : '',
        password: passwordInput ? passwordInput.value : ''
      });
      if (!result || !result.token || !roleOf(result.role)) {
        throw new Error('Máy chủ không trả về quyền truy cập hợp lệ cho tài khoản này.');
      }
      const session = {
        ...result,
        userId: result.userId ?? result.id,
        role: roleOf(result.role) === 'STAFF' ? String(result.role).toUpperCase() : roleOf(result.role)
      };
      setAuthSession(session);
      localStorage.setItem('auth_token', result.token);
      const destination = roleOf(result.role) === 'STAFF' ? 'room-map' : 'dashboard';
      window.location.replace(`portal.html?view=${encodeURIComponent(destination)}`);
    } catch (error) {
      status(error.message || 'Không thể đăng nhập. Vui lòng kiểm tra kết nối và thử lại.');
    } finally {
      if (button) button.disabled = false;
    }
  });

  byId('register-form')?.addEventListener('submit', async event => {
    event.preventDefault();
    const button = event.currentTarget.querySelector('button[type="submit"]');
    if (button) button.disabled = true;
    try {
      await api.post('/auth/register', {
        username: byId('register-username')?.value.trim(),
        password: byId('register-password')?.value,
        fullName: byId('register-name')?.value.trim(),
        identityCard: byId('register-identity')?.value.trim(),
        phone: byId('register-phone')?.value.trim(),
        email: byId('register-email')?.value.trim()
      });
      status('Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.', 'success');
      showAuth('login');
      const loginUser = byId('login-username');
      if (loginUser) loginUser.value = byId('register-username')?.value.trim() || '';
      byId('login-password')?.focus();
    } catch (error) {
      status(error.message || 'Không thể tạo tài khoản. Vui lòng kiểm tra thông tin.');
    } finally {
      if (button) button.disabled = false;
    }
  });

  byId('logout-button')?.addEventListener('click', () => {
    clearAuthSession();
    localStorage.removeItem('auth_token');
    alert('Bạn đã đăng xuất thành công.');
    window.location.href = 'index.html#login';
  });
} catch (e) {
  console.warn('Thiết lập sự kiện DOM:', e);
}

// Global icons refresh
try {
  if (window.lucide?.createIcons) window.lucide.createIcons();
} catch {}

// Definitive Auth Guard initialization wrapped in try...catch
try {
  const token = (typeof localStorage !== 'undefined' ? localStorage.getItem('auth_token') : null) || getAuthSession()?.token;

  if (!token) {
    // If no token is present: Display a user-friendly notification and redirect to index.html#login
    alert('Vui lòng đăng nhập để truy cập Cổng thành viên Aurelia.');
    window.location.href = 'index.html#login';
  } else {
    // If a token is present: IMMEDIATELY remove hidden or opacity-0 from main container to trigger paint
    const appView = byId('app-view');
    const authView = byId('auth-view');
    if (appView) {
      appView.hidden = false;
      appView.classList.remove('hidden', 'opacity-0');
    }
    if (authView) {
      authView.hidden = true;
    }

    let session = getAuthSession();
    if (!session) {
      session = {
        token: token,
        role: 'CUSTOMER',
        userId: 1,
        username: 'member',
        fullName: 'Quý Hội Viên'
      };
      setAuthSession(session);
    }
    if (!localStorage.getItem('auth_token') && session.token) {
      localStorage.setItem('auth_token', session.token);
    }

    enterPortal(session);
  }
} catch (guardErr) {
  console.error('Lỗi Auth Guard:', guardErr);
  const appView = byId('app-view');
  if (appView) {
    appView.hidden = false;
    appView.classList.remove('hidden', 'opacity-0');
  }
}
