import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { initConciergeChat } from '../public/js/concierge-chat.js';

const settle = () => new Promise(resolve => setImmediate(resolve));
const json = (data, status = 200) => new Response(JSON.stringify({ status: status === 200 ? 'success' : 'error', data }), { status });

function fixture(authenticated = true) {
  const dom = new JSDOM('<button id="quick-chat-btn">Chat</button>', { url: 'http://localhost:3000/' });
  const saved = Object.fromEntries(['window', 'document', 'localStorage', 'Event', 'fetch'].map(key => [key, globalThis[key]]));
  Object.assign(globalThis, { window: dom.window, document: dom.window.document, localStorage: dom.window.localStorage, Event: dom.window.Event });
  if (authenticated) localStorage.setItem('hotel_auth_session', JSON.stringify({ token: 'synthetic-test-token' }));
  initConciergeChat();
  const trigger = document.getElementById('quick-chat-btn');
  trigger.click();
  const root = document.getElementById('aurelia-concierge').shadowRoot;
  return {
    root, trigger,
    async send(text = 'Khách sạn có tiện nghi gì?') {
      root.querySelector('input').value = text;
      root.querySelector('form').dispatchEvent(new dom.window.Event('submit', { cancelable: true }));
      await settle();
    },
    close() { Object.assign(globalThis, saved); dom.window.close(); }
  };
}

test('anonymous visitors keep their question and can open the existing login dialog without sending AI requests', async () => {
  const ui = fixture(false);
  try {
    let calls = 0;
    globalThis.fetch = async () => { calls++; return json({ reply: 'unexpected' }); };
    await ui.send('Giờ nhận phòng?');
    assert.equal(calls, 0);
    assert.equal(ui.root.querySelector('input').value, 'Giờ nhận phòng?');
    assert.equal(ui.root.querySelector('#login').hidden, false);
    let loginOpened = false;
    window.openLoginModal = () => { loginOpened = true; };
    ui.root.querySelector('#login').click();
    assert.equal(loginOpened, true);
    assert.equal(ui.trigger.getAttribute('aria-expanded'), 'false');
  } finally { ui.close(); }
});

test('authenticated chat sends the bearer token, safely renders sources, and retains the session after reopening', async () => {
  const ui = fixture();
  try {
    const calls = [];
    globalThis.fetch = async (url, options) => {
      calls.push({ url, options, body: JSON.parse(options.body) });
      return json({ reply: '<img src=x onerror=alert(1)>', citations: [{ doc: 'Hotel guide', section: 'Tiện nghi' }] });
    };
    await ui.send();
    assert.match(calls[0].url, /\/api\/v1\/ai\/concierge\/chat$/);
    assert.equal(calls[0].options.headers.get('Authorization'), 'Bearer synthetic-test-token');
    assert.equal(ui.root.querySelector('.conversation img'), null);
    assert.match(ui.root.querySelector('.conversation').textContent, /<img src=x/);
    assert.match(ui.root.querySelector('small').textContent, /Hotel guide · Tiện nghi/);
    ui.root.querySelector('#close').click();
    ui.trigger.click();
    assert.match(ui.root.querySelector('.conversation').textContent, /Hotel guide/);
    await ui.send('Dịch vụ nào?');
    assert.equal(calls[0].body.session_id, calls[1].body.session_id);
    ui.root.querySelector('input').dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    assert.equal(ui.root.querySelector('section').hidden, true);
  } finally { ui.close(); }
});

test('a pending response blocks duplicate sends and releases controls on failure', async () => {
  const ui = fixture();
  try {
    let finish;
    let count = 0;
    globalThis.fetch = () => { count++; return new Promise(resolve => { finish = resolve; }); };
    await ui.send();
    await ui.send();
    assert.equal(count, 1);
    assert.equal(ui.root.querySelector('form button').disabled, true);
    finish(json(null, 503));
    await settle();
    assert.equal(ui.root.querySelector('form button').disabled, false);
    assert.equal(ui.root.querySelector('#retry').hidden, false);
    assert.equal(ui.root.querySelector('#escalate').hidden, false);
  } finally { ui.close(); }
});

