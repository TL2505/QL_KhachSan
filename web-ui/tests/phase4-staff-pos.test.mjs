import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const webUiDir = path.resolve(__dirname, '..');

// Helper to set up mock DOM
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
  const eventListeners = new Map();

  const createElement = (id, tag = 'div') => {
    const listeners = new Map();
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
      value: '',
      disabled: false,
      get className() {
        return Array.from(this.classList.classes).join(' ');
      },
      set className(val) {
        this.classList.classes.clear();
        String(val || '').split(/\s+/).filter(Boolean).forEach(c => this.classList.classes.add(c));
      },
      innerHTML: '',
      textContent: '',
      setAttribute(k, v) { this.attributes[k] = String(v); },
      getAttribute(k) { return this.attributes[k] ?? null; },
      removeAttribute(k) { delete this.attributes[k]; },
      hasAttribute(k) { return k in this.attributes; },
      addEventListener(evt, handler) {
        if (!listeners.has(evt)) listeners.set(evt, []);
        listeners.get(evt).push(handler);
      },
      removeEventListener(evt, handler) {
        if (!listeners.has(evt)) return;
        const arr = listeners.get(evt).filter(h => h !== handler);
        listeners.set(evt, arr);
      },
      dispatchEvent(evt) {
        const arr = listeners.get(evt.type) || [];
        arr.forEach(h => h(evt));
        return !evt.defaultPrevented;
      },
      click() {
        this.dispatchEvent({ type: 'click', target: this, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } });
      },
      focus() {
        if (globalThis.document) {
          globalThis.document.activeElement = this;
        }
      },
      select() {
        this.selected = true;
      }
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

  const doc = {
    activeElement: null,
    createElement: (tag) => {
      const generatedId = 'dynamic-' + Math.random().toString(36).slice(2, 9);
      const el = createElement(generatedId, tag);
      elements.set(generatedId, el);
      return el;
    },
    getElementById: (id) => elements.get(id) || null,
    querySelector: (sel) => {
      if (sel && sel.startsWith('#')) return elements.get(sel.slice(1)) || null;
      for (const el of elements.values()) {
        if (sel === `[data-view="${el.dataset?.view}"]`) return el;
        if (sel === `[data-action="${el.dataset?.action}"]`) return el;
        if (sel.includes('.modal') && el.classList.contains('modal')) return el;
      }
      return null;
    },
    querySelectorAll: (sel) => {
      const results = [];
      for (const el of elements.values()) {
        if (sel.includes('modal') && (el.classList.contains('modal') || el.classList.contains('modal-overlay'))) {
          results.push(el);
        }
      }
      return results;
    },
    body: createElement('body', 'body')
  };

  const windowListeners = new Map();
  const win = {
    location: { origin: 'http://localhost:3000', hostname: 'localhost', href: '' },
    localStorage: localStorageMock,
    document: doc,
    addEventListener: (evt, handler) => {
      if (!windowListeners.has(evt)) windowListeners.set(evt, []);
      windowListeners.get(evt).push(handler);
    },
    removeEventListener: (evt, handler) => {
      if (!windowListeners.has(evt)) return;
      const arr = windowListeners.get(evt).filter(h => h !== handler);
      windowListeners.set(evt, arr);
    },
    dispatchEvent: (evt) => {
      const arr = windowListeners.get(evt.type) || [];
      arr.forEach(h => h(evt));
      return !evt.defaultPrevented;
    },
    lucide: { createIcons: () => {} },
    showToast: () => {},
    showCustomAlert: () => {},
    closeModal: () => {}
  };

  const origWin = globalThis.window;
  const origDoc = globalThis.document;
  const origStorage = globalThis.localStorage;

  globalThis.window = win;
  globalThis.document = doc;
  globalThis.localStorage = localStorageMock;

  return {
    store,
    elements,
    getOrCreate,
    triggerWindowKey: (keyEvent) => {
      const arr = windowListeners.get('keydown') || [];
      let defaultPrevented = false;
      const ev = {
        ...keyEvent,
        defaultPrevented: false,
        preventDefault() { defaultPrevented = true; this.defaultPrevented = true; }
      };
      arr.forEach(h => h(ev));
      return { defaultPrevented };
    },
    teardown: () => {
      globalThis.window = origWin;
      globalThis.document = origDoc;
      globalThis.localStorage = origStorage;
    }
  };
}

