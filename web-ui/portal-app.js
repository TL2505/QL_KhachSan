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
    ['concierge', 'Dịch vụ & Ví ưu đãi', 'sparkles'],
    ['profile', 'Thông tin cá nhân', 'user-cog'],
    ['security', 'Bảo mật & Mật khẩu', 'shield-check']
  ]
};

const titles = Object.fromEntries(Object.values(menus).flat().map(([key, title]) => [key, title]));
titles['dashboard'] = 'Tổng quan & Hạng thẻ';
titles['bookings'] = 'Lịch sử đặt phòng';
titles['concierge'] = 'Dịch vụ & Ví ưu đãi';
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

const byId = id => (typeof document !== 'undefined' ? document.getElementById(id) : null);

function escapeHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

export function getStoredCustomerToken() {
  if (typeof localStorage === 'undefined') return null;
  return localStorage.getItem('customer_token')
    || localStorage.getItem('auth_token')
    || localStorage.getItem('token')
    || null;
}

function status(message, kind = 'error') {
  try {
    const node = byId('auth-status');
    if (node) {
      node.textContent = message;
      node.className = `auth-status ${kind === 'success' ? 'success' : ''}`;
    }
  } catch { }
}

// Dynamic Host Parity: Resolve API base URL dynamically with protocol detection (HTTP / HTTPS / Ngrok)
export const getDynamicApiBaseUrl = () => {
  if (typeof window === 'undefined' || !window.location || !window.location.hostname) {
    return 'http://localhost:8081/api/v1';
  }
  const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
  const hostname = window.location.hostname;
  return `${protocol}//${hostname}:8081/api/v1`;
};

export const API_BASE_URL = getDynamicApiBaseUrl();

export function setupAuthSwitching() {
  const tabLogin = byId('tab-login');
  const tabRegister = byId('tab-register');
  const showLogin = byId('show-login');
  const showRegister = byId('show-register');
  const loginForm = byId('login-form');
  const registerForm = byId('register-form');

  const refreshIcons = () => {
    if (typeof window !== 'undefined' && window.lucide && typeof window.lucide.createIcons === 'function') {
      try { window.lucide.createIcons(); } catch {}
    }
  };

  const switchToLogin = (e) => {
    if (e && typeof e.preventDefault === 'function') e.preventDefault();
    [tabLogin, showLogin].forEach(el => {
      if (el) {
        el.classList.add('active', 'tab-active');
        el.setAttribute('aria-selected', 'true');
      }
    });
    [tabRegister, showRegister].forEach(el => {
      if (el) {
        el.classList.remove('active', 'tab-active');
        el.setAttribute('aria-selected', 'false');
      }
    });
    if (loginForm) {
      loginForm.hidden = false;
      loginForm.style.display = '';
    }
    if (registerForm) {
      registerForm.hidden = true;
      registerForm.style.display = 'none';
    }
    status('');
    refreshIcons();
  };

  const switchToRegister = (e) => {
    if (e && typeof e.preventDefault === 'function') e.preventDefault();
    [tabRegister, showRegister].forEach(el => {
      if (el) {
        el.classList.add('active', 'tab-active');
        el.setAttribute('aria-selected', 'true');
      }
    });
    [tabLogin, showLogin].forEach(el => {
      if (el) {
        el.classList.remove('active', 'tab-active');
        el.setAttribute('aria-selected', 'false');
      }
    });
    if (registerForm) {
      registerForm.hidden = false;
      registerForm.style.display = '';
    }
    if (loginForm) {
      loginForm.hidden = true;
      loginForm.style.display = 'none';
    }
    status('');
    refreshIcons();
  };

  if (tabLogin) tabLogin.addEventListener('click', switchToLogin);
  if (showLogin) showLogin.addEventListener('click', switchToLogin);
  if (tabRegister) tabRegister.addEventListener('click', switchToRegister);
  if (showRegister) showRegister.addEventListener('click', switchToRegister);
}

export function setupAuthTabs() {
  setupAuthSwitching();
}

function showAuth(tab = 'login') {
  try {
    if (typeof document !== 'undefined' && document.body) {
      document.body.classList.remove('customer-portal');
    }
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

    if (typeof window !== 'undefined' && window.lucide?.createIcons) window.lucide.createIcons();
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
    if (typeof window !== 'undefined' && window.location) {
      const url = new URL(window.location.href);
      url.searchParams.set('view', tab);
      window.history.replaceState(null, '', url);
    }
  } catch { }
}

