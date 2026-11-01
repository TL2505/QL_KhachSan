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

// Mock DOM factory for headless node testing
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
    replaceChildren() { this.innerHTML = ''; this.textContent = ''; },
    focus: () => {},
    insertAdjacentElement(position, sibling) {
      if (position === 'afterend') this.nextSibling = sibling;
      return sibling;
    },
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
    click: async function() {
      await this.dispatchEvent({ type: 'click', preventDefault: () => {} });
    },
    querySelector: (sel) => {
      if (sel === 'button[type="submit"]') {
        return createMockElement('btn-submit', 'button');
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
    querySelector: (sel) => {
      if (sel === '#login-form button[type="submit"]') return getOrCreate('btn-login-submit', 'button');
      if (sel === '#register-form button[type="submit"]') return getOrCreate('btn-register-submit', 'button');
      return null;
    },
    querySelectorAll: () => [],
    addEventListener: () => {},
    removeEventListener: () => {},
    createElement: tag => {
      const element = createMockElement('dynamic', tag);
      let elementId = element.id;
      Object.defineProperty(element, 'id', {
        get: () => elementId,
        set: value => { elementId = String(value); registry.set(elementId, element); }
      });
      element.insertAdjacentElement = (position, sibling) => {
        if (position === 'afterend') element.nextSibling = sibling;
      };
      return element;
    },
    body: createMockElement('body', 'body')
  };

  const storageMap = new Map();
  const mockStorage = {
    getItem: k => storageMap.get(k) ?? null,
    setItem: (k, v) => storageMap.set(k, String(v)),
    removeItem: k => storageMap.delete(k),
    clear: () => storageMap.clear()
  };

  const originalDoc = globalThis.document;
  const originalWindow = globalThis.window;
  const originalStorage = globalThis.localStorage;
  const originalFetch = globalThis.fetch;
  const windowListeners = new Map();

  globalThis.document = mockDoc;
  globalThis.window = {
    document: mockDoc,
    location: {
      hostname: '192.168.56.102',
      origin: 'http://192.168.56.102:3000',
      href: 'http://192.168.56.102:3000/portal.html',
      search: '',
      replace: () => {}
    },
    localStorage: mockStorage,
    addEventListener: (type, listener) => {
      if (!windowListeners.has(type)) windowListeners.set(type, []);
      windowListeners.get(type).push(listener);
    },
    dispatchEvent: event => {
      for (const listener of windowListeners.get(event.type) || []) listener(event);
      return true;
    },
    setInterval: () => 1,
    clearInterval: () => {},
    lucide: { createIcons: () => {} }
  };
  globalThis.localStorage = mockStorage;

  return {
    getOrCreate,
    storageMap,
    teardown: () => {
      globalThis.document = originalDoc;
      globalThis.window = originalWindow;
      globalThis.localStorage = originalStorage;
      globalThis.fetch = originalFetch;
    }
  };
}

// =============================================================================
// STATIC STRUCTURAL & LUXURY AUDIT
// =============================================================================

