import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { enableInlineValidation } from '../public/js/shared/inline-validation.js';

test('inline form validation localizes errors, focuses the first invalid field and blocks submit', () => {
  const dom = new JSDOM(`<!doctype html><form id="form">
    <div class="form-group"><label for="room">Số phòng *</label><input id="room" required pattern="[A-Za-z0-9][A-Za-z0-9-]{0,19}"></div>
    <div class="form-group"><label for="type">Hạng phòng *</label><select id="type" required><option value="">Chọn hạng phòng</option><option value="4">QA Standard</option></select></div>
    <div class="form-group"><label for="price">Giá *</label><input id="price" type="number" min="1" max="99999999.99" step="0.01" required></div>
  </form>`, { url: 'http://localhost/portal.html' });
  const { document, Event } = dom.window;
  const form = document.querySelector('#form');
  enableInlineValidation(form, {
    room: { valueMissing: 'Vui lòng nhập số phòng.', patternMismatch: 'Số phòng chỉ gồm chữ cái, chữ số và dấu gạch nối.' },
    type: { valueMissing: 'Vui lòng chọn hạng phòng.' },
    price: { valueMissing: 'Vui lòng nhập đơn giá.', rangeUnderflow: 'Đơn giá phải lớn hơn 0.', rangeOverflow: 'Đơn giá vượt quá giới hạn.', stepMismatch: 'Đơn giá chỉ hỗ trợ tối đa 2 chữ số thập phân.', badInput: 'Vui lòng nhập đơn giá hợp lệ.' }
  });
  let submitCount = 0;
  form.addEventListener('submit', () => { submitCount += 1; });

  const invalidSubmit = new Event('submit', { bubbles: true, cancelable: true });
  form.dispatchEvent(invalidSubmit);

  assert.equal(form.noValidate, true);
  assert.equal(invalidSubmit.defaultPrevented, true);
  assert.equal(submitCount, 0);
  assert.equal(document.activeElement.id, 'room');
  assert.equal(document.querySelector('#room-validation-error').textContent, 'Vui lòng nhập số phòng.');
  assert.equal(document.querySelector('#type-validation-error').textContent, 'Vui lòng chọn hạng phòng.');
  assert.equal(document.querySelector('#price-validation-error').textContent, 'Vui lòng nhập đơn giá.');
  assert.equal(document.querySelector('#room').getAttribute('aria-invalid'), 'true');

  const room = document.querySelector('#room');
  room.value = 'QA-801';
  room.dispatchEvent(new Event('input', { bubbles: true }));
  const type = document.querySelector('#type');
  type.value = '4';
  type.dispatchEvent(new Event('change', { bubbles: true }));
  const price = document.querySelector('#price');
  price.value = '500000';
  price.dispatchEvent(new Event('input', { bubbles: true }));

  for (const id of ['room', 'type', 'price']) {
    assert.equal(document.querySelector(`#${id}-validation-error`).hidden, true);
    assert.equal(document.querySelector(`#${id}`).hasAttribute('aria-invalid'), false);
  }
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  assert.equal(submitCount, 1);
  dom.window.close();
});

test('inline form validation reports localized pattern and price-boundary messages', () => {
  const dom = new JSDOM('<form><div class="form-group"><input id="room" required pattern="[A-Za-z0-9][A-Za-z0-9-]{0,19}"></div><div class="form-group"><input id="price" type="number" min="1" max="99999999.99" step="0.01" required></div></form>');
  const { document, Event } = dom.window;
  const form = document.querySelector('form');
  enableInlineValidation(form, {
    room: { patternMismatch: 'Số phòng không đúng định dạng.' },
    price: { rangeUnderflow: 'Đơn giá phải lớn hơn 0.', rangeOverflow: 'Đơn giá vượt quá giới hạn.', stepMismatch: 'Đơn giá chỉ hỗ trợ tối đa 2 chữ số thập phân.' }
  });
  const room = document.querySelector('#room');
  room.value = '-101';
  room.dispatchEvent(new Event('input', { bubbles: true }));
  const price = document.querySelector('#price');
  price.value = '0';
  price.dispatchEvent(new Event('input', { bubbles: true }));
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  assert.equal(document.querySelector('#room-validation-error').textContent, 'Số phòng không đúng định dạng.');
  assert.equal(document.querySelector('#price-validation-error').textContent, 'Đơn giá phải lớn hơn 0.');
  dom.window.close();
});