// =============================================================================
// 1. CSS VISUAL SPECIFICATION AUDIT: portal-admin.css
// =============================================================================
test('Phase 4: portal-admin.css defines purplePulseAnimation and turnover-rush-pulse', () => {
  const cssPath = path.join(webUiDir, 'portal-admin.css');
  const css = fs.readFileSync(cssPath, 'utf8');

  // Verify @keyframes purplePulseAnimation
  assert.match(css, /@keyframes\s+purplePulseAnimation/, 'Must contain @keyframes purplePulseAnimation');
  assert.match(css, /rgba\(124,\s*58,\s*237,\s*0\.3\)/, '0%, 100% box-shadow purple');
  assert.match(css, /rgba\(168,\s*85,\s*247,\s*0\.6\)/, '0%, 100% border-color purple');
  assert.match(css, /rgba\(124,\s*58,\s*237,\s*0\.65\)/, '50% box-shadow purple');
  assert.match(css, /rgba\(192,\s*132,\s*252,\s*0\.9\)/, '50% border-color purple');

  // Verify .turnover-rush-pulse
  assert.match(css, /\.turnover-rush-pulse\s*\{\s*animation:\s*purplePulseAnimation\s+2\.2s\s+infinite\s+ease-in-out;?\s*\}/,
    'Must define .turnover-rush-pulse animation with 2.2s infinite ease-in-out');

  // Verify purple turnover badge and status classes
  assert.match(css, /\.turnover-badge-purple/, 'Must define .turnover-badge-purple');
  assert.match(css, /\.room-card\.status-clean/, 'Must define .status-clean color coding');
  assert.match(css, /\.room-card\.status-dirty/, 'Must define .status-dirty color coding');
  assert.match(css, /\.room-card\.status-occupied/, 'Must define .status-occupied color coding');
  assert.match(css, /\.room-card\.status-out_of_order/, 'Must define .status-out_of_order color coding');
});

