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
test('index.html contains #login-modal, tab switcher, login & register forms, and proper triggers', () => {
  for (const file of ['index.html', 'public/index.html']) {
    const html = fs.readFileSync(path.join(webUiDir, file), 'utf-8');
    
    // 1. Header login and register buttons with click handlers
    assert.match(html, /id="header-login-btn"/, `${file} must contain #header-login-btn`);
    assert.match(html, /onclick="openLoginModal\(event\)"/, `${file} must call openLoginModal`);
    assert.match(html, /id="header-register-btn"/, `${file} must contain #header-register-btn`);
    assert.match(html, /onclick="openRegisterModal\(event\)"/, `${file} must call openRegisterModal`);
    
    // 2. Mobile drawer login and register buttons with click handlers
    assert.match(html, /id="mobile-login-btn"/, `${file} must contain #mobile-login-btn`);
    assert.match(html, /id="mobile-register-btn"/, `${file} must contain #mobile-register-btn`);
    
    // 3. Tab Switcher
    assert.match(html, /id="tab-login-btn"/, `${file} must contain #tab-login-btn`);
    assert.match(html, /id="tab-register-btn"/, `${file} must contain #tab-register-btn`);

    // 4. Full #login-modal and form controls
    assert.match(html, /id="login-modal"/, `${file} must contain #login-modal`);
    assert.match(html, /id="login-form"/, `${file} must contain #login-form`);
    assert.match(html, /id="login-username"/, `${file} must contain #login-username`);
    assert.match(html, /id="login-password"/, `${file} must contain #login-password`);
    assert.match(html, /id="btn-login-submit"/, `${file} must contain #btn-login-submit`);
    assert.match(html, /id="close-login-modal"/, `${file} must contain #close-login-modal`);
    assert.match(html, /id="login-status-box"/, `${file} must contain #login-status-box`);

    // 5. Full #register-form controls
    assert.match(html, /id="register-form"/, `${file} must contain #register-form`);
    assert.match(html, /id="register-fullname"/, `${file} must contain #register-fullname`);
    assert.match(html, /id="register-username"/, `${file} must contain #register-username`);
    assert.match(html, /id="register-password"/, `${file} must contain #register-password`);
    assert.match(html, /id="register-status-box"/, `${file} must contain #register-status-box`);
    assert.match(html, /id="btn-register-submit"/, `${file} must contain #btn-register-submit`);
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
      if (sel === 'button[type="submit"]') {
        return id === 'register-form' ? createMockElement('btn-register-submit', 'button') : createMockElement('btn-login-submit', 'button');
      }
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

    // CRITICAL REQUIREMENT 2: Successfully saved to localStorage across all three keys
    assert.equal(
      globalThis.localStorage.getItem('auth_token'),
      jwtSample,
      'JWT token must be successfully saved to localStorage.getItem("auth_token")'
    );
    assert.equal(
      globalThis.localStorage.getItem('customer_token'),
      jwtSample,
      'JWT token must be saved to localStorage.getItem("customer_token")'
    );
    assert.equal(
      globalThis.localStorage.getItem('token'),
      jwtSample,
      'JWT token must be saved to localStorage.getItem("token")'
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

// Test 7: Functional verification of switchAuthTab and openRegisterModal
test('openRegisterModal and switchAuthTab toggle active tab and forms cleanly', async () => {
  const domElements = new Map();
  const modalEl = createMockElement('login-modal');
  modalEl.classList.add('hidden');
  const tabLoginBtn = createMockElement('tab-login-btn', 'button');
  const tabRegisterBtn = createMockElement('tab-register-btn', 'button');
  const loginForm = createMockElement('login-form', 'form');
  const registerForm = createMockElement('register-form', 'form');
  registerForm.classList.add('hidden');
  const loginStatusBox = createMockElement('login-status-box');
  const registerStatusBox = createMockElement('register-status-box');
  const registerFullname = createMockElement('register-fullname', 'input');
  const loginUsername = createMockElement('login-username', 'input');

  domElements.set('login-modal', modalEl);
  domElements.set('tab-login-btn', tabLoginBtn);
  domElements.set('tab-register-btn', tabRegisterBtn);
  domElements.set('login-form', loginForm);
  domElements.set('register-form', registerForm);
  domElements.set('login-status-box', loginStatusBox);
  domElements.set('register-status-box', registerStatusBox);
  domElements.set('register-fullname', registerFullname);
  domElements.set('login-username', loginUsername);

  setupMockDocument(domElements);
  globalThis.window = {
    location: { origin: 'http://localhost:3000', href: '' },
    lucide: { createIcons: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {}
  };

  const { openRegisterModal, switchAuthTab } = await import(`../public/js/app.js?t=${Date.now() + 5}`);

  let prevented = false;
  openRegisterModal({ preventDefault: () => { prevented = true; } });

  assert.equal(prevented, true, 'openRegisterModal must call e.preventDefault()');
  assert.equal(modalEl.classList.contains('hidden'), false, '#login-modal must NOT have hidden');
  assert.equal(modalEl.classList.contains('active'), true, '#login-modal must have active class');
  assert.equal(registerForm.classList.contains('hidden'), false, '#register-form must NOT have hidden on register tab');
  assert.equal(loginForm.classList.contains('hidden'), true, '#login-form must be hidden on register tab');
  assert.equal(tabRegisterBtn.getAttribute('aria-selected'), 'true');
  assert.equal(tabLoginBtn.getAttribute('aria-selected'), 'false');

  // Switching back to login tab
  switchAuthTab('login');
  assert.equal(loginForm.classList.contains('hidden'), false, '#login-form must NOT have hidden on login tab');
  assert.equal(registerForm.classList.contains('hidden'), true, '#register-form must be hidden on login tab');
  assert.equal(tabLoginBtn.getAttribute('aria-selected'), 'true');
  assert.equal(tabRegisterBtn.getAttribute('aria-selected'), 'false');
});

// Scenario 4: Successful Registration -> HTTP 200 -> Unlocks button, displays success, pre-fills username
test('Scenario 4 (Integration): Successful customer registration calls /api/v1/auth/register and unlocks button', async () => {
  let receivedBody = null;
  const server = http.createServer(async (req, res) => {
    assert.equal(req.method, 'POST');
    assert.equal(req.url, '/api/v1/auth/register');
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    receivedBody = JSON.parse(Buffer.concat(chunks).toString());

    res.writeHead(200, {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*'
    });
    res.end(JSON.stringify({
      status: 'success',
      message: 'Đăng ký thành công! Chào mừng bạn đến với hệ thống.'
    }));
  });

  const port = await listen(server);
  const serverOrigin = `http://127.0.0.1:${port}`;

  const domElements = new Map();
  const registerForm = createMockElement('register-form', 'form');
  const btnEl = createMockElement('btn-register-submit', 'button');
  btnEl.innerHTML = '<span>Đăng Ký Tài Khoản</span>';
  const fullnameInput = createMockElement('register-fullname', 'input');
  fullnameInput.value = 'Nguyễn Văn Test';
  const usernameInput = createMockElement('register-username', 'input');
  usernameInput.value = '0988776655';
  const passwordInput = createMockElement('register-password', 'input');
  passwordInput.value = 'password123';
  const statusBox = createMockElement('register-status-box');
  const loginUsername = createMockElement('login-username', 'input');

  domElements.set('register-form', registerForm);
  domElements.set('btn-register-submit', btnEl);
  domElements.set('register-fullname', fullnameInput);
  domElements.set('register-username', usernameInput);
  domElements.set('register-password', passwordInput);
  domElements.set('register-status-box', statusBox);
  domElements.set('login-username', loginUsername);

  setupMockDocument(domElements);
  globalThis.window = {
    location: { origin: serverOrigin, href: '' },
    lucide: { createIcons: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {}
  };
  globalThis.fetch = nativeFetch;

  try {
    const { handleRegisterSubmit } = await import(`../public/js/app.js?t=${Date.now() + 6}`);

    let prevented = false;
    const mockEvent = {
      preventDefault: () => { prevented = true; },
      currentTarget: registerForm
    };

    await handleRegisterSubmit(mockEvent);

    assert.equal(prevented, true, 'handleRegisterSubmit must call e.preventDefault()');
    assert.notEqual(receivedBody, null, 'Registration payload must be sent to server');
    assert.equal(receivedBody.username, '0988776655');
    assert.equal(receivedBody.fullName, 'Nguyễn Văn Test');
    assert.equal(receivedBody.password, 'password123');

    // Button unlocked in finally
    assert.equal(btnEl.disabled, false, 'Submit button must be unlocked after registration completes');
    assert.equal(statusBox.classList.contains('hidden'), false, 'Status box must display success message');
    assert.match(statusBox.textContent, /Đăng ký thành công/i, 'Success message must be displayed');
    assert.equal(loginUsername.value, '0988776655', 'Registered username must be pre-filled into login input');
  } finally {
    await stop(server);
  }
});

// Scenario 5: Duplicate registration returns HTTP 400 -> Unlocks button, displays error in #register-status-box
test('Scenario 5 (Integration): Duplicate registration returns HTTP 400 and unlocks button', async () => {
  const server = http.createServer(async (req, res) => {
    assert.equal(req.method, 'POST');
    assert.equal(req.url, '/api/v1/auth/register');
    res.writeHead(400, {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*'
    });
    res.end(JSON.stringify({
      status: 'error',
      message: 'Tên đăng nhập đã tồn tại trong hệ thống'
    }));
  });

  const port = await listen(server);
  const serverOrigin = `http://127.0.0.1:${port}`;

  const domElements = new Map();
  const registerForm = createMockElement('register-form', 'form');
  const btnEl = createMockElement('btn-register-submit', 'button');
  btnEl.innerHTML = '<span>Đăng Ký Tài Khoản</span>';
  const fullnameInput = createMockElement('register-fullname', 'input');
  fullnameInput.value = 'Trùng Lặp';
  const usernameInput = createMockElement('register-username', 'input');
  usernameInput.value = 'existing_user';
  const passwordInput = createMockElement('register-password', 'input');
  passwordInput.value = 'password123';
  const statusBox = createMockElement('register-status-box');

  domElements.set('register-form', registerForm);
  domElements.set('btn-register-submit', btnEl);
  domElements.set('register-fullname', fullnameInput);
  domElements.set('register-username', usernameInput);
  domElements.set('register-password', passwordInput);
  domElements.set('register-status-box', statusBox);

  setupMockDocument(domElements);
  globalThis.window = {
    location: { origin: serverOrigin, href: '' },
    lucide: { createIcons: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {}
  };
  globalThis.fetch = nativeFetch;

  try {
    const { handleRegisterSubmit } = await import(`../public/js/app.js?t=${Date.now() + 7}`);

    const mockEvent = {
      preventDefault: () => {},
      currentTarget: registerForm
    };

    await handleRegisterSubmit(mockEvent);

    assert.equal(btnEl.disabled, false, 'Submit button must be unlocked after registration error');
    assert.equal(statusBox.classList.contains('hidden'), false, 'Status box must display the error message');
    assert.match(statusBox.textContent, /Tên đăng nhập đã tồn tại|Thông tin chưa hợp lệ|Yêu cầu chưa được hoàn tất/i);
  } finally {
    await stop(server);
  }
});

// Scenario 6: Post-login flow retains user on homepage (no redirect), renders User Dropdown Menu, and handleLogout works
test('Scenario 6 (Integration): Post-login flow retains user on index.html, renders User Dropdown in header, and handleLogout clears session', async () => {
  const jwtSample = 'eyJhbGciOiJIUzI1NiJ9.sig_guest_token_888';
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
        role: 'CUSTOMER',
        userId: 42,
        username: 'guest_luxury',
        fullName: 'Nguyễn Văn Aurelia'
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
  const usernameInput = createMockElement('login-username', 'input');
  usernameInput.value = 'guest_luxury';
  const passwordInput = createMockElement('login-password', 'input');
  passwordInput.value = 'Pass@12345';
  const statusBox = createMockElement('login-status-box');
  const modalEl = createMockElement('login-modal');
  modalEl.classList.add('active');

  const headerContainer = createMockElement('header-auth-container');
  const mobileContainer = createMockElement('mobile-auth-container');

  domElements.set('login-modal', modalEl);
  domElements.set('login-form', formEl);
  domElements.set('btn-login-submit', btnEl);
  domElements.set('login-username', usernameInput);
  domElements.set('login-password', passwordInput);
  domElements.set('login-status-box', statusBox);
  domElements.set('header-auth-container', headerContainer);
  domElements.set('mobile-auth-container', mobileContainer);

  setupMockDocument(domElements);
  globalThis.window = {
    location: { origin: serverOrigin, href: 'http://localhost:3000/index.html', reload: () => {} },
    lucide: { createIcons: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {}
  };
  globalThis.fetch = nativeFetch;

  try {
    const { handleLoginSubmit, handleLogout } = await import(`../public/js/app.js?t=${Date.now() + 8}`);

    await handleLoginSubmit({ preventDefault: () => {}, currentTarget: formEl });

    // Assert that location.href was NOT redirected to portal.html
    assert.equal(globalThis.window.location.href, 'http://localhost:3000/index.html', 'Must not redirect away from index.html');

    // Wait for the modal closure timeout (600ms)
    await new Promise(r => setTimeout(r, 650));
    assert.equal(modalEl.classList.contains('hidden'), true, '#login-modal must be closed');

    // Assert Header contains User Dropdown Menu with required items
    assert.match(headerContainer.innerHTML, /Nguyễn Văn Aurelia/, 'Header must display guest name');
    assert.match(headerContainer.innerHTML, /portal\.html/, 'Header must link to Member Gateway');
    assert.match(headerContainer.innerHTML, /portal\.html\?view=dashboard/, 'Header must link to Booking History');
    assert.match(headerContainer.innerHTML, /id="btn-logout"/, 'Header must contain #btn-logout button');

    // Assert Mobile drawer contains user navigation
    assert.match(mobileContainer.innerHTML, /Nguyễn Văn Aurelia/);
    assert.match(mobileContainer.innerHTML, /id="mobile-btn-logout"/);

    // Assert handleLogout clears session and localStorage across all three keys
    assert.equal(globalThis.localStorage.getItem('auth_token'), jwtSample);
    assert.equal(globalThis.localStorage.getItem('customer_token'), jwtSample);
    assert.equal(globalThis.localStorage.getItem('token'), jwtSample);
    handleLogout();
    assert.equal(globalThis.localStorage.getItem('auth_token'), null, 'handleLogout must clear auth_token');
    assert.equal(globalThis.localStorage.getItem('customer_token'), null, 'handleLogout must clear customer_token');
    assert.equal(globalThis.localStorage.getItem('token'), null, 'handleLogout must clear token');
    assert.equal(globalThis.localStorage.getItem('hotel_auth_session'), null, 'handleLogout must clear hotel_auth_session');
  } finally {
    await stop(server);
  }
});

