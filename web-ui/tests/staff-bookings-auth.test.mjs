import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { api } from '../public/api.js';
import { renderBookings } from '../public/js/staff/bookings.js';

const tick = () => new Promise(resolve => setImmediate(resolve));
async function until(predicate) {
  for (let i = 0; i < 50; i++) {
    if (predicate()) return;
    await tick();
  }
  assert.fail('Expected staff booking error state was not rendered');
}

function fixture(t) {
  const dom = new JSDOM('<main></main>', { url: 'http://localhost' });
  const previous = {
    window: globalThis.window,
    document: globalThis.document,
    lucide: globalThis.lucide,
    get: api.get
  };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  globalThis.lucide = dom.window.lucide = { createIcons() {} };
  dom.window.setInterval = () => 1;
  dom.window.clearInterval = () => {};
  api.get = previous.get;
  t.after(() => {
    api.get = previous.get;
    globalThis.window = previous.window;
    globalThis.document = previous.document;
    globalThis.lucide = previous.lucide;
    dom.window.close();
  });
  return { root: dom.window.document.querySelector('main'), get: id => dom.window.document.getElementById(id) };
}

test('staff booking list replaces raw authorization details with sign-in guidance', async t => {
  const f = fixture(t);
  api.get = async () => {
    await Promise.resolve();
    const error = new Error('Missing or malformed Authorization header (Bearer <token>)');
    error.status = 401;
    throw error;
  };

  await renderBookings(f.root, { role: 'STAFF' });
  await until(() => f.get('staff-bookings-body')?.textContent.includes('Phiên đăng nhập đã hết hạn'));
  const message = f.get('staff-bookings-body').textContent;
  assert.match(message, /Vui lòng đăng nhập lại/);
  assert.doesNotMatch(message, /Authorization header|Bearer <token>/);
});

test('staff booking request can finish after auth recovery removed its view', async t => {
  const f = fixture(t);
  api.get = async () => {
    await Promise.resolve();
    f.root.replaceChildren();
    const error = new Error('Missing or malformed Authorization header (Bearer <token>)');
    error.status = 401;
    throw error;
  };

  await renderBookings(f.root, { role: 'STAFF' });
  await until(() => f.root.childElementCount === 0);
  await tick();
  assert.equal(f.root.textContent, '');
});