// =============================================================================
// 2. ROOM MATRIX GRID ENGINE: room-map.js
// =============================================================================
test('Phase 4: renderRoomMatrixCard correctly formats 4-color status system and back-to-back rush', async () => {
  const env = setupMockEnv();
  try {
    const { renderRoomMatrixCard, normalizeRoomStatus } = await import('../js/staff/room-map.js');

    // 1. Status normalization
    assert.equal(normalizeRoomStatus('clean'), 'clean');
    assert.equal(normalizeRoomStatus('vacant_clean'), 'clean');
    assert.equal(normalizeRoomStatus('available'), 'clean');
    assert.equal(normalizeRoomStatus('dirty'), 'dirty');
    assert.equal(normalizeRoomStatus('vacant_dirty'), 'dirty');
    assert.equal(normalizeRoomStatus('cleaning'), 'dirty');
    assert.equal(normalizeRoomStatus('occupied'), 'occupied');
    assert.equal(normalizeRoomStatus('booked'), 'occupied');
    assert.equal(normalizeRoomStatus('out_of_order'), 'out_of_order');
    assert.equal(normalizeRoomStatus('out-of-order'), 'out_of_order');

    // 2. Standard Clean Room (Green)
    const cleanRoom = { id: 101, roomNumber: '101', status: 'vacant_clean', price: 2000000, typeName: 'Deluxe' };
    let actionFired = false;
    const cleanCard = renderRoomMatrixCard(cleanRoom, (r) => { actionFired = true; });
    assert.ok(cleanCard.classList.contains('status-clean'), 'Clean room must have status-clean class');
    assert.match(cleanCard.innerHTML, /Clean/, 'Clean room must display Clean label');
    assert.equal(cleanCard.classList.contains('turnover-rush-pulse'), false, 'Non-back-to-back room must NOT have turnover-rush-pulse');
    assert.doesNotMatch(cleanCard.innerHTML, /\[Back-to-Back Turnover\]/, 'Non-back-to-back room must NOT have turnover badge');

    // Action callback trigger
    cleanCard.click();
    assert.equal(actionFired, true, 'Clicking room card must trigger action callback');

    // 3. Dirty Room (Yellow)
    const dirtyRoom = { id: 102, roomNumber: '102', status: 'vacant_dirty', price: 2000000 };
    const dirtyCard = renderRoomMatrixCard(dirtyRoom);
    assert.ok(dirtyCard.classList.contains('status-dirty'), 'Dirty room must have status-dirty class');
    assert.match(dirtyCard.innerHTML, /Dirty/, 'Dirty room must display Dirty label');

    // 4. Occupied Room (Red)
    const occRoom = { id: 201, roomNumber: '201', status: 'occupied', price: 3500000 };
    const occCard = renderRoomMatrixCard(occRoom);
    assert.ok(occCard.classList.contains('status-occupied'), 'Occupied room must have status-occupied class');
    assert.match(occCard.innerHTML, /Occupied/, 'Occupied room must display Occupied label');

    // 5. Out-of-Order Room (Gray)
    const oooRoom = { id: 301, roomNumber: '301', status: 'out_of_order', price: 5000000 };
    const oooCard = renderRoomMatrixCard(oooRoom);
    assert.ok(oooCard.classList.contains('status-out_of_order'), 'Out-of-order room must have status-out_of_order class');
    assert.match(oooCard.innerHTML, /Out-of-Order/, 'Out-of-order room must display Out-of-Order label');

    // 6. High-Velocity Back-to-Back Turnover Room (Purple Pulse & Timeline)
    const b2bRoom = {
      id: 202,
      roomNumber: '202',
      status: 'occupied',
      price: 4500000,
      isBackToBack: true,
      departingGuestName: 'Mr. David Miller',
      arrivingGuestName: 'Ms. Sophie Laurent'
    };
    const b2bCard = renderRoomMatrixCard(b2bRoom);
    assert.ok(b2bCard.classList.contains('turnover-rush-pulse'), 'Back-to-back room MUST have turnover-rush-pulse class');
    assert.match(b2bCard.innerHTML, /\[Back-to-Back Turnover\]/, 'Must display [Back-to-Back Turnover] purple badge');
    
    // Contiguous timeline verification: Checkout (12:00) -> 90-minute housekeeping (12:00 - 13:30) -> Check-in (14:00)
    assert.match(b2bCard.innerHTML, /Checkout \(12:00\)/, 'Timeline must display Checkout (12:00)');
    assert.match(b2bCard.innerHTML, /90-minute housekeeping window \(12:00 - 13:30\)/, 'Timeline must display 90-minute housekeeping window (12:00 - 13:30)');
    assert.match(b2bCard.innerHTML, /Check-in \(14:00\)/, 'Timeline must display Check-in (14:00)');
    assert.match(b2bCard.innerHTML, /Mr\. David Miller/, 'Must display departing guest name');
    assert.match(b2bCard.innerHTML, /Ms\. Sophie Laurent/, 'Must display arriving guest name');

  } finally {
    env.teardown();
  }
});

