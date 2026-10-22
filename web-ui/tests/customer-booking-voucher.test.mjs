import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { api } from '../public/api.js';
import { renderDiscovery } from '../public/js/customer/discovery.js';
import { customerBookingUrl } from '../public/js/shared/booking-action.js';

const tick = () => new Promise(resolve => setImmediate(resolve));
async function until(predicate) {
  for (let n = 0; n < 100; n++) { if (predicate()) return; await tick(); }
  assert.fail('Expected UI state was not reached');
}

for (const invalid of [false, true]) {
  test(invalid ? 'invalid voucher stops confirmation and does not submit a booking'
    : 'selected voucher changes server quote and travels in the booking payload', async () => {
    const dom = new JSDOM('<main id="view-container"></main>', {
      url: 'http://localhost/portal.html?view=discovery&voucher=ROOM10'
    });
    const saved = Object.fromEntries(['window','document','sessionStorage','localStorage','CSS'].map(k => [k, globalThis[k]]));
    Object.assign(globalThis, { window: dom.window, document: dom.window.document,
      sessionStorage: dom.window.sessionStorage, localStorage: dom.window.localStorage,
      CSS: { escape: String } });
    dom.window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
    dom.window.HTMLDialogElement.prototype.close = function () { this.open = false; };
    const originalGet = api.get, originalPost = api.post;
    const room = { id: 1, room_type_id: 1, room_number: '101', version: 1,
      capacity: 2, price: 100, room_type_name: 'Standard', availability_status: 'available' };
    const base = { nightly_rates: [{date:'2026-10-22',amount:100}],
      room_subtotal:100, service_charge:5, vat:8, total_amount:113 };
    let submitted, preview;
    api.get = async endpoint => endpoint.startsWith('/rooms/availability') ? [room]
      : endpoint === '/customer-portal/profile' ? {fullName:'Fixture',phone:'0901234567'} : base;
    api.post = async (endpoint, payload) => {
      if (endpoint === '/promotions/validate') {
        preview = payload;
        if (invalid) throw new Error('Voucher is invalid or expired');
        return {discount_amount:10,room_subtotal:90,service_charge:4.5,vat:7.2,total_amount:101.7};
      }
      submitted = payload;
      return {booking_code:'FIXTURE',folio:{balance:101.7}};
    };
    try {
      await renderDiscovery(document.getElementById('view-container'), {token:'fixture',role:'CUSTOMER'});
      assert.equal(document.getElementById('booking-voucher-code').value, 'ROOM10');
      document.querySelector('[data-book]').click();
      await until(() => invalid ? document.getElementById('discovery-feedback').textContent.includes('expired')
        : document.getElementById('booking-dialog').open);
      assert.equal(preview.voucher_code, 'ROOM10');
      assert.equal(preview.room_type_id, 1);
      if (invalid) {
        assert.equal(document.getElementById('booking-dialog').open, false);
        assert.equal(submitted, undefined);
      } else {
        assert.match(document.getElementById('booking-dialog-content').textContent, /ROOM10/);
        const checkbox = document.getElementById('booking-personal-confirmed');
        checkbox.checked = true;
        checkbox.dispatchEvent(new dom.window.Event('change'));
        document.getElementById('confirm-booking').click();
        await until(() => submitted !== undefined);
        assert.equal(submitted.voucher_code, 'ROOM10');
        assert.equal(submitted.quoted_total, 101.7);
      }
    } finally {
      await tick();
      api.get = originalGet; api.post = originalPost;
      Object.assign(globalThis, saved);
      dom.window.close();
    }
  });
}

test('shared booking link carries the selected code without changing the destination', () => {
  const url = new URL(customerBookingUrl('ROOM10'), 'http://localhost/');
  assert.equal(url.searchParams.get('view'), 'discovery');
  assert.equal(url.searchParams.get('voucher'), 'ROOM10');
});
