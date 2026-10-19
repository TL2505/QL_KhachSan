import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const webUiDir = path.resolve(__dirname, '..');

// Helper to load ES module
const loadModule = async (relativePath) => {
  return await import(pathToFileURL(path.resolve(webUiDir, relativePath)).href);
};

function createMockElement(id, tagName = 'div') {
  const classes = new Set();
  const attrs = new Map();
  const listeners = new Map();

  return {
    id,
    tagName: tagName.toUpperCase(),
    value: '',
    disabled: false,
    innerHTML: '',
    textContent: '',
    className: '',
    hidden: false,
    dataset: {},
    style: {},
    classList: {
      add: (...c) => c.forEach(name => classes.add(name)),
      remove: (...c) => c.forEach(name => classes.delete(name)),
      toggle: (name, val) => {
        if (val === undefined) {
          classes.has(name) ? classes.delete(name) : classes.add(name);
        } else if (val) {
          classes.add(name);
        } else {
          classes.delete(name);
        }
      },
      contains: name => classes.has(name)
    },
    setAttribute: (k, v) => attrs.set(k, String(v)),
    getAttribute: k => attrs.get(k) ?? null,
    removeAttribute: k => attrs.delete(k),
    focus: () => {},
    addEventListener: (type, fn) => {
      if (!listeners.has(type)) listeners.set(type, []);
      listeners.get(type).push(fn);
    },
    removeEventListener: (type, fn) => {
      if (listeners.has(type)) {
        listeners.set(type, listeners.get(type).filter(h => h !== fn));
      }
    },
    dispatchEvent: async (event) => {
      const handlers = listeners.get(event.type) || [];
      for (const h of handlers) {
        await h(event);
      }
    },
    querySelector: (sel) => {
      if (sel === 'button[type="submit"]') {
        return createMockElement('submit-btn', 'button');
      }
      return null;
    },
    querySelectorAll: () => []
  };
}

function setupMockEnvironment() {
  const registry = new Map();
  const getOrCreate = (id, tag = 'div') => {
    if (!registry.has(id)) {
      registry.set(id, createMockElement(id, tag));
    }
    return registry.get(id);
  };

  const rootEl = createMockElement('html', 'html');
  const mockDoc = {
    documentElement: rootEl,
    getElementById: id => getOrCreate(id),
    querySelector: () => null,
    querySelectorAll: () => [],
    createElement: tag => createMockElement('dynamic', tag),
    body: createMockElement('body', 'body')
  };

  const storageMap = new Map();
  const mockStorage = {
    getItem: k => storageMap.get(k) ?? null,
    setItem: (k, v) => storageMap.set(k, String(v)),
    removeItem: k => storageMap.delete(k),
    clear: () => storageMap.clear()
  };

  const originalWindow = globalThis.window;
  const originalDoc = globalThis.document;
  const originalStorage = globalThis.localStorage;
  const originalSession = globalThis.sessionStorage;

  globalThis.document = mockDoc;
  globalThis.window = {
    document: mockDoc,
    localStorage: mockStorage,
    sessionStorage: mockStorage,
    lucide: { createIcons: () => {} },
    location: { href: 'http://localhost:3000/portal.html', search: '' }
  };
  globalThis.localStorage = mockStorage;
  globalThis.sessionStorage = mockStorage;

  const teardown = () => {
    globalThis.window = originalWindow;
    globalThis.document = originalDoc;
    globalThis.localStorage = originalStorage;
    globalThis.sessionStorage = originalSession;
  };

  return { registry, getOrCreate, teardown };
}

// =============================================================================
// TASK 13: PORTAL UI & AUTH TABS VERIFICATION
// =============================================================================
test('Task 13: portal.html contains Royal Golden Shield emblem, circular close button, and pill tabs', () => {
  const portalHtml = fs.readFileSync(path.join(webUiDir, 'portal.html'), 'utf-8');

  // Royal Golden Shield emblem
  assert.match(portalHtml, /royal-shield-emblem/, 'Header must contain Royal Golden Shield emblem');

  // Circular close button to index.html
  assert.match(portalHtml, /portal-close-circle/, 'Must contain circular close button');
  assert.match(portalHtml, /href="index\.html"/, 'Circular button must link to index.html');

  // Pill-shaped tabs
  assert.match(portalHtml, /auth-pill-tabs/, 'Auth card must contain pill-shaped tabs');
  assert.match(portalHtml, /id="show-login"/, 'Pill tabs must contain #show-login');
  assert.match(portalHtml, /id="show-register"/, 'Pill tabs must contain #show-register');

  // Lifecycle Notification Tray
  assert.match(portalHtml, /id="btn-notification-bell"/, 'Must contain notification bell button');
  assert.match(portalHtml, /id="notification-dropdown"/, 'Must contain notification dropdown');
  assert.match(portalHtml, /id="notif-unread-badge"/, 'Must contain unread badge');
});