// =============================================================================
// 3. KEYBOARD HOTKEYS ACCELERATION: hotkeys.js
// =============================================================================
test('Phase 4: hotkeys.js registers Alt+N, Alt+F, Alt+C, Escape and respects WCAG 2.2 AA text input constraints', async () => {
  const env = setupMockEnv();
  try {
    const { handleStaffHotkey, initStaffHotkeys } = await import('../js/staff/hotkeys.js');

    let walkinInvoked = false;
    let searchFocused = false;
    let checkoutInvoked = false;
    let modalClosed = false;

    // Set up dummy elements in mock DOM
    const walkinBtn = env.getOrCreate('btn-new-walkin', 'button');
    walkinBtn.click = () => { walkinInvoked = true; };

    const searchInput = env.getOrCreate('staff-search-input', 'input');
    searchInput.focus = () => { searchFocused = true; };
    searchInput.select = () => {};

    const checkoutBtn = env.getOrCreate('btn-checkout', 'button');
    checkoutBtn.click = () => { checkoutInvoked = true; };

    const globalModal = env.getOrCreate('global-modal', 'div');
    globalModal.classList.add('active');

    // 1. Test Alt + N (Walk-in)
    const altNResult = handleStaffHotkey({
      altKey: true,
      key: 'n',
      preventDefault() {}
    });
    assert.equal(altNResult, true, 'Alt + N must be handled');
    assert.equal(walkinInvoked, true, 'Alt + N must invoke walkin button click');

    // 2. Test Alt + F (Search Focus)
    const altFResult = handleStaffHotkey({
      altKey: true,
      key: 'f',
      preventDefault() {}
    });
    assert.equal(altFResult, true, 'Alt + F must be handled');
    assert.equal(searchFocused, true, 'Alt + F must focus staff-search-input');

    // 3. Test Alt + C (Checkout)
    const altCResult = handleStaffHotkey({
      altKey: true,
      key: 'c',
      preventDefault() {}
    });
    assert.equal(altCResult, true, 'Alt + C must be handled');
    assert.equal(checkoutInvoked, true, 'Alt + C must invoke checkout action');

    // 4. Test Escape (Dismiss Modals)
    const escResult = handleStaffHotkey({
      altKey: false,
      key: 'Escape',
      preventDefault() {}
    }, { onModalClose: () => { modalClosed = true; } });
    assert.equal(escResult, true, 'Escape must be handled');
    assert.equal(modalClosed, true, 'Escape must invoke onModalClose');

    // 5. Test Non-interference with regular typing in input fields (WCAG 2.2 AA)
    globalThis.document.activeElement = searchInput;
    const plainNResult = handleStaffHotkey({
      altKey: false,
      key: 'n',
      preventDefault() {}
    });
    assert.equal(plainNResult, false, 'Plain "n" key without Alt modifier must NOT trigger walkin when typing in input');

  } finally {
    env.teardown();
  }
});

// =============================================================================
// 4. RFID KEYCARD ENCODER BRIDGE: keycard-bridge.js
// =============================================================================
test('Phase 4: KeycardEncoderBridge interfaces via WebSocket ws://localhost:8089/encoder', async () => {
  // Mock WebSocket
  class MockWebSocket {
    constructor(url) {
      this.url = url;
      this.readyState = 1; // OPEN
      this.sentData = [];
      setTimeout(() => {
        if (typeof this.onopen === 'function') this.onopen();
      }, 5);
    }
    send(str) {
      this.sentData.push(str);
      const data = JSON.parse(str);
      setTimeout(() => {
        if (typeof this.onmessage === 'function') {
          if (data.action === 'ENCODE_KEYCARD') {
            this.onmessage({
              data: JSON.stringify({
                status: 'SUCCESS',
                action: 'ENCODE_KEYCARD',
                room: data.payload.room,
                cardUid: '04A12B89'
              })
            });
          } else if (data.action === 'PRINT_REGISTRATION_CARD') {
            this.onmessage({
              data: JSON.stringify({
                status: 'SUCCESS',
                action: 'PRINT_REGISTRATION_CARD',
                bookingId: data.payload.bookingId,
                jobId: 'PRN-902'
              })
            });
          }
        }
      }, 5);
    }
    close() {
      this.readyState = 3; // CLOSED
    }
  }

  const origWs = globalThis.WebSocket;
  globalThis.WebSocket = MockWebSocket;

  try {
    const { KeycardEncoderBridge } = await import('../js/staff/keycard-bridge.js');

    const bridge = new KeycardEncoderBridge('ws://localhost:8089/encoder');
    assert.equal(bridge.bridgeUrl, 'ws://localhost:8089/encoder', 'Bridge URL must default to ws://localhost:8089/encoder');

    // Test encodeCard
    const encodeRes = await bridge.encodeCard('1402', '2026-10-25T12:00:00Z', 'GUEST');
    assert.equal(encodeRes.status, 'SUCCESS');
    assert.equal(encodeRes.cardUid, '04A12B89');
    assert.equal(encodeRes.room, '1402');

    // Test printRegistrationCard
    const printRes = await bridge.printRegistrationCard('BK-8812', '1402', { fullName: 'Valued Guest' });
    assert.equal(printRes.status, 'SUCCESS');
    assert.equal(printRes.jobId, 'PRN-902');

    // Test completeCheckInProcess
    const fullProcess = await bridge.completeCheckInProcess({
      bookingId: 'BK-8812',
      roomNumber: '1402',
      checkOutDate: '2026-10-25T12:00:00Z',
      keyLevel: 'VIP',
      guestData: { fullName: 'Senator Montgomery', vipTier: 'Platinum' }
    });
    assert.equal(fullProcess.status, 'SUCCESS');
    assert.equal(fullProcess.success, true);
    assert.equal(fullProcess.encodeResult.cardUid, '04A12B89');
    assert.equal(fullProcess.printResult.jobId, 'PRN-902');

    bridge.disconnect();
    assert.equal(bridge.socket, null);

  } finally {
    globalThis.WebSocket = origWs;
  }
});

