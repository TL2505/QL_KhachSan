import { api, clearAuthSession, getAuthSession, setAuthSession } from './api.js';
import { loadAdminView } from './js/view-admin.js?v=3.6.4';
import { loadStaffView } from './js/view-staff.js';
import { loadCustomerView } from './js/view-customer.js?v=3.6.5';
import { loadProfileView } from './js/view-shared.js';
import './js/shared/theme.js';
import './js/shared/pagination.js?v=3.6.4';

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
  if (!modal || !content) {
    if (typeof showToast === 'function') {
      showToast(message, options?.kind === 'success' ? 'success' : 'error');
    } else {
      console.warn(message);
    }
    resolve();
    return;
  }
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
    ['dashboard', 'Tổng quan', 'chart-no-axes-combined'],
    ['rooms', 'Quản lý phòng', 'bed-double'],
    ['bookings', 'Đặt phòng', 'calendar-check'],
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
    ['dashboard', 'Tổng quan', 'layout-dashboard'],
    ['bookings', 'Đặt phòng', 'calendar-check'],
    ['concierge', 'Dịch vụ & ưu đãi', 'sparkles'],
    ['profile', 'Hồ sơ', 'user-cog'],
    ['security', 'Bảo mật', 'shield-check']
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

let googleChallengeId = null;

// Only return to known hotel pages on this origin, never an arbitrary redirect.
function bookingReturnUrl() {
  if (typeof location === 'undefined') return null;
  const value = new URLSearchParams(location.search).get('returnTo');
  if (!value) return null;
  try {
    const url = new URL(value, location.origin);
    if (url.origin !== location.origin || !['/', '/index.html', '/booking/', '/booking/index.html'].includes(url.pathname)) return null;
    return url.pathname + url.search + url.hash;
  } catch { return null; }
}

let authStatusTimer = null;

export function clearAuthStatus() {
  if (authStatusTimer) {
    clearTimeout(authStatusTimer);
    authStatusTimer = null;
  }
  const node = byId('auth-status');
  if (node) {
    node.textContent = '';
    node.className = 'auth-status';
    node.style.opacity = '1';
    node.style.display = 'none';
  }
}