test('Task 13: setupAuthTabs handles tab transitions smoothly without null errors', async () => {
  const env = setupMockEnvironment();
  try {
    const showLogin = env.getOrCreate('show-login', 'button');
    const showRegister = env.getOrCreate('show-register', 'button');
    const loginForm = env.getOrCreate('login-form', 'form');
    const registerForm = env.getOrCreate('register-form', 'form');

    const { setupAuthTabs } = await loadModule('portal-app.js');
    setupAuthTabs();

    // Click register tab
    await showRegister.dispatchEvent({ type: 'click', preventDefault: () => {} });
    assert.equal(showRegister.classList.contains('active'), true, 'Register tab must become active');
    assert.equal(showLogin.classList.contains('active'), false, 'Login tab must become inactive');
    assert.equal(registerForm.hidden, false, 'Register form must be visible');
    assert.equal(loginForm.hidden, true, 'Login form must be hidden');

    // Click login tab
    await showLogin.dispatchEvent({ type: 'click', preventDefault: () => {} });
    assert.equal(showLogin.classList.contains('active'), true, 'Login tab must become active');
    assert.equal(showRegister.classList.contains('active'), false, 'Register tab must become inactive');
    assert.equal(loginForm.hidden, false, 'Login form must be visible');
    assert.equal(registerForm.hidden, true, 'Register form must be hidden');
  } finally {
    env.teardown();
  }
});

// =============================================================================
// TASK 14: IN-STAY CONCIERGE ORDER UI VERIFICATION
// =============================================================================
test('Task 14: Concierge module renders 3 categorized service sections and in-stay order modal', async () => {
  const env = setupMockEnvironment();
  try {
    const { renderConcierge } = await loadModule('public/js/customer/concierge.js');
    const container = env.getOrCreate('view-container');

    const { api } = await loadModule('public/api.js');
    const savedGet = api.get;
    api.get = async path => path.includes('/services/catalog') ? [
      {id:1,category:'FNB',name:'Bít Tết Bò Wagyu A5 Dùng Tại Phòng',price:100},
      {id:2,category:'SPA',name:'Trị Liệu Massage Thụy Điển Toàn Thân',price:200},
      {id:3,category:'HOUSEKEEPING',name:'Dịch Vụ Buồng Phòng: Đổi Khăn Tắm',price:0}
    ] : [];
    try { await renderConcierge(container, { fullName: 'Trần Văn VIP', userId: 99 }); }
    finally { api.get = savedGet; }

    const html = container.innerHTML;
    assert.match(html, /Thực Đơn & Dịch Vụ Phục Vụ Tại Phòng/, 'Must render Room Services header');
    assert.match(html, /Bít Tết Bò Wagyu A5 Dùng Tại Phòng/, 'Must render F&B item');
    assert.match(html, /Trị Liệu Massage Thụy Điển Toàn Thân/, 'Must render Spa item');
    assert.match(html, /Dịch Vụ Buồng Phòng: Đổi Khăn Tắm/, 'Must render Housekeeping item');
    assert.match(html, /id="service-order-modal"/, 'Must contain In-Stay order modal');
    assert.match(html, /id="order-voucher-code"/, 'Must contain voucher input for service orders');
    assert.match(html, /id="btn-submit-order"/, 'Must contain submit order button');
  } finally {
    env.teardown();
  }
});

// =============================================================================
// TASK 15: REVIEW-TO-REWARD 5-STAR RATING FORM VERIFICATION
// =============================================================================
test('Task 15: Bookings module renders 4-criteria 5-star rating modal with +200 points incentive', async () => {
  const env = setupMockEnvironment();
  try {
    const { renderBookings } = await loadModule('public/js/customer/bookings.js');
    const container = env.getOrCreate('view-container');

    await renderBookings(container, { fullName: 'Trần Văn VIP', userId: 99 });

    const html = container.innerHTML;
    assert.match(html, /id="review-modal"/, 'Must render Review-to-Reward modal');
    assert.match(html, /Nhận ngay 200 điểm thưởng hội viên/, 'Must mention 200 loyalty points');
    assert.match(html, /id="stars-room"/, 'Must have room rating row');
    assert.match(html, /id="stars-fnb"/, 'Must have fnb rating row');
    assert.match(html, /id="stars-service"/, 'Must have service rating row');
    assert.match(html, /id="stars-cleanliness"/, 'Must have cleanliness rating row');
    assert.match(html, /id="review-overall-display"/, 'Must display overall rating average');
    assert.match(html, /id="review-comment"/, 'Must contain comments textarea');
    assert.match(html, /id="btn-submit-review"/, 'Must contain submit review button');
  } finally {
    env.teardown();
  }
});