/**
 * Task 15: Lifecycle Notification Tray Center
 */
async function initNotificationTray(session) {
  const bellBtn = byId('btn-notification-bell');
  const dropdown = byId('notification-dropdown');
  const badge = byId('notif-unread-badge');
  const listEl = byId('notification-list');
  const markAllBtn = byId('btn-mark-all-read');

  if (!bellBtn || !dropdown) return;

  // Toggle Dropdown
  bellBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    dropdown.classList.toggle('hidden');
    if (!dropdown.classList.contains('hidden')) {
      loadNotifications();
    }
  });

  // Close on outside click
  if (typeof document !== 'undefined') {
    document.addEventListener('click', (e) => {
      if (!dropdown.contains(e.target) && !bellBtn.contains(e.target)) {
        dropdown.classList.add('hidden');
      }
    });
  }

  async function loadNotifications() {
    if (!listEl) return;
    try {
      const resp = await api.get('/notifications?limit=20').catch(() => null);
      let notifs = [];
      let unread = 0;

      if (resp && resp.data && Array.isArray(resp.data)) {
        notifs = resp.data;
        unread = resp.unread_count ?? notifs.filter(n => !n.is_read).length;
      } else if (Array.isArray(resp)) {
        notifs = resp;
        unread = notifs.filter(n => !n.is_read).length;
      } else {
        // Fallback default notifications if offline / demo
        notifs = [
          {
            id: 1,
            notification_type: 'checkin_welcome',
            title: 'Chào Mừng Quý Khách Đến Với Aurelia!',
            message: 'Trân trọng gửi tặng Quý khách mã ưu đãi ẩm thực AURELIA_DINE15 giảm 15% cho dịch vụ F&B tại phòng.',
            voucher_code: 'AURELIA_DINE15',
            action_url: 'portal.html?view=concierge',
            is_read: false,
            created_at: new Date().toISOString()
          },
          {
            id: 2,
            notification_type: 'checkout_gratitude',
            title: 'Tri Ân Nghỉ Dưỡng Trở Lại',
            message: 'Kính tặng Quý khách mã tri ân MEMORIAL_ROOM25 giảm 25% giá phòng cho kỳ nghỉ kế tiếp.',
            voucher_code: 'MEMORIAL_ROOM25',
            action_url: 'portal.html?view=bookings',
            is_read: true,
            created_at: new Date(Date.now() - 86400000).toISOString()
          }
        ];
        unread = 1;
      }

      // Update badge
      if (badge) {
        if (unread > 0) {
          badge.textContent = unread > 99 ? '99+' : String(unread);
          badge.classList.remove('hidden');
        } else {
          badge.classList.add('hidden');
        }
      }

      if (notifs.length === 0) {
        listEl.innerHTML = `
          <div class="p-6 text-center text-xs text-slate-400">
            <i data-lucide="bell-off" class="w-8 h-8 text-slate-600 mx-auto mb-2"></i>
            <p>Quý khách chưa có thông báo mới.</p>
          </div>
        `;
        if (typeof window !== 'undefined' && window.lucide?.createIcons) window.lucide.createIcons();
        return;
      }

      listEl.innerHTML = notifs.map(n => {
        const isUnread = !n.is_read;
        const safeTitle = escapeHtml(n.title || 'Thông báo');
        const safeMsg = escapeHtml(n.message || '');
        const voucher = n.voucher_code ? escapeHtml(n.voucher_code) : '';
        const timeStr = n.created_at ? new Date(n.created_at).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' }) : 'Vừa xong';

        return `
          <div class="notif-item p-3 rounded-xl transition-colors cursor-pointer ${isUnread ? 'bg-amber-500/5 border-l-2 border-amber-400' : 'bg-slate-950/40'}" data-id="${n.id}">
            <div class="flex items-start justify-between gap-2 mb-1">
              <h4 class="text-xs font-bold ${isUnread ? 'text-amber-300' : 'text-slate-200'}">${safeTitle}</h4>
              <span class="text-[10px] text-slate-500 shrink-0 font-mono">${timeStr}</span>
            </div>
            <p class="text-[11px] text-slate-400 leading-relaxed mb-2">${safeMsg}</p>
            ${voucher ? `
              <div class="p-2 rounded-lg bg-slate-900 border border-amber-500/20 flex items-center justify-between gap-2 mt-1.5">
                <span class="font-mono text-xs font-bold text-amber-400 select-all">${voucher}</span>
                <div class="flex items-center gap-1.5">
                  <button type="button" class="btn-copy-notif-voucher px-2 py-1 rounded bg-slate-800 hover:bg-amber-400 hover:text-slate-950 text-slate-200 text-[10px] font-semibold transition-colors flex items-center gap-1" data-code="${voucher}">
                    <i data-lucide="copy" class="w-3 h-3"></i>
                    <span>Sao chép</span>
                  </button>
                  <a href="${n.action_url || 'portal.html?view=concierge'}" class="px-2 py-1 rounded bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 text-[10px] font-semibold transition-colors flex items-center gap-1">
                    <i data-lucide="sparkles" class="w-3 h-3"></i>
                    <span>Dùng ngay</span>
                  </a>
                </div>
              </div>
            ` : ''}
          </div>
        `;
      }).join('');

      // Copy voucher handler
      listEl.querySelectorAll('.btn-copy-notif-voucher').forEach(btn => {
        btn.addEventListener('click', async (e) => {
          e.stopPropagation();
          const code = btn.getAttribute('data-code');
          if (!code) return;
          try {
            if (navigator.clipboard?.writeText) {
              await navigator.clipboard.writeText(code);
            }
            btn.innerHTML = `<i data-lucide="check" class="w-3 h-3 text-emerald-400"></i><span class="text-emerald-400">Đã chép!</span>`;
            if (typeof window !== 'undefined' && window.lucide?.createIcons) window.lucide.createIcons();
            setTimeout(() => {
              btn.innerHTML = `<i data-lucide="copy" class="w-3 h-3"></i><span>Sao chép</span>`;
              if (typeof window !== 'undefined' && window.lucide?.createIcons) window.lucide.createIcons();
            }, 1800);
          } catch { }
        });
      });

      // Mark single notification read on click
      listEl.querySelectorAll('.notif-item').forEach(item => {
        item.addEventListener('click', async () => {
          const id = item.getAttribute('data-id');
          if (!id) return;
          try {
            await api.put(`/notifications/${id}/read`, {});
            item.classList.remove('bg-amber-500/5', 'border-l-2', 'border-amber-400');
            item.classList.add('bg-slate-950/40');
            const unreadItems = listEl.querySelectorAll('.border-l-2').length;
            if (badge) {
              if (unreadItems > 0) {
                badge.textContent = String(unreadItems);
              } else {
                badge.classList.add('hidden');
              }
            }
          } catch { }
        });
      });

      if (typeof window !== 'undefined' && window.lucide?.createIcons) window.lucide.createIcons();

    } catch (err) {
      console.warn('Không thể tải thông báo:', err);
    }
  }

  if (markAllBtn) {
    markAllBtn.addEventListener('click', async () => {
      listEl.querySelectorAll('.notif-item').forEach(async item => {
        const id = item.getAttribute('data-id');
        if (id) {
          api.put(`/notifications/${id}/read`, {}).catch(() => { });
          item.classList.remove('bg-amber-500/5', 'border-l-2', 'border-amber-400');
          item.classList.add('bg-slate-950/40');
        }
      });
      if (badge) badge.classList.add('hidden');
    });
  }

  // Initial load
  loadNotifications();
}

