import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { api } from '../public/api.js';
import { renderAdminMembership } from '../public/js/admin/loyalty.js';

const fixtures = () => Array.from({ length: 13 }, (_, i) => ({ id: i + 1, fullName: i === 0 ? 'Đặng Ánh <img src=x onerror=alert(1)>' : `Khách ${i + 1}`, phone: `09000000${i}`, email: `member${i}@test.invalid`, loyaltyPoints: i === 0 ? 200 : i * 100, totalLoyaltyPoints: i === 0 ? 1200 : i * 100 }));
async function setup(t, get = async () => fixtures()) {
    const dom = new JSDOM('<main></main>', { url: 'http://localhost' });
    const previous = { window: globalThis.window, document: globalThis.document, get: api.get, post: api.post };
    globalThis.window = dom.window; globalThis.document = dom.window.document;
    dom.window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
    dom.window.HTMLDialogElement.prototype.close = function () { this.open = false; this.dispatchEvent(new dom.window.Event('close')); };
    api.get = get;
    t.after(() => { api.get = previous.get; api.post = previous.post; globalThis.window = previous.window; globalThis.document = previous.document; dom.window.close(); });
    const container = document.querySelector('main');
    await renderAdminMembership(container);
    return { dom, container, $: selector => container.querySelector(selector), event: type => new dom.window.Event(type, { bubbles: true, cancelable: true }) };
}
const settled = () => new Promise(resolve => setImmediate(resolve));

test('members can be paginated, searched without accents and filtered by lifetime tier', async t => {
    const { $, event } = await setup(t);
    assert.equal($('#membership-body').children.length, 10);
    assert.equal($('.membership-heading h1').textContent, 'Quản lý thành viên');
    $('#pagination-membership [aria-label="Trang sau"]').click();
    assert.match($('#pagination-membership').textContent, /Hiển thị 11–13 trên 13 mục/);
    const size = $('#pagination-membership select');
    size.value = '5'; size.dispatchEvent(event('change'));
    assert.equal($('#membership-body').children.length, 5);
    assert.match($('#pagination-membership').textContent, /Trang 1 \/ 3/);
    $('#membership-search').value = 'dang anh'; $('#membership-search').dispatchEvent(event('input'));
    assert.equal($('#membership-body').children.length, 1);
    assert.equal($('#membership-body img'), null, 'Member names must be escaped');
    assert.match($('#membership-body').textContent, /Gold/);
    $('#membership-tier').value = 'Silver'; $('#membership-tier').dispatchEvent(event('change'));
    assert.match($('#membership-body').textContent, /Không tìm thấy/);
});

test('adjustment rejects overdrafts and sends one request while saving, then updates balance', async t => {
    const { $, event } = await setup(t);
    $('[data-adjust="1"]').click();
    const form = $('#membership-adjust-form');
    assert.equal($('#membership-dialog [data-close]').getAttribute('aria-label'), 'Đóng');
    assert.equal($('#membership-dialog').querySelectorAll('[data-close]').length, 1);
    assert.equal($('#membership-dialog .modal-action'), null);
    assert.equal($('.membership-points-sign').hidden, true);
    form.elements.direction.value = '-1'; form.elements.direction.dispatchEvent(event('change'));
    assert.equal($('.membership-points-sign').hidden, false, 'Deduction displays its minus sign even before entering an amount');
    form.elements.points.value = '201'; form.elements.reason.value = 'Điều chỉnh';
    let calls = 0, finish;
    api.post = async (url, payload) => { calls++; assert.equal(url, '/loyalty/adjust'); assert.deepEqual(payload, { customerId: 1, pointsChange: -100, reason: 'Điều chỉnh' }); return new Promise(resolve => { finish = resolve; }); };
    form.dispatchEvent(event('submit')); assert.equal(calls, 0);
    assert.match($('#membership-adjust-error').textContent, /vượt quá/);
    form.elements.points.value = '100'; form.dispatchEvent(event('submit')); form.dispatchEvent(event('submit'));
    assert.equal(calls, 1); assert.equal(form.querySelector('button').disabled, true);
    finish({ loyaltyPoints: 100, totalLoyaltyPoints: 1200, loyaltyLevel: 'Gold' }); await settled();
    assert.equal($('#membership-dialog').open, false);
    assert.match($('#membership-notice').textContent, /Đã trừ 100 điểm/);
    assert.match($('[data-adjust="1"]').closest('tr').textContent, /Gold/);
    assert.equal($('[data-adjust="1"]').closest('tr').querySelector('.membership-points').textContent, '100');
});

test('history escapes content and displays signed earn/redeem entries', async t => {
    const { $, event } = await setup(t, async url => url.includes('/history/') ? [{ type: 'earn', pointsChange: 500, description: '<img src=x>', createdAt: '2026-10-22' }, { type: 'redeem', pointsChange: -100, description: 'Đổi điểm' }, ...Array.from({ length: 10 }, () => ({ type: 'earn', pointsChange: 10 }))] : fixtures());
    $('[data-history="1"]').click(); await settled();
    assert.match($('#membership-history').textContent, /\+500/);
    assert.match($('#membership-history').textContent, /−100/);
    assert.equal($('#membership-history img'), null);
    assert.equal($('#membership-dialog').querySelectorAll('[data-close]').length, 1);
    $('#pagination-membership-history [aria-label="Trang sau"]').click();
    assert.equal($('#membership-history tbody').children.length, 2);
    const size = $('#pagination-membership-history select'); size.value = '5'; size.dispatchEvent(event('change'));
    assert.equal($('#membership-history tbody').children.length, 5);
    assert.match($('#pagination-membership-history').textContent, /Trang 1 \/ 3/);
});

test('loading failure offers retry and save failure keeps form editable', async t => {
    let fail = true;
    const { $, event } = await setup(t, async () => { if (fail) throw new Error('Mất kết nối'); return fixtures(); });
    assert.match($('#membership-body').textContent, /Mất kết nối/);
    fail = false; $('#membership-retry').click(); await settled();
    $('[data-adjust="1"]').click();
    api.post = async () => { throw new Error('Không thể lưu'); };
    const form = $('#membership-adjust-form'); form.elements.points.value = '10'; form.elements.reason.value = 'Test';
    form.dispatchEvent(event('submit')); await settled();
    assert.equal($('#membership-dialog').open, true);
    assert.match($('#membership-adjust-error').textContent, /Không thể lưu/);
    assert.equal(form.querySelector('button').disabled, false);
    form.elements.points.value = '10'; form.elements.direction.value = '-1'; form.elements.direction.dispatchEvent(event('change'));
    assert.equal($('.membership-points-sign').hidden, false);
    form.elements.direction.value = '1'; form.elements.direction.dispatchEvent(event('change'));
    assert.equal($('.membership-points-sign').hidden, true);
    assert.equal(form.elements.points.value, '10', 'Switching direction preserves the entered magnitude');
});
