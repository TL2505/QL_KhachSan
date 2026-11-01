import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const webUiDir = path.resolve(__dirname, '..');

// Helper to mock DOM environment for testing components
function setupMockEnv() {
  const store = new Map();
  const localStorageMock = {
    getItem: (k) => store.get(k) ?? null,
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
    clear: () => store.clear(),
    key: (i) => Array.from(store.keys())[i] ?? null,
    get length() { return store.size; }
  };

  const elements = new Map();
  const createElement = (id, tag = 'div') => {
    const el = {
      id,
      tagName: tag.toUpperCase(),
      dataset: {},
      attributes: {},
      classList: {
        classes: new Set(),
        add(...cls) { cls.forEach(c => this.classes.add(c)); },
        remove(...cls) { cls.forEach(c => this.classes.delete(c)); },
        contains(c) { return this.classes.has(c); },
        toggle(c, force) {
          if (force === undefined) {
            this.classes.has(c) ? this.classes.delete(c) : this.classes.add(c);
          } else if (force) {
            this.classes.add(c);
          } else {
            this.classes.delete(c);
          }
        }
      },
      style: {},
      children: [],
      value: '',
      disabled: false,
      innerHTML: '',
      textContent: '',
      setAttribute(k, v) { this.attributes[k] = String(v); },
      getAttribute(k) { return this.attributes[k] ?? null; },
      removeAttribute(k) { delete this.attributes[k]; },
      hasAttribute(k) { return k in this.attributes; },
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => true,
      focus: () => {}
    };
    return el;
  };

  const getOrCreate = (id, tag = 'div') => {
    let el = elements.get(id);
    if (!el) {
      el = createElement(id, tag);
      elements.set(id, el);
    }
    return el;
  };

  const htmlDocEl = createElement('html', 'html');
  const doc = {
    createElement: (tag) => getOrCreate('dynamic-' + Math.random().toString(36).slice(2, 8), tag),
    getElementById: (id) => getOrCreate(id),
    querySelector: (sel) => {
      if (sel && sel.startsWith('#')) return getOrCreate(sel.slice(1));
      return null;
    },
    querySelectorAll: () => [],
    body: getOrCreate('body', 'body'),
    documentElement: htmlDocEl
  };

  const originalWindow = globalThis.window;
  const originalDoc = globalThis.document;
  const originalStorage = globalThis.localStorage;
  const originalFetch = globalThis.fetch;

  globalThis.localStorage = localStorageMock;
  globalThis.document = doc;
  globalThis.window = {
    location: {
      origin: 'http://localhost:3000',
      hostname: 'localhost',
      protocol: 'http:',
      port: '3000',
      replace: () => {}
    },
    localStorage: localStorageMock,
    document: doc,
    addEventListener: () => {},
    removeEventListener: () => {},
    matchMedia: () => ({ matches: false, addEventListener: () => {} }),
    lucide: { createIcons: () => {} }
  };

  return {
    store,
    elements,
    getOrCreate,
    teardown: () => {
      globalThis.window = originalWindow;
      globalThis.document = originalDoc;
      globalThis.localStorage = originalStorage;
      globalThis.fetch = originalFetch;
    }
  };
}

// =============================================================================
// TEST SUITE: PHASE 3 B2C MEMBER BOOKING PORTAL (GUEST WEB PORTAL)
// =============================================================================