function enterPortal(session) {
  try {
    const role = roleOf(session?.role);
    if (!role) {
      clearAuthSession();
      localStorage.removeItem('auth_token');
      localStorage.removeItem('customer_token');
      localStorage.removeItem('token');
      alert('Tài khoản này chưa được cấp quyền truy cập cổng web.');
      window.location.href = 'index.html#login';
      return;
    }

    const isCustomer = role === 'CUSTOMER';
    if (typeof document !== 'undefined' && document.body) {
      document.body.classList.toggle('customer-portal', isCustomer);
    }

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

    const requested = typeof window !== 'undefined' && window.location ? new URLSearchParams(window.location.search).get('view') : null;
    const defaultTab = role === 'STAFF' ? 'room-map' : 'dashboard';
    openView(session, role, requested || defaultTab);

    // Initialize Lifecycle Notifications
    initNotificationTray(session);

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
          localStorage.removeItem('customer_token');
          localStorage.removeItem('token');
          window.location.href = 'index.html#login';
        });
        if (typeof window !== 'undefined' && window.lucide?.createIcons) window.lucide.createIcons();
      }
    }
  } catch (err) {
    console.error('Lỗi khởi tạo cổng:', err);
  }
}

export async function handleLoginSubmit(e) {
  if (e && typeof e.preventDefault === 'function') {
    e.preventDefault();
  }

  const usernameInput = byId('login-username');
  const passwordInput = byId('login-password');
  const username = usernameInput ? usernameInput.value.trim() : '';
  const password = passwordInput ? passwordInput.value : '';

  if (!username || !password) {
    status('Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.');
    return;
  }

  const loginForm = byId('login-form');
  const submitBtn = loginForm ? loginForm.querySelector('button[type="submit"]') : null;
  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.setAttribute('aria-busy', 'true');
  }

  status('Đang xác thực bảo mật...', 'success');

  try {
    const res = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify({ username, password })
    });

    const data = await res.json().catch(() => null);

    if (!res.ok || (data && (data.status === 'error' || data.status === 'failed'))) {
      const errorMsg = data?.message || (res.status === 401 ? 'Sai tên đăng nhập hoặc mật khẩu.' : 'Đăng nhập không thành công.');
      status(errorMsg);
      return;
    }

    const authData = (data && data.data) ? data.data : data;
    const token = authData?.token;
    if (!token) {
      status('Máy chủ không trả về quyền truy cập hợp lệ cho tài khoản này.');
      return;
    }

    // Session Harmonization: store across 3 keys
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('customer_token', token);
      localStorage.setItem('auth_token', token);
      localStorage.setItem('token', token);
      const user = {
        token: token,
        userId: authData.userId || authData.id,
        username: authData.username || username,
        fullName: authData.fullName || authData.username || 'Quý Hội Viên',
        role: authData.role || 'CUSTOMER'
      };
      localStorage.setItem('hotel_auth_session', JSON.stringify(user));
      setAuthSession(user);
    }

    status('Đăng nhập thành công! Đang vào cổng thành viên...', 'success');

    const role = String(authData.role || 'CUSTOMER').toUpperCase();
    const destination = (role === 'STAFF' || role === 'ADMIN') ? 'room-map' : 'dashboard';

    setTimeout(() => {
      if (typeof window !== 'undefined' && window.location) {
        window.location.href = `portal.html?view=${encodeURIComponent(destination)}`;
      }
    }, 350);

  } catch (error) {
    console.error('Lỗi khi đăng nhập:', error);
    status('Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại mạng hoặc thử lại sau.');
  } finally {
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.removeAttribute('aria-busy');
    }
  }
}