test('housekeeping has a separate workbench role and no access to staff or customer notifications', async () => {
  const env = setupMockEnvironment();
  try {
    let staffRequests = 0, customerRequests = 0;
    globalThis.fetch = async url => {
      const endpoint = new URL(String(url)).pathname;
      if (endpoint.endsWith('/staff-notifications')) staffRequests++;
      else if (endpoint.endsWith('/notifications')) customerRequests++;
      return {ok:true,status:200,headers:{get:()=>null},text:async()=>JSON.stringify({status:'success',data:[]})};
    };
    const { initNotificationTray } = await loadModule('public/portal-app.js');
    for (const role of ['ADMIN', 'STAFF']) {
      await initNotificationTray({ role, token: 'valid-test-token' });
      assert.equal(env.getOrCreate('btn-notification-bell').hidden, false);
    }
    await initNotificationTray({ role: 'HOUSEKEEPING', token: 'valid-test-token' });
    assert.equal(env.getOrCreate('btn-notification-bell').hidden, true);
    assert.equal(staffRequests, 2);
    assert.equal(customerRequests, 0);

    env.getOrCreate('auth-view').hidden = true;
    env.getOrCreate('app-view').hidden = false;
    env.getOrCreate('view-container').innerHTML = '<p>protected staff data</p>';
    localStorage.setItem('hotel_auth_session', JSON.stringify({ token: 'stale-staff-token', role: 'STAFF' }));
    localStorage.setItem('auth_token', 'stale-staff-token');
    window.dispatchEvent(new Event('hotel:session-expired'));
    assert.equal(env.getOrCreate('auth-view').hidden, false);
    assert.equal(env.getOrCreate('app-view').hidden, true);
    assert.equal(env.getOrCreate('view-container').innerHTML, '');
    assert.equal(env.getOrCreate('auth-status').textContent, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    assert.equal(localStorage.getItem('auth_token'), null);
  } finally { env.teardown(); }
});

test('housekeeping login opens its role-specific workbench by default', async () => {
  const env = setupMockEnvironment();
  try {
    window.location.replace = destination => { window.location.href = destination; };
    const { completeLogin } = await loadModule('public/portal-app.js');
    completeLogin({ token: 'synthetic-housekeeping-session', role: 'housekeeping', userId: 27 });
    const session = JSON.parse(localStorage.getItem('hotel_auth_session'));
    assert.equal(session.role, 'HOUSEKEEPING');
    assert.equal(window.location.href, 'portal.html?view=housekeeping');
  } finally { env.teardown(); }
});

test('password login routes administrators to the dashboard', async () => {
  const env = setupMockEnvironment();
  const originalTimeout = globalThis.setTimeout;
  try {
    globalThis.fetch = async () => ({ ok: true, json: async () => ({ status: 'success', data: {token:'test-token', userId:1, role:'ADMIN'} }) });
    globalThis.setTimeout = callback => { callback(); return 0; };
    window.location.replace = destination => { window.location.href = destination; };
    env.getOrCreate('login-username').value = 'admin_main';
    env.getOrCreate('login-password').value = 'test-password';
    const { handleLoginSubmit } = await loadModule('public/portal-app.js');
    await handleLoginSubmit({preventDefault(){}});
    assert.equal(window.location.href, 'portal.html?view=dashboard');
  } finally { globalThis.setTimeout = originalTimeout; env.teardown(); }
});

test('completed Google session keeps the linked customer ID independently of the user ID', async () => {
  const env = setupMockEnvironment();
  try {
    window.location.replace = destination => { window.location.href = destination; };
    const { completeLogin } = await loadModule('public/portal-app.js');
    completeLogin({ token: 'synthetic-google-session', role: 'CUSTOMER', userId: 19, customerId: 12 });
    const session = JSON.parse(localStorage.getItem('hotel_auth_session'));
    assert.equal(session.userId, 19);
    assert.equal(session.customerId, 12);
    assert.equal(window.location.href, 'portal.html?view=dashboard');
  } finally { env.teardown(); }
});
test('portal.html: contains secure login/register forms and one shared Google button without Facebook', () => {
  for (const file of ['portal.html', 'public/portal.html']) {
    const html = fs.readFileSync(path.join(webUiDir, file), 'utf-8');

    // 1. Tab-switching interface with tab-pill and active states
    assert.match(html, /id="tab-login"/, `${file} must contain #tab-login`);
    assert.match(html, /id="tab-register"/, `${file} must contain #tab-register`);
    assert.match(html, /tab-pill\s+active/, `${file} must have tab-pill active on login tab`);

    // 2. Login form controls
    assert.match(html, /id="login-form"/, `${file} must contain #login-form`);
    assert.match(html, /id="login-username"/, `${file} must contain #login-username`);
    assert.match(html, /id="login-password"/, `${file} must contain #login-password`);
    assert.match(html, /Secure Login/, `${file} submit button must be labeled 'Secure Login'`);

    // 3. Registration form controls
    assert.match(html, /id="register-form"/, `${file} must contain #register-form`);
    assert.match(html, /id="reg-fullname"/, `${file} must contain #reg-fullname`);
    assert.match(html, /id="reg-email"/, `${file} must contain #reg-email`);
    assert.match(html, /id="reg-phone"/, `${file} must contain #reg-phone`);
    assert.match(html, /id="reg-password"/, `${file} must contain #reg-password`);
    assert.match(html, /id="reg-confirm-password"/, `${file} must contain #reg-confirm-password`);
    assert.match(html, /Register Membership/, `${file} submit button must be labeled 'Register Membership'`);

    // Both tabs share the real Google GIS widget outside the password forms.
    assert.match(html, /Hoặc tiếp tục với/, `${file} must contain the Google divider`);
    assert.equal((html.match(/id="google-signin-button"/g) || []).length, 1);
    assert.doesNotMatch(html, /facebook|connect\.facebook\.net/i);
    assert.doesNotMatch(html, /id="google-signin-btn"/);
  }
});

// =============================================================================
// DYNAMIC TAB-SWITCHING VERIFICATION
// =============================================================================
test('portal-app.js: setupAuthSwitching toggles active classes and form display without null errors', async () => {
  const env = setupMockEnvironment();
  try {
    const tabLogin = env.getOrCreate('tab-login', 'button');
    const tabRegister = env.getOrCreate('tab-register', 'button');
    const loginForm = env.getOrCreate('login-form', 'form');
    const registerForm = env.getOrCreate('register-form', 'form');

    const { setupAuthSwitching } = await loadModule('portal-app.js');
    setupAuthSwitching();

    // Switch to Register Tab
    await tabRegister.click();
    assert.equal(tabRegister.classList.contains('active'), true, 'Register tab must become active');
    assert.equal(tabLogin.classList.contains('active'), false, 'Login tab must become inactive');
    assert.equal(registerForm.hidden, false, 'Register form must be visible');
    assert.equal(loginForm.hidden, true, 'Login form must be hidden');

    // Switch back to Login Tab
    await tabLogin.click();
    assert.equal(tabLogin.classList.contains('active'), true, 'Login tab must become active');
    assert.equal(tabRegister.classList.contains('active'), false, 'Register tab must become inactive');
    assert.equal(loginForm.hidden, false, 'Login form must be visible');
    assert.equal(registerForm.hidden, true, 'Register form must be hidden');
  } finally {
    env.teardown();
  }
});

// =============================================================================
// ASYNCHRONOUS SECURE LOGIN VERIFICATION (ZERO QUERY-STRING EXPOSURE)
// =============================================================================
test('portal-app.js: handleLoginSubmit prevents default, calls API via POST JSON, and harmonizes session', async () => {
  const env = setupMockEnvironment();
  try {
    let preventDefaultCalled = false;
    let requestedUrl = null;
    let requestOptions = null;

    globalThis.fetch = async (url, options) => {
      requestedUrl = url;
      requestOptions = options;
      return {
        ok: true,
        status: 200,
        json: async () => ({
          status: 'success',
          message: 'Đăng nhập thành công',
          data: {
            token: 'test-jwt-token-aurelia-2026',
            userId: 77,
            username: 'royal_guest',
            fullName: 'Lord Aurelia',
            role: 'CUSTOMER'
          }
        })
      };
    };

    const userInput = env.getOrCreate('login-username', 'input');
    const passInput = env.getOrCreate('login-password', 'input');
    userInput.value = 'royal_guest';
    passInput.value = 'Secret@1234';

    const { handleLoginSubmit, API_BASE_URL } = await loadModule('portal-app.js');

    // Dynamic Host Parity assertion
    assert.equal(API_BASE_URL, 'http://192.168.56.102:8081/api/v1', 'API_BASE_URL must resolve to browser host IP on port 8081');

    await handleLoginSubmit({
      preventDefault: () => { preventDefaultCalled = true; }
    });

    // Verify e.preventDefault() was triggered
    assert.equal(preventDefaultCalled, true, 'e.preventDefault() must be called to eliminate page reload');

    // Verify API call was asynchronous JSON POST
    assert.equal(requestedUrl, 'http://192.168.56.102:8081/api/v1/auth/login');
    assert.equal(requestOptions.method, 'POST');
    const sentBody = JSON.parse(requestOptions.body);
    assert.equal(sentBody.username, 'royal_guest');
    assert.equal(sentBody.password, 'Secret@1234');

    // Verify Session Harmonization across 3 keys
    assert.equal(env.storageMap.get('customer_token'), 'test-jwt-token-aurelia-2026');
    assert.equal(env.storageMap.get('auth_token'), 'test-jwt-token-aurelia-2026');
    assert.equal(env.storageMap.get('token'), 'test-jwt-token-aurelia-2026');
    const userSession = JSON.parse(env.storageMap.get('hotel_auth_session'));
    assert.equal(userSession.userId, 77);
    assert.equal(userSession.role, 'CUSTOMER');
  } finally {
    env.teardown();
  }
});

// =============================================================================
// ASYNCHRONOUS REGISTRATION & PASSWORD CONFIRMATION VERIFICATION
// =============================================================================
test('portal-app.js: handleRegisterSubmit shows field-level password mismatch and switches to login upon success', async () => {
  const env = setupMockEnvironment();
  try {
    let preventDefaultCalled = false;
    let requestedUrl = null;
    let requestOptions = null;

    globalThis.fetch = async (url, options) => {
      requestedUrl = url;
      requestOptions = options;
      return {
        ok: true,
        status: 200,
        json: async () => ({
          status: 'success',
          message: 'Đăng ký thành công! Chào mừng bạn đến với hệ thống.'
        })
      };
    };

    const nameInput = env.getOrCreate('reg-fullname', 'input');
    const emailInput = env.getOrCreate('reg-email', 'input');
    const phoneInput = env.getOrCreate('reg-phone', 'input');
    const passInput = env.getOrCreate('reg-password', 'input');
    const confirmInput = env.getOrCreate('reg-confirm-password', 'input');
    const statusBox = env.getOrCreate('auth-status', 'p');
    const tabLogin = env.getOrCreate('tab-login', 'button');
    const loginUser = env.getOrCreate('login-username', 'input');

    nameInput.value = 'Nguyễn Quý Khách';
    emailInput.value = 'vip@aurelia.vn';
    phoneInput.value = '0901234567';
    passInput.value = 'Secret@1234';
    confirmInput.value = 'DifferentPassword'; // MISMATCH TEST

    const { handleRegisterSubmit, setupAuthSwitching } = await loadModule('portal-app.js');
    setupAuthSwitching();

    // 1. Password mismatch case
    await handleRegisterSubmit({
      preventDefault: () => { preventDefaultCalled = true; }
    });
    assert.equal(preventDefaultCalled, true);
    assert.match(confirmInput.nextSibling.textContent, /không khớp/i, 'Must show mismatch under password confirmation');
    assert.equal(confirmInput.getAttribute('aria-invalid'), 'true');
    assert.equal(requestedUrl, null, 'Fetch must not be triggered on mismatch');

    // 2. Successful registration case
    confirmInput.value = 'Secret@1234';
    await handleRegisterSubmit({
      preventDefault: () => {}
    });

    assert.equal(requestedUrl, 'http://192.168.56.102:8081/api/v1/auth/register');
    assert.equal(requestOptions.method, 'POST');
    const body = JSON.parse(requestOptions.body);
    assert.equal(body.email, 'vip@aurelia.vn');
    assert.equal(body.fullName, 'Nguyễn Quý Khách');
    assert.equal(Object.hasOwn(body, 'identityCard'), false, 'Registration must not send a fake identity number');

    // Verify auto-switch to Login and pre-fill username
    assert.equal(loginUser.value, 'vip@aurelia.vn', 'Email/username must be prefilled in login form');
  } finally {
    env.teardown();
  }
});

test('portal-app.js: invalid registration values are reported next to each affected field', async () => {
  const env = setupMockEnvironment();
  try {
    let requests = 0;
    globalThis.fetch = async () => { requests++; throw new Error('Fetch should not run for invalid form data'); };
    const fullName = env.getOrCreate('reg-fullname', 'input');
    const email = env.getOrCreate('reg-email', 'input');
    const phone = env.getOrCreate('reg-phone', 'input');
    const password = env.getOrCreate('reg-password', 'input');
    const confirm = env.getOrCreate('reg-confirm-password', 'input');
    fullName.value = '';
    email.value = 'wrong@';
    phone.value = '123';
    password.value = 'short';
    confirm.value = 'different';

    const { handleRegisterSubmit } = await loadModule('portal-app.js');
    await handleRegisterSubmit({ preventDefault() {} });

    assert.match(fullName.nextSibling.textContent, /họ và tên/i);
    assert.match(email.nextSibling.textContent, /email hợp lệ/i);
    assert.match(phone.nextSibling.textContent, /8 đến 15 chữ số/i);
    assert.match(password.nextSibling.textContent, /ít nhất 8 ký tự/i);
    assert.match(confirm.nextSibling.textContent, /không khớp/i);
    for (const input of [fullName, email, phone, password, confirm]) {
      assert.equal(input.getAttribute('aria-invalid'), 'true');
    }
    assert.equal(requests, 0);
  } finally {
    env.teardown();
  }
});

test('portal-app.js: server username conflicts are shown under email/username', async () => {
  const env = setupMockEnvironment();
  try {
    globalThis.fetch = async () => ({
      ok: false,
      status: 409,
      json: async () => ({
        status: 'error',
        message: 'Tên đăng nhập đã tồn tại.',
        fieldErrors: { email: 'Tên đăng nhập đã tồn tại.' }
      })
    });
    env.getOrCreate('reg-fullname', 'input').value = 'Nguyễn Quý Khách';
    const email = env.getOrCreate('reg-email', 'input');
    email.value = 'taken@aurelia.vn';
    env.getOrCreate('reg-password', 'input').value = 'Secret@1234';
    env.getOrCreate('reg-confirm-password', 'input').value = 'Secret@1234';

    const { handleRegisterSubmit } = await loadModule('portal-app.js');
    await handleRegisterSubmit({ preventDefault() {} });

    assert.match(email.nextSibling.textContent, /đã tồn tại/i);
    assert.equal(email.getAttribute('aria-invalid'), 'true');
  } finally {
    env.teardown();
  }
});