export function status(message, kind = 'error') {
  try {
    if (authStatusTimer) {
      clearTimeout(authStatusTimer);
      authStatusTimer = null;
    }
    const node = byId('auth-status');
    if (!node) return;
    if (!message) {
      clearAuthStatus();
      return;
    }
    node.textContent = message;
    node.className = `auth-status has-content ${kind === 'success' ? 'success' : 'error'}`;
    node.style.display = 'flex';
    node.style.opacity = '1';

    if (typeof document !== 'undefined' && typeof document.createElement === 'function' && typeof node.appendChild === 'function') {
      try {
        const closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'btn-close-status';
        closeBtn.setAttribute('aria-label', 'Đóng thông báo');
        closeBtn.textContent = '✕';
        closeBtn.addEventListener('click', (e) => {
          if (e && typeof e.preventDefault === 'function') e.preventDefault();
          clearAuthStatus();
        });
        node.appendChild(closeBtn);
      } catch {}
    }

    // Auto-fade / disappear after 5000 ms
    authStatusTimer = setTimeout(() => {
      node.style.opacity = '0';
      authStatusTimer = setTimeout(() => {
        clearAuthStatus();
      }, 350);
    }, 5000);
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
    clearAuthStatus();
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
    refreshIcons();
  };

  const switchToRegister = (e) => {
    if (e && typeof e.preventDefault === 'function') e.preventDefault();
    clearAuthStatus();
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

function placeAccountControls(isCustomer) {
  const account = byId('portal-account');
  const header = byId('customer-header-account');
  const sidebar = byId('portal-sidebar');
  if (!account || !header || !sidebar) return;
  const destination = isCustomer ? header : sidebar;
  if (account.parentElement !== destination && typeof destination.appendChild === 'function') {
    destination.appendChild(account);
  }
  header.hidden = !isCustomer;
}

function showAuth(tab = 'login') {
  try {
    placeAccountControls(false);
    if (typeof document !== 'undefined' && document.body) {
      document.body.classList.remove('customer-portal');
      document.body.classList.remove('admin-portal');
      document.body.classList.remove('staff-portal');
      document.body.classList.remove('admin-sidebar-collapsed');
      document.body.classList.remove('portal-sidebar-collapsed');
    }
    const authView = byId('auth-view');
    const appView = byId('app-view');
    if (authView) authView.hidden = false;
    if (appView) appView.hidden = true;

    const login = tab === 'login';
    const loginForm = byId('login-form');
    const registerForm = byId('register-form');
    const otpForm = byId('google-otp-form');
    if (loginForm) loginForm.hidden = !login;
    if (registerForm) registerForm.hidden = login;
    if (otpForm) otpForm.hidden = true;

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

function syncPortalNavOrientation() {
  if (!document.body?.matches?.('.customer-portal, .admin-portal')) return;
  const compact = window.matchMedia?.('(max-width: 820px)').matches;
  byId('portal-nav')?.setAttribute('aria-orientation', compact ? 'horizontal' : 'vertical');
}

function renderNav(session, activeTab) {
  try {
    const role = roleOf(session.role) || 'CUSTOMER';
    const items = menus[role] || menus.CUSTOMER;

    const sidebarRole = byId('sidebar-role');
    if (sidebarRole) sidebarRole.textContent = (roleNames[role] || 'Thành viên').toUpperCase();

    const userDisplayName = byId('user-display-name');
    const displayName = session.fullName || session.username || 'Aurelia';
    if (userDisplayName) userDisplayName.textContent = displayName;

    const topbarUserName = byId('topbar-user-name');
    if (topbarUserName) topbarUserName.textContent = displayName;

    const accountRole = byId('account-role');
    if (accountRole) accountRole.textContent = roleNames[role] || 'Thành viên';

    const sidebarToggle = byId('admin-sidebar-toggle');
    // Staff keeps its original full menu; collapse controls belong to other portals.
    if (sidebarToggle && role === 'STAFF') sidebarToggle.remove?.();
    if (sidebarToggle && role !== 'STAFF') {
      const sidebar = byId('portal-sidebar');
      if (sidebar) {
        sidebar.onpointerleave = () => sidebar.classList.remove('admin-sidebar-hover-suppressed');
      }
      const collapsedClass = role === 'ADMIN' ? 'admin-sidebar-collapsed' : 'portal-sidebar-collapsed';
      const collapsed = document.body.classList.contains(collapsedClass);
      sidebarToggle.hidden = false;
      sidebarToggle.setAttribute('aria-expanded', String(!collapsed));
      sidebarToggle.setAttribute('aria-label', collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng');
      sidebarToggle.title = collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng';
      sidebarToggle.onclick = () => {
        const isCollapsed = document.body.classList.toggle(collapsedClass);
        sidebar?.classList.toggle('admin-sidebar-hover-suppressed', isCollapsed);
        sidebarToggle.setAttribute('aria-expanded', String(!isCollapsed));
        sidebarToggle.setAttribute('aria-label', isCollapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng');
        sidebarToggle.title = isCollapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng';
        const icon = sidebarToggle.querySelector('[data-lucide]');
        icon?.setAttribute('data-lucide', isCollapsed ? 'panel-left-open' : 'panel-left-close');
        window.lucide?.createIcons?.();
        if (isCollapsed) sidebarToggle.blur();
        try { localStorage.setItem(role === 'ADMIN' ? 'admin-sidebar-collapsed' : 'portal-sidebar-collapsed', String(isCollapsed)); } catch {}
      };
    }

    const accountAvatar = byId('account-avatar');
    const avatarInitial = String(displayName).trim().charAt(0).toUpperCase() || 'A';
    if (accountAvatar) {
      accountAvatar.textContent = avatarInitial;
    }

    const topbarAvatarInitial = byId('topbar-avatar-initial');
    const topbarAvatarImage = byId('topbar-avatar-image');
    if (topbarAvatarInitial) topbarAvatarInitial.textContent = avatarInitial;
    if (topbarAvatarImage) {
      const avatarUrl = session.avatarUrl || session.avatar_url || session.profileImage || session.profile_image || session.photoURL || session.picture || '';
      topbarAvatarImage.hidden = !avatarUrl;
      topbarAvatarImage.onerror = () => { topbarAvatarImage.hidden = true; };
      if (avatarUrl) topbarAvatarImage.src = avatarUrl;
      else topbarAvatarImage.removeAttribute('src');
    }

    const portalNav = byId('portal-nav');
    if (portalNav) {
      portalNav.setAttribute('role', 'tablist');
      portalNav.setAttribute('aria-label', 'Điều hướng cổng');
      syncPortalNavOrientation();
      portalNav.innerHTML = items.map(([key, label, icon]) => {
        const isCurrent = key === activeTab;
        return `
          <button type="button" role="tab" id="tab-${key}" aria-controls="view-container" aria-label="${label}" title="${label}" aria-selected="${isCurrent ? 'true' : 'false'}" data-view="${key}" class="btn btn-ghost ${isCurrent ? 'active tab-active' : ''}" aria-current="${isCurrent ? 'page' : 'false'}">
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
    if (heading) heading.textContent = (role === 'CUSTOMER' ? titles[tab] : menus[role]?.find(([key]) => key === tab)?.[1]) || 'Tổng quan';
    const contentBar = heading?.closest?.('.portal-content-bar');
    if (contentBar) contentBar.style.display = role === 'CUSTOMER' && tab === 'profile' ? 'none' : '';

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
export async function initNotificationTray(session) {
  const bellBtn = byId('btn-notification-bell');
  const dropdown = byId('notification-dropdown');
  const badge = byId('notif-unread-badge');
  const listEl = byId('notification-list');
  const markAllBtn = byId('btn-mark-all-read');

  if (!bellBtn || !dropdown) return;
  // Lifecycle notifications belong to customer profiles, not staff/admin accounts.
  const isCustomer = roleOf(session?.role) === 'CUSTOMER';
  bellBtn.hidden = !isCustomer;
  bellBtn.style.display = isCustomer ? '' : 'none';
  if (!isCustomer) {
    dropdown.classList.add('hidden');
    return;
  }

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
      const errBox = document.getElementById('auth-status');
      if (errBox) {
        errBox.textContent = 'Tài khoản này chưa được cấp quyền truy cập cổng web.';
        errBox.classList.remove('hidden');
      } else if (typeof showToast === 'function') {
        showToast('Tài khoản này chưa được cấp quyền truy cập cổng web.', 'error');
      }
      window.location.href = 'index.html#login';
      return;
    }

    const isCustomer = role === 'CUSTOMER';
    placeAccountControls(isCustomer);
    if (typeof document !== 'undefined' && document.body) {
      document.body.classList.remove('admin-sidebar-collapsed');
      document.body.classList.remove('portal-sidebar-collapsed');
      if (role === 'ADMIN') {
        try { document.body.classList.toggle('admin-sidebar-collapsed', localStorage.getItem('admin-sidebar-collapsed') === 'true'); } catch {}
      } else if (role !== 'STAFF') {
        try { document.body.classList.toggle('portal-sidebar-collapsed', localStorage.getItem('portal-sidebar-collapsed') === 'true'); } catch {}
      }
      document.body.classList.toggle('customer-portal', isCustomer);
      document.body.classList.toggle('admin-portal', role === 'ADMIN');
      document.body.classList.toggle('staff-portal', role === 'STAFF');
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

export function completeLogin(result, defaultUsername) {
  const payload = result?.data || result || {};
  const token = payload.token || payload.access_token || payload.jwt || result?.token;
  const rawRole = payload.role || result?.role || 'CUSTOMER';
  const role = roleOf(rawRole) || 'CUSTOMER';
  if (!token) throw new Error('Máy chủ không trả về quyền truy cập hợp lệ cho tài khoản này.');

  if (typeof localStorage !== 'undefined') {
    localStorage.setItem('customer_token', token);
    localStorage.setItem('auth_token', token);
    localStorage.setItem('token', token);
    const user = {
      token: token,
      userId: payload.userId ?? payload.id ?? payload.user_id ?? 1,
      username: payload.username || defaultUsername || payload.email || 'member',
      fullName: payload.fullName || payload.full_name || payload.name || payload.username || defaultUsername || 'Quý Hội Viên',
      avatarUrl: payload.avatarUrl || payload.avatar_url || payload.profileImage || payload.profile_image || payload.photoURL || payload.picture || payload.user?.avatarUrl || payload.user?.avatar_url || '',
      role: role === 'STAFF' ? String(rawRole).toUpperCase() : role,
      customer: payload.customer || (payload.customerId ? { id: payload.customerId } : null)
    };
    localStorage.setItem('hotel_auth_session', JSON.stringify(user));
    setAuthSession(user);
    // Instant DOM switch from #auth-view to Member Portal
    const authView = byId('auth-view');
    const appView = byId('app-view');
    if (authView) authView.hidden = true;
    if (appView) {
      appView.hidden = false;
      appView.classList.remove('hidden', 'opacity-0');
    }
  }
  const returnUrl = bookingReturnUrl();
  if (returnUrl) {
    if (typeof window !== 'undefined' && window.location?.replace) {
      window.location.replace(returnUrl);
    }
    return;
  }
  const destination = role === 'STAFF' ? 'room-map' : 'dashboard';
  if (typeof window !== 'undefined' && window.location?.replace) {
    window.location.replace(`portal.html?view=${encodeURIComponent(destination)}`);
  }
}

async function startGoogleOtp(googleResponse) {
  if (!googleResponse?.credential) { status('Google không trả về thông tin xác thực. Vui lòng thử lại.'); return; }
  status('Đang xác thực tài khoản Google…', 'success');
  try {
    const data = await api.post('/auth/google/request-otp', { credential: googleResponse.credential });
    googleChallengeId = data.challenge_id;
    if (byId('login-form')) byId('login-form').hidden = true;
    if (byId('register-form')) byId('register-form').hidden = true;
    if (byId('google-otp-form')) {
      byId('google-otp-form').hidden = false;
      if (byId('google-otp-email')) byId('google-otp-email').textContent = data.email || 'email đã xác minh';
      if (byId('google-otp-code')) {
        byId('google-otp-code').value = '';
        byId('google-otp-code').focus();
      }
    }
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
    if (byId('google-signin-button')) {
      window.google.accounts.id.renderButton(byId('google-signin-button'), { theme: 'outline', size: 'large', text: 'signin_with', locale: 'vi', width: 300 });
    }
    if (byId('google-login-area')) byId('google-login-area').hidden = false;
  } catch { /* Configuration is intentionally silent until the hotel enables Google login. */ }
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
    const data = await api.post('/auth/login', { username, password });
    completeLogin(data, username);
  } catch (error) {
    console.error('Lỗi khi đăng nhập:', error);
    status(error.message || 'Sai tên đăng nhập hoặc mật khẩu.');
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

    await api.post('/auth/register', payload);

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
    status(error.message || 'Không thể kết nối đến máy chủ. Vui lòng thử lại sau.');
  } finally {
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.removeAttribute('aria-busy');
    }
  }
}

export function initAuthEvents() {
  setupAuthSwitching();

  // Clear auth status on user typing across all form fields
  const inputIds = [
    'reg-fullname', 'reg-email', 'reg-phone', 'reg-password', 'reg-confirm-password',
    'login-username', 'login-password'
  ];
  inputIds.forEach(id => {
    const el = byId(id);
    if (el) {
      el.addEventListener('input', () => clearAuthStatus());
    }
  });

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

  // Google OTP Form
  const otpForm = byId('google-otp-form');
  if (otpForm) {
    otpForm.addEventListener('submit', async event => {
      event.preventDefault();
      const button = event.currentTarget.querySelector('button[type="submit"]');
      if (button) button.disabled = true;
      try {
        const result = await api.post('/auth/google/verify-otp', {
          challenge_id: googleChallengeId,
          otp: byId('google-otp-code')?.value.trim()
        });
        completeLogin(result);
      } catch (error) {
        status(error.message || 'Không thể xác nhận mã. Vui lòng thử lại.');
      } finally {
        if (button) button.disabled = false;
      }
    });
  }

  byId('google-otp-resend')?.addEventListener('click', async event => {
    event.currentTarget.disabled = true;
    try {
      const data = await api.post('/auth/google/resend-otp', { challenge_id: googleChallengeId });
      if (byId('google-otp-email')) {
        byId('google-otp-email').textContent = data.email || byId('google-otp-email').textContent;
      }
      status('Mã mới đã được gửi. Mã cũ không còn hiệu lực.', 'success');
    } catch (error) {
      status(error.message || 'Không thể gửi lại mã.');
    } finally {
      event.currentTarget.disabled = false;
    }
  });

  byId('google-otp-cancel')?.addEventListener('click', () => {
    googleChallengeId = null;
    clearAuthStatus();
    showAuth('login');
  });

  // Helper to determine if Dev/Mock fallback mode is permitted
  const isOAuthMockAllowed = () => {
    if (typeof window !== 'undefined') {
      if (window.ALLOW_INSECURE_OAUTH_MOCK === true || window.ALLOW_INSECURE_OAUTH_MOCK === 'true') return true;
      if (window.__ALLOW_INSECURE_OAUTH_MOCK__ === true) return true;
    }
    if (typeof localStorage !== 'undefined') {
      const val = localStorage.getItem('ALLOW_INSECURE_OAUTH_MOCK');
      if (val === 'true' || val === '1') return true;
    }
    return false;
  };

  // Social Auth handlers: Google Identity Services & Meta Facebook SDK
  const triggerGoogleAuth = async (e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    clearAuthStatus();

    const isMockForced = isOAuthMockAllowed();
    const clientId = typeof window !== 'undefined' ? window.AURELIA_GOOGLE_CLIENT_ID : null;
    const isPlaceholder = !clientId || String(clientId).includes('placeholder') || String(clientId).includes('YOUR_');
    const isHttpsOrLocalhost = typeof window !== 'undefined' && window.location && (
      window.location.protocol === 'https:' ||
      window.location.hostname === 'localhost' ||
      window.location.hostname === '127.0.0.1'
    );
    const hasGsi = typeof window !== 'undefined' && Boolean(window.google?.accounts?.id);

    // FLOW 1: Real Google Identity Services (GSI)
    if (!isMockForced && !isPlaceholder && isHttpsOrLocalhost && hasGsi) {
      try {
        let callbackExecuted = false;
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: async (credentialResponse) => {
            callbackExecuted = true;
            await handleGoogleLogin(credentialResponse);
          },
          auto_select: false,
          cancel_on_tap_outside: true
        });

        window.google.accounts.id.prompt((notification) => {
          if (!callbackExecuted && notification) {
            const isNotDisplayed = typeof notification.isNotDisplayed === 'function' && notification.isNotDisplayed();
            const isSkipped = typeof notification.isSkippedMoment === 'function' && notification.isSkippedMoment();
            const isDismissed = typeof notification.isDismissedMoment === 'function' && notification.isDismissedMoment();

            if (isNotDisplayed || isSkipped) {
              console.warn('[OAuth] Google prompt not displayed or skipped. Checking mock fallback...');
              callbackExecuted = true;
              handleGoogleLogin(`mock_google_${Date.now()}`);
            } else if (isDismissed) {
              callbackExecuted = true;
              status('Bạn đã đóng cửa sổ đăng nhập Google. Vui lòng bấm để thử lại.');
            }
          }
        });

        return; // Flow 1 initiated; wait for asynchronous callback from Google GIS
      } catch (err) {
        console.warn('[OAuth] Google SDK initialization error, fallback to mock mode:', err);
      }
    }

    // FLOW 2 (Fallback): If SDK is unavailable (e.g. blocked by Brave Shields/ad blocker), HTTPS unavailable, or mock mode active
    console.info('[OAuth] Executing Google Flow 2 (Dev/Mock Fallback Mode)...');
    await handleGoogleLogin(`mock_google_${Date.now()}`);
  };

  const triggerFacebookAuth = async (e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    clearAuthStatus();

    const isMockForced = isOAuthMockAllowed();
    const appId = typeof window !== 'undefined' ? (window.AURELIA_FB_APP_ID || '1437307811675321') : null;
    const isPlaceholder = !appId || String(appId).includes('123456');
    const isHttpsOrLocalhost = typeof window !== 'undefined' && window.location && (
      window.location.protocol === 'https:' ||
      window.location.hostname === 'localhost' ||
      window.location.hostname === '127.0.0.1'
    );
    const hasFb = typeof window !== 'undefined' && Boolean(window.FB);

    // FLOW 1: Real Meta Facebook SDK
    if (!isMockForced && !isPlaceholder && isHttpsOrLocalhost && hasFb) {
      try {
        if (typeof window.FB.init === 'function' && !window.fbInitialized) {
          window.FB.init({
            appId: appId,
            cookie: true,
            xfbml: true,
            version: 'v19.0'
          });
          window.fbInitialized = true;
        }

        window.FB.login((response) => {
          handleFacebookLogin(response);
        }, { scope: 'public_profile,email' });
        return; // Flow 1 initiated; wait for Facebook login callback
      } catch (err) {
        console.warn('[OAuth] Facebook SDK error, fallback to mock mode:', err);
      }
    }

    // FLOW 2 (Fallback): FB SDK blocked, non-HTTPS, or mock mode active
    console.info('[OAuth] Executing Facebook Flow 2 (Dev/Mock Fallback Mode)...');
    await handleFacebookLogin(`mock_fb_${Date.now()}`);
  };

  ['btnGoogleLogin', 'google-signin-btn'].forEach(id => {
    byId(id)?.addEventListener('click', triggerGoogleAuth);
  });
  if (typeof document !== 'undefined' && document.querySelectorAll) {
    document.querySelectorAll('.btn-social-google').forEach(el => {
      el.addEventListener('click', triggerGoogleAuth);
    });
  }

  ['btnFacebookLogin', 'facebook-signin-btn'].forEach(id => {
    byId(id)?.addEventListener('click', triggerFacebookAuth);
  });
  if (typeof document !== 'undefined' && document.querySelectorAll) {
    document.querySelectorAll('.btn-social-facebook').forEach(el => {
      el.addEventListener('click', triggerFacebookAuth);
    });
  }

  if (typeof window !== 'undefined') {
    window.triggerGoogleAuth = triggerGoogleAuth;
    window.triggerFacebookAuth = triggerFacebookAuth;
  }
}

export async function handleGoogleLogin(response) {
  try {
    let credential = null;
    if (typeof response === 'object' && response !== null) {
      credential = response.credential || response.token || response.id_token;
    } else if (typeof response === 'string') {
      credential = response;
    }

    if (typeof credential === 'string') {
      credential = credential.trim();
    }

    if (!credential) {
      const isMockEnv = Boolean(
        (typeof window !== 'undefined' && (window.ALLOW_INSECURE_OAUTH_MOCK === true || window.ALLOW_INSECURE_OAUTH_MOCK === 'true')) ||
        (typeof localStorage !== 'undefined' && localStorage.getItem('ALLOW_INSECURE_OAUTH_MOCK') === 'true')
      );
      const hasGsi = typeof window !== 'undefined' && Boolean(window.google?.accounts?.id);
      const isHttpsOrLocalhost = typeof window !== 'undefined' && window.location && (
        window.location.protocol === 'https:' ||
        window.location.hostname === 'localhost' ||
        window.location.hostname === '127.0.0.1'
      );

      if (isMockEnv || !hasGsi || !isHttpsOrLocalhost) {
        console.warn('[OAuth] Google SDK did not return a credential. Activating mock token fallback...');
        credential = `mock_google_${Date.now()}`;
      } else {
        console.warn('[OAuth] Empty credential received from Google SDK.');
        status('Không nhận được thông tin xác thực từ Google. Vui lòng thử lại.');
        return;
      }
    }

    await sendGoogleCredential(credential);
  } catch (err) {
    console.error('Google OAuth error:', err);
    status(err.message || 'Lỗi kết nối máy chủ xác thực Google');
  }
}

export async function handleFacebookLogin(response) {
  try {
    let accessToken = null;
    if (typeof response === 'object' && response !== null) {
      accessToken = response?.authResponse?.accessToken || response?.accessToken || response?.token;
    } else if (typeof response === 'string') {
      accessToken = response;
    }

    if (typeof accessToken === 'string') {
      accessToken = accessToken.trim();
    }

    if (!accessToken) {
      const isMockEnv = Boolean(
        (typeof window !== 'undefined' && (window.ALLOW_INSECURE_OAUTH_MOCK === true || window.ALLOW_INSECURE_OAUTH_MOCK === 'true')) ||
        (typeof localStorage !== 'undefined' && localStorage.getItem('ALLOW_INSECURE_OAUTH_MOCK') === 'true')
      );
      const hasFb = typeof window !== 'undefined' && Boolean(window.FB);
      const isHttpsOrLocalhost = typeof window !== 'undefined' && window.location && (
        window.location.protocol === 'https:' ||
        window.location.hostname === 'localhost' ||
        window.location.hostname === '127.0.0.1'
      );

      if (isMockEnv || !hasFb || !isHttpsOrLocalhost) {
        console.warn('[OAuth] Facebook SDK did not return an access token. Activating mock token fallback...');
        accessToken = `mock_fb_${Date.now()}`;
      } else {
        console.warn('[OAuth] Empty access token received from Facebook SDK.');
        status('Không nhận được thông tin xác thực từ Facebook. Vui lòng thử lại.');
        return;
      }
    }

    await sendFacebookAccessToken(accessToken);
  } catch (err) {
    console.error('Facebook OAuth error:', err);
    status(err.message || 'Lỗi kết nối máy chủ xác thực Facebook');
  }
}

export async function sendGoogleCredential(credential) {
  let token = typeof credential === 'object' && credential !== null
    ? (credential.credential || credential.token || credential.id_token)
    : credential;

  if (typeof token === 'string') {
    token = token.trim();
  }

  if (!token) {
    const isMockEnv = Boolean(
      (typeof window !== 'undefined' && (window.ALLOW_INSECURE_OAUTH_MOCK === true || window.ALLOW_INSECURE_OAUTH_MOCK === 'true')) ||
      (typeof localStorage !== 'undefined' && localStorage.getItem('ALLOW_INSECURE_OAUTH_MOCK') === 'true')
    );
    const hasGsi = typeof window !== 'undefined' && Boolean(window.google?.accounts?.id);
    const isHttpsOrLocalhost = typeof window !== 'undefined' && window.location && (
      window.location.protocol === 'https:' ||
      window.location.hostname === 'localhost' ||
      window.location.hostname === '127.0.0.1'
    );

    if (isMockEnv || !hasGsi || !isHttpsOrLocalhost) {
      console.warn('[OAuth] Google credential was empty. Generating mock credential fallback...');
      token = `mock_google_${Date.now()}`;
    } else {
      console.warn('[OAuth] Google credential cannot be empty. Aborting API dispatch.');
      status('Thông tin xác thực Google không hợp lệ hoặc bị trống.');
      return;
    }
  }

  status('Đang xác thực bảo mật với Google...', 'success');
  try {
    // Standardized endpoint: exclusively POST /api/v1/auth/oauth/google (no secondary fallback to legacy /auth/google)
    const data = await api.post('/auth/oauth/google', { credential: token, token: token });
    status('Đăng nhập Google thành công!', 'success');

    const authToken = data?.data?.token || data?.token || data?.access_token || data?.jwt;
    if (authToken && typeof localStorage !== 'undefined') {
      localStorage.setItem('customer_token', authToken);
      localStorage.setItem('auth_token', authToken);
      localStorage.setItem('token', authToken);
      localStorage.setItem('hotel_auth_session', JSON.stringify(data));
    }

    // Immediately transition the interface to Member Portal view to avoid leaving users stranded
    const authView = byId('auth-view');
    const appView = byId('app-view');
    if (authView) authView.hidden = true;
    if (appView) {
      appView.hidden = false;
      appView.classList.remove('hidden', 'opacity-0');
    }

    if (typeof enterPortal === 'function') {
      try {
        enterPortal(data?.data || data);
      } catch (portalErr) {
        console.warn('enterPortal warning:', portalErr);
      }
    }

    completeLogin(data, data?.data?.email || data?.email || 'google_user');
  } catch (error) {
    console.error('Lỗi khi đăng nhập Google:', error);
    status(error.message || 'Không thể kết nối đến máy chủ xác thực Google. Vui lòng thử lại sau.');
  }
}

export async function sendFacebookAccessToken(accessToken) {
  let token = typeof accessToken === 'object' && accessToken !== null
    ? (accessToken.accessToken || accessToken.access_token || accessToken.token)
    : accessToken;

  if (typeof token === 'string') {
    token = token.trim();
  }

  if (!token) {
    const isMockEnv = Boolean(
      (typeof window !== 'undefined' && (window.ALLOW_INSECURE_OAUTH_MOCK === true || window.ALLOW_INSECURE_OAUTH_MOCK === 'true')) ||
      (typeof localStorage !== 'undefined' && localStorage.getItem('ALLOW_INSECURE_OAUTH_MOCK') === 'true')
    );
    const hasFb = typeof window !== 'undefined' && Boolean(window.FB);
    const isHttpsOrLocalhost = typeof window !== 'undefined' && window.location && (
      window.location.protocol === 'https:' ||
      window.location.hostname === 'localhost' ||
      window.location.hostname === '127.0.0.1'
    );

    if (isMockEnv || !hasFb || !isHttpsOrLocalhost) {
      console.warn('[OAuth] Facebook access token was empty. Generating mock token fallback...');
      token = `mock_fb_${Date.now()}`;
    } else {
      console.warn('[OAuth] Facebook token cannot be empty. Aborting API dispatch.');
      status('Thông tin xác thực Facebook không hợp lệ hoặc bị trống.');
      return;
    }
  }

  status('Đang xác thực bảo mật với Facebook...', 'success');
  try {
    // Standardized endpoint: exclusively POST /api/v1/auth/oauth/facebook (no secondary fallback to legacy /auth/facebook)
    const data = await api.post('/auth/oauth/facebook', { access_token: token, token: token });
    status('Đăng nhập Facebook thành công!', 'success');

    const authToken = data?.data?.token || data?.token || data?.access_token || data?.jwt;
    if (authToken && typeof localStorage !== 'undefined') {
      localStorage.setItem('customer_token', authToken);
      localStorage.setItem('auth_token', authToken);
      localStorage.setItem('token', authToken);
      localStorage.setItem('hotel_auth_session', JSON.stringify(data));
    }

    // Immediately transition the interface to Member Portal view to avoid leaving users stranded
    const authView = byId('auth-view');
    const appView = byId('app-view');
    if (authView) authView.hidden = true;
    if (appView) {
      appView.hidden = false;
      appView.classList.remove('hidden', 'opacity-0');
    }

    if (typeof enterPortal === 'function') {
      try {
        enterPortal(data?.data || data);
      } catch (portalErr) {
        console.warn('enterPortal warning:', portalErr);
      }
    }

    completeLogin(data, data?.data?.email || data?.email || 'facebook_user');
  } catch (error) {
    console.error('Lỗi khi đăng nhập Facebook:', error);
    status(error.message || 'Không thể kết nối đến máy chủ xác thực Facebook. Vui lòng thử lại sau.');
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
    localStorage.removeItem('hotel_auth_session');
    if (typeof window !== 'undefined' && window.location) {
      window.location.href = 'index.html#login';
    }
  });
  byId('topbar-logout-button')?.addEventListener('click', () => byId('logout-button')?.click());
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
    // If no token is present: display auth-card on portal.html without blocking modal or popup
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
      if (!localStorage.getItem('hotel_auth_session') && session.token) {
        localStorage.setItem('hotel_auth_session', JSON.stringify(session));
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
  // apiFetch clears an invalid JWT and dispatches this event. Return to the
  // existing login screen so protected views cannot remain in a loading state.
  if (typeof window.addEventListener === 'function') {
    window.matchMedia?.('(max-width: 820px)').addEventListener?.('change', syncPortalNavOrientation);
    window.addEventListener('hotel:session-expired', () => {
      clearAuthSession();
      if (typeof history !== 'undefined' && history.replaceState) history.replaceState(null, '', 'portal.html');
      if (byId('view-container')) byId('view-container').replaceChildren();
      showAuth();
      status('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      byId('login-username')?.focus();
    });
  }

  window.handleLoginSubmit = handleLoginSubmit;
  window.handleRegisterSubmit = handleRegisterSubmit;
  window.setupAuthSwitching = setupAuthSwitching;
  window.setupAuthTabs = setupAuthTabs;
  window.completeLogin = completeLogin;
  window.sendGoogleCredential = sendGoogleCredential;
  window.sendFacebookAccessToken = sendFacebookAccessToken;
  window.handleGoogleLogin = handleGoogleLogin;
  window.handleFacebookLogin = handleFacebookLogin;
  window.renderDepartureActions = renderDepartureActions;
  window.renderEarlyArrivalCard = renderEarlyArrivalCard;
  window.submitGuestPreferences = submitGuestPreferences;
  window.fetchAvailableRooms = fetchAvailableRooms;
  window.calculateInclusivePricing = calculateInclusivePricing;
  window.submitPublicGuestBooking = submitPublicGuestBooking;
}

try {
  initialiseGoogleLogin();
} catch {}

// =============================================================================
// PHASE 3: B2C GUEST PORTAL & BACK-TO-BACK / EARLY ARRIVAL COMPONENTS
// =============================================================================

function generateUUID() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

/**
 * Fetch real-time room availability without stale caching.
 * Endpoint: GET /api/v1/rooms/available
 * Invariant 3 (P1): Dynamic Host Normalization via getDynamicApiBaseUrl()
 */
export async function fetchAvailableRooms(checkIn, checkOut, roomType = '') {
  const rawBase = typeof getDynamicApiBaseUrl === 'function' ? getDynamicApiBaseUrl() : 'http://localhost:8081/api/v1';
  const cleanBase = String(rawBase).replace(/\/+$/, '');
  const root = cleanBase.endsWith('/api/v1') ? cleanBase : `${cleanBase}/api/v1`;

  const params = new URLSearchParams();
  if (checkIn) {
    params.append('checkInDate', checkIn);
    params.append('check_in_date', checkIn);
  }
  if (checkOut) {
    params.append('checkOutDate', checkOut);
    params.append('check_out_date', checkOut);
  }
  if (roomType) {
    params.append('roomTypeId', roomType);
    params.append('room_type_id', roomType);
  }

  const queryString = params.toString() ? `?${params.toString()}` : '';
  const response = await fetch(`${root}/rooms/available${queryString}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error(`Failed to load luxury inventory: HTTP ${response.status}`);
  }
  return response.json();
}

/**
 * Transparent inclusive pricing calculation adhering to 5-star standard:
 * Gross = BaseRate * (1 - Discount) * 1.05 * 1.08
 */
export function calculateInclusivePricing(baseRate, nights = 1, tierName = 'Member') {
  const discounts = {
    'VIP': 0.15,
    'DIAMOND': 0.15,
    'PLATINUM': 0.12,
    'GOLD': 0.10,
    'SILVER': 0.05,
    'MEMBER': 0.00
  };
  const key = String(tierName || 'Member').trim().toUpperCase();
  const discountRate = discounts[key] !== undefined ? discounts[key] : (discounts[tierName] || 0.00);

  const n = Math.max(1, Number(nights) || 1);
  const rate = Number(baseRate) || 0;

  // Transparent calculation: Gross = BaseRate * (1 - Discount) * 1.05 * 1.08
  const subtotal = rate * n * (1 - discountRate);
  const serviceCharge = Math.round(subtotal * 0.05); // 5% luxury service charge
  const vat = Math.round((subtotal + serviceCharge) * 0.08); // 8% VAT
  const gross = Math.round(subtotal * 1.05 * 1.08);
  const total = gross;

  return {
    baseRate: rate,
    nights: n,
    tierName: tierName || 'Member',
    discountRate,
    discountApplied: discountRate > 0,
    subtotal: Math.round(subtotal),
    serviceCharge,
    vat,
    gross,
    total
  };
}

/**
 * Public Guest Booking Submission
 * Invariant 1 (P0): status: 'pending', paymentStatus: 'pending'
 * Includes X-Idempotency-Key header and securely transmits payload via getDynamicApiBaseUrl()
 */
export async function submitPublicGuestBooking(bookingData) {
  if (!bookingData) throw new Error('Dữ liệu đặt phòng không hợp lệ.');

  let checkIn = String(bookingData.checkInDate || bookingData.check_in_date || '').trim();
  if (checkIn && checkIn.length === 10) checkIn += ' 14:00:00';
  let checkOut = String(bookingData.checkOutDate || bookingData.check_out_date || '').trim();
  if (checkOut && checkOut.length === 10) checkOut += ' 12:00:00';

  const payload = {
    roomTypeId: Number(bookingData.roomTypeId || bookingData.room_type_id || bookingData.roomId || bookingData.room_id || 1),
    roomId: Number(bookingData.roomId || bookingData.room_id || bookingData.roomTypeId || bookingData.room_type_id || 1),
    room_id: Number(bookingData.roomId || bookingData.room_id || bookingData.roomTypeId || bookingData.room_type_id || 1),
    room_version: Number(bookingData.roomVersion ?? bookingData.room_version ?? 0),
    guest_count: Number(bookingData.guestCount || bookingData.guest_count || 1),
    customerName: String(bookingData.customerName || bookingData.fullName || bookingData.customer_name || '').trim(),
    customer_name: String(bookingData.customerName || bookingData.fullName || bookingData.customer_name || '').trim(),
    customerPhone: String(bookingData.customerPhone || bookingData.phone || '').trim(),
    phone: String(bookingData.customerPhone || bookingData.phone || '').trim(),
    customerEmail: String(bookingData.customerEmail || bookingData.email || '').trim(),
    email: String(bookingData.customerEmail || bookingData.email || '').trim(),
    checkInDate: checkIn,
    check_in_date: checkIn,
    checkOutDate: checkOut,
    check_out_date: checkOut,
    specialRequests: bookingData.specialRequests || bookingData.special_requests || '',
    special_requests: bookingData.specialRequests || bookingData.special_requests || '',
    voucherCode: bookingData.voucherCode || bookingData.voucher_code || '',
    voucher_code: bookingData.voucherCode || bookingData.voucher_code || '',
    status: 'pending',                  // Mandatory Invariant 1 (P0)
    paymentStatus: 'pending',           // Mandatory Invariant 1 (P0)
    payment_status: 'pending'
  };

  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  let idempotencyKey = bookingData.idempotencyKey || bookingData.idempotency_key || bookingData['X-Idempotency-Key'] || bookingData['Idempotency-Key'];
  if (!idempotencyKey || !uuidRegex.test(idempotencyKey)) {
    idempotencyKey = generateUUID();
  }

  const rawBase = typeof getDynamicApiBaseUrl === 'function' ? getDynamicApiBaseUrl() : 'http://localhost:8081/api/v1';
  const cleanBase = String(rawBase).replace(/\/+$/, '');
  const root = cleanBase.endsWith('/api/v1') ? cleanBase : `${cleanBase}/api/v1`;

  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'X-Idempotency-Key': idempotencyKey,
    'Idempotency-Key': idempotencyKey
  };

  try {
    const token = typeof localStorage !== 'undefined'
      ? (localStorage.getItem('customer_token') || localStorage.getItem('auth_token') || localStorage.getItem('token'))
      : null;
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  } catch {}

  const response = await fetch(`${root}/bookings/public`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const errorJson = await response.json().catch(() => ({}));
    const errorMsg = errorJson.message || `Unable to register room hold. Room may have been reserved. (HTTP ${response.status})`;
    if (typeof showToast === 'function') {
      showToast(errorMsg, 'error');
    }
    throw new Error(errorMsg);
  }

  const result = await response.json();
  if (typeof showToast === 'function') {
    showToast('Yêu cầu giữ phòng thành công! Vui lòng hoàn tất thanh toán để xác nhận.', 'success');
  }
  return result;
}

/**
 * Departure Actions Component (Back-to-Back Turnover Governance)
 */
export function renderDepartureActions(booking, containerElement) {
  if (!containerElement) return;
  booking = booking || {};
  const isBackToBack = Boolean(booking.isBackToBack || booking.is_back_to_back);
  const checkoutDateStr = booking.checkOutDate || booking.check_out_date || '';
  const roomNumber = booking.roomNumber || booking.room_number || '101';
  const bookingId = booking.id || booking.bookingId || 0;

  const html = `
    <div class="stay-lifecycle-card bg-slate-900/90 border border-amber-500/30 rounded-2xl p-6 shadow-2xl backdrop-blur-md">
      <div class="flex items-center justify-between pb-4 border-b border-slate-800">
        <div>
          <span class="text-xs font-semibold uppercase tracking-wider text-amber-400">Kỳ Nghỉ Phòng ${roomNumber}</span>
          <h2 class="text-xl font-serif text-white font-bold">Thao Tác Trả Phòng (Check-out)</h2>
        </div>
        <span class="px-3 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700">
          Giờ tiêu chuẩn: 12:00 PM
        </span>
      </div>

      ${isBackToBack ? `
        <div id="back-to-back-notice" class="mt-4 p-4 rounded-xl bg-amber-950/40 border border-amber-500/40 flex items-start gap-3 text-amber-200">
          <i data-lucide="sparkles" class="w-5 h-5 text-amber-400 shrink-0 mt-0.5"></i>
          <div class="text-sm leading-relaxed">
            <strong class="font-semibold block text-amber-300">Thông báo từ Aurelia Concierge:</strong>
            Phòng của Quý khách đã được chuẩn bị để đón vị khách tiếp theo vào đầu giờ chiều nay. 
            Giờ trả phòng tiêu chuẩn là trước <strong>12:00 PM</strong> nhằm đảm bảo quy trình khử khuẩn 5 sao độc quyền.
          </div>
        </div>
      ` : ''}

      <div class="mt-6 flex flex-col sm:flex-row gap-3">
        <button 
          type="button" 
          id="btn-express-checkout" 
          class="btn portal-primary flex-1 py-3 px-5 rounded-xl font-medium flex items-center justify-center gap-2"
          onclick="typeof handleExpressCheckout === 'function' ? handleExpressCheckout(${bookingId}) : (window.handleExpressCheckout && window.handleExpressCheckout(${bookingId}))">
          <i data-lucide="log-out" class="w-4 h-4"></i>
          <span>Trả Phòng Nhanh (Express Check-out)</span>
        </button>

        <div class="relative group flex-1">
          <button 
            type="button" 
            id="btn-request-late-checkout" 
            class="w-full py-3 px-5 rounded-xl text-sm font-medium transition-all flex items-center justify-center gap-2 border ${
              isBackToBack 
                ? 'bg-slate-800/50 text-slate-500 border-slate-700/50 cursor-not-allowed opacity-60' 
                : 'bg-slate-800 text-amber-300 border-amber-500/30 hover:bg-amber-500/10 cursor-pointer'
            }"
            ${isBackToBack ? 'disabled aria-disabled="true" title="Không khả dụng do có khách nhận phòng kế tiếp"' : ''}
            onclick="${isBackToBack ? 'return false;' : `typeof openLateCheckoutModal === 'function' ? openLateCheckoutModal(${bookingId}) : (window.openLateCheckoutModal && window.openLateCheckoutModal(${bookingId}))`}">
            <i data-lucide="clock" class="w-4 h-4"></i>
            <span>Gia Hạn Giờ Trả Phòng</span>
          </button>
          
          ${isBackToBack ? `
            <div class="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 hidden group-hover:block w-64 p-2.5 bg-slate-950 text-xs text-slate-300 rounded-lg shadow-xl border border-slate-700 text-center pointer-events-none z-20">
              Phòng có khách nhận cùng ngày. Tùy chọn gia hạn tạm khóa để bảo đảm chuẩn bị buồng phòng.
            </div>
          ` : ''}
        </div>
      </div>
    </div>
  `;

  containerElement.innerHTML = html;
  if (typeof window !== 'undefined' && window.lucide && typeof window.lucide.createIcons === 'function') {
    try { window.lucide.createIcons(); } catch {}
  }
}

/**
 * Early Arrival Component (Before 12:00 PM Sanitization & Priority Luggage Storage)
 */
export function renderEarlyArrivalCard(booking, containerElement) {
  if (!containerElement) return;
  booking = booking || {};
  const customerName = booking.customerName || booking.customer_name || 'Quý khách';
  const roomNumber = booking.roomNumber || booking.room_number || '101';
  const bookingId = booking.id || booking.bookingId || 0;
  const luggageToken = `AUR-LUG-${bookingId}-${Date.now().toString(36).toUpperCase()}`;

  const qrSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" class="w-28 h-28 mx-auto" shape-rendering="crispEdges">
    <rect width="100" height="100" fill="#ffffff"/>
    <rect x="10" y="10" width="24" height="24" fill="#0b0f17"/>
    <rect x="14" y="14" width="16" height="16" fill="#ffffff"/>
    <rect x="18" y="18" width="8" height="8" fill="#d4af37"/>
    <rect x="66" y="10" width="24" height="24" fill="#0b0f17"/>
    <rect x="70" y="14" width="16" height="16" fill="#ffffff"/>
    <rect x="74" y="18" width="8" height="8" fill="#d4af37"/>
    <rect x="10" y="66" width="24" height="24" fill="#0b0f17"/>
    <rect x="14" y="70" width="16" height="16" fill="#ffffff"/>
    <rect x="18" y="74" width="8" height="8" fill="#d4af37"/>
    <rect x="38" y="10" width="4" height="4" fill="#0b0f17"/>
    <rect x="46" y="10" width="4" height="4" fill="#0b0f17"/>
    <rect x="54" y="10" width="4" height="4" fill="#0b0f17"/>
    <rect x="10" y="38" width="4" height="4" fill="#0b0f17"/>
    <rect x="10" y="46" width="4" height="4" fill="#0b0f17"/>
    <rect x="10" y="54" width="4" height="4" fill="#0b0f17"/>
    <rect x="38" y="38" width="8" height="8" fill="#d4af37"/>
    <rect x="50" y="38" width="12" height="4" fill="#0b0f17"/>
    <rect x="38" y="50" width="4" height="12" fill="#0b0f17"/>
    <rect x="46" y="46" width="8" height="8" fill="#d4af37"/>
    <rect x="58" y="46" width="8" height="4" fill="#0b0f17"/>
    <rect x="46" y="58" width="4" height="8" fill="#0b0f17"/>
    <rect x="54" y="54" width="10" height="10" fill="#0b0f17"/>
    <rect x="38" y="70" width="12" height="4" fill="#0b0f17"/>
    <rect x="54" y="70" width="8" height="8" fill="#d4af37"/>
    <rect x="66" y="70" width="4" height="16" fill="#0b0f17"/>
    <rect x="74" y="78" width="12" height="8" fill="#0b0f17"/>
    <rect x="74" y="42" width="8" height="8" fill="#0b0f17"/>
    <rect x="86" y="50" width="4" height="12" fill="#0b0f17"/>
    <rect x="42" y="82" width="8" height="4" fill="#0b0f17"/>
  </svg>`;
  const qrDataUrl = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(qrSvg)}`;

  const html = `
    <div class="early-arrival-card bg-gradient-to-br from-slate-900 via-slate-900 to-amber-950/30 border border-amber-500/40 rounded-2xl p-6 shadow-2xl">
      <div class="flex items-center gap-3 pb-4 border-b border-slate-800">
        <span class="p-2.5 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-400">
          <i data-lucide="shield-check" class="w-6 h-6"></i>
        </span>
        <div>
          <span class="inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold tracking-wide uppercase bg-amber-500/20 text-amber-300 border border-amber-500/40 mb-1">
            Quy trình chuẩn bị 5 sao
          </span>
          <h2 class="text-lg font-serif font-bold text-white">Phòng Đang Được Khử Khuẩn & Tiệt Trùng Toàn Diện</h2>
        </div>
      </div>

      <p class="text-sm text-slate-300 mt-4 leading-relaxed">
        Kính chào <strong>${customerName}</strong>, Phòng <strong>${roomNumber}</strong> của Quý khách đang trải qua quy trình khử trùng ozone y tế 
        và thay mới 100% lanh cao cấp từ nghệ nhân Aurelia. Giờ bàn giao phòng dự kiến: <strong>14:00</strong>.
      </p>

      <div class="mt-6 grid grid-cols-1 md:grid-cols-2 gap-4">
        <!-- Luggage Storage Pass -->
        <div class="bg-slate-950/80 border border-slate-800 rounded-xl p-4 flex flex-col items-center text-center">
          <h3 class="text-xs font-semibold uppercase tracking-wider text-amber-400 mb-2">Thẻ Ký Gửi Hành Lý Ưu Tiên</h3>
          <div class="p-2 bg-white rounded-lg shadow-inner my-2 flex items-center justify-center">
            <img 
              src="${qrDataUrl}" 
              alt="Luggage Pass QR" 
              class="w-28 h-28 object-contain" />
          </div>
          <span class="font-mono text-xs text-amber-300 font-bold">${luggageToken}</span>
          <p class="text-[11px] text-slate-400 mt-2">
            Vui lòng xuất trình mã này tại quầy Concierge/Bellman ở sảnh chính. Hành lý sẽ được chuyển thẳng lên phòng ngay khi hoàn tất vệ sinh.
          </p>
        </div>

        <!-- Lobby Lounge Hospitality Pass -->
        <div class="bg-slate-950/80 border border-amber-500/20 rounded-xl p-4 flex flex-col justify-between">
          <div>
            <div class="flex items-center justify-between mb-2">
              <span class="text-xs font-semibold uppercase tracking-wider text-amber-400">Đặc Quyền Thành Viên</span>
              <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-300">COMPLIMENTARY</span>
            </div>
            <h4 class="text-base font-serif font-bold text-white mb-1">Thưởng Trà & Bánh Macaron Tại Sảnh</h4>
            <p class="text-xs text-slate-300 leading-relaxed">
              Trân trọng kính mời Quý khách nghỉ ngơi và thưởng thức trà thảo mộc thượng hạng kèm set bánh macarons Pháp tại <strong>Aurelia Lobby Lounge</strong> trong thời gian buồng phòng chuẩn bị.
            </p>
          </div>
          <div class="mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between text-xs text-amber-400">
            <span><i data-lucide="coffee" class="inline w-3.5 h-3.5 mr-1"></i> Phục vụ tại Tầng G</span>
            <span class="font-semibold">Mở cửa: 07:00 - 23:00</span>
          </div>
        </div>
      </div>
    </div>
  `;

  containerElement.innerHTML = html;
  if (typeof window !== 'undefined' && window.lucide && typeof window.lucide.createIcons === 'function') {
    try { window.lucide.createIcons(); } catch {}
  }
}

/**
 * Pre-arrival Concierge: Submit Guest Preferences
 * Handles pillow type, transportation arrangements, and food allergies
 */
export async function submitGuestPreferences(bookingId, preferences) {
  preferences = preferences || {};
  const payload = {
    bookingId: Number(bookingId),
    booking_id: Number(bookingId),
    pillowType: preferences.pillowType || preferences.pillow_type || 'GOOSE_DOWN',
    welcomeDrink: preferences.welcomeDrink || preferences.welcome_drink || 'CHAMPAGNE_DOM_PERIGNON',
    airportLimousine: Boolean(preferences.airportLimousine || preferences.transportation || preferences.airport_limousine),
    flightNumber: preferences.flightNumber || preferences.flight_number || null,
    dietaryAllergies: preferences.dietaryAllergies || preferences.foodAllergies || preferences.food_allergies || preferences.dietary_allergies || []
  };

  const rawBase = typeof getDynamicApiBaseUrl === 'function' ? getDynamicApiBaseUrl() : 'http://localhost:8081/api/v1';
  const cleanBase = String(rawBase).replace(/\/+$/, '');
  const root = cleanBase.endsWith('/api/v1') ? cleanBase : `${cleanBase}/api/v1`;

  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  };

  try {
    const token = typeof localStorage !== 'undefined'
      ? (localStorage.getItem('customer_token') || localStorage.getItem('auth_token') || localStorage.getItem('token'))
      : null;
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  } catch {}

  const response = await fetch(`${root}/guests/preferences`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const errorJson = await response.json().catch(() => ({}));
    const errorMsg = errorJson.message || `Lỗi cập nhật tùy chọn: HTTP ${response.status}`;
    if (typeof showToast === 'function') {
      showToast(errorMsg, 'error');
    }
    throw new Error(errorMsg);
  }

  const result = await response.json();
  if (typeof showToast === 'function') {
    showToast('Tùy chọn dịch vụ tiền sảnh đã được lưu thành công!', 'success');
  }
  return result;
}