export async function handleRegisterSubmit(e) {
  if (e && typeof e.preventDefault === 'function') {
    e.preventDefault();
  }

  const fullNameInput = byId('reg-fullname') || byId('register-name');
  const emailInput = byId('reg-email') || byId('register-email');
  const phoneInput = byId('reg-phone') || byId('register-phone');
  const passwordInput = byId('reg-password') || byId('register-password');
  const confirmPasswordInput = byId('reg-confirm-password');

  const fullName = fullNameInput ? fullNameInput.value.trim() : '';
  const email = emailInput ? emailInput.value.trim() : '';
  const phone = phoneInput ? phoneInput.value.trim() : '';
  const password = passwordInput ? passwordInput.value : '';
  const confirmPassword = confirmPasswordInput ? confirmPasswordInput.value : '';

  if (!email || !password) {
    status('Vui lòng nhập đầy đủ email/tên đăng nhập và mật khẩu.');
    return;
  }

  if (confirmPasswordInput && password !== confirmPassword) {
    status('Mật khẩu xác nhận không khớp. Vui lòng kiểm tra lại.');
    return;
  }

  if (password.length < 8) {
    status('Mật khẩu phải chứa ít nhất 8 ký tự.');
    return;
  }

  const registerForm = byId('register-form');
  const submitBtn = registerForm ? registerForm.querySelector('button[type="submit"]') : null;
  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.setAttribute('aria-busy', 'true');
  }

  status('Đang khởi tạo tài khoản...', 'success');

  try {
    const payload = {
      username: email,
      full_name: fullName || email,
      fullName: fullName || email,
      email: email,
      phone: phone || '',
      password: password,
      identityCard: 'TEMP-' + Date.now(),
      identity_card: 'TEMP-' + Date.now()
    };

    const res = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const data = await res.json().catch(() => null);

    if (!res.ok || (data && (data.status === 'error' || data.status === 'failed'))) {
      const errorMsg = data?.message || 'Đăng ký không thành công. Vui lòng thử lại.';
      status(errorMsg);
      return;
    }

    status('Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay.', 'success');

    // Automatically switch back to the Login tab and pre-fill the email/username field
    const tabLogin = byId('tab-login') || byId('show-login');
    if (tabLogin) tabLogin.click();

    const loginUser = byId('login-username');
    if (loginUser) loginUser.value = email;

    const loginPass = byId('login-password');
    if (loginPass) loginPass.focus();

  } catch (error) {
    console.error('Lỗi khi đăng ký:', error);
    status('Không thể kết nối đến máy chủ. Vui lòng thử lại sau.');
  } finally {
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.removeAttribute('aria-busy');
    }
  }
}

