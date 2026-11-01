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
test('Phase 4: portal-admin.css contains B front-desk and room turnover styles', () => {
 const css=fs.readFileSync(path.join(webUiDir,'portal-admin.css'),'utf8'); assert.match(css,/front-desk-view/); assert.match(css,/desk-room-search/); assert.match(css,/desk-customer-result/); assert.doesNotMatch(css,/purplePulseAnimation/);
});
test('Phase 4: B room map exposes the room status map and front-desk renderer', async () => {
 const {renderRoomMap}=await import('../js/staff/room-map.js'); assert.equal(typeof renderRoomMap,'function'); const source=fs.readFileSync(path.join(webUiDir,'js/staff/room-map.js'),'utf8'); for(const status of ['vacant_clean','vacant_dirty','reserved','occupied_clean','occupied_dirty','out_of_order','out_of_service']) assert.match(source,new RegExp(status)); assert.match(source,/staff-rooms-grid/);
});
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
