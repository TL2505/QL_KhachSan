import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const webUiDir = path.resolve(__dirname, '..');
const nativeFetch = globalThis.fetch;

const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
const stop = server => new Promise(resolve => {
  if (typeof server.closeAllConnections === 'function') server.closeAllConnections();
  server.close(resolve);
});

// =============================================================================
// CATEGORY 1: AUDIT GIT DIFF & SOURCE CODE INTEGRITY (STATIC CHECK)
// =============================================================================
test('index.html contains #login-modal and proper login buttons', () => {
  for (const file of ['index.html', 'public/index.html']) {
    const html = fs.readFileSync(path.join(webUiDir, file), 'utf-8');
    
    // 1. Header login button with click handler
    assert.match(html, /id="header-login-btn"/, `${file} must contain #header-login-btn`);
    assert.match(html, /onclick="openLoginModal\(event\)"/, `${file} must call openLoginModal`);
    
    // 2. Mobile drawer login button with click handler
    assert.match(html, /id="mobile-login-btn"/, `${file} must contain #mobile-login-btn`);
    
    // 3. Full #login-modal and form controls to prevent "Cannot read properties of null"
    assert.match(html, /id="login-modal"/, `${file} must contain #login-modal`);
    assert.match(html, /id="login-form"/, `${file} must contain #login-form`);
    assert.match(html, /id="login-username"/, `${file} must contain #login-username`);
    assert.match(html, /id="login-password"/, `${file} must contain #login-password`);
    assert.match(html, /id="btn-login-submit"/, `${file} must contain #btn-login-submit`);
    assert.match(html, /id="close-login-modal"/, `${file} must contain #close-login-modal`);
    assert.match(html, /id="login-status-box"/, `${file} must contain #login-status-box`);
  }
});

// Setup DOM mock for functional tests of openLoginModal, closeLoginModal, ensureLoginModal, handleLoginSubmit
function createMockElement(id, tagName = 'div') {
  const classes = new Set();
  const attrs = new Map();
  return {
    id,
    tagName: tagName.toUpperCase(),
    value: '',
    disabled: false,
    innerHTML: '',
    textContent: '',
    className: '',
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
    setAttribute: (k, v) => attrs.set(k, v),
    getAttribute: k => attrs.get(k) ?? null,
    removeAttribute: k => attrs.delete(k),
    focus: () => {},
    querySelector: sel => {
      if (sel === 'button[type="submit"]') return createMockElement('btn-login-submit', 'button');
      return null;
    },
    addEventListener: () => {},
    removeEventListener: () => {}
  };
}