export function initAuthEvents() {
  setupAuthSwitching();

  const loginForm = byId('login-form');
  if (loginForm) {
    loginForm.removeEventListener('submit', handleLoginSubmit);
    loginForm.addEventListener('submit', handleLoginSubmit);
  }

  const registerForm = byId('register-form');
  if (registerForm) {
    registerForm.removeEventListener('submit', handleRegisterSubmit);
    registerForm.addEventListener('submit', handleRegisterSubmit);
  }

  // Social Auth Scaffolding handlers
  const googleBtn = byId('google-signin-btn');
  if (googleBtn) {
    googleBtn.addEventListener('click', (e) => {
      e.preventDefault();
      status('Google GIS SDK: Sẵn sàng kết nối Google Client ID.', 'success');
    });
  }

  const facebookBtn = byId('facebook-signin-btn');
  if (facebookBtn) {
    facebookBtn.addEventListener('click', (e) => {
      e.preventDefault();
      status('Facebook Login SDK: Sẵn sàng kết nối Facebook App ID.', 'success');
    });
  }
}

// Initial DOM events setup wrapped in try...catch
try {
  initAuthEvents();

  byId('logout-button')?.addEventListener('click', () => {
    clearAuthSession();
    localStorage.removeItem('auth_token');
    localStorage.removeItem('customer_token');
    localStorage.removeItem('token');
    if (typeof window !== 'undefined' && window.location) {
      window.location.href = 'index.html#login';
    }
  });
} catch (e) {
  console.warn('Thiết lập sự kiện DOM:', e);
}

// Global icons refresh
try {
  if (typeof window !== 'undefined' && window.lucide?.createIcons) window.lucide.createIcons();
} catch { }

// Definitive Auth Guard initialization wrapped in try...catch
try {
  const token = getStoredCustomerToken() || (typeof localStorage !== 'undefined' ? localStorage.getItem('auth_token') : null) || getAuthSession()?.token;

  if (!token) {
    // If no token is present: display auth-card on portal.html without blocking alert()
    const authView = byId('auth-view');
    const appView = byId('app-view');
    if (authView) authView.hidden = false;
    if (appView) appView.hidden = true;
    // Fallback: unauthenticated navigation targets index.html#login
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
    if (typeof localStorage !== 'undefined') {
      if (!localStorage.getItem('auth_token') && session.token) {
        localStorage.setItem('auth_token', session.token);
      }
      if (!localStorage.getItem('customer_token') && session.token) {
        localStorage.setItem('customer_token', session.token);
      }
      if (!localStorage.getItem('token') && session.token) {
        localStorage.setItem('token', session.token);
      }
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

if (typeof window !== 'undefined') {
  window.handleLoginSubmit = handleLoginSubmit;
  window.handleRegisterSubmit = handleRegisterSubmit;
  window.setupAuthSwitching = setupAuthSwitching;
  window.setupAuthTabs = setupAuthTabs;
}
