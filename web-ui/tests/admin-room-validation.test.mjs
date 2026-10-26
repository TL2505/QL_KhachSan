import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';
import { api } from '../public/api.js';
import { renderRooms } from '../public/js/admin/rooms.js?v=3.6.13';
import { renderRoomTypes } from '../public/js/admin/room-types.js?v=1.0.2';

const settle = () => new Promise(resolve => setImmediate(resolve));
const sampleType = {
  id: 4, name: 'QA Standard', description: '', basePrice: 1500000,
  daily_base_price: 1500000, hourly_base_price: 500000,
  hourly_extra_hour_price: 250000, overnight_price: 1100000,
  capacity: 2, imageUrl: null, imageAlt: '', amenities: '', visible: true, roomCount: 0
};

async function setup(t) {
  const dom = new JSDOM('<body><main id="view-container"></main><div id="global-modal"><div id="global-modal-content"></div></div></body>', { url: 'http://localhost/portal.html' });
  const old = {
    window: globalThis.window, document: globalThis.document,
    get: api.get, post: api.post, put: api.put, del: api.delete
  };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  dom.window.closeModal = () => dom.window.document.querySelector('#global-modal')?.classList.remove('active');
  const calls = [];
  api.get = async path => {
    if (path === '/rooms') return [];
    if (path === '/rooms/types') return [sampleType];
    if (path === '/admin/rental-policy') return [sampleType];
    throw new Error(`Unexpected GET ${path}`);
  };
  api.post = async (path, body) => { calls.push({ method: 'POST', path, body }); return {}; };
  api.put = async (path, body) => { calls.push({ method: 'PUT', path, body }); return {}; };
  api.delete = async path => { calls.push({ method: 'DELETE', path }); return {}; };
  t.after(() => {
    api.get = old.get; api.post = old.post; api.put = old.put; api.delete = old.del;
    dom.window.close(); globalThis.window = old.window; globalThis.document = old.document;
  });
  return { dom, root: document.querySelector('#view-container'), modal: document.querySelector('#global-modal-content'), calls };
}

test('room add modal shows inline Vietnamese field errors and never reaches the API when empty', async t => {
  const { dom, root, modal, calls } = await setup(t);
  await renderRooms(root);
  root.querySelector('#btn-add-room-admin').click();
  const form = modal.querySelector('#form-room-admin-action');
  assert.equal(form.noValidate, true);
  modal.querySelector('button[form="form-room-admin-action"][type="submit"]').click();
  await settle();

  assert.deepEqual(calls, []);
  assert.equal(modal.querySelector('#ar-number-validation-error').textContent, 'Vui lòng nhập số phòng.');
  assert.equal(modal.querySelector('#ar-type-validation-error').textContent, 'Vui lòng chọn hạng phòng.');
  assert.equal(modal.querySelector('#ar-price-validation-error').textContent, 'Vui lòng nhập đơn giá.');
  assert.equal(document.activeElement.id, 'ar-number');

  const number = modal.querySelector('#ar-number');
  number.value = 'QA-902';
  number.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
  const type = modal.querySelector('#ar-type');
  type.value = '4';
  type.dispatchEvent(new dom.window.Event('change', { bubbles: true }));
  const price = modal.querySelector('#ar-price');
  price.value = '2500000';
  price.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
  modal.querySelector('button[form="form-room-admin-action"][type="submit"]').click();
  await settle();
  await settle();
  assert.deepEqual(calls[0], {
    method: 'POST', path: '/rooms',
    body: { roomNumber: 'QA-902', roomTypeId: 4, imageUrl: null, price: 2500000, status: 'vacant_dirty' }
  });
});

test('room-type add modal uses the same inline validation and blocks invalid API writes', async t => {
  const { dom, root, modal, calls } = await setup(t);
  await renderRoomTypes(root);
  root.querySelector('#room-type-add').click();
  const form = modal.querySelector('#room-type-form');
  assert.equal(form.noValidate, true);
  form.querySelector('button[type="submit"]').click();
  await settle();

  assert.deepEqual(calls, []);
  assert.equal(modal.querySelector('#rt-name-validation-error').textContent, 'Vui lòng nhập tên hạng phòng.');
  assert.equal(modal.querySelector('#rt-base-price-validation-error').textContent, 'Vui lòng nhập giá ngày tham chiếu.');
  assert.equal(document.activeElement.id, 'rt-name');

  const name = modal.querySelector('#rt-name');
  name.value = 'QA room type';
  name.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
  const capacity = modal.querySelector('#rt-capacity');
  capacity.value = '0';
  capacity.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
  form.querySelector('button[type="submit"]').click();
  await settle();
  assert.equal(modal.querySelector('#rt-capacity-validation-error').textContent, 'Sức chứa phải ít nhất là 1 khách.');
  assert.equal(document.activeElement.id, 'rt-capacity');
  assert.deepEqual(calls, []);
});

test('room validation helper is identical in source and served JavaScript', async () => {
  const source = await readFile(fileURLToPath(new URL('../js/shared/inline-validation.js', import.meta.url)), 'utf8');
  const served = await readFile(fileURLToPath(new URL('../public/js/shared/inline-validation.js', import.meta.url)), 'utf8');
  assert.equal(served, source);
});