test('failed AI requests can be escalated with the original question and show a real ticket id', async () => {
  const ui = fixture();
  try {
    const calls = [];
    globalThis.fetch = async (url, options) => {
      calls.push({ url, body: JSON.parse(options.body) });
      return url.endsWith('/escalate') ? json({ ticket_id: 42 }) : json(null, 503);
    };
    await ui.send('Tôi cần hỗ trợ');
    ui.root.querySelector('#escalate').click();
    await settle();
    assert.equal(calls[1].body.reason, 'Tôi cần hỗ trợ');
    assert.equal(calls[0].body.session_id, calls[1].body.session_id);
    assert.match(ui.root.querySelector('.feedback').textContent, /42/);
    assert.equal(ui.root.querySelector('#escalate').hidden, true);
  } finally { ui.close(); }
});

test('automatic escalation recognizes the Java escalate field without creating another ticket', async () => {
  const ui = fixture();
  try {
    globalThis.fetch = async () => json({ reply: 'Đã tiếp nhận yêu cầu', escalate: true, ticket_id: 17 });
    await ui.send();
    assert.match(ui.root.querySelector('.feedback').textContent, /17/);
    assert.equal(ui.root.querySelector('#escalate').hidden, true);
  } finally { ui.close(); }
});

test('rate limits and forbidden responses offer appropriate recovery', async () => {
  for (const status of [429, 403]) {
    const ui = fixture();
    try {
      globalThis.fetch = async () => json(null, status);
      await ui.send();
      assert.match(ui.root.querySelector('.conversation').textContent, status === 429 ? /đợi một phút/ : /chưa thể sử dụng/);
      assert.equal(ui.root.querySelector('#escalate').hidden, true);
      assert.equal(ui.root.querySelector('form button').disabled, false);
    } finally { ui.close(); }
  }
});

test('account changes discard an in-flight response and the previous conversation', async () => {
  const ui = fixture();
  try {
    let finish;
    globalThis.fetch = () => new Promise(resolve => { finish = resolve; });
    await ui.send('private test question');
    localStorage.setItem('hotel_auth_session', JSON.stringify({ token: 'different-synthetic-token' }));
    finish(json({ reply: 'private test answer' }));
    await settle();
    assert.doesNotMatch(ui.root.querySelector('.conversation').textContent, /private test/);
    assert.equal(ui.root.querySelector('form button').disabled, false);
  } finally { ui.close(); }
});

test('expired authentication clears chat and offers login, without a duplicate request', async () => {
  const ui = fixture();
  try {
    let calls = 0;
    globalThis.fetch = async () => { calls++; return json(null, 401); };
    await ui.send('expired session question');
    assert.equal(calls, 1);
    assert.equal(localStorage.getItem('hotel_auth_session'), null);
    assert.equal(ui.root.querySelector('#login').hidden, false);
    assert.doesNotMatch(ui.root.querySelector('.conversation').textContent, /expired session question/);
  } finally { ui.close(); }
});

test('switching accounts cannot escalate the previous account question', async () => {
  const ui = fixture();
  try {
    let calls = 0;
    globalThis.fetch = async () => { calls++; return json(null, 503); };
    await ui.send('previous account question');
    localStorage.setItem('hotel_auth_session', JSON.stringify({ token: 'another-test-token' }));
    ui.root.querySelector('#escalate').click();
    await settle();
    assert.equal(calls, 1);
    assert.doesNotMatch(ui.root.querySelector('.conversation').textContent, /previous account question/);
  } finally { ui.close(); }
});

test('provider credential rejection is separate from guest authentication and preserves the session', async () => {
  const ui = fixture();
  try {
    globalThis.fetch = async () => new Response(JSON.stringify({ status: 'error', message: 'Trợ lý AI chưa sẵn sàng do lỗi kết nối với nhà cung cấp.' }), { status: 422 });
    await ui.send();
    assert.match(ui.root.querySelector('.conversation').textContent, /nhà cung cấp/);
    assert.match(localStorage.getItem('hotel_auth_session'), /synthetic-test-token/);
    assert.equal(ui.root.querySelector('#login').hidden, true);
  } finally { ui.close(); }
});

test('another in-flight question prompts waiting without expiring the session or offering escalation', async () => {
  const ui = fixture();
  try {
    globalThis.fetch = async () => json(null, 409);
    await ui.send();
    assert.match(ui.root.querySelector('.conversation').textContent, /Câu hỏi trước đang được xử lý/);
    assert.equal(ui.root.querySelector('#escalate').hidden, true);
    assert.equal(ui.root.querySelector('#retry').hidden, false);
    assert.match(localStorage.getItem('hotel_auth_session'), /synthetic-test-token/);
  } finally { ui.close(); }
});