// =============================================================================
// 5. STATIC SECURITY & COMPLIANCE: staff.html and Anti-405 Defense
// =============================================================================
test('Phase 4: staff.html conforms to WCAG 2.2 AA contrast, Anti-405 onsubmit, and Session Harmonization', () => {
  const staffHtmlPath = path.join(webUiDir, 'staff.html');
  assert.ok(fs.existsSync(staffHtmlPath), 'web-ui/staff.html must exist');
  const html = fs.readFileSync(staffHtmlPath, 'utf8');

  // 1. Dark theme & high contrast (#0b0f17)
  assert.match(html, /#0b0f17/, 'staff.html must reference #0b0f17 dark theme');

  // 2. Anti-405 Defense: ALL forms in staff.html must have onsubmit="return false;"
  const formMatches = html.match(/<form[^>]*>/gi) || [];
  assert.ok(formMatches.length >= 2, 'staff.html must have forms for operations (walkin, keycard, etc.)');
  formMatches.forEach(formTag => {
    assert.match(formTag, /onsubmit=["']return false;?["']/i, `All forms must include onsubmit="return false;": ${formTag}`);
  });

  // 3. Session Harmonization keys
  assert.match(html, /staff_token/, 'Must handle staff_token');
  assert.match(html, /auth_token/, 'Must handle auth_token');
  assert.match(html, /token/, 'Must handle token');
  assert.match(html, /hotel_auth_session/, 'Must handle hotel_auth_session');
  assert.match(html, /STAFF|RECEPTIONIST/, 'Must support STAFF or RECEPTIONIST roles');

  // 4. Hotkeys integration in staff.html
  assert.match(html, /btn-new-walkin/, 'Must have btn-new-walkin for Alt+N');
  assert.match(html, /staff-search-input/, 'Must have staff-search-input for Alt+F');
  assert.match(html, /btn-checkout/, 'Must have btn-checkout for Alt+C');
  assert.match(html, /Alt\+N/, 'Must show keyboard shortcut badge for Alt+N');
  assert.match(html, /Alt\+F/, 'Must show keyboard shortcut badge for Alt+F');
  assert.match(html, /Alt\+C/, 'Must show keyboard shortcut badge for Alt+C');

  // 5. Room Matrix Grid container
  assert.match(html, /id=["']room-matrix-container["']/, 'Must have room-matrix-container');

  // 6. Check Anti-405 Defense in room-map.js
  const roomMapJs = fs.readFileSync(path.join(webUiDir, 'js/staff/room-map.js'), 'utf8');
  const mapForms = roomMapJs.match(/<form[^>]*>/gi) || [];
  mapForms.forEach(formTag => {
    assert.match(formTag, /onsubmit=["']return false;?["']/i, `room-map.js forms must have onsubmit="return false;": ${formTag}`);
  });
});