function setupMockDocument(domElements, createElementCustom = null) {
  const root = createMockElement('html');
  const doc = {
    documentElement: root,
    querySelectorAll: () => [],
    getElementById: id => domElements.get(id) || null,
    createElement: createElementCustom || (tag => createMockElement('dynamic', tag)),
    body: { appendChild: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {}
  };
  globalThis.document = doc;
  return doc;
}

// Test 2: Functional verification of openLoginModal & closeLoginModal
test('openLoginModal prevents default event and toggles modal visibility classes', async () => {
  const domElements = new Map();
  const modalEl = createMockElement('login-modal');
  modalEl.classList.add('hidden');
  domElements.set('login-modal', modalEl);
  domElements.set('login-username', createMockElement('login-username', 'input'));
  domElements.set('login-status-box', createMockElement('login-status-box'));

  setupMockDocument(domElements);
  globalThis.window = {
    location: { origin: 'http://localhost:3000', href: '' },
    lucide: { createIcons: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {}
  };

  const { openLoginModal, closeLoginModal } = await import(`../public/js/app.js?t=${Date.now()}`);

  let prevented = false;
  const mockEvent = {
    preventDefault: () => { prevented = true; }
  };

  // Opening the modal
  openLoginModal(mockEvent);
  assert.equal(prevented, true, 'openLoginModal must call e.preventDefault()');
  assert.equal(modalEl.classList.contains('hidden'), false, '#login-modal must NOT have hidden');
  assert.equal(modalEl.classList.contains('active'), true, '#login-modal must have active class');

  // Closing the modal
  prevented = false;
  closeLoginModal(mockEvent);
  assert.equal(prevented, true, 'closeLoginModal must call e.preventDefault()');
  assert.equal(modalEl.classList.contains('hidden'), true, '#login-modal must have hidden class');
  assert.equal(modalEl.classList.contains('active'), false, '#login-modal must NOT have active');
});

// Test 3: ensureLoginModal prevents null errors when modal is missing from DOM
test('ensureLoginModal creates modal dynamically if not in DOM, avoiding null exceptions', async () => {
  let created = null;
  const domElements = new Map();
  const doc = setupMockDocument(domElements, tag => {
    const el = createMockElement('dynamic', tag);
    created = el;
    return el;
  });
  doc.body.appendChild = el => { created = el; };
  doc.getElementById = id => (id === 'login-modal' ? created : null);

  const { ensureLoginModal, openLoginModal } = await import(`../public/js/app.js?t=${Date.now() + 1}`);

  const modal = ensureLoginModal();
  assert.notEqual(modal, null, 'ensureLoginModal must return a valid DOM element');
  assert.equal(modal.id, 'login-modal');

  // Calling openLoginModal should not throw null errors
  assert.doesNotThrow(() => {
    openLoginModal({ preventDefault: () => {} });
  });
});

// =============================================================================
// CATEGORY 4: REAL-WORLD INTEGRATION TESTS (SCENARIOS 1, 2, 3)
// =============================================================================

// Scenario 1: Log in with the wrong account/password -> Returns HTTP 401 -> Displays friendly error message in #login-status-box, recovery button
test('Scenario 1 (Integration): HTTP 401 Invalid credentials displays friendly error message and unlocks button for recovery', async () => {
  const server = http.createServer(async (req, res) => {
    assert.equal(req.method, 'POST');
    assert.equal(req.url, '/api/v1/auth/login');
    res.writeHead(401, {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*'
    });
    res.end(JSON.stringify({
      status: 'error',
      message: 'Tên đăng nhập hoặc mật khẩu không chính xác'
    }));
  });

  const port = await listen(server);
  const serverOrigin = `http://127.0.0.1:${port}`;

  const domElements = new Map();
  const formEl = createMockElement('login-form', 'form');
  const btnEl = createMockElement('btn-login-submit', 'button');
  btnEl.innerHTML = '<span>Đăng Nhập An Toàn</span>';
  const usernameInput = createMockElement('login-username', 'input');
  usernameInput.value = 'wrong_user';
  const passwordInput = createMockElement('login-password', 'input');
  passwordInput.value = 'wrong_password';
  const statusBox = createMockElement('login-status-box');

  domElements.set('login-form', formEl);
  domElements.set('btn-login-submit', btnEl);
  domElements.set('login-username', usernameInput);
  domElements.set('login-password', passwordInput);
  domElements.set('login-status-box', statusBox);

  setupMockDocument(domElements);
  globalThis.window = {
    location: { origin: serverOrigin, href: '' },
    lucide: { createIcons: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {}
  };
  globalThis.fetch = nativeFetch;

  try {
    const { handleLoginSubmit } = await import(`../public/js/app.js?t=${Date.now() + 2}`);

    let prevented = false;
    const mockEvent = {
      preventDefault: () => { prevented = true; },
      currentTarget: formEl
    };

    await handleLoginSubmit(mockEvent);

    assert.equal(prevented, true, 'handleLoginSubmit must call e.preventDefault()');
    // Button must be recovered/unlocked
    assert.equal(btnEl.disabled, false, 'Submit button must be re-enabled after HTTP 401 error');
    assert.equal(statusBox.classList.contains('hidden'), false, 'Status box must be visible to display error');
    assert.match(
      statusBox.textContent,
      /Tên đăng nhập|mật khẩu|Phiên đăng nhập|Thông tin chưa hợp lệ/i,
      'Friendly error message must be displayed'
    );
  } finally {
    await stop(server);
  }
});

// Scenario 2: Successful login with a valid account -> Received JWT Token -> Successfully saved to localStorage.getItem('auth_token') -> Securely closed modal
test('Scenario 2 (Integration): Successful login stores JWT in localStorage.getItem("auth_token") and closes modal', async () => {
  const jwtSample = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiJ9.sig_sample_jwt_token_123';
  const server = http.createServer(async (req, res) => {
    assert.equal(req.method, 'POST');
    assert.equal(req.url, '/api/v1/auth/login');
    res.writeHead(200, {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*'
    });
    res.end(JSON.stringify({
      status: 'success',
      data: {
        token: jwtSample,
        role: 'ADMIN',
        userId: 1,
        username: 'admin',
        fullName: 'Quản Trị Viên Khách Sạn'
      }
    }));
  });

  const port = await listen(server);
  const serverOrigin = `http://127.0.0.1:${port}`;

  const storageMap = new Map();
  globalThis.localStorage = {
    getItem: key => storageMap.get(key) ?? null,
    setItem: (key, val) => storageMap.set(key, String(val)),
    removeItem: key => storageMap.delete(key),
    clear: () => storageMap.clear()
  };

  const domElements = new Map();
  const formEl = createMockElement('login-form', 'form');
  const btnEl = createMockElement('btn-login-submit', 'button');
  btnEl.innerHTML = '<span>Đăng Nhập An Toàn</span>';
  const usernameInput = createMockElement('login-username', 'input');
  usernameInput.value = 'admin';
  const passwordInput = createMockElement('login-password', 'input');
  passwordInput.value = 'Secret@123';
  const statusBox = createMockElement('login-status-box');
  const modalEl = createMockElement('login-modal');
  modalEl.classList.add('active'); // currently open

  domElements.set('login-modal', modalEl);
  domElements.set('login-form', formEl);
  domElements.set('btn-login-submit', btnEl);
  domElements.set('login-username', usernameInput);
  domElements.set('login-password', passwordInput);
  domElements.set('login-status-box', statusBox);

  setupMockDocument(domElements);
  globalThis.window = {
    location: { origin: serverOrigin, href: '' },
    lucide: { createIcons: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {}
  };
  globalThis.fetch = nativeFetch;

  try {
    const { handleLoginSubmit } = await import(`../public/js/app.js?t=${Date.now() + 3}`);

    const mockEvent = {
      preventDefault: () => {},
      currentTarget: formEl
    };

    await handleLoginSubmit(mockEvent);

    // CRITICAL REQUIREMENT 2: Successfully saved to localStorage.getItem('auth_token')
    assert.equal(
      globalThis.localStorage.getItem('auth_token'),
      jwtSample,
      'JWT token must be successfully saved to localStorage.getItem("auth_token")'
    );

    // Also assert hotel_auth_session is populated
    const session = JSON.parse(globalThis.localStorage.getItem('hotel_auth_session') || '{}');
    assert.equal(session.token, jwtSample);
    assert.equal(session.role, 'ADMIN');

    // Wait for the modal closure timeout in handleLoginSubmit (600ms)
    await new Promise(r => setTimeout(r, 650));

    // CRITICAL: Modal is securely closed
    assert.equal(modalEl.classList.contains('hidden'), true, '#login-modal must have "hidden" class after successful login');
    assert.equal(modalEl.classList.contains('active'), false, '#login-modal must NOT have "active" class after successful login');
  } finally {
    await stop(server);
  }
});

// Scenario 3: Network Error (Timeout) Simulation -> Catch exception and unlock button again, without freezing page
test('Scenario 3 (Integration): Network error/timeout is cleanly caught, button is unlocked, UI does not freeze', async () => {
  // Simulate a hanging/resetting server that aborts the socket connection immediately
  const server = http.createServer((req, res) => {
    req.socket.destroy();
  });

  const port = await listen(server);
  const serverOrigin = `http://127.0.0.1:${port}`;

  const domElements = new Map();
  const formEl = createMockElement('login-form', 'form');
  const btnEl = createMockElement('btn-login-submit', 'button');
  btnEl.innerHTML = '<span>Đăng Nhập An Toàn</span>';
  const usernameInput = createMockElement('login-username', 'input');
  usernameInput.value = 'guest_user';
  const passwordInput = createMockElement('login-password', 'input');
  passwordInput.value = 'any_password';
  const statusBox = createMockElement('login-status-box');

  domElements.set('login-form', formEl);
  domElements.set('btn-login-submit', btnEl);
  domElements.set('login-username', usernameInput);
  domElements.set('login-password', passwordInput);
  domElements.set('login-status-box', statusBox);

  setupMockDocument(domElements);
  globalThis.window = {
    location: { origin: serverOrigin, href: '' },
    lucide: { createIcons: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {}
  };
  globalThis.fetch = nativeFetch;

  try {
    const { handleLoginSubmit } = await import(`../public/js/app.js?t=${Date.now() + 4}`);

    const mockEvent = {
      preventDefault: () => {},
      currentTarget: formEl
    };

    // Must not throw an unhandled exception or crash
    await assert.doesNotReject(
      async () => {
        await handleLoginSubmit(mockEvent);
      },
      'handleLoginSubmit must catch the network exception internally'
    );

    // CRITICAL REQUIREMENT 3: Button must be unlocked and not stuck in disabled state
    assert.equal(btnEl.disabled, false, 'Submit button must be unlocked and re-enabled after network timeout/error');
    assert.equal(statusBox.classList.contains('hidden'), false, 'Status box must display the error message');
    assert.match(
      statusBox.textContent,
      /Không thể kết nối|Không thể đăng nhập|fetch failed|socket hang up/i,
      'Friendly connection error message must be shown'
    );
  } finally {
    await stop(server);
  }
});