test('Phase 3: calculateInclusivePricing accurately computes Gross = BaseRate * (1 - Discount) * 1.05 * 1.08', async () => {
  const { calculateInclusivePricing } = await import('../js/customer/bookings.js');

  // Case 1: Member Tier (0% discount)
  // Base 1,000,000 * (1 - 0.00) * 1.05 * 1.08 = 1,134,000
  const memberQuote = calculateInclusivePricing(1000000, 1, 'Member');
  assert.equal(memberQuote.subtotal, 1000000);
  assert.equal(memberQuote.serviceCharge, 50000); // 5%
  assert.equal(memberQuote.vat, 84000);          // 8% of (1,000,000 + 50,000)
  assert.equal(memberQuote.gross, 1134000);
  assert.equal(memberQuote.total, 1134000);
  assert.equal(memberQuote.discountApplied, false);

  // Case 2: Silver Tier (5% discount)
  // Base 1,000,000 * (1 - 0.05) = 950,000
  // 950,000 * 1.05 * 1.08 = 1,077,300
  const silverQuote = calculateInclusivePricing(1000000, 1, 'Silver');
  assert.equal(silverQuote.subtotal, 950000);
  assert.equal(silverQuote.serviceCharge, 47500);
  assert.equal(silverQuote.vat, 79800);
  assert.equal(silverQuote.gross, 1077300);
  assert.equal(silverQuote.total, 1077300);
  assert.equal(silverQuote.discountApplied, true);

  // Case 3: Gold Tier (10% discount)
  // Base 2,000,000 * (1 - 0.10) = 1,800,000
  // 1,800,000 * 1.05 * 1.08 = 2,041,200
  const goldQuote = calculateInclusivePricing(2000000, 1, 'Gold');
  assert.equal(goldQuote.subtotal, 1800000);
  assert.equal(goldQuote.gross, 2041200);
  assert.equal(goldQuote.discountApplied, true);

  // Case 4: VIP Tier (15% discount)
  // Base 5,000,000 * (1 - 0.15) = 4,250,000
  // 4,250,000 * 1.05 * 1.08 = 4,819,500
  const vipQuote = calculateInclusivePricing(5000000, 1, 'VIP');
  assert.equal(vipQuote.subtotal, 4250000);
  assert.equal(vipQuote.gross, 4819500);
  assert.equal(vipQuote.discountApplied, true);

  // Case 5: Multiple nights (3 nights VIP)
  // Base 5,000,000 * 3 * (1 - 0.15) = 12,750,000
  // 12,750,000 * 1.05 * 1.08 = 14,458,500
  const multiNightVip = calculateInclusivePricing(5000000, 3, 'VIP');
  assert.equal(multiNightVip.subtotal, 12750000);
  assert.equal(multiNightVip.gross, 14458500);
});

