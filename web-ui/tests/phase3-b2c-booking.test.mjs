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

test('Phase 3: booking history uses the server-backed B portal module', async () => {
 const {renderBookings}=await import('../js/customer/bookings.js'); assert.equal(typeof renderBookings,'function'); const backend=fs.readFileSync(path.join(webUiDir,'..','src/quanlykhachsan/backend/booking/PublicBookingService.java'),'utf8'); assert.match(backend,/total_price/); assert.match(backend,/status/);
});
test('Phase 3: B booking transport targets the versioned availability endpoint', async () => {
 const {api}=await import('../js/booking-api.js'); let url; globalThis.fetch=async(u,o)=>{url=u;assert.equal(o.method,'GET');return new Response(JSON.stringify({status:'success',data:[]}),{status:200});}; assert.deepEqual(await api('/rooms/availability?check_in=2026-11-01'),[]); assert.equal(url,'/api/v1/rooms/availability?check_in=2026-11-01');
});
test('Phase 3: B booking transport supports idempotency keys', async () => {
 const {api,bookingKey}=await import('../js/booking-api.js'); const memory=new Map(); globalThis.sessionStorage={getItem:k=>memory.get(k)??null,setItem:(k,v)=>memory.set(k,String(v))}; const payload={roomId:101,checkInDate:'2026-11-01',checkOutDate:'2026-11-03'}, key=bookingKey(payload); assert.match(key,/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i); let request; globalThis.fetch=async(url,options)=>{request={url,options};return new Response(JSON.stringify({status:'success',data:{id:10,status:'pending'}}),{status:200});}; const result=await api('/bookings/public',{method:'POST',body:payload,key}); assert.equal(request.url,'/api/v1/bookings/public'); assert.equal(request.options.headers['Idempotency-Key'],key); assert.equal(result.status,'pending');
});
test('Phase 3: B booking history presents server-backed stay actions', async () => {
 const {renderBookings}=await import('../js/customer/bookings.js'); assert.equal(typeof renderBookings,'function'); const source=fs.readFileSync(path.join(webUiDir,'js/customer/bookings.js'),'utf8'); assert.match(source,/customer-qr/); assert.match(source,/customer-portal\/bookings/);
});
test('Phase 3: B booking history provides QR payment and check-in actions', async () => {
 const {renderBookings}=await import('../js/customer/bookings.js'); assert.equal(typeof renderBookings,'function'); const source=fs.readFileSync(path.join(webUiDir,'js/customer/bookings.js'),'utf8'); assert.match(source,/checkin-passes/); assert.match(source,/payment-qr/);
});
test('Phase 3: B booking portal keeps concierge chat as its supported guest channel', () => {
 const chat=fs.readFileSync(path.join(webUiDir,'js/concierge-chat.js'),'utf8'); assert.match(chat,/export function initConciergeChat/); assert.match(chat,/ai\/concierge\/chat/);
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

test('Phase 3: B portal forms prevent native submission and synchronous alerts', () => {
 for(const rel of ['portal.html','portal-app.js','js/customer/bookings.js','js/staff/room-map.js']) { const content=fs.readFileSync(path.join(webUiDir,rel),'utf8'), code=content.replace(/\/\*[\s\S]*?\*\/|\/\/.*/g,''); assert.doesNotMatch(code,/\balert\s*\(/); for(const form of content.match(/<form\b[^>]*>/g)||[]) if (!/method=["']dialog["']/i.test(form)) assert.match(form,/onsubmit=["']return false;?["']/i); } assert.match(fs.readFileSync(path.join(webUiDir,'js/staff/room-map.js'),'utf8'),/<form id="form-quick-booking" onsubmit="return false;">/);
});
