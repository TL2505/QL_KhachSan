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

// =============================================================================
// Mock DOM Factory for headless Node environment
// =============================================================================
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

  const mockDoc = {
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
    lucide: { createIcons: () => {} }
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
// 1. SOURCE SYNCHRONIZATION AND STATIC SECURITY AUDIT
// =============================================================================
test('100% File synchronization between web-ui/ and web-ui/public/', () => {
  const syncFiles = [
    ['portal.html', 'public/portal.html'],
    ['portal-app.js', 'public/portal-app.js'],
    ['portal.css', 'public/portal.css'],
    ['portal-customer.css', 'public/portal-customer.css'],
    ['portal-theme.css', 'public/portal-theme.css'],
    ['js/view-customer.js', 'public/js/view-customer.js'],
    ['js/customer/tier-helper.js', 'public/js/customer/tier-helper.js'],
    ['js/customer/dashboard.js', 'public/js/customer/dashboard.js'],
    ['js/customer/bookings.js', 'public/js/customer/bookings.js'],
    ['js/customer/profile.js', 'public/js/customer/profile.js'],
    ['js/customer/security.js', 'public/js/customer/security.js']
  ];

  for (const [fileA, fileB] of syncFiles) {
    const pathA = path.join(webUiDir, fileA);
    const pathB = path.join(webUiDir, fileB);
    assert.ok(fs.existsSync(pathA), `File must exist: ${fileA}`);
    assert.ok(fs.existsSync(pathB), `File must exist: ${fileB}`);

    const contentA = fs.readFileSync(pathA, 'utf-8');
    const contentB = fs.readFileSync(pathB, 'utf-8');
    assert.equal(
      contentA.replace(/\r\n/g, '\n'),
      contentB.replace(/\r\n/g, '\n'),
      `Content between ${fileA} and ${fileB} must be 100% identical`
    );
  }
});

test('portal.html: Tailwind CSS configuration is safe against ReferenceError and uses module script', () => {
  for (const file of ['portal.html', 'public/portal.html']) {
    const html = fs.readFileSync(path.join(webUiDir, file), 'utf-8');

    // CDN script is declared before configuration
    const cdnIndex = html.indexOf('cdn.tailwindcss.com');
    const configIndex = html.indexOf('tailwind.config');
    assert.ok(cdnIndex !== -1, `${file} must include Tailwind CDN`);
    assert.ok(configIndex !== -1, `${file} must configure Tailwind`);
    assert.ok(cdnIndex < configIndex, `${file}: CDN must appear before tailwind.config`);

    // Window guard to prevent ReferenceError
    assert.match(html, /window\.tailwind\s*=\s*window\.tailwind\s*\|\|\s*\{\}/, `${file} must guard window.tailwind`);
    assert.match(html, /typeof\s+tailwind\s*!==\s*['"]undefined['"]/, `${file} must safely check typeof tailwind`);

    // Logic script is loaded as ES module
    assert.match(html, /<script\s+type="module"\s+src="portal-app\.js(\?v=[^"]*)?"><\/script>/, `${file} must use script type="module"`);

    // Accessibility attributes
    assert.match(html, /id="portal-nav"[^>]*role="tablist"/, `${file} #portal-nav must have role="tablist"`);
    assert.match(html, /id="view-container"[^>]*role="tabpanel"/, `${file} #view-container must have role="tabpanel"`);
  }
});

// =============================================================================
// 2. LEGACY MEMBERSHIP TIER MAPPING & CALCULATION ENGINE
// =============================================================================
test('getLegacyTierInfo maps tiers and calculates points & spend correctly (1 point = 1,000 VND)', async () => {
  const { getLegacyTierInfo } = await loadModule('public/js/customer/tier-helper.js');

  // Tier 1: Member (< 500 pts)
  const member0 = getLegacyTierInfo(0);
  assert.equal(member0.tier, 'Member');
  assert.equal(member0.discountPct, 5);
  assert.equal(member0.nextTier, 'Silver');
  assert.equal(member0.nextMinPts, 500);
  assert.equal(member0.remainingPts, 500);
  assert.equal(member0.remainingSpend, 500000); // 500 * 1000
  assert.equal(member0.progressPct, 0);

  const member250 = getLegacyTierInfo(250);
  assert.equal(member250.tier, 'Member');
  assert.equal(member250.remainingPts, 250);
  assert.equal(member250.remainingSpend, 250000);
  assert.equal(member250.progressPct, 50);

  // Tier 2: Silver (500 - 999 pts)
  const silver500 = getLegacyTierInfo(500);
  assert.equal(silver500.tier, 'Silver');
  assert.equal(silver500.discountPct, 5);
  assert.equal(silver500.nextTier, 'Gold');
  assert.equal(silver500.nextMinPts, 1000);
  assert.equal(silver500.remainingPts, 500);
  assert.equal(silver500.remainingSpend, 500000);
  assert.equal(silver500.progressPct, 0);

  const silver750 = getLegacyTierInfo(750);
  assert.equal(silver750.tier, 'Silver');
  assert.equal(silver750.progressPct, 50);

  // Tier 3: Gold (1,000 - 4,999 pts)
  const gold1000 = getLegacyTierInfo(1000);
  assert.equal(gold1000.tier, 'Gold');
  assert.equal(gold1000.discountPct, 10);
  assert.equal(gold1000.nextTier, 'VIP');
  assert.equal(gold1000.nextMinPts, 5000);
  assert.equal(gold1000.remainingPts, 4000);
  assert.equal(gold1000.remainingSpend, 4000000);
  assert.equal(gold1000.progressPct, 0);

  const gold3000 = getLegacyTierInfo(3000);
  assert.equal(gold3000.tier, 'Gold');
  assert.equal(gold3000.remainingPts, 2000);
  assert.equal(gold3000.remainingSpend, 2000000);
  assert.equal(gold3000.progressPct, 50);

  // Tier 4: VIP (>= 5,000 pts)
  const vip5000 = getLegacyTierInfo(5000);
  assert.equal(vip5000.tier, 'VIP');
  assert.equal(vip5000.discountPct, 15);
  assert.equal(vip5000.nextTier, null);
  assert.equal(vip5000.remainingPts, 0);
  assert.equal(vip5000.remainingSpend, 0);
  assert.equal(vip5000.progressPct, 100);

  const vip12000 = getLegacyTierInfo(12000, 15000000);
  assert.equal(vip12000.tier, 'VIP');
  assert.equal(vip12000.discountPct, 15);
  assert.equal(vip12000.totalSpent, 15000000);
});

// =============================================================================
// 3. TAB 1: DASHBOARD MODULE (DIGITAL MEMBER CARD & SUMMARY STATS)
// =============================================================================
test('Dashboard module renders Digital Member Card, Tier Progress, and 3 Summary Stats', async () => {
  const env = setupMockEnvironment();
  try {
    const { renderDashboard } = await loadModule('public/js/customer/dashboard.js');
    const container = env.getOrCreate('view-container');

    const sampleSession = {
      userId: 42,
      username: 'aurelia_vip',
      fullName: 'Trần Hoàng Long',
      role: 'CUSTOMER'
    };

    await renderDashboard(container, sampleSession);

    const html = container.innerHTML;
    assert.match(html, /Trần Hoàng Long/, 'Must display member full name in greeting');
    assert.match(html, /id="member-card-wrapper"/, 'Must contain member-card-wrapper');
    assert.match(html, /id="member-stats-wrapper"/, 'Must contain member-stats-wrapper');
    assert.match(html, /id="member-summary-stats"/, 'Must contain member-summary-stats');
  } finally {
    env.teardown();
  }
});

// =============================================================================
// 4. TAB 2: BOOKING HISTORY MODULE (TABLE, FIELDS & SEARCH)
// =============================================================================
test('Bookings module renders booking history table with required columns and filter input', async () => {
  const env = setupMockEnvironment();
  try {
    const { renderBookings } = await loadModule('public/js/customer/bookings.js');
    const container = env.getOrCreate('view-container');

    await renderBookings(container, { userId: 42, role: 'CUSTOMER' });

    const html = container.innerHTML;
    assert.match(html, /Lịch Sử Đặt Phòng/, 'Must render booking history header');
    assert.match(html, /Mã Đặt Phòng/, 'Must include Booking ID column');
    assert.match(html, /Hạng Phòng/, 'Must include Room Type column');
    assert.match(html, /Ngày Check-in/, 'Must include Check-in column');
    assert.match(html, /Ngày Check-out/, 'Must include Check-out column');
    assert.match(html, /Tổng Thanh Toán/, 'Must include Total Amount column');
    assert.match(html, /Trạng Thái/, 'Must include Status column');
    assert.match(html, /id="filter-booking-input"/, 'Must have filter search input');
  } finally {
    env.teardown();
  }
});

// =============================================================================
// 5. TAB 3: PROFILE SETTINGS MODULE (FORM FIELDS & BUTTON LIFECYCLE)
// =============================================================================
test('Profile module contains all required input IDs and btn-save-profile re-enables reliably in try...finally', async () => {
  const env = setupMockEnvironment();
  try {
    const { renderProfile } = await loadModule('public/js/customer/profile.js');
    const container = env.getOrCreate('view-container');

    const session = {
      userId: 77,
      username: 'guest_test',
      fullName: 'Nguyễn Văn An',
      phone: '0901234567',
      email: 'an.nguyen@example.com',
      identityCard: '079090001234',
      dateOfBirth: '1990-05-15',
      address: '123 Lê Lợi, Quận 1, TP.HCM'
    };

    await renderProfile(container, session);

    // Verify all mandatory element IDs
    const form = env.getOrCreate('profile-form');
    const fullname = env.getOrCreate('profile-fullname');
    const phone = env.getOrCreate('profile-phone');
    const email = env.getOrCreate('profile-email');
    const idcard = env.getOrCreate('profile-idcard');
    const dob = env.getOrCreate('profile-dob');
    const address = env.getOrCreate('profile-address');
    const statusBox = env.getOrCreate('profile-status-box');
    const btn = env.getOrCreate('btn-save-profile');

    assert.ok(form, '#profile-form must exist');
    assert.ok(fullname, '#profile-fullname must exist');
    assert.ok(phone, '#profile-phone must exist');
    assert.ok(email, '#profile-email must exist');
    assert.ok(idcard, '#profile-idcard must exist');
    assert.ok(dob, '#profile-dob must exist');
    assert.ok(address, '#profile-address must exist');
    assert.ok(statusBox, '#profile-status-box must exist');
    assert.ok(btn, '#btn-save-profile must exist');

    // Verify pre-fill
    assert.equal(fullname.value, 'Nguyễn Văn An');
    assert.equal(phone.value, '0901234567');
    assert.equal(email.value, 'an.nguyen@example.com');
    assert.equal(idcard.value, '079090001234');
    assert.equal(dob.value, '1990-05-15');
    assert.equal(address.value, '123 Lê Lợi, Quận 1, TP.HCM');

    // Trigger form submit and verify button re-enabling
    fullname.value = 'Nguyễn Văn An Đã Đổi';
    await form.dispatchEvent({ type: 'submit', preventDefault: () => {} });

    // Wait microtasks
    await new Promise(r => setTimeout(r, 50));

    // Button MUST NOT be stuck in disabled state
    assert.equal(btn.disabled, false, '#btn-save-profile must be re-enabled after submission');
    assert.equal(btn.getAttribute('aria-busy'), null, 'aria-busy must be cleared');
    assert.match(statusBox.innerHTML, /thành công/i, 'Status box must display confirmation');
  } finally {
    env.teardown();
  }
});

// =============================================================================
// 6. TAB 4: SECURITY & PASSWORD MODULE (VALIDATION & BUTTON LIFECYCLE)
// =============================================================================
test('Security module validates passwords properly and btn-save-password re-enables in try...finally', async () => {
  const env = setupMockEnvironment();
  try {
    const { renderSecurity } = await loadModule('public/js/customer/security.js');
    const container = env.getOrCreate('view-container');

    const session = { userId: 77, username: 'guest_test' };
    await renderSecurity(container, session);

    // Verify required form elements
    const form = env.getOrCreate('password-form');
    const currentPass = env.getOrCreate('current-password');
    const newPass = env.getOrCreate('new-password');
    const confirmPass = env.getOrCreate('confirm-password');
    const statusBox = env.getOrCreate('password-status-box');
    const btn = env.getOrCreate('btn-save-password');

    assert.ok(form, '#password-form must exist');
    assert.ok(currentPass, '#current-password must exist');
    assert.ok(newPass, '#new-password must exist');
    assert.ok(confirmPass, '#confirm-password must exist');
    assert.ok(statusBox, '#password-status-box must exist');
    assert.ok(btn, '#btn-save-password must exist');

    // Test 1: Empty current password validation
    currentPass.value = '';
    newPass.value = 'Secret123';
    confirmPass.value = 'Secret123';
    await form.dispatchEvent({ type: 'submit', preventDefault: () => {} });
    assert.match(statusBox.innerHTML, /mật khẩu hiện tại/i, 'Must report missing current password');
    assert.equal(btn.disabled, false, 'Button must remain enabled on validation error');

    // Test 2: New password < 6 characters validation
    currentPass.value = 'OldPassword123';
    newPass.value = '123';
    confirmPass.value = '123';
    await form.dispatchEvent({ type: 'submit', preventDefault: () => {} });
    assert.match(statusBox.innerHTML, /ít nhất 6 ký tự/i, 'Must report password length < 6');
    assert.equal(btn.disabled, false, 'Button must remain enabled on length validation error');

    // Test 3: Password mismatch validation
    currentPass.value = 'OldPassword123';
    newPass.value = 'Secret123';
    confirmPass.value = 'SecretMismatch';
    await form.dispatchEvent({ type: 'submit', preventDefault: () => {} });
    assert.match(statusBox.innerHTML, /không khớp/i, 'Must report password mismatch');
    assert.equal(btn.disabled, false, 'Button must remain enabled on mismatch error');

    // Test 4: Valid submission lifecycle
    currentPass.value = 'OldPassword123';
    newPass.value = 'NewPass987!';
    confirmPass.value = 'NewPass987!';
    await form.dispatchEvent({ type: 'submit', preventDefault: () => {} });

    await new Promise(r => setTimeout(r, 60));

    // Submit button MUST reliably re-enable
    assert.equal(btn.disabled, false, '#btn-save-password must be re-enabled after operation');
    assert.equal(btn.getAttribute('aria-busy'), null, 'aria-busy must be cleared');
  } finally {
    env.teardown();
  }
});

// =============================================================================
// 7. AUTH GUARD & VIEW ROUTING VERIFICATION
// =============================================================================
test('portal-app.js configures 5 customer tabs and handles view routing cleanly', async () => {
  const portalAppSource = fs.readFileSync(path.join(webUiDir, 'portal-app.js'), 'utf-8');

  // Verify menus.CUSTOMER contains all 5 required modules
  assert.match(portalAppSource, /\['dashboard',\s*'Tổng quan & Hạng thẻ'/);
  assert.match(portalAppSource, /\['bookings',\s*'Lịch sử đặt phòng'/);
  assert.match(portalAppSource, /\['concierge',\s*'Dịch vụ & Ví ưu đãi'/);
  assert.match(portalAppSource, /\['profile',\s*'Thông tin cá nhân'/);
  assert.match(portalAppSource, /\['security',\s*'Bảo mật & Mật khẩu'/);

  // Verify Auth Guard logic
  assert.match(portalAppSource, /localStorage\.getItem\(['"]auth_token['"]\)/, 'Auth Guard must check auth_token');
  assert.match(portalAppSource, /index\.html#login/, 'Auth Guard must redirect unauthenticated users to index.html#login');
  assert.match(portalAppSource, /appView\.classList\.remove\(['"]hidden['"],\s*['"]opacity-0['"]\)/, 'Must immediately unhide appView on token presence');
});

// =============================================================================
// 8. TAB 5: CONCIERGE & VOUCHER WALLET MODULE (FORM & BUTTON LIFECYCLE)
// =============================================================================
test('Concierge module renders Voucher Wallet and form with reliable button lifecycle', async () => {
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
    assert.match(html, /Ví Voucher & Mã Giảm Giá/, 'Must render Voucher Wallet');
    assert.match(html, /LUXURY2026/, 'Must include LUXURY2026 voucher');
    assert.match(html, /VIP5STAR/, 'Must include VIP5STAR voucher');
    assert.match(html, /Yêu Cầu Dịch Vụ Trực Tuyến/, 'Must render Concierge request header');

    const form = env.getOrCreate('concierge-form');
    const room = env.getOrCreate('concierge-room');
    const category = env.getOrCreate('concierge-category');
    const notes = env.getOrCreate('concierge-notes');
    const btn = env.getOrCreate('btn-submit-concierge');

    assert.ok(form, '#concierge-form must exist');
    assert.ok(room, '#concierge-room must exist');
    assert.ok(category, '#concierge-category must exist');
    assert.ok(notes, '#concierge-notes must exist');
    assert.ok(btn, '#btn-submit-concierge must exist');

    room.value = '501';
    notes.value = 'Bổ sung thêm 2 khăn tắm';
    await form.dispatchEvent({ type: 'submit', preventDefault: () => {} });

    await new Promise(r => setTimeout(r, 60));
    assert.equal(btn.disabled, false, '#btn-submit-concierge must be re-enabled after submission');
    assert.equal(btn.getAttribute('aria-busy'), null, 'aria-busy must be cleared');
  } finally {
    env.teardown();
  }
});