test('Phase 3: fetchAvailableRooms calls GET /api/v1/rooms/available via dynamic API base URL', async () => {
  const env = setupMockEnv();
  try {
    const { fetchAvailableRooms } = await import('../js/customer/bookings.js');

    let requestedUrl = null;
    let requestedMethod = null;
    let requestedHeaders = null;

    globalThis.fetch = async (url, options) => {
      requestedUrl = url;
      requestedMethod = options.method;
      requestedHeaders = options.headers;
      return new Response(JSON.stringify({
        status: 'success',
        data: [
          { id: 101, room_number: '101', room_type: 'Deluxe Ocean View', price: 2500000 }
        ]
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    };

    const rooms = await fetchAvailableRooms('2026-11-01', '2026-11-03', '2');

    assert.ok(requestedUrl, 'Fetch must have been called');
    assert.match(requestedUrl, /\/api\/v1\/rooms\/available/, 'Endpoint must be /api/v1/rooms/available');
    assert.match(requestedUrl, /checkInDate=2026-11-01/, 'Query params must contain checkInDate');
    assert.match(requestedUrl, /checkOutDate=2026-11-03/, 'Query params must contain checkOutDate');
    assert.match(requestedUrl, /roomTypeId=2/, 'Query params must contain roomTypeId');
    assert.equal(requestedMethod, 'GET');
    assert.equal(requestedHeaders['Accept'], 'application/json');
    assert.equal(rooms.status, 'success');
    assert.equal(rooms.data.length, 1);
  } finally {
    env.teardown();
  }
});

test('Phase 3: submitPublicGuestBooking sets pending status and includes X-Idempotency-Key header', async () => {
  const env = setupMockEnv();
  try {
    const { submitPublicGuestBooking } = await import('../js/customer/bookings.js');

    let requestBody = null;
    let requestHeaders = null;
    let requestMethod = null;

    globalThis.fetch = async (url, options) => {
      requestMethod = options.method;
      requestHeaders = options.headers;
      requestBody = JSON.parse(options.body);
      return new Response(JSON.stringify({
        status: 'success',
        booking_code: 'AUR-20261101-998811',
        data: {
          id: 456,
          status: 'pending',
          paymentStatus: 'pending',
          vietqr_url: 'https://img.vietqr.io/image/970422-AUR20261101-compact.png'
        }
      }), { status: 201, headers: { 'Content-Type': 'application/json' } });
    };

    const bookingPayload = {
      roomTypeId: 3,
      fullName: 'Trần Gia Bảo',
      phone: '0912345678',
      email: 'giabao@example.com',
      checkInDate: '2026-11-01 14:00:00',
      checkOutDate: '2026-11-03 12:00:00',
      specialRequests: 'High floor, feather pillows',
      status: 'confirmed', // Attempt to spoof confirmed status
      paymentStatus: 'paid' // Attempt to spoof paid status
    };

    const result = await submitPublicGuestBooking(bookingPayload);

    // Invariant 1 (P0) verification: Client MUST enforce status: pending and paymentStatus: pending
    assert.equal(requestBody.status, 'pending', 'Submitted status MUST be forced to pending');
    assert.equal(requestBody.paymentStatus, 'pending', 'Submitted paymentStatus MUST be forced to pending');
    assert.equal(requestMethod, 'POST');

    // Header check: X-Idempotency-Key must be present and formatted as UUID v4
    const idempotencyKey = requestHeaders['X-Idempotency-Key'];
    assert.ok(idempotencyKey, 'Must include X-Idempotency-Key header');
    const uuidV4Regex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
    assert.match(idempotencyKey, uuidV4Regex, 'X-Idempotency-Key must be a valid UUID v4');

    assert.equal(result.status, 'success');
    assert.equal(result.data.status, 'pending');
  } finally {
    env.teardown();
  }
});

test('Phase 3: renderDepartureActions disables late checkout button when isBackToBack == true', async () => {
  const env = setupMockEnv();
  try {
    const { renderDepartureActions } = await import('../js/customer/bookings.js');
    const container = env.getOrCreate('departure-actions-container');

    // Scenario A: isBackToBack is true
    const backToBackBooking = {
      id: 88,
      roomNumber: '201',
      checkOutDate: '2026-11-02',
      isBackToBack: true
    };

    renderDepartureActions(backToBackBooking, container);
    const htmlA = container.innerHTML;

    assert.match(htmlA, /Thông báo từ Aurelia Concierge/, 'Must display concierge notice for back-to-back');
    assert.match(htmlA, /12:00 PM/, 'Notice must specify sanitization deadline before 12:00 PM');
    assert.match(htmlA, /id="back-to-back-notice"/, 'Must contain #back-to-back-notice container');
    assert.match(htmlA, /id="btn-request-late-checkout"[^>]*aria-disabled="true"/, 'Late checkout button must have aria-disabled="true"');
    assert.match(htmlA, /id="btn-request-late-checkout"[^>]*disabled/, 'Late checkout button must be disabled');

    // Scenario B: isBackToBack is false
    const normalBooking = {
      id: 89,
      roomNumber: '202',
      checkOutDate: '2026-11-05',
      isBackToBack: false
    };

    renderDepartureActions(normalBooking, container);
    const htmlB = container.innerHTML;

    assert.doesNotMatch(htmlB, /id="back-to-back-notice"/, 'Notice must NOT appear when not back-to-back');
    assert.doesNotMatch(htmlB, /aria-disabled="true"/, 'Late checkout button must NOT be aria-disabled when not back-to-back');
  } finally {
    env.teardown();
  }
});

test('Phase 3: renderEarlyArrivalCard displays 5-Star sanitization card, luggage QR pass and lounge pass', async () => {
  const env = setupMockEnv();
  try {
    const { renderEarlyArrivalCard } = await import('../js/customer/bookings.js');
    const container = env.getOrCreate('early-arrival-container');

    const earlyBooking = {
      id: 99,
      customerName: 'Trần Quốc Bảo',
      roomNumber: '305',
      checkInDate: '2026-11-02'
    };

    renderEarlyArrivalCard(earlyBooking, container);
    const html = container.innerHTML;

    // 1. Sanitization status
    assert.match(html, /Quy trình chuẩn bị 5 sao/, 'Must display 5-star sanitization process badge');
    assert.match(html, /Phòng Đang Được Khử Khuẩn & Tiệt Trùng Toàn Diện/, 'Must announce room sanitization underway');
    assert.match(html, /14:00/, 'Must mention scheduled room handover at 14:00');

    // 2. Luggage QR Pass
    assert.match(html, /Thẻ Ký Gửi Hành Lý Ưu Tiên/, 'Must provide priority luggage storage pass');
    assert.match(html, /AUR-LUG-99-/, 'Must provide unique luggage token');
    assert.match(html, /alt="Luggage Pass QR"/, 'Must provide QR pass image element');

    // 3. Lobby Lounge Invitation
    assert.match(html, /Thưởng Trà & Bánh Macaron Tại Sảnh/, 'Must issue complimentary lounge tea & macaron pass');
    assert.match(html, /Aurelia Lobby Lounge/, 'Must name Aurelia Lobby Lounge venue');
    assert.match(html, /COMPLIMENTARY/, 'Must denote complimentary hospitality perk');
  } finally {
    env.teardown();
  }
});

test('Phase 3: submitGuestPreferences submits pre-arrival concierge preferences to API', async () => {
  const env = setupMockEnv();
  try {
    const { submitGuestPreferences } = await import('../js/customer/bookings.js');

    let dispatchedUrl = null;
    let dispatchedBody = null;

    globalThis.fetch = async (url, options) => {
      dispatchedUrl = url;
      dispatchedBody = JSON.parse(options.body);
      return new Response(JSON.stringify({
        status: 'success',
        message: 'Guest preferences recorded successfully'
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    };

    const preferences = {
      pillowType: 'MEMORY_FOAM',
      welcomeDrink: 'CHAMPAGNE_DOM_PERIGNON',
      airportLimousine: true,
      flightNumber: 'VN-123',
      dietaryAllergies: ['SHELLFISH', 'PEANUTS']
    };

    const res = await submitGuestPreferences(101, preferences);

    assert.match(dispatchedUrl, /\/api\/v1\/guests\/preferences/, 'Must POST to /api/v1/guests/preferences');
    assert.equal(dispatchedBody.bookingId, 101);
    assert.equal(dispatchedBody.pillowType, 'MEMORY_FOAM');
    assert.equal(dispatchedBody.airportLimousine, true);
    assert.equal(dispatchedBody.flightNumber, 'VN-123');
    assert.deepEqual(dispatchedBody.dietaryAllergies, ['SHELLFISH', 'PEANUTS']);
    assert.equal(res.status, 'success');
  } finally {
    env.teardown();
  }
});

test('Phase 3: Invariant 6 (P1) - Session Harmonization synchronizes the 4 required localStorage keys', async () => {
  const env = setupMockEnv();
  try {
    const { completeLogin } = await import('../portal-app.js');

    const authResult = {
      token: 'jwt-aurelia-token-xyz',
      role: 'CUSTOMER',
      userId: 77,
      customerId: 33,
      username: 'member_aurelia',
      fullName: 'Hoàng Minh Châu'
    };

    completeLogin(authResult, 'member_aurelia');

    // Invariant 6: Ensure customer_token, auth_token, token, hotel_auth_session are synchronized
    assert.equal(env.store.get('customer_token'), 'jwt-aurelia-token-xyz', 'customer_token must be set');
    assert.equal(env.store.get('auth_token'), 'jwt-aurelia-token-xyz', 'auth_token must be set');
    assert.equal(env.store.get('token'), 'jwt-aurelia-token-xyz', 'token must be set');

    const rawSession = env.store.get('hotel_auth_session');
    assert.ok(rawSession, 'hotel_auth_session must be set');
    const parsed = JSON.parse(rawSession);
    assert.equal(parsed.token, 'jwt-aurelia-token-xyz');
    assert.equal(parsed.userId, 77);
    assert.equal(parsed.customerId, 33);
  } finally {
    env.teardown();
  }
});

test('Phase 3: Invariant 1 (P0) & Invariant 4 (P1) - Zero synchronous alert() and form onsubmit="return false;"', () => {
  const filesToCheck = [
    'portal.html',
    'portal-app.js',
    'js/portal-app.js',
    'js/customer/bookings.js'
  ];

  for (const rel of filesToCheck) {
    const filePath = path.join(webUiDir, rel);
    const content = fs.readFileSync(filePath, 'utf-8');

    // Strip comments before verifying no synchronous alert() calls
    const codeWithoutComments = content.replace(/\/\*[\s\S]*?\*\/|\/\/.*/g, '');
    assert.doesNotMatch(codeWithoutComments, /\balert\s*\(/, `${rel} must not call synchronous alert()`);

    // Invariant 1: Any <form> tags must specify onsubmit="return false;"
    const formTags = content.match(/<form\b[^>]*>/g) || [];
    for (const formTag of formTags) {
      assert.match(
        formTag,
        /onsubmit=["']return false;?["']/i,
        `<form> tag in ${rel} must have onsubmit="return false;": ${formTag}`
      );
    }
  }
});
